package com.sobrietree.android.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {

    private val pass = "correct horse battery staple".toCharArray()
    private val payload = """{"entries":[{"id":"1","name":"IPA","abv":6.5}]}""".toByteArray()

    @Test
    fun `round trips the payload`() {
        val blob = BackupCrypto.encrypt(payload, pass)
        assertArrayEquals(payload, BackupCrypto.decrypt(blob, pass))
    }

    @Test
    fun `the plaintext never appears in the blob`() {
        val blob = BackupCrypto.encrypt(payload, pass)
        val text = String(blob, Charsets.ISO_8859_1)
        assertFalse(text.contains("IPA"))
        assertFalse(text.contains("entries"))
    }

    @Test
    fun `wrong passphrase is rejected`() {
        val blob = BackupCrypto.encrypt(payload, pass)
        try {
            BackupCrypto.decrypt(blob, "wrong passphrase".toCharArray())
            fail("expected a BackupFormatException")
        } catch (e: BackupCrypto.BackupFormatException) {
            // Deliberately the same message as a damaged file.
            assertTrue(e.message!!.contains("Wrong passphrase"))
        }
    }

    @Test
    fun `an empty passphrase is refused rather than producing a fake-secure file`() {
        // Settings blocks this at the field, but a backup that looks encrypted
        // and isn't would be worse than no backup, so the codec refuses too.
        try {
            BackupCrypto.encrypt(payload, charArrayOf())
            fail("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("can't be empty"))
        }
    }

    @Test
    fun `tampering with the ciphertext is detected`() {
        val blob = BackupCrypto.encrypt(payload, pass)
        // Flip a bit well past the header, inside the ciphertext.
        blob[blob.size - 5] = (blob[blob.size - 5].toInt() xor 0x01).toByte()
        try {
            BackupCrypto.decrypt(blob, pass)
            fail("GCM should reject a modified ciphertext")
        } catch (_: BackupCrypto.BackupFormatException) {
        }
    }

    @Test
    fun `tampering with the salt is detected`() {
        val blob = BackupCrypto.encrypt(payload, pass)
        blob[BackupCrypto.MAGIC.size + 2] = (blob[BackupCrypto.MAGIC.size + 2].toInt() xor 0x01).toByte()
        try {
            BackupCrypto.decrypt(blob, pass)
            fail("a changed salt derives a different key and must fail")
        } catch (_: BackupCrypto.BackupFormatException) {
        }
    }

    @Test
    fun `a foreign file is rejected before any crypto runs`() {
        val notABackup = "id,name,abv\n1,IPA,6.5\n".toByteArray()
        try {
            BackupCrypto.decrypt(notABackup, pass)
            fail("expected a BackupFormatException")
        } catch (e: BackupCrypto.BackupFormatException) {
            assertTrue(e.message!!.contains("Not a SobrieTree backup"))
        }
    }

    @Test
    fun `a truncated file is rejected rather than crashing`() {
        val blob = BackupCrypto.encrypt(payload, pass)
        try {
            BackupCrypto.decrypt(blob.copyOfRange(0, BackupCrypto.MAGIC.size + 3), pass)
            fail("expected a BackupFormatException")
        } catch (_: BackupCrypto.BackupFormatException) {
        }
    }

    @Test
    fun `a future version is refused with a useful message`() {
        val blob = BackupCrypto.encrypt(payload, pass)
        blob[BackupCrypto.MAGIC.size] = 99
        try {
            BackupCrypto.decrypt(blob, pass)
            fail("expected a BackupFormatException")
        } catch (e: BackupCrypto.BackupFormatException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test
    fun `two backups of the same data share no bytes past the magic`() {
        // Fresh salt and IV each time, so identical input must not produce
        // identical output - otherwise a watcher of a synced folder could tell
        // that nothing changed.
        val a = BackupCrypto.encrypt(payload, pass)
        val b = BackupCrypto.encrypt(payload, pass)
        assertEquals(a.size, b.size)
        val header = BackupCrypto.MAGIC.size + 1
        assertNotEquals(
            String(a.copyOfRange(header, a.size), Charsets.ISO_8859_1),
            String(b.copyOfRange(header, b.size), Charsets.ISO_8859_1)
        )
        // ...and both still open.
        assertArrayEquals(payload, BackupCrypto.decrypt(a, pass))
        assertArrayEquals(payload, BackupCrypto.decrypt(b, pass))
    }

    @Test
    fun `a large backup survives the round trip`() {
        // ~2MB, well past anything a real log produces.
        val big = ByteArray(2_000_000) { (it % 251).toByte() }
        val blob = BackupCrypto.encrypt(big, pass)
        assertArrayEquals(big, BackupCrypto.decrypt(blob, pass))
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `pbkdf2 matches the published SHA-256 test vectors`() {
        // Standard PBKDF2-HMAC-SHA256 vectors for P="password", S="salt".
        // The hand-rolled implementation exists because the JCE algorithm name
        // is API 26+, so it has to be checked against something external.
        val p = "password".toCharArray()
        val s = "salt".toByteArray()
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(BackupCrypto.pbkdf2(p, s, 1, 32))
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            hex(BackupCrypto.pbkdf2(p, s, 2, 32))
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(BackupCrypto.pbkdf2(p, s, 4096, 32))
        )
    }

    @Test
    fun `pbkdf2 spans multiple blocks correctly`() {
        // 40 bytes needs two SHA-256 blocks, exercising the block-index path.
        val long = BackupCrypto.pbkdf2("passwordPASSWORDpassword".toCharArray(), "saltSALTsalt".toByteArray(), 4096, 40)
        assertEquals(40, long.size)
        assertEquals(
            "eb9c34ef458ef0a5c86b5a186116d2c24753561141c70506d9be65e0f8942cc9a49c770cd6618b4f",
            hex(long)
        )
    }

    @Test
    fun `non-ascii passphrases work`() {
        val unicode = "träume größer 🌱".toCharArray()
        val blob = BackupCrypto.encrypt(payload, unicode)
        assertArrayEquals(payload, BackupCrypto.decrypt(blob, unicode))
        try {
            BackupCrypto.decrypt(blob, "traume grosser".toCharArray())
            fail("expected a BackupFormatException")
        } catch (_: BackupCrypto.BackupFormatException) {
        }
    }

    @Test
    fun `the file signature survives app renames`() {
        // A backup is the only route data has between installs, and the app has
        // changed identity twice. Renaming this constant would strand every
        // backup written under an older name - so it is pinned by value, not
        // just by round-trip, which would pass happily after a rename.
        assertEquals("TTBK", String(BackupCrypto.MAGIC, Charsets.US_ASCII))
        assertEquals(1.toByte(), BackupCrypto.VERSION)
    }

    @Test
    fun `key stretching is not accidentally weakened`() {
        // A regression here would silently downgrade every future backup.
        assertTrue(BackupCrypto.ITERATIONS >= 210_000)
    }
}
