@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.sbtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.App
import com.sbtracker.ble.BleManager
import com.sbtracker.ble.BleService
import com.sbtracker.core.Charge
import com.sbtracker.core.Charges
import com.sbtracker.core.Hits
import com.sbtracker.core.Sessions
import com.sbtracker.core.Summaries
import com.sbtracker.core.Summary
import com.sbtracker.data.DeviceInfo
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.ExtendedData
import com.sbtracker.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Navigation — hand-rolled to avoid a nav-compose dep for four destinations. */
sealed interface Screen {
    data object Now      : Screen
    data object History  : Screen
    data object Settings : Screen
    data class  Detail(val sessionId: Long) : Screen
}

/**
 * Single ViewModel backing every screen. It binds to [BleService] and exposes
 * derived state as [StateFlow]s. No business logic here — the logic is in
 * [Sessions] / [Hits] / [Summaries] / [Analytics] / [Charges].
 */
class AppViewModel : ViewModel() {

    private val db    = App.get().db
    private val prefs = App.get().prefs

    // ── Service binding ─────────────────────────────────────────────────────

    private val _service = MutableStateFlow<BleService?>(null)

    fun onBind(service: BleService) { _service.value = service }
    fun onUnbind() { _service.value = null }

    // ── Navigation ──────────────────────────────────────────────────────────

    private val _screen = MutableStateFlow<Screen>(Screen.Now)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun navigate(to: Screen) { _screen.value = to }
    fun back() {
        if (_screen.value is Screen.Detail) _screen.value = Screen.History
    }

    // ── Connection / address ────────────────────────────────────────────────

    val connection: StateFlow<BleManager.State> = _service
        .flatMapLatest { it?.state ?: flowOf(BleManager.State.Disconnected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BleManager.State.Disconnected)

    private val address: StateFlow<String?> = connection
        .map { (it as? BleManager.State.Connected)?.address }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Preferences ─────────────────────────────────────────────────────────

    val useCelsius: StateFlow<Boolean> = prefs.useCelsius
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setUseCelsius(on: Boolean) = viewModelScope.launch { prefs.setUseCelsius(on) }

    // ── God-log window + device info ────────────────────────────────────────

    /** Last 24 hours of log for the connected device — source of live derivations. */
    private val recentLog: StateFlow<List<DeviceStatus>> = address
        .flatMapLatest { addr ->
            if (addr == null) flowOf(emptyList())
            else db.status().observeSince(addr, System.currentTimeMillis() - LIVE_WINDOW_MS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latestStatus: StateFlow<DeviceStatus?> = recentLog
        .map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val info: StateFlow<DeviceInfo?> = address
        .flatMapLatest { addr ->
            if (addr == null) flowOf(null)
            else db.info().observeAll().map { list -> list.firstOrNull { it.deviceAddress == addr } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val extended: StateFlow<ExtendedData?> = address
        .flatMapLatest { addr -> if (addr == null) flowOf(null) else db.extended().observe(addr) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Live session + hits ─────────────────────────────────────────────────

    /** Provisional summary of the currently-running session, if heater is on. */
    val liveSession: StateFlow<Summary?> = recentLog
        .map { log ->
            val last = log.lastOrNull() ?: return@map null
            if (last.heaterMode == 0) return@map null
            val sessions = Sessions.derive(log)
            val current  = sessions.lastOrNull() ?: return@map null
            val slice    = log.filter { it.timestampMs in current.startTimeMs..current.endTimeMs }
            Summaries.of(current, slice)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chargeEta: StateFlow<Int?> = recentLog
        .map { log ->
            val last = log.lastOrNull() ?: return@map null
            if (!last.isCharging) return@map null
            val target = if (last.batteryLevel >= 80) 100 else 80
            Charges.etaMinutes(log, last.batteryLevel, target)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── History ─────────────────────────────────────────────────────────────

    val sessions: StateFlow<List<Session>> = address
        .flatMapLatest { addr -> if (addr == null) flowOf(emptyList()) else db.sessions().observe(addr) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Summaries for the visible history — recomputed on any session change. */
    val summaries: StateFlow<List<Summary>> = combine(sessions, address) { list, addr -> list to addr }
        .flatMapLatest { (list, addr) ->
            kotlinx.coroutines.flow.flow {
                emit(if (addr == null) emptyList() else summarizeAll(list, addr))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun summarizeAll(list: List<Session>, addr: String): List<Summary> =
        list.map { s ->
            val slice = db.status().getRange(addr, s.startTimeMs, s.endTimeMs)
            Summaries.of(s, slice)
        }

    // ── Detail ──────────────────────────────────────────────────────────────

    data class Detail(
        val session: Session,
        val summary: Summary,
        val log:     List<DeviceStatus>,
        val hits:    List<com.sbtracker.core.Hit>,
    )

    private val _detail = MutableStateFlow<Detail?>(null)
    val detail: StateFlow<Detail?> = _detail.asStateFlow()

    fun openDetail(sessionId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val addr = address.value ?: return@launch
        val s    = sessions.value.firstOrNull { it.id == sessionId } ?: return@launch
        val log  = db.status().getRange(addr, s.startTimeMs, s.endTimeMs)
        val hits = Hits.detect(log)
        val sum  = Summaries.of(s, log)
        _detail.value = Detail(s, sum, log, hits)
        navigate(Screen.Detail(sessionId))
    }

    fun closeDetail() {
        _detail.value = null
        back()
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    fun scan()         = _service.value?.startScan()
    fun disconnect()   = _service.value?.disconnect()
    fun heatOn()       = _service.value?.setHeater(true)
    fun heatOff()      = _service.value?.setHeater(false)
    fun setTemp(c: Int) = _service.value?.setTemperature(c)

    /** Re-derive sessions from the full log, inserting any we don't already have. */
    fun rebuildSessions() = viewModelScope.launch(Dispatchers.IO) {
        for (addr in db.status().getAllAddresses()) {
            val log = db.status().getAll(addr)
            val derived = Sessions.derive(log)
            for (s in derived) {
                if (db.sessions().findNear(addr, s.startTimeMs) == null) db.sessions().insert(s)
            }
        }
    }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) {
        val addr = address.value ?: return@launch
        db.sessions().clear(addr)
        db.status().clear(addr)
    }

    // ── Charges view ────────────────────────────────────────────────────────

    val charges: StateFlow<List<Charge>> = recentLog
        .map { Charges.derive(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        private const val LIVE_WINDOW_MS: Long = 24L * 60 * 60 * 1000
    }
}
