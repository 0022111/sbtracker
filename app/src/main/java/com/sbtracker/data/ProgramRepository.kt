package com.sbtracker.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ProgramRepository @Inject constructor(
    private val dao: SessionProgramDao
) {
    val programs: Flow<List<SessionProgram>> = dao.observeAll()

    /** Seed default presets if the table is empty. Call once at app start. */
    suspend fun seedDefaultsIfNeeded() {
        if (dao.count() > 0) return
        val defaults = listOf(
            SessionProgram(
                name = "Terpene Optimization",
                targetTempC = 170,
                boostStepsJson = "[{\"offsetSec\":0,\"boostC\":0},{\"offsetSec\":60,\"boostC\":5},{\"offsetSec\":120,\"boostC\":10}]",
                isDefault = true,
                stayOnAtEnd = false
            ),
            SessionProgram(
                name = "Even Step",
                targetTempC = 185,
                boostStepsJson = "[{\"offsetSec\":0,\"boostC\":0},{\"offsetSec\":90,\"boostC\":5},{\"offsetSec\":180,\"boostC\":10}]",
                isDefault = true,
                stayOnAtEnd = false
            ),
            SessionProgram(
                name = "Full Heat Max Rip",
                targetTempC = 210,
                boostStepsJson = "[{\"offsetSec\":0,\"boostC\":10}]",
                isDefault = true,
                stayOnAtEnd = false
            )
        )
        defaults.forEach { dao.insertOrUpdate(it) }
    }

    suspend fun saveProgram(program: SessionProgram): Long = dao.insertOrUpdate(program)

    suspend fun deleteProgram(id: Long) = dao.deleteUserProgram(id)

    suspend fun getById(id: Long): SessionProgram? = dao.getById(id)
}
