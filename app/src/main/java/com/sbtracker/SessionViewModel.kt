package com.sbtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.sbtracker.data.ProgramRepository
import com.sbtracker.data.SessionProgram
import org.json.JSONArray

/**
 * Owns device write commands: heater control, temperature, boost, brightness,
 * and hardware toggle operations. Delegates BLE writes to [BleManager].
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val programRepository: ProgramRepository
) : ViewModel() {

    val programs: StateFlow<List<SessionProgram>> = programRepository.programs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultPrograms: StateFlow<List<SessionProgram>> = programs
        .map { it.filter { p -> p.isDefault } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customPrograms: StateFlow<List<SessionProgram>> = programs
        .map { it.filter { p -> !p.isDefault } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Program execution state
    private val _selectedProgram = MutableStateFlow<SessionProgram?>(null)
    val selectedProgram: StateFlow<SessionProgram?> = _selectedProgram.asStateFlow()

    private var boostJob: Job? = null

    fun selectProgram(program: SessionProgram?) {
        _selectedProgram.value = program
    }

    fun saveProgram(program: SessionProgram) {
        viewModelScope.launch { programRepository.saveProgram(program) }
    }

    fun deleteProgram(id: Long) {
        viewModelScope.launch { programRepository.deleteProgram(id) }
    }

    // BoostStep data class and parsing helper for program execution
    private data class BoostStep(val offsetSec: Int, val boostC: Int)

    private fun parseBoostSteps(json: String): List<BoostStep> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                BoostStep(obj.optInt("offsetSec", 0), obj.optInt("boostC", 0))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun startSessionWithProgram(program: SessionProgram) {
        boostJob?.cancel()

        // 1. Set target temperature (mode 1 = standard heater mode)
        setTemp(program.targetTempC, 1)

        // 2. Start the heater
        setHeater(true)

        // 3. Schedule boost steps
        val steps = parseBoostSteps(program.boostStepsJson)
        if (steps.isEmpty()) return

        boostJob = viewModelScope.launch {
            for (step in steps) {
                if (step.offsetSec > 0) delay(step.offsetSec * 1000L)
                if (!isActive) break // cancelled — do not fire further commands
                if (step.boostC > 0) {
                    setBoost(step.boostC)
                }
            }
        }
    }

    fun cancelBoostSchedule() {
        boostJob?.cancel()
        boostJob = null
    }

    fun startSession(targetTemp: Int) {
        val clamped = targetTemp.coerceIn(40, 230)
        bleManager.sendWrite(BlePacket.buildStatusWrite(
            BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE,
            tempC = clamped, mode = 1
        ))
    }

    fun setHeater(on: Boolean) = bleManager.sendWrite(BlePacket.buildSetHeater(on))

    fun setHeaterMode(mode: Int, currentTarget: Int) {
        bleManager.sendWrite(BlePacket.buildStatusWrite(
            BleConstants.WRITE_HEATER_STATE or BleConstants.WRITE_TEMPERATURE,
            tempC = currentTarget, mode = mode
        ))
    }

    fun setTemp(tempC: Int, currentMode: Int) {
        val clamped = tempC.coerceIn(40, 230)
        val mask = BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE
        bleManager.sendWrite(BlePacket.buildStatusWrite(mask, tempC = clamped, mode = currentMode))
    }

    fun setBoost(offsetC: Int) =
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_BOOST, boostC = offsetC.coerceAtLeast(0)))

    fun setSuperBoost(offsetC: Int) =
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_SUPERBOOST, superBoostC = offsetC.coerceAtLeast(0)))

    fun setAutoShutdown(seconds: Int) =
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_AUTO_SHUTDOWN, shutdownSec = seconds.coerceAtLeast(0)))

    fun toggleVibrationLevel(currentLevel: Int) {
        val next = if (currentLevel > 0) 0 else 1
        bleManager.sendWrite(BlePacket.buildSetVibrationLevel(next))
    }

    fun toggleBoostVisualization(currentViz: Boolean, deviceType: String) {
        bleManager.sendWrite(BlePacket.buildSetBoostVisualization(!currentViz, deviceType))
    }

    fun toggleChargeCurrentOpt(current: Boolean) {
        bleManager.sendWrite(BlePacket.buildSetChargeCurrentOpt(!current))
    }

    fun toggleChargeVoltageLimit(current: Boolean) {
        bleManager.sendWrite(BlePacket.buildSetChargeVoltageLimit(!current))
    }

    fun togglePermanentBle(current: Boolean) {
        bleManager.sendWrite(BlePacket.buildSetPermanentBle(!current))
    }

    fun toggleBoostTimeout(currentTimeout: Int) {
        val next = if (currentTimeout > 0) 0 else 1
        bleManager.sendWrite(BlePacket.buildSetBoostTimeout(next))
    }

    fun setBrightness(level: Int) {
        bleManager.sendWrite(BlePacket.buildSetBrightness(level.coerceIn(1, 9)))
    }

    fun setVibrationLevel(level: Int) =
        bleManager.sendWrite(BlePacket.buildSetVibrationLevel(level.coerceIn(0, 100)))

    fun setBoostTimeout(seconds: Int) =
        bleManager.sendWrite(BlePacket.buildSetBoostTimeout(seconds.coerceIn(0, 255)))
}
