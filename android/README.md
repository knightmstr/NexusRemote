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
    │   ├── ConsumerIrAdapter.kt     # ConsumerIrManager with 23 verified TV profiles & 8 IR pulse encoders
    │   └── ProtocolRegistry.kt      # Factory mapping SavedDevice to Protocol Adapter
    │
    ├── viewmodel/
    │   └── NexusViewModels.kt       # DeviceManagementViewModel & RemoteControlViewModel
    │
    └── ui/
        └── NexusMainScreen.kt       # Jetpack Compose UI with Foldable & Tablet Dual-Pane
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
- **Input Sanitization & Allowlisting**: ADB shell execution is restricted to allowlisted regex (`^[a-zA-Z0-9._]+$`) for package names and validated integer Android keycodes (`KeyEvent.KEYCODE_*`).

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
| **Universal IR** | Consumer IR (8 encoders) | Optical IR (36–40 kHz) | N/A | None (One-way, 23 models) | No | No | Yes (IR Power Code) |

---

## 🎯 Universal Consumer IR: Supported TV Models & Protocols

`ConsumerIrAdapter.kt` implements real `ConsumerIrManager.transmit()` pulse-timing encoders verified against **`probonopd/irdb`** and **`Lucaslhm/Flipper-IRDB`**:

- **Supported Hardware Encoders (8)**:
  - `NEC` (38 kHz, 8-bit address + inverse)
  - `NEC_EXT` (38 kHz, 16-bit extended address + subAddress)
  - `SAMSUNG` (38 kHz NECx2, 4.5 ms leader + address/subAddress)
  - `SONY_SIRC` (40 kHz 12-bit pulse-width modulation)
  - `RC5` (36 kHz Manchester bi-phase with alternating toggle bit)
  - `RC6` (36 kHz Philips Mode 0 Manchester with double-width trailer bit)
  - `PANASONIC` (37 kHz 48-bit Kaseikyo with OEM `0x02:0x20` + XOR checksum)
  - `RCA` (38 kHz RCA-38 24-bit MSB-first pulse-distance)

| # | Brand / Profile | Supported Series / OS | Protocol | Freq | Addr / Sub | Source |
|---|---|---|---|---|---|---|
| 1 | **Samsung** | Neo QLED / OLED / Crystal 4K | `SAMSUNG` | 38 kHz | `0x07` / `0x07` | `probonopd/irdb · Samsung/TV/7,7.csv` |
| 2 | **LG Electronics** | webOS OLED C3 / QNED / NanoCell | `NEC` | 38 kHz | `0x04` | `probonopd/irdb · 4,-1.csv` & `Flipper-IRDB LG_OLED48C37LA` |
| 3 | **Sony Bravia** | XR OLED / X80K / Google TV | `SONY_SIRC` | 40 kHz | `0x01` | `probonopd/irdb · 1,-1.csv` & `Flipper-IRDB Sony_KD-55X80CK` |
| 4 | **Roku TV Universal** | TCL / Hisense / Onn / Insignia / RCA Roku OS | `NEC_EXT` | 38 kHz | `0xEA` / `0xC7` | `Flipper-IRDB · TCL_32S327 & Onn_Roku_TV.ir` |
| 5 | **TCL Google TV** | QLED C635 / S4 / QM8 Android & Google TV | `RCA` | 38 kHz | `0x0F` | `probonopd/irdb · TCL/15,-1.csv` & `Flipper-IRDB TCL_65C635K` |
| 6 | **Hisense VIDAA** | E7K / A4H / ULED Smart TV | `NEC_EXT` | 38 kHz | `0x00` / `0xBF` | `Flipper-IRDB · Hisense_55E7KQ & 32A4HAU.ir` |
| 7 | **Hisense Google TV** | U6K / U7G / U8K Mini-LED | `NEC` | 38 kHz | `0x04` | `Flipper-IRDB · Hisense_55U6K & Hisense_U7G.ir` |
| 8 | **Vizio SmartCast** | M-Series Quantum / V-Series / P-Series | `NEC` | 38 kHz | `0x04` | `probonopd/irdb · Vizio/4,-1.csv` & `Flipper-IRDB Vizio.ir` |
| 9 | **Panasonic Viera** | JX800 / OLED / 4K Pro Studio | `PANASONIC` | 37 kHz | `0x80` / `0x00` | `probonopd/irdb · Panasonic/128,0.csv` & `Flipper-IRDB` |
| 10 | **Philips OLED / Android** | Ambilight 806 OLED / PUS Android TV | `RC6` | 36 kHz | `0x00` | `Flipper-IRDB · Philips_TV_48OLED806 & Universal.ir` |
| 11 | **Philips Classic** | PFL Smart LED / Legacy Ambilight | `RC5` | 36 kHz | `0x00` | `probonopd/irdb · Philips/TV/0,-1.csv` |
| 12 | **Amazon Fire TV** | Omni Series 4K / 4-Series / Insignia Fire TV | `NEC_EXT` | 38 kHz | `0x02` / `0x7D` | `Flipper-IRDB · FireTV_Omni_Series_4K.ir` |
| 13 | **Toshiba Fire TV** | C350 / LF421U Fire TV Edition | `NEC_EXT` | 38 kHz | `0x02` / `0x7D` | `Flipper-IRDB · Toshiba_50C350LC & 43LF421U21.ir` |
| 14 | **Toshiba Regza** | AV / L-Series Standard HDTV | `NEC` | 38 kHz | `0x40` | `probonopd/irdb · Toshiba/64,-1.csv` & `32AV502U.ir` |
| 15 | **Sharp Aquos** | 4K UHD / BG3E / Smart LED | `NEC_EXT` | 38 kHz | `0x00` / `0x7F` | `Flipper-IRDB · Sharp/Aquos.ir & 32BG3E.ir` |
| 16 | **Insignia / Dynex** | NS-Series / DX-Series LED HDTV | `NEC_EXT` | 38 kHz | `0x86` / `0x05` | `probonopd/irdb · Insignia/134,5.csv` & `Flipper-IRDB` |
| 17 | **Element / Westinghouse** | 100-Series / RMT Smart LED TV | `NEC_EXT` | 38 kHz | `0x02` / `0x7D` | `Flipper-IRDB · Element_100-Series & Westinghouse.ir` |
| 18 | **Sanyo** | DP / FW Series LCD & LED TV | `NEC` | 38 kHz | `0x38` | `probonopd/irdb · Sanyo/56,-1.csv` & `Sanyo_DP26640.ir` |
| 19 | **RCA Classic** | CRK / Classic HD & LED Series | `RCA` | 38 kHz | `0x0F` | `probonopd/irdb · RCA/TV/15,-1.csv` |
| 20 | **RCA LED (NEC)** | RLDED Series Budget LED TV | `NEC` | 38 kHz | `0x20` | `Flipper-IRDB · RCA_RLDED3258A-B.ir` |
| 21 | **Hitachi / JVC Smart** | RC43140 / Vestel / 4K Smart TV | `RC5` | 36 kHz | `0x03` | `Flipper-IRDB · Hitachi_RC43140.ir` |
| 22 | **Sceptre** | X50 / U-Series 4K UHD LED | `SONY_SIRC` | 40 kHz | `0x01` | `Flipper-IRDB · Sceptre_X50_Series.ir` |
| 23 | **Haier** | L42 / Smart Android LED TV | `NEC` | 38 kHz | `0x04` | `Flipper-IRDB · Haier_L42C1180.ir` |

