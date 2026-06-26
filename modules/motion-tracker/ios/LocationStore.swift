import Foundation
import SQLite3

class LocationStore {
    static let shared = LocationStore()
    private var db: OpaquePointer?

    private init() {
        let fileURL = try! FileManager.default
            .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("LocationTracker.db")

        if sqlite3_open(fileURL.path, &db) == SQLITE_OK {
            let createTable = """
                CREATE TABLE IF NOT EXISTS locations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    latitude REAL,
                    longitude REAL,
                    timestamp INTEGER,
                    source TEXT
                )
            """
            sqlite3_exec(db, createTable, nil, nil, nil)
        } else {
            NSLog("MotionTracker: Failed to open database")
        }
    }

    deinit {
        sqlite3_close(db)
    }

    func insertLocation(latitude: Double, longitude: Double, timestamp: Int64, source: String) {
        let sql = "INSERT INTO locations (latitude, longitude, timestamp, source) VALUES (?, ?, ?, ?)"
        var stmt: OpaquePointer?

        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_double(stmt, 1, latitude)
            sqlite3_bind_double(stmt, 2, longitude)
            sqlite3_bind_int64(stmt, 3, timestamp)
            sqlite3_bind_text(stmt, 4, (source as NSString).utf8String, -1, nil)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    func getAllLocations() -> [[String: Any]] {
        var locations: [[String: Any]] = []
        let sql = "SELECT * FROM locations ORDER BY timestamp DESC"
        var stmt: OpaquePointer?

        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            while sqlite3_step(stmt) == SQLITE_ROW {
                let id = Int(sqlite3_column_int(stmt, 0))
                let lat = sqlite3_column_double(stmt, 1)
                let lng = sqlite3_column_double(stmt, 2)
                let ts = Int(sqlite3_column_int64(stmt, 3))
                let src = String(cString: sqlite3_column_text(stmt, 4))

                locations.append([
                    "id": id,
                    "latitude": lat,
                    "longitude": lng,
                    "timestamp": ts,
                    "source": src
                ])
            }
        }
        sqlite3_finalize(stmt)
        return locations
    }
}
