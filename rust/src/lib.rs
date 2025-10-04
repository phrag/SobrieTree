use std::sync::{Mutex, OnceLock};
use rusqlite::Connection;
use serde::{Deserialize, Serialize};
use chrono::{Utc, NaiveDate};
use uuid::Uuid;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;
// use std::fmt; // not used
use jni::objects::{JClass, JString};
use jni::sys::{jdouble, jstring as jni_jstring};
use jni::JNIEnv;

#[derive(Debug, thiserror::Error)]
pub enum BrewLogError {
    #[error("Database error: {0}")]
    DatabaseError(String),
    #[error("Invalid input: {0}")]
    InvalidInput(String),
    #[error("Not found: {0}")]
    NotFound(String),
}

impl From<rusqlite::Error> for BrewLogError {
    fn from(err: rusqlite::Error) -> Self {
        BrewLogError::DatabaseError(err.to_string())
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BeerEntry {
    pub id: String,
    pub name: String,
    pub alcohol_percentage: f64,
    pub volume_ml: f64,
    pub date: String,
    pub notes: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConsumptionGoal {
    pub id: String,
    pub daily_target: f64,
    pub weekly_target: f64,
    pub start_date: String,
    pub end_date: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Baseline {
    pub average_daily_consumption: f64,
    pub average_weekly_consumption: f64,
    pub calculated_date: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProgressStats {
    pub current_daily_average: f64,
    pub current_weekly_average: f64,
    pub reduction_percentage: f64,
    pub period_start: String,
    pub period_end: String,
}

pub struct BrewLog {
    db: Mutex<Connection>,
}

impl BrewLog {
    pub fn new() -> Result<Self, BrewLogError> {
        let conn = Connection::open_in_memory()?;
        let log = BrewLog {
            db: Mutex::new(conn),
        };
        log.init_database()?;
        Ok(log)
    }

    pub fn new_with_path(path: &str) -> Result<Self, BrewLogError> {
        let conn = Connection::open(path)?;
        let log = BrewLog { db: Mutex::new(conn) };
        log.init_database()?;
        Ok(log)
    }

    fn init_database(&self) -> Result<(), BrewLogError> {
        let conn = self.db.lock().unwrap();
        
        conn.execute(
            "CREATE TABLE IF NOT EXISTS beer_entries (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                alcohol_percentage REAL NOT NULL,
                volume_ml REAL NOT NULL,
                date TEXT NOT NULL,
                notes TEXT,
                created_at TEXT NOT NULL
            )",
            [],
        )?;

        conn.execute(
            "CREATE TABLE IF NOT EXISTS consumption_goals (
                id TEXT PRIMARY KEY,
                daily_target REAL NOT NULL,
                weekly_target REAL NOT NULL,
                start_date TEXT NOT NULL,
                end_date TEXT NOT NULL,
                created_at TEXT NOT NULL
            )",
            [],
        )?;

        Ok(())
    }

    pub fn add_beer_entry(
        &self,
        name: String,
        alcohol_percentage: f64,
        volume_ml: f64,
        notes: String,
    ) -> Result<(), BrewLogError> {
        if name.is_empty() {
            return Err(BrewLogError::InvalidInput("Name cannot be empty".to_string()));
        }
        if alcohol_percentage < 0.0 || alcohol_percentage > 100.0 {
            return Err(BrewLogError::InvalidInput("Alcohol percentage must be between 0 and 100".to_string()));
        }
        if volume_ml <= 0.0 {
            return Err(BrewLogError::InvalidInput("Volume must be positive".to_string()));
        }

        let conn = self.db.lock().unwrap();
        let id = Uuid::new_v4().to_string();
        let now = Utc::now().to_rfc3339();
        let today = Utc::now().date_naive().to_string();

        conn.execute(
            "INSERT INTO beer_entries (id, name, alcohol_percentage, volume_ml, date, notes, created_at) 
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            (&id, &name, &alcohol_percentage, &volume_ml, &today, &notes, &now),
        )?;

        Ok(())
    }

    pub fn get_beer_entries(&self, start_date: String, end_date: String) -> Result<Vec<BeerEntry>, BrewLogError> {
        let conn = self.db.lock().unwrap();
        
        let mut stmt = conn.prepare(
            "SELECT id, name, alcohol_percentage, volume_ml, date, notes 
             FROM beer_entries 
             WHERE date BETWEEN ?1 AND ?2 
             ORDER BY date DESC, created_at DESC"
        )?;

        let entries = stmt.query_map([&start_date, &end_date], |row| {
            Ok(BeerEntry {
                id: row.get(0)?,
                name: row.get(1)?,
                alcohol_percentage: row.get(2)?,
                volume_ml: row.get(3)?,
                date: row.get(4)?,
                notes: row.get(5)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;

        Ok(entries)
    }

    pub fn set_consumption_goal(
        &self,
        daily_target: f64,
        weekly_target: f64,
        start_date: String,
        end_date: String,
    ) -> Result<(), BrewLogError> {
        if daily_target < 0.0 {
            return Err(BrewLogError::InvalidInput("Daily target must be non-negative".to_string()));
        }
        if weekly_target < 0.0 {
            return Err(BrewLogError::InvalidInput("Weekly target must be non-negative".to_string()));
        }

        let conn = self.db.lock().unwrap();
        let id = Uuid::new_v4().to_string();
        let now = Utc::now().to_rfc3339();

        // Delete any existing goals
        conn.execute("DELETE FROM consumption_goals", [])?;

        conn.execute(
            "INSERT INTO consumption_goals (id, daily_target, weekly_target, start_date, end_date, created_at) 
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            (&id, &daily_target, &weekly_target, &start_date, &end_date, &now),
        )?;

        Ok(())
    }

    pub fn get_current_goal(&self) -> Result<ConsumptionGoal, BrewLogError> {
        let conn = self.db.lock().unwrap();
        
        let mut stmt = conn.prepare(
            "SELECT id, daily_target, weekly_target, start_date, end_date 
             FROM consumption_goals 
             ORDER BY created_at DESC 
             LIMIT 1"
        )?;

        let goal = stmt.query_row([], |row| {
            Ok(ConsumptionGoal {
                id: row.get(0)?,
                daily_target: row.get(1)?,
                weekly_target: row.get(2)?,
                start_date: row.get(3)?,
                end_date: row.get(4)?,
            })
        })?;

        Ok(goal)
    }

    pub fn calculate_baseline(&self, start_date: String, end_date: String) -> Result<Baseline, BrewLogError> {
        let entries = self.get_beer_entries(start_date.clone(), end_date.clone())?;
        
        if entries.is_empty() {
            return Err(BrewLogError::NotFound("No entries found for baseline calculation".to_string()));
        }

        let total_volume: f64 = entries.iter().map(|e| e.volume_ml).sum();
        let days = entries.len() as f64; // Simplified - in reality you'd calculate actual days
        
        let average_daily = total_volume / days;
        let average_weekly = average_daily * 7.0;

        Ok(Baseline {
            average_daily_consumption: average_daily,
            average_weekly_consumption: average_weekly,
            calculated_date: Utc::now().to_rfc3339(),
        })
    }

    pub fn get_progress_stats(&self, period_start: String, period_end: String) -> Result<ProgressStats, BrewLogError> {
        let current_entries = self.get_beer_entries(period_start.clone(), period_end.clone())?;
        
        if current_entries.is_empty() {
            return Err(BrewLogError::NotFound("No entries found for progress calculation".to_string()));
        }

        let total_volume: f64 = current_entries.iter().map(|e| e.volume_ml).sum();
        let days = current_entries.len() as f64; // Simplified calculation
        
        let current_daily_average = total_volume / days;
        let current_weekly_average = current_daily_average * 7.0;

        // For now, we'll use a simple reduction calculation
        // In a real app, you'd compare against the baseline
        let reduction_percentage = 0.0; // Placeholder

        Ok(ProgressStats {
            current_daily_average,
            current_weekly_average,
            reduction_percentage,
            period_start,
            period_end,
        })
    }

    pub fn get_daily_consumption(&self, date: String) -> Result<f64, BrewLogError> {
        let entries = self.get_beer_entries(date.clone(), date)?;
        let total_volume: f64 = entries.iter().map(|e| e.volume_ml).sum();
        Ok(total_volume)
    }

    pub fn get_weekly_consumption(&self, week_start_date: String) -> Result<f64, BrewLogError> {
        // Calculate end of week (7 days later)
        let start_date = NaiveDate::parse_from_str(&week_start_date, "%Y-%m-%d")
            .map_err(|_| BrewLogError::InvalidInput("Invalid date format".to_string()))?;
        let end_date = start_date + chrono::Duration::days(6);
        
        let entries = self.get_beer_entries(week_start_date, end_date.to_string())?;
        let total_volume: f64 = entries.iter().map(|e| e.volume_ml).sum();
        Ok(total_volume)
    }

    pub fn delete_beer_entry(&self, id: String) -> Result<(), BrewLogError> {
        let conn = self.db.lock().unwrap();
        
        let rows_affected = conn.execute("DELETE FROM beer_entries WHERE id = ?1", [&id])?;
        
        if rows_affected == 0 {
            return Err(BrewLogError::NotFound(format!("Beer entry with id {} not found", id)));
        }

        Ok(())
    }

    pub fn update_beer_entry(
        &self,
        id: String,
        name: String,
        alcohol_percentage: f64,
        volume_ml: f64,
        notes: String,
    ) -> Result<(), BrewLogError> {
        if name.is_empty() {
            return Err(BrewLogError::InvalidInput("Name cannot be empty".to_string()));
        }
        if alcohol_percentage < 0.0 || alcohol_percentage > 100.0 {
            return Err(BrewLogError::InvalidInput("Alcohol percentage must be between 0 and 100".to_string()));
        }
        if volume_ml <= 0.0 {
            return Err(BrewLogError::InvalidInput("Volume must be positive".to_string()));
        }

        let conn = self.db.lock().unwrap();
        
        let rows_affected = conn.execute(
            "UPDATE beer_entries 
             SET name = ?1, alcohol_percentage = ?2, volume_ml = ?3, notes = ?4 
             WHERE id = ?5",
            (&name, &alcohol_percentage, &volume_ml, &notes, &id),
        )?;

        if rows_affected == 0 {
            return Err(BrewLogError::NotFound(format!("Beer entry with id {} not found", id)));
        }

        Ok(())
    }

    pub fn update_beer_entry_date(&self, id: String, date: String) -> Result<(), BrewLogError> {
        let conn = self.db.lock().unwrap();
        let rows = conn.execute(
            "UPDATE beer_entries SET date = ?1 WHERE id = ?2",
            (&date, &id),
        )?;
        if rows == 0 {
            return Err(BrewLogError::NotFound(format!("Beer entry with id {} not found", id)));
        }
        Ok(())
    }

    pub fn add_beer_entry_full(
        &self,
        id: Option<String>,
        name: String,
        alcohol_percentage: f64,
        volume_ml: f64,
        date: String,
        notes: String,
    ) -> Result<(), BrewLogError> {
        if name.is_empty() { return Err(BrewLogError::InvalidInput("Name cannot be empty".to_string())); }
        if alcohol_percentage < 0.0 || alcohol_percentage > 100.0 { return Err(BrewLogError::InvalidInput("Alcohol percentage must be between 0 and 100".to_string())); }
        if volume_ml <= 0.0 { return Err(BrewLogError::InvalidInput("Volume must be positive".to_string())); }
        let conn = self.db.lock().unwrap();
        let idv = id.unwrap_or_else(|| Uuid::new_v4().to_string());
        let now = Utc::now().to_rfc3339();
        conn.execute(
            "INSERT OR REPLACE INTO beer_entries (id, name, alcohol_percentage, volume_ml, date, notes, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            (&idv, &name, &alcohol_percentage, &volume_ml, &date, &notes, &now),
        )?;
        Ok(())
    }

    pub fn clear_all_data(&self) -> Result<(), BrewLogError> {
        let conn = self.db.lock().unwrap();
        conn.execute("DELETE FROM beer_entries", [])?;
        conn.execute("DELETE FROM consumption_goals", [])?;
        Ok(())
    }
}

// Global instance for JNI/FFI
static LOG: OnceLock<BrewLog> = OnceLock::new();

// JNI Functions
#[no_mangle]
pub extern "C" fn init_brew_log() -> *mut c_char {
    let msg = match BrewLog::new() {
        Ok(log) => { let _ = LOG.set(log); "OK".to_string() }
        Err(e) => format!("Error: {}", e),
    };
    CString::new(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn init_brew_log_with_path(path: *const c_char) -> *mut c_char {
    if path.is_null() {
        return CString::new("Error: path is null").unwrap().into_raw();
    }
    let path_str = unsafe { CStr::from_ptr(path).to_string_lossy().into_owned() };
    let msg = match BrewLog::new_with_path(&path_str) {
        Ok(log) => { let _ = LOG.set(log); "OK".to_string() }
        Err(e) => format!("Error: {}", e),
    };
    CString::new(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn add_beer_entry(
    name: *const c_char,
    alcohol_percentage: f64,
    volume_ml: f64,
    notes: *const c_char,
) -> *mut c_char {
    unsafe {
        let Some(log) = LOG.get() else { return CString::new("Error: Log not initialized").unwrap().into_raw(); };
        let name_str = CStr::from_ptr(name).to_string_lossy().into_owned();
        let notes_str = CStr::from_ptr(notes).to_string_lossy().into_owned();
        match log.add_beer_entry(name_str, alcohol_percentage, volume_ml, notes_str) {
            Ok(_) => CString::new("OK").unwrap().into_raw(),
            Err(e) => CString::new(format!("Error: {}", e)).unwrap().into_raw()
        }
    }
}

#[no_mangle]
pub extern "C" fn get_daily_consumption(date: *const c_char) -> f64 {
    unsafe {
        let Some(log) = LOG.get() else { return -1.0; };
        let date_str = CStr::from_ptr(date).to_string_lossy().into_owned();
        match log.get_daily_consumption(date_str) { Ok(consumption) => consumption, Err(_) => -1.0 }
    }
}

#[no_mangle]
pub extern "C" fn get_weekly_consumption(week_start_date: *const c_char) -> f64 {
    unsafe {
        let Some(log) = LOG.get() else { return -1.0; };
        let date_str = CStr::from_ptr(week_start_date).to_string_lossy().into_owned();
        match log.get_weekly_consumption(date_str) { Ok(consumption) => consumption, Err(_) => -1.0 }
    }
}

#[no_mangle]
pub extern "C" fn set_consumption_goal(
    daily_target: f64,
    weekly_target: f64,
    start_date: *const c_char,
    end_date: *const c_char,
) -> *mut c_char {
    unsafe {
        let Some(log) = LOG.get() else { return CString::new("Error: Log not initialized").unwrap().into_raw(); };
        let start_date_str = CStr::from_ptr(start_date).to_string_lossy().into_owned();
        let end_date_str = CStr::from_ptr(end_date).to_string_lossy().into_owned();
        match log.set_consumption_goal(daily_target, weekly_target, start_date_str, end_date_str) {
            Ok(_) => CString::new("OK").unwrap().into_raw(),
            Err(e) => CString::new(format!("Error: {}", e)).unwrap().into_raw()
        }
    }
}

#[no_mangle]
pub extern "C" fn get_beer_entries_json(start_date: *const c_char, end_date: *const c_char) -> *mut c_char {
    unsafe {
        let Some(log) = LOG.get() else { return CString::new("Error: Log not initialized").unwrap().into_raw(); };
        let start = CStr::from_ptr(start_date).to_string_lossy().into_owned();
        let end = CStr::from_ptr(end_date).to_string_lossy().into_owned();
        match log.get_beer_entries(start, end) {
            Ok(entries) => match serde_json::to_string(&entries) {
                Ok(s) => CString::new(s).unwrap().into_raw(),
                Err(e) => CString::new(format!("Error: {}", e)).unwrap().into_raw(),
            },
            Err(e) => CString::new(format!("Error: {}", e)).unwrap().into_raw(),
        }
    }
}

#[no_mangle]
pub extern "C" fn delete_beer_entry_jni(id: *const c_char) -> *mut c_char {
    unsafe {
        let Some(log) = LOG.get() else { return CString::new("Error: Log not initialized").unwrap().into_raw(); };
        let id_str = CStr::from_ptr(id).to_string_lossy().into_owned();
        match log.delete_beer_entry(id_str) { Ok(()) => CString::new("OK").unwrap().into_raw(), Err(e) => CString::new(format!("Error: {}", e)).unwrap().into_raw() }
    }
}

#[no_mangle]
pub extern "C" fn update_beer_entry_jni(
    id: *const c_char,
    name: *const c_char,
    alcohol_percentage: f64,
    volume_ml: f64,
    notes: *const c_char,
) -> *mut c_char {
    unsafe {
        let Some(log) = LOG.get() else { return CString::new("Error: Log not initialized").unwrap().into_raw(); };
        let id_str = CStr::from_ptr(id).to_string_lossy().into_owned();
        let name_str = CStr::from_ptr(name).to_string_lossy().into_owned();
        let notes_str = CStr::from_ptr(notes).to_string_lossy().into_owned();
        match log.update_beer_entry(id_str, name_str, alcohol_percentage, volume_ml, notes_str) { Ok(()) => CString::new("OK").unwrap().into_raw(), Err(e) => CString::new(format!("Error: {}", e)).unwrap().into_raw() }
    }
}

// JNI wrappers for Android (class: com.brewlog.android.BrewLogNative)
#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_init_1brew_1log(env: JNIEnv, _cls: JClass) -> jni_jstring {
    let msg = match BrewLog::new() {
        Ok(log) => { let _ = LOG.set(log); "OK".to_string() },
        Err(e) => format!("Error: {}", e),
    };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_init_1brew_1log_1with_1path(mut env: JNIEnv, _cls: JClass, path: JString) -> jni_jstring {
    let path_str: String = env.get_string(&path).unwrap().into();
    let msg = match BrewLog::new_with_path(&path_str) {
        Ok(log) => { let _ = LOG.set(log); "OK".to_string() },
        Err(e) => format!("Error: {}", e),
    };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_add_1beer_1entry(mut env: JNIEnv, _cls: JClass, name: JString, alcohol_percentage: jdouble, volume_ml: jdouble, notes: JString) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        let n: String = env.get_string(&name).unwrap().into();
        let notes_s: String = env.get_string(&notes).unwrap().into();
        match log.add_beer_entry(n, alcohol_percentage as f64, volume_ml as f64, notes_s) {
            Ok(_) => "OK".to_string(),
            Err(e) => format!("Error: {}", e),
        }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_get_1daily_1consumption(mut env: JNIEnv, _cls: JClass, date: JString) -> jdouble {
    if let Some(log) = LOG.get() {
        let d: String = env.get_string(&date).unwrap().into();
        match log.get_daily_consumption(d) { Ok(v) => v as jdouble, Err(_) => -1.0 }
    } else { -1.0 }
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_get_1weekly_1consumption(mut env: JNIEnv, _cls: JClass, week_start_date: JString) -> jdouble {
    if let Some(log) = LOG.get() {
        let d: String = env.get_string(&week_start_date).unwrap().into();
        match log.get_weekly_consumption(d) { Ok(v) => v as jdouble, Err(_) => -1.0 }
    } else { -1.0 }
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_set_1consumption_1goal(mut env: JNIEnv, _cls: JClass, daily_target: jdouble, weekly_target: jdouble, start_date: JString, end_date: JString) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        let s: String = env.get_string(&start_date).unwrap().into();
        let e: String = env.get_string(&end_date).unwrap().into();
        match log.set_consumption_goal(daily_target as f64, weekly_target as f64, s, e) {
            Ok(_) => "OK".to_string(),
            Err(e) => format!("Error: {}", e),
        }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_get_1beer_1entries_1json(mut env: JNIEnv, _cls: JClass, start_date: JString, end_date: JString) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        let s: String = env.get_string(&start_date).unwrap().into();
        let e: String = env.get_string(&end_date).unwrap().into();
        match log.get_beer_entries(s, e) {
            Ok(entries) => serde_json::to_string(&entries).unwrap_or_else(|e| format!("Error: {}", e)),
            Err(e) => format!("Error: {}", e),
        }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_delete_1beer_1entry_1jni(mut env: JNIEnv, _cls: JClass, id: JString) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        let id_s: String = env.get_string(&id).unwrap().into();
        match log.delete_beer_entry(id_s) { Ok(()) => "OK".to_string(), Err(e) => format!("Error: {}", e) }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_update_1beer_1entry_1jni(mut env: JNIEnv, _cls: JClass, id: JString, name: JString, alcohol_percentage: jdouble, volume_ml: jdouble, notes: JString) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        let id_s: String = env.get_string(&id).unwrap().into();
        let name_s: String = env.get_string(&name).unwrap().into();
        let notes_s: String = env.get_string(&notes).unwrap().into();
        match log.update_beer_entry(id_s, name_s, alcohol_percentage as f64, volume_ml as f64, notes_s) { Ok(()) => "OK".to_string(), Err(e) => format!("Error: {}", e) }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_update_1beer_1entry_1date_1jni(mut env: JNIEnv, _cls: JClass, id: JString, date: JString) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        let id_s: String = env.get_string(&id).unwrap().into();
        let date_s: String = env.get_string(&date).unwrap().into();
        match log.update_beer_entry_date(id_s, date_s) { Ok(()) => "OK".to_string(), Err(e) => format!("Error: {}", e) }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_add_1beer_1entry_1full_1jni(mut env: JNIEnv, _cls: JClass, id: JString, name: JString, alcohol_percentage: jdouble, volume_ml: jdouble, date: JString, notes: JString) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        let id_s: String = env.get_string(&id).unwrap().into();
        let name_s: String = env.get_string(&name).unwrap().into();
        let date_s: String = env.get_string(&date).unwrap().into();
        let notes_s: String = env.get_string(&notes).unwrap().into();
        match log.add_beer_entry_full(Some(id_s), name_s, alcohol_percentage as f64, volume_ml as f64, date_s, notes_s) { Ok(()) => "OK".to_string(), Err(e) => format!("Error: {}", e) }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_brewlog_android_BrewLogNative_delete_1all_1data(env: JNIEnv, _cls: JClass) -> jni_jstring {
    let msg = if let Some(log) = LOG.get() {
        match log.clear_all_data() { Ok(()) => "OK".to_string(), Err(e) => format!("Error: {}", e) }
    } else { "Error: Log not initialized".to_string() };
    env.new_string(msg).unwrap().into_raw()
}

// Test functions
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_brew_log_creation() {
        let log = BrewLog::new();
        assert!(log.is_ok());
    }

    #[test]
    fn test_add_beer_entry() {
        let log = BrewLog::new().unwrap();
        
        let result = log.add_beer_entry(
            "Test Beer".to_string(),
            5.0,
            330.0,
            "Test notes".to_string(),
        );
        
        assert!(result.is_ok());
    }

    #[test]
    fn test_add_beer_entry_validation() {
        let log = BrewLog::new().unwrap();
        
        // Test empty name
        let result = log.add_beer_entry(
            "".to_string(),
            5.0,
            330.0,
            "Test notes".to_string(),
        );
        assert!(result.is_err());
        
        // Test invalid alcohol percentage
        let result = log.add_beer_entry(
            "Test Beer".to_string(),
            101.0,
            330.0,
            "Test notes".to_string(),
        );
        assert!(result.is_err());
        
        // Test invalid volume
        let result = log.add_beer_entry(
            "Test Beer".to_string(),
            5.0,
            -1.0,
            "Test notes".to_string(),
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_get_beer_entries() {
        let log = BrewLog::new().unwrap();
        
        // Add a test entry
        log.add_beer_entry(
            "Test Beer".to_string(),
            5.0,
            330.0,
            "Test notes".to_string(),
        ).unwrap();
        
        let today = chrono::Utc::now().date_naive().to_string();
        let entries = log.get_beer_entries(today.clone(), today);
        
        assert!(entries.is_ok());
        let entries = entries.unwrap();
        assert!(!entries.is_empty());
        assert_eq!(entries[0].name, "Test Beer");
        assert_eq!(entries[0].alcohol_percentage, 5.0);
        assert_eq!(entries[0].volume_ml, 330.0);
    }

    #[test]
    fn test_set_and_get_goals() {
        let log = BrewLog::new().unwrap();
        
        let today = chrono::Utc::now().date_naive().to_string();
        let end_date = (chrono::Utc::now().date_naive() + chrono::Duration::days(30)).to_string();
        
        let result = log.set_consumption_goal(500.0, 3500.0, today, end_date);
        assert!(result.is_ok());
        
        let goal = log.get_current_goal();
        assert!(goal.is_ok());
        let goal = goal.unwrap();
        assert_eq!(goal.daily_target, 500.0);
        assert_eq!(goal.weekly_target, 3500.0);
    }

    #[test]
    fn test_daily_consumption() {
        let log = BrewLog::new().unwrap();
        
        // Add a test entry
        log.add_beer_entry(
            "Test Beer".to_string(),
            5.0,
            330.0,
            "Test notes".to_string(),
        ).unwrap();
        
        let today = chrono::Utc::now().date_naive().to_string();
        let consumption = log.get_daily_consumption(today);
        
        assert!(consumption.is_ok());
        assert_eq!(consumption.unwrap(), 330.0);
    }
} 