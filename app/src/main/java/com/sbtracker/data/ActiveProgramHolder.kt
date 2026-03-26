package com.sbtracker.data

import com.sbtracker.data.SessionProgram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges SessionViewModel (which selects/executes a program) and MainViewModel
 * (which commits the session and its metadata).
 *
 * This @Singleton avoids a circular dependency between ViewModels while
 * ensuring the selected program state persists across fragment transactions.
 */
@Singleton
class ActiveProgramHolder @Inject constructor() {
    private val _activeProgram = MutableStateFlow<SessionProgram?>(null)
    val activeProgram: StateFlow<SessionProgram?> = _activeProgram.asStateFlow()

    fun set(program: SessionProgram?) {
        _activeProgram.value = program
    }

    /**
     * Consume the active program and clear the holder.
     * Returns the program that was active, if any.
     */
    fun consume(): SessionProgram? {
        val p = _activeProgram.value
        _activeProgram.value = null
        return p
    }
}
