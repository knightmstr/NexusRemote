# NexusRemote: Complete Project History & Evolution Log

This document records the chronological history, engineering decisions, architectural iterations, and milestone achievements throughout the development of **NexusRemote**.

---

## 📅 Chronology & Milestones

### Milestone 1: Prototype Inception & Baseline Audit
- **Objective**: Assess the initial prototype codebase from `knightmstr/NexusRemote` and identify limitations, hardcoded parameters, and missing production components.
- **Audit Findings**:
  - **Simulations Identified**:
    - ADB RSA authentication in `FireTvManager.kt` was stubbed with mock responses without actual cryptographic signing.
    - App listing defaulted to a static fallback list when `pm list packages -3` timed out.
    - IR blaster presence was hardcoded as a state toggle rather than querying `ConsumerIrManager.hasIrEmitter()`.
  - **Hardcoded Elements**:
    - Target IP address fixed to `192.168.1.150`.
    - Port fixed to `5555`.
    - IR carrier frequency fixed to `38,000 Hz`.
  - **Reusable Components Extracted**:
    - 24-byte ADB packet framing logic (`A_CNXN`, `A_OPEN`, `A_WRTE`, `A_OKAY`, `A_CLSE`).
    - Shell text input sanitization routines.
    - Mathematical timing arrays for NEC and RC-5 IR waveforms.
  - **Missing Systems Cataloged**:
    - Network discovery (mDNS, SSDP).
    - Database persistence (Room).
    - Hardware-backed secrets storage (Android Keystore).
    - Multi-brand protocol support (Roku, Tizen, webOS, BRAVIA, VIDAA, Panasonic).
    - Dynamic capability detection (zero dead buttons).
    - Foldable / tablet dual-pane UI.

---

### Milestone 2: Native Android Foundation & Security Layer
- **Package Created**: `com.nexusremote` under `android/app/src/main/java/com/nexusremote/`.
- **Domain Modeling (`domain/model/DeviceModels.kt`)**:
  - Created `SavedDevice` Room entity with UUID primary keys, brand, model, IP, port, MAC, room assignment, favorites flag, default flag, and timestamp.
  - Defined `DeviceCapability` flags (`POWER`, `DPAD`, `VOLUME`, `TEXT_INJECTION`, `APP_DOCK`, `TOUCHPAD`, `COLOR_BUTTONS`, `MACROS`, `CHANNEL_CONTROL`).
  - Defined `RemoteCommand` sealed types and `ProtocolType` enumerations.
- **Hardware-Backed Keystore Cryptography (`security/KeystoreSecretManager.kt`)**:
  - Implemented AES-256 GCM encryption/decryption using the `AndroidKeyStore` provider for client keys, tokens, and PSKs.
  - Implemented 2048-bit RSA keypair generation in the TEE/Secure Element for genuine ADB `SHA256withRSA` signature generation.
- **Local Persistence (`data/local/NexusDatabase.kt` & `DevicePreferences.kt`)**:
  - Built Room 2.6.1 database with `DeviceDao` supporting Flow queries, room filtering, and transactional default updates.
  - Built Preferences DataStore for default device ID, haptic feedback toggle, hold-to-repeat delay, and touchpad gestures.

---

### Milestone 3: Local Network Discovery Engine (`discovery/NetworkDiscoveryManager.kt`)
- **Subnet Restriction**:
  - Interrogated `WifiManager.connectionInfo.ipAddress` to compute the active `/24` subnet mask (e.g. `192.168.1.0/24`), ensuring scans never touch arbitrary or public IP ranges.
- **Dual-Stack Discovery**:
  - **mDNS (`NsdManager`)**: Scans for `_androidtvremote2._tcp`, `_adb-tls-connect._tcp`, and `_roku._tcp`.
  - **SSDP Multicast (Port 1900)**: Transmits `M-SEARCH * HTTP/1.1` to `239.255.255.250:1900` to detect UPnP MediaRenderers, Roku ECP, and Sony IRCC endpoints.
- **Smart Reconnect & Battery Optimizations**:
  - Implemented lightweight TCP socket reachability checks (500ms timeout) against the saved IP first.
  - Triggered full subnet rediscovery only if the target TV's IP address changed via DHCP.

---

