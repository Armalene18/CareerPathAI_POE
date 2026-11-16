
package com.example.careerpathai.database

import kotlinx.coroutines.flow.Flow

class CareerRepository(
    private val careerDao: CareerDao
) {

    suspend fun saveAssessment(
        assessment: CareerAssessment
    ) {
        careerDao.insertAssessment(assessment)
    }

    fun getAllAssessments(): Flow<List<CareerAssessment>> {
        return careerDao.getAllAssessments()
    }

    suspend fun deleteAllAssessments() {
        careerDao.deleteAllAssessments()
    }
}
