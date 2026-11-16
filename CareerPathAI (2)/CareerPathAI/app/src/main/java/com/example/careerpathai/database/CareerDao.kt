
package com.example.careerpathai.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CareerDao {

    @Insert
    suspend fun insertAssessment(
        assessment: CareerAssessment
    )

    @Query("SELECT * FROM career_assessments ORDER BY createdAt DESC")
    fun getAllAssessments(): Flow<List<CareerAssessment>>

    @Query("DELETE FROM career_assessments")
    suspend fun deleteAllAssessments()
}
