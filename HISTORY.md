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
  - **Foldable Dual-Pane Support**: Evaluates window width using `BoxWithConstraints`. Devices with width `>= 600dp` (such as unfolded foldable displays and tablets) render a split-screen console (Navigation/D-Pad on left, Touchpad/Keyboard/Apps/Macros on right). Folded phones render an ergonomic single-column remote.
  - **Zero Dead Buttons Engine**: Each UI button queries `activeController.capabilities.contains(DeviceCapability.XYZ)`. Unsupported controls are either cleanly hidden or display clear explanatory notifications (e.g. informing the user that Samsung Tizen does not support background text injection).
  - **Haptic Engine & Hold-to-Repeat**: Pointer input gestures with 500ms initial repeat delay, 80ms interval, and tactile vibration feedback.
  - **Interactive Keyboard Text Injector**: Direct text injection for search dialogs and URL inputs on supported platforms.
  - **App Quick-Launch Dock**: Horizontal carousel dynamically populated with installed TV applications.

---

### Milestone 6: Web Simulation Companion & Dev Environment
- Integrated a live React/Tailwind companion interface (`src/`) enabling browser-based visualization of the remote control, TV screen simulator, Android Logcat console, and interactive Kotlin Architecture Studio viewer.
- Verified compilation and linting: `compile_applet` and `tsc --noEmit` passing with 0 errors.

---

### Milestone 7: Principal Security Engineer Hardening & OWASP MASVS Compliance
- **Manifest & Backup Controls**:
  - Configured `android:allowBackup="false"`, `android:dataExtractionRules` (`@xml/data_extraction_rules`), and `android:fullBackupContent` (`@xml/backup_rules`) to exclude Room DB (`nexus_remote_db`) and secure vault SharedPreferences (`nexus_remote_secure_vault`, `nexus_device_prefs`).
  - Added `<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="32" />` for subnet discovery on Android 8.0–12 while preserving Android 13+ location-free discovery.
  - Created `@xml/network_security_config` enforcing `cleartextTrafficPermitted="false"` globally.
- **Cryptographic Hardening (`KeystoreSecretManager.kt`)**:
  - Replaced single global AES master key with per-device AES-256-GCM keys (`nexus_key_$deviceId`) enabling true cryptographic erasure in `removeDeviceSecrets(deviceId)`.
  - Bound storage keys (`"${deviceId}::${secretKeyName}"`) as Additional Authenticated Data (`cipher.updateAAD()`) to defeat cross-device ciphertext substitution.
  - Applied `setUnlockedDeviceRequired(true)` (API 28+) and attempted `setIsStrongBoxBacked(true)` with graceful TEE fallback.
  - Formatted `getAdbPublicKeyBytes()` into native 524-byte ADB `RSAPublicKey` struct (modulus, R^2 mod N, -N^-1 mod 2^32) and configured RSA signing with `NONEwithRSA` and pre-built SHA-1 ASN.1 DigestInfo header.
- **TLS Certificate Pinning & Protocol Sanitization (`TofuTrustManager.kt` & Adapters)**:
  - Built Trust-On-First-Use (TOFU) `X509TrustManager` computing SHA-256 leaf certificate fingerprints on initial pair and rejecting connection mismatches.
  - Defaulted Samsung Tizen (WSS 8002) and LG webOS (WSS 3001) to TLS sockets with user consent guards on plaintext fallback.
  - Hardened `AdbProtocolAdapter.kt` by enforcing strict package allowlist regex (`^[a-zA-Z0-9._]+$`) and restricting keyevents strictly to standard `KeyEvent.KEYCODE_*` integers.

---

### Milestone 8: Gradle Build System & GitHub Actions CI/CD Integration
- Added root Gradle project configuration (`android/build.gradle.kts`, `android/settings.gradle.kts`, `android/gradle.properties`, `android/app/proguard-rules.pro`) aligned with AGP `8.5.2`, Kotlin `2.0.21`, and Gradle `8.7`.
- Wired `-PversionCode` and `-PversionName` Gradle properties into `android/app/build.gradle.kts` to support automated versioning from `.github/workflows/android.yml`.
- Added adaptive vector launcher icon resources (`res/mipmap-anydpi-v26/`, `res/drawable/ic_launcher_foreground.xml`, `res/values/colors.xml`) referenced by `AndroidManifest.xml`.
- Fixed API 28 (`Build.VERSION_CODES.P`) guards in `KeystoreSecretManager.kt`, synced exact Room/DataStore filenames in backup rules, and resolved legacy `RemoteViewModel.kt` `Flow.combine` compilation issues.

---

### Milestone 9: Verified Universal IR Database Expansion & Web Remote Skinning
- **Trusted IR Database Verification**: Expanded the Universal IR profile catalog from 10 placeholder entries to **23 verified TV models/series** cross-checked against **`probonopd/irdb`** and **`Lucaslhm/Flipper-IRDB`** (fixing legacy Vizio, Toshiba Regza, Panasonic, TCL, Hisense, and Sharp profiles and adding Roku TV Universal, Amazon Fire TV, Toshiba Fire TV, Philips OLED RC6, Insignia/Dynex, Element/Westinghouse, Sanyo, RCA Classic & LED, Hitachi/JVC, Sceptre, and Haier).
- **Extended Android Hardware Encoders (`ConsumerIrAdapter.kt`)**: Implemented native `ConsumerIrManager` waveform encoders for `NEC_EXT` (16-bit extended), `PANASONIC` (48-bit Kaseikyo at 37 kHz), `RCA` (RCA-38 24-bit MSB-first), and `RC6` (Mode 0 Manchester with double-width trailer bit), alongside a searchable model/protocol selector in `NexusMainScreen.kt`.
- **Web Remote Visual Skinning (`src/`)**: Added a CSS-based skinning system allowing users to switch between **Classic Infrared**, **Modern Streaming**, and **Minimalist** remote styles with a searchable 23-model TV selector.

---

## 📈 Architecture Decision Records (ADRs)

| ADR ID | Decision Title | Status | Rationale |
|---|---|---|---|
| **ADR-001** | Hardware-Backed Keystore for Credentials | Accepted | Storing tokens, PSKs, or RSA keys in SharedPreferences or Room in plaintext exposes user devices to unauthorized access. All secrets are encrypted with AES-256 GCM or signed via RSA-2048 directly within the TEE/StrongBox. |
| **ADR-002** | Common `DeviceController` Interface | Accepted | Decouples the UI and ViewModel from brand-specific network mechanics. Adding new protocols requires only a new adapter implementation without touching core logic. |
| **ADR-003** | Capability-Driven UI Rendering | Accepted | Eliminates "dead buttons" by inspecting protocol capabilities at runtime. Users never encounter non-functional controls. |
| **ADR-004** | Subnet-Bounded Discovery (`/24`) | Accepted | Prevents network security exceptions on Android 12+ and avoids scanning unauthorized or public subnets. |
| **ADR-005** | No-Assumption IR Hardware Detection | Accepted | Devices and foldables vary across global hardware revisions. Calling `hasIrEmitter()` prevents runtime crashes on hardware variants without an IR diode. |
| **ADR-006** | TOFU TLS Certificate Pinning | Accepted | Smart TV local SSL/TLS endpoints often generate self-signed certificates. Trust-On-First-Use pins the leaf SHA-256 fingerprint on pair and blocks MitM interception on subnets. |
| **ADR-007** | Per-Device AES Keys & AAD Binding | Accepted | Isolated per-device keys enable instant cryptographic erasure upon device deletion, while AAD label binding prevents ciphertext translocation between saved devices. |

