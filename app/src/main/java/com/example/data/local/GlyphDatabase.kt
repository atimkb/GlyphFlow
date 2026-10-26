package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GlyphAnimationEntity::class, TriggerRuleEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(GlyphTypeConverters::class)
abstract class GlyphDatabase : RoomDatabase() {

    abstract fun glyphDao(): GlyphDao

    companion object {
        @Volatile
        private var INSTANCE: GlyphDatabase? = null

        fun getInstance(context: Context): GlyphDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GlyphDatabase::class.java,
                    "glyphflow_np3_clean_v7.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