---

## 📱 Hardware Verification & Adaptive Form Factors

### Consumer IR Blaster Verification
- **Dynamic Hardware Check**: NexusRemote explicitly calls `ConsumerIrManager.hasIrEmitter()`.
- **Zero Assumptions**: NexusRemote **never assumes any device has an IR blaster**. If the hardware emitter is absent, IR mode is automatically hidden, and only Wi-Fi protocol adapters are presented.

### Foldable & Tablet Dual-Pane Optimization
- Optimized for foldable smartphones and Android tablets:
  - **Expanded / Unfolded (`>= 600dp`)**: Side-by-side dual pane layout.
    - **Left Pane**: Remote navigation, 5-way D-Pad gesture surface, tactile volume & channel rockers.
    - **Right Pane**: Persistent keyboard text injector, touchpad swipe surface, installed app dock, and one-tap macros.
  - **Folded / Compact (`< 600dp`)**: Single-column vertical layout with capability enforcement.

---

## 🛠️ Installation & Device Setup Guide

### 1. Installing from GitHub Releases (Recommended)
- **Supported Android Versions**: Android **8.0 (API 26)** through Android **15 (API 35)** (`compileSdk = 35`, `targetSdk = 35`).
1. On your Android device, open the repository's **Releases** page on GitHub and download the latest `NexusRemote-*-debug.apk` (built automatically by `.github/workflows/android.yml`).
2. Tap the downloaded APK file in your browser or file manager. If prompted, enable **Allow from this source** (*Install unknown apps*) in Android Settings, then tap **Install**.
3. Open **NexusRemote** and grant **Nearby Devices** (Android 13+) or **Location** (Android 8.0–12) if prompted for Wi-Fi TV discovery. Universal IR mode works immediately offline without network permissions if your phone includes an IR emitter.

### 2. Building from Source (Developers Only)
Open the `android/` folder in **Android Studio (Jellyfish / Koala or newer)** with **JDK 17** and **Android SDK 35**, or push a `v*` tag to trigger an automated APK build and GitHub Release via GitHub Actions.

### 3. Permissions & Hardware Declarations
- **Local Network Permissions**: Android 13+ uses `NEARBY_WIFI_DEVICES` with `android:usesPermissionFlags="neverForLocation"` in `AndroidManifest.xml`. On Android 8.0–12, `ACCESS_FINE_LOCATION` is scoped with `android:maxSdkVersion="32"` so modern devices never prompt for GPS/location access.
- **IR Hardware Optional**: `android.permission.TRANSMIT_IR` is declared alongside `<uses-feature android:name="android.hardware.consumerir" android:required="false" />`, allowing devices without an IR blaster to install and run all Wi-Fi Smart TV protocols normally.
