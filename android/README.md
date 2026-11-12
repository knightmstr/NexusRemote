# NexusRemote: Native Android Architecture & Technical Guide

Production-grade, native Android television remote application and modular protocol ecosystem.

---

## 📦 Architecture & Package Structure

```
android/app/src/main/
├── AndroidManifest.xml
└── java/com/nexusremote/
    ├── MainActivity.kt
    │
    ├── domain/model/
    │   └── DeviceModels.kt         # SavedDevice Room entity, ProtocolType, DeviceCapability, RemoteCommand
    │
    ├── security/
    │   └── KeystoreSecretManager.kt # Android Keystore hardware-backed AES-256 GCM & RSA-2048 signing
    │
    ├── data/local/
    │   ├── NexusDatabase.kt        # Room DB, DeviceDao (survives reboots and updates)
    │   └── DevicePreferences.kt    # Preferences DataStore for default device & UI settings
    │
    ├── discovery/
    │   └── NetworkDiscoveryManager.kt # Local mDNS (NsdManager), SSDP/UPnP Multicast, Smart Reconnect
    │
    ├── protocols/
    │   ├── DeviceController.kt     # Common interface for all TV protocol adapters
    │   ├── AdbProtocolAdapter.kt   # Real ADB TCP/IP 5555 with RSA auth, keyevents, text, apps
    │   ├── RokuEcpAdapter.kt       # Roku External Control Protocol (HTTP 8060)
    │   ├── SamsungTizenAdapter.kt  # Samsung Tizen Smart TV WebSocket (8001/8002) with token auth
    │   ├── LgWebOsAdapter.kt       # LG webOS SSAP WebSocket (3000/3001) with client-key
    │   ├── SonyBraviaAdapter.kt    # Sony BRAVIA REST JSON-RPC & IRCC-IP SOAP with PSK
    │   ├── HisenseVidaaAdapter.kt  # Hisense VIDAA SSL TCP / RemoteNOW (5000) with PIN
    │   ├── PanasonicAdapter.kt     # Panasonic Viera UPnP SOAP (55000)
    │   ├── AndroidTvRemoteAdapter.kt # Google Android TV Remote v2 (TLS 6467)
    │   ├── ConsumerIrAdapter.kt    # ConsumerIrManager with Magic V5 detection & brand DB
    │   └── ProtocolRegistry.kt     # Factory mapping SavedDevice to Protocol Adapter
    │
    ├── viewmodel/
    │   └── NexusViewModels.kt      # DeviceManagementViewModel & RemoteControlViewModel
    │
    └── ui/
        └── NexusMainScreen.kt      # Jetpack Compose UI with Foldable Dual-Pane (HONOR Magic V5)
```

---

## 🔒 Security Architecture (Android Keystore)

NexusRemote enforces strict cryptographic security standards:
- **No Plaintext Tokens**: All client keys (LG webOS), authentication tokens (Samsung Tizen), pre-shared keys (Sony BRAVIA), and PINs are encrypted using **hardware-backed AES-256 GCM** generated inside the `AndroidKeyStore` provider.
- **Hardware-Isolated ADB RSA Keypair**: Generates a 2048-bit RSA keypair in the Keystore (`nexus_remote_adb_rsa_key`). When an Android TV/Fire TV sends an `A_AUTH` token challenge, the signature is computed directly inside the Secure Element/TEE (`SHA256withRSA`).
- **Cryptographic Erasure**: Deleting a device calls `keystoreSecretManager.removeDeviceSecrets(deviceId)`, immediately destroying the encryption keys.

---

## 📡 Local Network Discovery & Smart Reconnect

### 1. Local Subnet Scanning Only
- Unlike insecure implementations that broadcast to public networks, `NetworkDiscoveryManager` extracts the active Wi-Fi interface subnet mask (`/24`) and restricts multicast/probe traffic strictly to the user's private network (e.g. `192.168.1.0/24`).

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
- It pings the cached IP directly with a 1200ms socket probe.
- Only if the TV IP has changed due to DHCP lease renewal does it execute a targeted subnet rediscovery, match the device signature, and update the Room database seamlessly.

---

## 📺 Protocol Limitations & Compatibility Matrix

| Platform | Protocol | Transport | Port | Pairing Method | Text Injection | App Launch | Power ON (Standby) |
|---|---|---|---|---|---|---|---|
| **Fire TV / Android TV** | ADB over TCP/IP | Raw TCP Socket | 5555 | RSA Key Confirmation on TV | Yes (`input text`) | Yes (`monkey -p`) | No (Needs WoL) |
| **Android TV v2** | Google Remote v2 | TLS Socket | 6467 | 6-digit PIN on TV | Yes (Protobuf) | Via Deep Link | No (Needs WoL) |
| **Roku** | ECP REST | HTTP | 8060 | Permissive Network (No PIN) | Yes (`/keypress/Lit_`) | Yes (`/launch/<id>`) | Yes (`PowerOn` ECP) |
| **Samsung Tizen** | Tizen Remote | WebSocket | 8001/8002 | On-screen 'Allow' prompt | No (D-Pad only) | Yes (Deep Link) | No (Needs WoWLAN) |
| **LG webOS** | SSAP | WebSocket | 3000/3001 | On-screen 'Allow' prompt | Yes (`insertText`) | Yes (`open` launcher) | No (Needs WoWLAN) |
| **Sony BRAVIA** | IRCC-IP / REST | HTTP SOAP | 80 | Pre-Shared Key (PSK) | No (D-Pad only) | Via REST | Yes (Remote Start) |
| **Hisense VIDAA** | RemoteNOW | SSL TCP | 5000 | 4-digit PIN on TV | No (D-Pad only) | No | No (Needs WoL) |
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
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Grant **Local Network / Nearby Devices** permission when prompted on first launch.

### 2. Local Network Permissions
Android 13+ requires the `NEARBY_WIFI_DEVICES` permission with `android:usesPermissionFlags="neverForLocation"` in `AndroidManifest.xml`. This enables mDNS and SSDP discovery without requiring GPS location access.
