package expo.modules.motiontracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocationDbHelper(context: Context) : SQLiteOpenHelper(context, "LocationTracker.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL,
                longitude REAL,
                timestamp INTEGER,
                source TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS locations")
        onCreate(db)
    }

    // Called by your TrackingService
    fun insertLocation(lat: Double, lng: Double, timestamp: Long, source:String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("latitude", lat)
            put("longitude", lng)
            put("timestamp", timestamp)
            put("source", source)
        }
        db.insert("locations", null, values)
    }

    // Called by your React Native JS Bridge
    fun getAllLocations(): List<Map<String, Any>> {
        val locationList = mutableListOf<Map<String, Any>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM locations ORDER BY timestamp DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val map = mapOf(
                    "id" to cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    "latitude" to cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    "longitude" to cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    "timestamp" to cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    "source" to cursor.getString(cursor.getColumnIndexOrThrow("source"))
                )
                locationList.add(map)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return locationList
    }
}