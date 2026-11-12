# NexusRemote: Native Android Architecture & Technical Guide

Production-grade, native Android television remote application and modular protocol ecosystem hardened for OWASP MASVS compliance.

> 📜 **Project History & Evolution**: For the full chronological development history, prototype audit, security milestones, and architecture decision records, see the [Project History Log](../HISTORY.md).

---

## 📦 Architecture & Package Structure

```
android/app/src/main/
├── AndroidManifest.xml              # Backup rules, maxSdkVersion location permission, network security config
├── res/xml/
│   ├── backup_rules.xml             # Full backup exclusions for Room & SharedPreferences
│   ├── data_extraction_rules.xml    # Android 12+ cloud/device data extraction rules
│   └── network_security_config.xml  # Global cleartextTrafficPermitted=false policy
└── java/com/nexusremote/
    ├── MainActivity.kt
    │
    ├── domain/model/
    │   └── DeviceModels.kt          # SavedDevice Room entity, ProtocolType, DeviceCapability, RemoteCommand
    │
    ├── security/
    │   ├── KeystoreSecretManager.kt  # Per-device AES-256 GCM, AAD binding, StrongBox HSM fallback, 524-byte RSA struct
    │   └── TofuTrustManager.kt      # Trust-On-First-Use (TOFU) TLS certificate pinning for TV sockets
    │
    ├── data/local/
    │   ├── NexusDatabase.kt         # Room DB, DeviceDao (excluded from cloud backup)
    │   └── DevicePreferences.kt     # Preferences DataStore for default device & UI settings
    │
    ├── discovery/
    │   └── NetworkDiscoveryManager.kt  # Local mDNS (NsdManager), SSDP/UPnP Multicast, Smart Reconnect
    │
    ├── protocols/
    │   ├── DeviceController.kt      # Common interface for all TV protocol adapters
    │   ├── AdbProtocolAdapter.kt    # ADB TCP/IP 5555 with RSA auth, package allowlists & keycode validation
    │   ├── RokuEcpAdapter.kt        # Roku External Control Protocol (HTTP 8060)
    │   ├── SamsungTizenAdapter.kt   # Samsung Tizen WSS (8002) with TOFU TLS & plaintext fallback guard
    │   ├── LgWebOsAdapter.kt        # LG webOS SSAP WSS (3001) with TOFU TLS & plaintext fallback guard
    │   ├── SonyBraviaAdapter.kt     # Sony BRAVIA REST JSON-RPC & IRCC-IP SOAP with Keystore PSK
    │   ├── HisenseVidaaAdapter.kt   # Hisense VIDAA SSL TCP / RemoteNOW (5000) with TOFU TLS
    │   ├── PanasonicAdapter.kt      # Panasonic Viera UPnP SOAP (55000)
    │   ├── AndroidTvRemoteAdapter.kt  # Google Android TV Remote v2 TLS (6467) with TOFU TLS
    │   ├── ConsumerIrAdapter.kt     # ConsumerIrManager with Magic V5 detection & brand DB
    │   └── ProtocolRegistry.kt      # Factory mapping SavedDevice to Protocol Adapter
    │
    ├── viewmodel/
    │   └── NexusViewModels.kt       # DeviceManagementViewModel & RemoteControlViewModel
    │
    └── ui/
        └── NexusMainScreen.kt       # Jetpack Compose UI with Foldable Dual-Pane (HONOR Magic V5)
```

---

## 🔒 Security Architecture (Android Keystore & OWASP MASVS)

NexusRemote enforces strict cryptographic security and OWASP MASVS standards:
- **Per-Device AES-256-GCM Keys & Cryptographic Erasure**: Each paired device receives a unique `nexus_key_$deviceId` in Android Keystore. Calling `removeDeviceSecrets(deviceId)` executes `keyStore.deleteEntry()`, resulting in **true cryptographic erasure**.
- **Additional Authenticated Data (AAD) Label Binding**: Storage keys bind `"${deviceId}::${secretKeyName}"` as AAD via `cipher.updateAAD()` during encryption and decryption, defeating cross-device ciphertext translocation.
- **StrongBox HSM & Device Unlock Requirements**: Key generation uses `.setUnlockedDeviceRequired(true)` (API 28+) and attempts `.setIsStrongBoxBacked(true)` with a graceful fallback to standard TEE if StrongBox hardware is absent.
- **Native 524-Byte ADB `RSAPublicKey` Struct & `NONEwithRSA` Token Signing**: Formats public keys into the native 524-byte AOSP `RSAPublicKey` C-struct format (`len` = 64, `n0inv`, `n[64]`, `rr[64]`) and signs 20-byte `A_AUTH` token challenges using `NONEwithRSA` with a pre-built 15-byte SHA-1 ASN.1 `DigestInfo` header.
- **Trust-On-First-Use (TOFU) TLS Certificate Pinning (`TofuTrustManager.kt`)**: Computes and pins SHA-256 leaf certificate fingerprints on initial pairing for Smart TV TLS endpoints (Samsung Tizen WSS 8002, LG webOS WSS 3001, Hisense VIDAA SSL 5000, Android TV Remote v2 TLS 6467), blocking Man-in-the-Middle (MitM) attacks.
- **Plaintext Fallback Protection**: Unencrypted WS fallback (`8001` or `3000`) is rejected unless `SavedDevice.allowPlaintextFallback == true` is explicitly authorized by the user.
- **Input Sanitization & Allowlisting**:ADB shell execution is restricted to allowlisted regex (`^[a-zA-Z0-9._]+$`) for package names and validated integer Android keycodes (`KeyEvent.KEYCODE_*`).

