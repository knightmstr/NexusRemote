# NexusRemote: Universal Multi-Protocol Smart TV & IR Remote Architecture

An enterprise-grade, native Android application architecture and interactive simulation suite supporting **Google Android TV, Amazon Fire TV, Roku, Samsung Tizen, LG webOS, Sony BRAVIA, Hisense VIDAA, Panasonic Viera, and Universal Consumer IR**.

Built natively with **Kotlin, Jetpack Compose Material 3, Room, Android Keystore Cryptography, Preferences DataStore, and Coroutines/Flow**.

---

## 🔍 Technical Audit of Legacy / Prototype Codebase

Before constructing the production architecture, a file-by-file audit of the initial prototype repository (`knightmstr/NexusRemote`) was conducted:

### 1. What was Simulated
- **ADB RSA Authentication**: The prototype checked for `A_AUTH` and set `AUTH_REQUIRED`, but lacked RSA keypair generation, public key transfer (`A_AUTH RSAPUBLICKEY`), and cryptographic signature token generation (`A_AUTH SIGNATURE`).
- **Installed TV App Discovery**: If parsing `pm list packages -3` failed or returned empty, the prototype fell back to a hardcoded list of 7 package strings (`com.amazon.amazonvideo.livingroom`, `com.netflix.ninja`, etc.).
- **Smart TV Protocols**: Prototype only implemented legacy raw ADB TCP port 5555 and Consumer IR; protocols for Roku, Samsung, LG, Sony, Hisense, and Panasonic did not exist.

### 2. What was Hardcoded
- **Target IP Addresses**: Fallback IP `192.168.1.150` hardcoded in `RemoteViewModel.kt`.
- **Single-Device Focus**: No Room database or device management entity existed; only a single transient IP address in Preferences DataStore was maintained.
- **Fixed Port**: Port `5555` was assumed for all network interactions.

### 3. What was Reusable
- **Low-level ADB Packet Framing**: 24-byte little-endian header packing, command checksumming, and XOR magic masking (`A_CNXN`, `A_OPEN`, `A_OKAY`, `A_WRTE`, `A_CLSE`).
- **ADB Shell Text Sanitization**: Meta-character escaping for `input text` (escaping quotes, spaces with `%s`, ampersands, semicolons).
- **Consumer IR Waveform Timing**: High-precision microsecond mark/space encoders for 38 kHz NEC, Samsung, 40 kHz Sony SIRC, and 36 kHz RC-5 Manchester.

### 4. What was Missing (Now Fully Implemented)
- **Local Network Discovery**: No mDNS (`NsdManager`) or SSDP/UPnP multicast scanning engine.
- **Saved Devices & Room Database**: No persistent `SavedDevice` Room entity surviving device reboots and app updates.
- **Android Keystore Cryptography**: No secure storage for pairing tokens, client-keys, or RSA private keys.
- **Modular Protocol Adapters**: Missing Roku ECP, Samsung Tizen WebSocket, LG webOS SSAP, Sony BRAVIA IRCC-IP, Hisense VIDAA, and Android TV Remote v2.
- **Dynamic Capability Detection**: Dead buttons were displayed regardless of whether the connected TV supported the feature.
- **Foldable & Tablet Dual-Pane UI**: No adaptive support for foldable phones (e.g. HONOR Magic V5).
- **Smart Reconnection**: No lightweight reachability monitoring or targeted rediscovery.

---

## 🏗️ Architecture & Component Design

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       UI LAYER (Jetpack Compose M3)                         │
│                    com.nexusremote.ui.NexusMainScreen                       │
│  • Home Screen: Saved TV cards, Room filters, Online status diodes, Favs    │
│  • Discovery Radar: Local mDNS & SSDP Multicast scanner, manual IP prober   │
│  • Dual-Pane Foldable Layout: HONOR Magic V5 / Tablet adaptive UI           │
│  • Dynamic Capability Engine: Zero dead buttons; renders only supported keys│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ StateFlows
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                      STATE & VIEWMODEL LAYER (MVVM)                         │
│                  com.nexusremote.viewmodel.NexusViewModels                  │
│  • DeviceManagementViewModel: Saved TV CRUD, Smart Reconnect, reachability  │
│  • RemoteControlViewModel: Active session, capability filtering, macros     │
└───────────────────┬──────────────────────────────────────┬──────────────────┘
                    │                                      │
