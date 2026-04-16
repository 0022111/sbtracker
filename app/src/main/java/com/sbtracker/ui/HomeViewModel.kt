@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.sbtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.App
import com.sbtracker.ble.BleManager
import com.sbtracker.ble.BleService
import com.sbtracker.core.Sessions
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * Binds the foreground [BleService] to the UI. No business logic lives here —
 * it just exposes service state and forwards user actions.
 */
class HomeViewModel : ViewModel() {

    private val db = App.get().db

    private val _service = MutableStateFlow<BleService?>(null)

    /** Service connection state, or Disconnected if not yet bound. */
    val connection: StateFlow<BleManager.State> = _service
        .flatMapLatest { it?.state ?: flowOf(BleManager.State.Disconnected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BleManager.State.Disconnected)

    /** Latest status row for the connected device, or null. */
    val status: StateFlow<DeviceStatus?> = connection
        .map { (it as? BleManager.State.Connected)?.address }
        .distinctUntilChanged()
        .flatMapLatest { addr -> if (addr == null) flowOf(null) else db.status().observeLatest(addr) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Session list derived from the log every time the log changes. */
    val sessions: StateFlow<List<Session>> = connection
        .map { (it as? BleManager.State.Connected)?.address }
        .distinctUntilChanged()
        .flatMapLatest { addr ->
            if (addr == null) flowOf(emptyList()) else db.sessions().observe(addr)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onBind(service: BleService) { _service.value = service }
    fun onUnbind() { _service.value = null }

    fun scan()        = _service.value?.startScan()
    fun disconnect()  = _service.value?.disconnect()
    fun igniteOn()    = _service.value?.setHeater(true)
    fun igniteOff()   = _service.value?.setHeater(false)
    fun setTemp(c: Int) = _service.value?.setTemperature(c)

    /** Admin: rebuild the session index from the raw log. Useful after algo tweaks. */
    fun rebuildSessions() = viewModelScope.launch(Dispatchers.IO) {
        for (address in db.status().getAllAddresses()) {
            val log = db.status().getAll(address)
            db.sessions().clear(address)
            for (s in Sessions.derive(log)) db.sessions().insert(s)
        }
    }
}
