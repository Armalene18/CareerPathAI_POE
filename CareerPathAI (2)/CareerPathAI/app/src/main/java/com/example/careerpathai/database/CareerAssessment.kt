
package com.example.careerpathai.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "career_assessments")
data class CareerAssessment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val answers: String,
    val topCareer: String,
    val createdAt: Long = System.currentTimeMillis()
)
