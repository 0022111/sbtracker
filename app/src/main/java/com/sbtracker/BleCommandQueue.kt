package com.sbtracker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serial queue for Android GATT operations.
 *
 * Android's GATT stack silently drops concurrent operations.  Every write and
 * descriptor-write must be funnelled through this queue so that only one
 * operation is in-flight at a time, with a mandatory 50 ms gap between them.
 *
 * Commands are suspend lambdas; they are processed one-at-a-time on the IO
 * dispatcher.  [enqueue] is safe to call from any thread/coroutine.
 */
class BleCommandQueue {

    private val channel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            for (command in channel) {
                try {
                    command()
                } catch (_: Exception) {
                    // Swallow individual command errors so the queue keeps running.
                }
                delay(50L) // Mandatory minimum gap between GATT operations.
            }
        }
    }

    /** Add a suspend command to the back of the queue. Returns immediately. */
    fun enqueue(command: suspend () -> Unit) {
        channel.trySend(command)
    }

    /** Stop processing and release resources. Call when the BLE connection is torn down. */
    fun shutdown() {
        channel.close()
        scope.cancel()
    }
}