┌───────────────────▼──────────────────┐   ┌───────────────▼──────────────────┐
│             DATA & LOCAL             │   │       SECURITY & DISCOVERY       │
│  • Room DB: SavedDevice & DeviceDao  │   │  • KeystoreSecretManager (AES)   │
│  • DataStore: Default TV & Settings  │   │  • NetworkDiscoveryManager       │
└──────────────────────────────────────┘   └──────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    MODULAR PROTOCOL ADAPTER ENGINE                          │
│               com.nexusremote.protocols.DeviceController                    │
│  ├── AdbProtocolAdapter: Fire TV / Android TV ADB 5555 with RSA auth        │
│  ├── RokuEcpAdapter: Roku External Control Protocol HTTP 8060               │
│  ├── SamsungTizenAdapter: Samsung Tizen WebSocket 8001/8002 with token      │
│  ├── LgWebOsAdapter: LG webOS SSAP WebSocket 3000/3001 with client-key      │
│  ├── SonyBraviaAdapter: Sony BRAVIA IRCC-IP & REST SOAP with PSK            │
│  ├── HisenseVidaaAdapter: Hisense VIDAA SSL TCP 5000 with PIN challenge     │
│  ├── PanasonicAdapter: Panasonic Viera UPnP SOAP HTTP 55000                 │
│  ├── AndroidTvRemoteAdapter: Google Android TV Remote v2 (TLS 6467)         │
│  └── ConsumerIrAdapter: ConsumerIrManager with HONOR Magic V5 check & DB   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📺 Supported TV Platforms & Limitations

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

### 1. Consumer IR Blaster Verification
- **Dynamic Hardware Check**: NexusRemote explicitly calls `ConsumerIrManager.hasIrEmitter()`.
- **Zero Assumptions**: NexusRemote **never assumes the HONOR Magic V5 or any device has an IR blaster**. If the hardware emitter is absent, IR mode is automatically hidden, and only Wi-Fi protocol adapters are presented.

### 2. Foldable & Tablet Dual-Pane Optimization
- Tested for foldable smartphones (such as the HONOR Magic V5 unfolded state at `8.0"` display) and Android tablets:
  - **Expanded / Unfolded (`>= 600dp`)**: Side-by-side dual pane layout.
    - **Left Pane**: Remote navigation, 5-way D-Pad gesture surface, tactile volume & channel rockers.
    - **Right Pane**: Persistent keyboard text injector, touchpad swipe surface, installed app dock, and one-tap macros.
  - **Folded / Compact (`< 600dp`)**: Single-column vertical layout with capability enforcement.

---

## 🛠️ Pairing Guides for Supported Platforms

### 1. Amazon Fire TV & Android TV (ADB)
1. On Fire TV: Go to **Settings > My Fire TV > About**, and click your Fire TV name **7 times** to unlock Developer Options.
2. Go to **Settings > My Fire TV > Developer Options** and toggle **ADB Debugging** to **ON**.
3. In NexusRemote, tap your Fire TV and click Connect.
4. When prompted on the TV screen with **"Allow USB/Network debugging?"**, check **"Always allow from this computer"** and select **OK**.

### 2. Roku TVs (TCL, Hisense, Roku Express)
1. Ensure the TV is on the same local Wi-Fi.
2. Go to **Settings > System > Advanced system settings > Control by mobile apps**, and verify **Network access** is set to **Permissive** or **Default**.
3. NexusRemote discovers Roku automatically via SSDP and connects with zero PIN requirements.

### 3. Samsung Tizen Smart TV
1. Turn on TV and connect to the same Wi-Fi.
2. Tap the TV in NexusRemote; a popup will appear in the top-right of the Samsung TV screen: **"Allow NexusRemote to connect?"**.
3. Select **Allow** using the physical Samsung remote. The authentication token is securely cached in Android Keystore.

### 4. LG webOS Smart TV
1. Turn on TV and connect to the same Wi-Fi.
2. Go to **Settings > Network > LG Connect Apps**, and set to **ON**.
3. Tap the TV in NexusRemote; a prompt will appear on the TV screen: **"NexusRemote would like to pair with this TV"**.
4. Select **Yes**. The client-key is securely encrypted and stored in Android Keystore.

### 5. Sony BRAVIA TV
1. Go to **Settings > Network > Home Network Setup > IP Control**.
2. Set **Authentication** to **Pre-Shared Key (PSK)**.
3. Enter a PIN (e.g. `0000` or `1234`).
4. Enter this Pre-Shared Key in NexusRemote when saving the device.

---

## 🔒 Local Network Permissions Guide

Android 13, 14, and 15 enforce strict local network privacy:
1. `NEARBY_WIFI_DEVICES`: Declared with `android:usesPermissionFlags="neverForLocation"` to discover local TVs over Wi-Fi without requiring GPS location tracking.
2. `CHANGE_WIFI_MULTICAST_STATE`: Required to acquire `MulticastLock` for SSDP search (`239.255.255.250:1900`).
3. `INTERNET` & `ACCESS_NETWORK_STATE`: Required for socket and HTTP transport.
4. `TRANSMIT_IR`: Declared with `<uses-feature android:name="android.hardware.consumerir" android:required="false" />` so devices lacking an IR blaster (such as certain foldable variants) can install and run the app cleanly.

---

## 📄 License

Apache License 2.0.