### Milestone 4: Modular Protocol Adapters Behind `DeviceController`
- **Common Interface (`protocols/DeviceController.kt`)**:
  - Declared uniform asynchronous contracts for connection, command dispatch, text injection, app querying, app launching, capabilities, and limitation disclosures.
- **Production Protocol Adapters Developed**:
  1. `AdbProtocolAdapter`: Raw TCP socket on port `5555` with Keystore RSA token challenge signing, stream multiplexing, and shell command execution.
  2. `RokuEcpAdapter`: HTTP REST client targeting port `8060` (`/keypress/`, `/launch/`, `/query/apps`).
  3. `SamsungTizenAdapter`: WebSocket client on port `8001`/`8002` with token-based handshake and Keystore token caching.
  4. `LgWebOsAdapter`: SSAP WebSocket client on port `3000`/`3001` with registration manifest handshake and Keystore client-key persistence.
  5. `SonyBraviaAdapter`: HTTP JSON-RPC and IRCC-IP SOAP client with Keystore-encrypted Pre-Shared Key (PSK).
  6. `HisenseVidaaAdapter`: SSL TCP client on port `5000` with 4-digit PIN authentication challenge.
  7. `PanasonicAdapter`: UPnP SOAP client on port `55000` for Panasonic Viera command execution.
  8. `AndroidTvRemoteAdapter`: Google Android TV Remote v2 over TLS on port `6467`.
  9. `ConsumerIrAdapter`: Consumer IR adapter dynamically verifying `ConsumerIrManager.hasIrEmitter()`, supporting 10 universal brand waveform databases (NEC, RC-5, Sony SIRC).
  10. `ProtocolRegistry`: Factory pattern routing `SavedDevice` instances to the appropriate `DeviceController`.

---

### Milestone 5: UI & Features Engineering
- **Jetpack Compose UI (`ui/NexusMainScreen.kt`)**:
  - **Foldable Dual-Pane Support**: Evaluates window width using `BoxWithConstraints`. Devices with width `>= 600dp` (such as the **HONOR Magic V5** 8.0" unfolded display) render a split-screen console (Navigation/D-Pad on left, Touchpad/Keyboard/Apps/Macros on right). Folded phones render an ergonomic single-column remote.
  - **Zero Dead Buttons Engine**: Each UI button queries `activeController.capabilities.contains(DeviceCapability.XYZ)`. Unsupported controls are either cleanly hidden or display clear explanatory notifications (e.g. informing the user that Samsung Tizen does not support background text injection).
  - **Haptic Engine & Hold-to-Repeat**: Pointer input gestures with 500ms initial repeat delay, 80ms interval, and tactile vibration feedback.
  - **Interactive Keyboard Text Injector**: Direct text injection for search dialogs and URL inputs on supported platforms.
  - **App Quick-Launch Dock**: Horizontal carousel dynamically populated with installed TV applications.

---

### Milestone 6: Web Simulation Companion & Dev Environment
- Integrated a live React/Tailwind companion interface (`src/`) enabling browser-based visualization of the remote control, TV screen simulator, Android Logcat console, and interactive Kotlin Architecture Studio viewer.
- Verified compilation and linting: `compile_applet` and `tsc --noEmit` passing with 0 errors.

---

## 📈 Architecture Decision Records (ADRs)

| ADR ID | Decision Title | Status | Rationale |
|---|---|---|---|
| **ADR-001** | Hardware-Backed Keystore for Credentials | Accepted | Storing tokens, PSKs, or RSA keys in SharedPreferences or Room in plaintext exposes user devices to unauthorized access. All secrets are encrypted with AES-256 GCM or signed via RSA-2048 directly within the TEE/StrongBox. |
| **ADR-002** | Common `DeviceController` Interface | Accepted | Decouples the UI and ViewModel from brand-specific network mechanics. Adding new protocols requires only a new adapter implementation without touching core logic. |
| **ADR-003** | Capability-Driven UI Rendering | Accepted | Eliminates "dead buttons" by inspecting protocol capabilities at runtime. Users never encounter non-functional controls. |
| **ADR-004** | Subnet-Bounded Discovery (`/24`) | Accepted | Prevents network security exceptions on Android 12+ and avoids scanning unauthorized or public subnets. |
| **ADR-005** | No-Assumption IR Hardware Detection | Accepted | The HONOR Magic V5 and modern foldables vary across global hardware revisions. Calling `hasIrEmitter()` prevents runtime crashes on variants without an IR diode. |
