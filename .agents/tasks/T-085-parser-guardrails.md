# T-085 — Add Length Validation to BLE Packet Parsers (B-012)

**Phase**: Phase 3 — Data Trust & Protocol Reliability
**Blocked by**: nothing
**Estimated diff**: ~80 lines across 2 files

## Goal
Add defensive length checks to all BLE packet parsers to prevent `ArrayIndexOutOfBounds` crashes when device sends malformed or truncated packets. This is a critical data-trust hardening task.

## Context
The device occasionally sends incomplete BLE packets due to MTU fragmentation issues or firmware glitches. Current parsers assume full packet structure and can crash with `ArrayIndexOutOfBounds` exceptions. We need to validate packet length before accessing array indices.

## Read these files first
- `app/src/main/java/com/sbtracker/ble/BlePacketParser.kt` — main parser + device cmd handlers
- `app/src/main/java/com/sbtracker/ble/BleConstants.kt` — packet format specs (EXPECTED_LENGTH_* constants)
- Check git history for any crash logs mentioning ArrayIndexOutOfBounds

## Change only these files
- `app/src/main/java/com/sbtracker/ble/BlePacketParser.kt`

## Steps

### 1. Define Expected Packet Lengths in BleConstants.kt (if not present)
Verify or add these constants:
```kotlin
object BleConstants {
    const val PACKET_LENGTH_STATUS = 20  // CMD 0x04 device_status packet
    const val PACKET_LENGTH_DEVICE_INFO = 16  // CMD 0x05 device identity
    const val PACKET_LENGTH_DEVICE_TIME = 10  // CMD 0x20 time sync
    // ... etc for each packet type
}
```

### 2. Wrap All Parser Functions with Length Guards
In `BlePacketParser.kt`, modify each parsing function:

**Example for `parseStatusPacket()` (CMD 0x04):**
```kotlin
fun parseStatusPacket(data: ByteArray): DeviceStatus? {
    // Guard: validate minimum length
    if (data.size < BleConstants.PACKET_LENGTH_STATUS) {
        Log.w(TAG, "Status packet too short: ${data.size} bytes, expected ${BleConstants.PACKET_LENGTH_STATUS}")
        return null  // or return default/error status
    }

    return try {
        DeviceStatus(
            heaterActive = (data[4].toInt() and 0xFF) != 0,
            targetTempC = data[5].toInt() and 0xFF,
            currentTempC = data[6].toInt() and 0xFF,
            batteryPercent = data[7].toInt() and 0xFF,
            // ... etc
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing status packet", e)
        null
    }
}
```

**Example for `parseDeviceInfo()` (CMD 0x05):**
```kotlin
fun parseDeviceInfo(data: ByteArray): DeviceInfo? {
    if (data.size < BleConstants.PACKET_LENGTH_DEVICE_INFO) {
        Log.w(TAG, "Device info packet too short: ${data.size} bytes, expected ${BleConstants.PACKET_LENGTH_DEVICE_INFO}")
        return null
    }

    return try {
        val serial = String(data.copyOfRange(4, 10), Charsets.UTF_8).trim('\u0000')
        val deviceType = data[10].toInt() and 0xFF
        val colorCode = data[11].toInt() and 0xFF

        DeviceInfo(
            serial = serial,
            type = deviceType,
            color = colorCode
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing device info", e)
        null
    }
}
```

### 3. Apply Guards to All Parsers
Identify all parsing functions (grep for `fun parse*` in BlePacketParser.kt) and add:
- Minimum length check before any array access
- Return `null` or default value on short packet
- Log warning with actual vs. expected length
- Wrap in try-catch for malformed data

**Checklist:**
- [ ] `parseStatusPacket()` — CMD 0x04
- [ ] `parseDeviceInfo()` — CMD 0x05
- [ ] `parseTimeSync()` — CMD 0x20 (if exists)
- [ ] Any other CMD handlers

### 4. Test with Truncated Data
Add a manual test (can be added to T-083 test suite):
```kotlin
@Test
fun parseStatusPacket_withTruncatedData_returnsNull() {
    val truncatedData = byteArrayOf(0x04, 0x01, 0x02)  // Too short
    val result = BlePacketParser.parseStatusPacket(truncatedData)
    assertTrue(result == null)
}
```

### 5. Run Tests & Verify No Crashes
```bash
./gradlew test
./gradlew assembleDebug
```

## Acceptance criteria
- [ ] All packet parser functions validate minimum length before accessing indices
- [ ] Short packets logged (warn level) with actual vs. expected size
- [ ] No function can throw `ArrayIndexOutOfBounds` for any input
- [ ] All parsers return `null` or safe default for malformed packets
- [ ] `./gradlew assembleDebug` passes
- [ ] `./gradlew test` passes (including new truncation tests)

## Do NOT
- Modify the normal packet parsing logic (only add guards)
- Change expected packet sizes (those are device protocol constants)
- Silently ignore truncated packets — always log at warn level
- Attempt to "fix" truncated packets by padding with zeros