---

## 📡 Local Network Discovery & Smart Reconnect

### 1. Local Subnet Scanning Only
- `NetworkDiscoveryManager` extracts the active Wi-Fi interface subnet mask (`/24`) and restricts multicast/probe traffic strictly to the user's private network (e.g. `192.168.1.0/24`).
- Location permission is scoped to `maxSdkVersion="32"` for Android 8.0–12, preserving location-free discovery on Android 13+ via `NEARBY_WIFI_DEVICES`.

### 2. Multi-Protocol Discovery Radar
- **mDNS (`NsdManager`)**:
  - `_androidtvremote2._tcp`: Google TV / Android TV Remote v2 (TLS 6467)
  - `_adb-tls-connect._tcp`: Modern Android TV ADB over TLS
  - `_roku._tcp`: Roku streaming devices and Roku TVs
  - `_googlecast._tcp`: Chromecast built-in devices
- **SSDP / UPnP Multicast (`239.255.255.250:1900`)**:
  - Queries `urn:schemas-upnp-org:device:MediaRenderer:1`, `roku:ecp`, `ssap://webos`, and `urn:schemas-sony-com:service:IRCC:1`.

### 3. Smart Reconnect Strategy
- When opening a saved TV, NexusRemote **never performs a full network scan**.
- It pings the cached IP directly with a 500ms socket probe.
- Only if the TV IP has changed due to DHCP lease renewal does it execute a targeted subnet rediscovery, match the device signature, and update the Room database seamlessly.

---

## 📺 Protocol Limitations & Compatibility Matrix

| Platform | Protocol | Transport | Port | Pairing Method | Text Injection | App Launch | Power ON (Standby) |
|---|---|---|---|---|---|---|---|
| **Fire TV / Android TV** | ADB over TCP/IP | Raw TCP Socket | 5555 | RSA Key Confirmation on TV | Yes (`input text`) | Yes (`monkey -p`) | No (Needs WoL) |
| **Android TV v2** | Google Remote v2 | TLS Socket (TOFU) | 6467 | 6-digit PIN on TV | Yes (Protobuf) | Via Deep Link | No (Needs WoL) |
| **Roku** | ECP REST | HTTP | 8060 | Permissive Network (No PIN) | Yes (`/keypress/Lit_`) | Yes (`/launch/<id>`) | Yes (`PowerOn` ECP) |
| **Samsung Tizen** | Tizen Remote | WSS (TOFU) | 8002 | On-screen 'Allow' prompt | No (D-Pad only) | Yes (Deep Link) | No (Needs WoWLAN) |
| **LG webOS** | SSAP | WSS (TOFU) | 3001 | On-screen 'Allow' prompt | Yes (`insertText`) | Yes (`open` launcher) | No (Needs WoWLAN) |
| **Sony BRAVIA** | IRCC-IP / REST | HTTP SOAP | 80 | Pre-Shared Key (PSK) | No (D-Pad only) | Via REST | Yes (Remote Start) |
| **Hisense VIDAA** | RemoteNOW | SSL TCP (TOFU) | 5000 | 4-digit PIN on TV | No (D-Pad only) | No | No (Needs WoL) |
| **Panasonic** | Viera Network | UPnP SOAP | 55000 | Network Control 'ON' | No (D-Pad only) | No | Yes (Power On by App) |
| **Universal IR** | Consumer IR | Optical IR | N/A | None (One-way) | No | No | Yes (IR Power Code) |

---

## 📱 Hardware Verification: HONOR Magic V5 & Foldables

### Consumer IR Blaster Verification
- **Dynamic Hardware Check**: NexusRemote explicitly calls `ConsumerIrManager.hasIrEmitter()`.
- **Zero Assumptions**: NexusRemote **never assumes the HONOR Magic V5 or any device has an IR blaster**. If the hardware emitter is absent, IR mode is automatically hidden, and only Wi-Fi protocol adapters are presented.

### Foldable & Tablet Dual-Pane Optimization
- Tested for foldable smartphones (such as the HONOR Magic V5 unfolded state at `8.0"` display) and Android tablets:
  - **Expanded / Unfolded (`>= 600dp`)**: Side-by-side dual pane layout.
    - **Left Pane**: Remote navigation, 5-way D-Pad gesture surface, tactile volume & channel rockers.
    - **Right Pane**: Persistent keyboard text injector, touchpad swipe surface, installed app dock, and one-tap macros.
  - **Folded / Compact (`< 600dp`)**: Single-column vertical layout with capability enforcement.

---

## 🛠️ Installation & Device Setup Guide

### 1. Installation on a Physical Android Device
1. Enable **Developer Options** and **USB Debugging** on your phone (e.g. HONOR Magic V5: *Settings > About Phone > Tap 'Build Number' 7 times*).
2. Connect your phone via USB or build an APK:
   ```bash
   cd android
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Grant **Local Network / Nearby Devices** permission when prompted on first launch.

### 2. Local Network Permissions
Android 13+ requires the `NEARBY_WIFI_DEVICES` permission with `android:usesPermissionFlags="neverForLocation"` in `AndroidManifest.xml`. On Android 8.0–12, `ACCESS_FINE_LOCATION` is scoped with `android:maxSdkVersion="32"` to allow subnet discovery without requiring GPS permissions on modern versions.
