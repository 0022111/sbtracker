2026-03-27 — Fix Bluetooth Connectivity (T-BLUETOOTH-FIX)
- **Fixed** app failing to find devices by broadening scan name filters.
- **Improved** connection stability by adding stabilization delays during service discovery.
- **Fixed** initialization handshake by prioritizing CMD 0x1D (Status) before CMD 0x02 (Reset).
- **Hardened** GATT lifecycle by removing unnecessary and brittle MTU 512 requests.
