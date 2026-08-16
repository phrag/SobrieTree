package com.treetotal.android.engine

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted backup blobs.
 *
 * TreeTotal keeps everything on the device and takes no network permission, so
 * a backup is the one artefact that can leave it - onto an SD card, a synced
 * folder, wherever the user points it. That makes the file, not the app, the
 * exposed surface, so it is encrypted before it is ever written: whoever ends
 * up holding it learns nothing without the passphrase.
 *
 * AES-256-GCM for confidentiality and tamper-detection in one, with the key
 * stretched from the passphrase by PBKDF2-HMAC-SHA256. A random salt per backup
 * means two backups of the same data under the same passphrase share no key,
 * and a random IV per backup keeps GCM's nonce-reuse rule intact.
 *
 * Layout: MAGIC | version | salt(16) | iv(12) | ciphertext+tag.
 * The header is plaintext by necessity - it is what decryption needs to begin -
 * and reveals nothing beyond "this is a TreeTotal backup".
 */
object BackupCrypto {

    /** File signature, so a wrong file is rejected with a clear error not a crash. */
    val MAGIC = "TTBK".toByteArray(Charsets.US_ASCII)

    const val VERSION: Byte = 1

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /**
     * PBKDF2 iterations. OWASP's floor for PBKDF2-HMAC-SHA256 is 210,000 (2023);
     * this sits at that floor because the work runs on phones, including old
     * ones, and a backup must stay openable on the device that wrote it.
     */
    const val ITERATIONS = 210_000

    /** Thrown for anything a user could plausibly cause: wrong passphrase, wrong file. */
    class BackupFormatException(message: String) : Exception(message)

    val headerSize: Int get() = MAGIC.size + 1 + SALT_BYTES + IV_BYTES

    fun encrypt(plaintext: ByteArray, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.isNotEmpty()) { "A backup passphrase can't be empty." }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(plaintext)

        return MAGIC + byteArrayOf(VERSION) + salt + iv + body
    }

    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        if (blob.size <= headerSize) throw BackupFormatException("Not a TreeTotal backup file.")
        if (!blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupFormatException("Not a TreeTotal backup file.")
        }
        val version = blob[MAGIC.size]
        if (version != VERSION) {
            throw BackupFormatException("This backup was written by a newer version of TreeTotal.")
        }

        var offset = MAGIC.size + 1
        val salt = blob.copyOfRange(offset, offset + SALT_BYTES); offset += SALT_BYTES
        val iv = blob.copyOfRange(offset, offset + IV_BYTES); offset += IV_BYTES
        val body = blob.copyOfRange(offset, blob.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return try {
            cipher.doFinal(body)
        } catch (_: Exception) {
            // GCM fails the same way for a wrong passphrase and for a corrupted
            // file; the user can act on either, and distinguishing them would
            // mean telling an attacker which guess was closer.
            throw BackupFormatException("Wrong passphrase, or the file is damaged.")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec =
        SecretKeySpec(pbkdf2(passphrase, salt, ITERATIONS, KEY_BITS / 8), "AES")

    /**
     * PBKDF2-HMAC-SHA256 (RFC 8018).
     *
     * Written out rather than taken from SecretKeyFactory because the JCE name
     * "PBKDF2WithHmacSHA256" only exists from API 26, and TreeTotal supports 24.
     * Asking for it there throws NoSuchAlgorithmException - the backup would
     * fail on exactly the older, more likely-to-be-lost phone that needs one.
     * HmacSHA256 itself has been available since API 1.
     *
     * Verified against the published test vectors in [BackupCryptoTest].
     */
    internal fun pbkdf2(passphrase: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
        val passwordBytes = toUtf8(passphrase)
        val mac = Mac.getInstance("HmacSHA256")
        try {
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            val hashLen = mac.macLength
            val blocks = (keyBytes + hashLen - 1) / hashLen
            val out = ByteArray(blocks * hashLen)

            for (block in 1..blocks) {
                // U1 = PRF(password, salt || INT_32_BE(block))
                mac.update(salt)
                mac.update((block ushr 24).toByte())
                mac.update((block ushr 16).toByte())
                mac.update((block ushr 8).toByte())
                mac.update(block.toByte())
                var u = mac.doFinal()
                val accumulator = u.copyOf()
                // Ui = PRF(password, Ui-1), XORed into the running total
                for (i in 1 until iterations) {
                    u = mac.doFinal(u)
                    for (j in accumulator.indices) accumulator[j] = (accumulator[j].toInt() xor u[j].toInt()).toByte()
                }
                System.arraycopy(accumulator, 0, out, (block - 1) * hashLen, hashLen)
            }
            return out.copyOf(keyBytes)
        } finally {
            passwordBytes.fill(0)
        }
    }

    /** UTF-8 bytes of a passphrase without ever materialising it as a String. */
    private fun toUtf8(passphrase: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(passphrase))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        // The encoder's backing array may still hold a copy.
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}
