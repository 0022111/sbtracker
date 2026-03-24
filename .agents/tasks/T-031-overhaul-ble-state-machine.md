# T-031 — Overhaul BLE State Machine

**Status**: ready
**Phase**: 2
**Blocked by**: —
**Blocks**: —

---

## Goal
The current BLE logic in `BleManager` is complex and prone to race conditions or silent failures during reconnection. Overhaul the BLE layers to use a formal state machine (e.g., using a Sealed Class for states) for better resilience and predictability.

---

## High-level steps
1.  **Define `BleState` sealed class**: `Scanning`, `Connecting`, `Connected`, `Disconnecting`, `Disconnected(reason)`.
2.  **Refactor `BleManager`**: Use a single `StateFlow<BleState>` as the source of truth.
3.  **Robust Reconnection**: Implement exponential backoff for reconnection attempts.
4.  **Command Queue Overhaul**: Ensure GATT operations are strictly serialized and have clear timeout/retry semantics.
5.  **Service Lifecycle**: Ensure `BleService` correctly handles state transitions and foreground service requirements.

## Target Files
- `app/src/main/java/com/sbtracker/BleManager.kt`
- `app/src/main/java/com/sbtracker/BleService.kt`
- `app/src/main/java/com/sbtracker/BleCommandQueue.kt`

## Do NOT touch
- BLE Packet parsing (`BlePacket.kt`) — protocol itself is stable.
- Database layer.
- UI layer (beyond observing the new state flow).
