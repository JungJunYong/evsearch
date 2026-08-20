package com.evsearch.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedChargerEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedChargerDao(): SavedChargerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v3 -> v4: 상태 지속 시작 시각(충전 경과 시간 표기)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_chargers ADD COLUMN stateSinceAt TEXT DEFAULT NULL")
            }
        }

        // v2 -> v3: 위젯 목록 / 즐겨찾기 목록 분리 플래그 추가
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_chargers ADD COLUMN isWidget INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_chargers ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_chargers ADD COLUMN alertEnabled INTEGER NOT NULL DEFAULT 1")
                // 기존 행은 위젯 등록 + 즐겨찾기 양쪽에 모두 넣어 기능 손실을 막는다.
                db.execSQL("UPDATE saved_chargers SET isWidget = 1, isFavorite = 1")
            }
        }

        // v1 -> v2: customName 컬럼 추가
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_chargers ADD COLUMN customName TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "evsearch_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
