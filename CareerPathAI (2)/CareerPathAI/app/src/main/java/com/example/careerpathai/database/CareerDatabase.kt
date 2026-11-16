
package com.example.careerpathai.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CareerAssessment::class],
    version = 1,
    exportSchema = false
)
abstract class CareerDatabase : RoomDatabase() {

    abstract fun careerDao(): CareerDao

    companion object {

        @Volatile
        private var INSTANCE: CareerDatabase? = null

        fun getDatabase(context: Context): CareerDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CareerDatabase::class.java,
                    "careerpath_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
