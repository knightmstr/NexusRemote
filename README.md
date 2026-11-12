# NexusRemote: Universal Multi-Protocol Smart TV & IR Remote

[![Project History & Evolution Log](https://img.shields.io/badge/History-HISTORY.md-blue?style=flat-square)](HISTORY.md)
[![Platform](https://img.shields.io/badge/Platform-Android%2014%2B%20(API%2034)-green?style=flat-square)](android/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?style=flat-square)](android/app/src/main/java/com/nexusremote/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-brightgreen?style=flat-square)](android/app/src/main/java/com/nexusremote/ui/)
[![Security](https://img.shields.io/badge/OWASP%20MASVS-Hardened-red?style=flat-square)](android/app/src/main/java/com/nexusremote/security/)

NexusRemote is a production-grade, native Android television remote control application and modular protocol ecosystem. It unifies network-connected Smart TVs and traditional IR-controlled appliances behind a single, capability-aware control interface.

> 📜 **Project History**: For a complete chronicle of how this project evolved from its initial prototype audit through each architecture milestone and security hardening refactoring, see the [Project History & Evolution Log](HISTORY.md).

---

## ⚡ Features Implemented

### 1. Multi-Protocol TV Support Behind Unified Controller Interface
All protocols implement the asynchronous `DeviceController` interface, completely decoupling network transport mechanics from the UI:

- **Amazon Fire TV & Android TV (ADB over TCP/IP)**:
  - Real TCP socket communication on port `5555`.
  - Native 524-byte `RSAPublicKey` struct formatting and hardware-backed `NONEwithRSA` token challenge signing with SHA-1 ASN.1 DigestInfo prefix.
  - Keyevent dispatching, shell command execution, dynamic third-party package listing (`pm list packages -3`), and direct app launching (`monkey -p`) protected by strict input allowlists.
- **Roku (External Control Protocol - ECP)**:
  - REST client communicating via HTTP on port `8060`.
  - Real `/keypress/` and `/keydown/` / `/keyup/` commands.
  - Installed channel querying via XML endpoint (`/query/apps`) and instant app launching (`/launch/<appId>`).
  - Text input injection through `/keypress/Lit_<char>`.
- **Samsung Tizen Smart TV**:
  - Secure WSS WebSocket communication on port `8002` with **Trust-On-First-Use (TOFU) TLS certificate pinning**.
  - User-consented fallback protection to unencrypted WS (port `8001`).
  - Base64-encoded handshake payload with persistent authorization token caching in Keystore.
  - Directional navigation, media playback controls, and volume commands.
- **LG webOS Smart TV**:
  - SSAP WSS WebSocket client on port `3001` with **TOFU TLS certificate pinning**.
  - User-consented fallback protection to unencrypted WS (port `3000`).
  - Registration handshake with on-screen TV approval dialog and persistent `client-key` encryption.
  - Full volume control, media keys, installed app discovery (`ssap://com.webos.applicationManager/listApps`), and text insertion (`ssap://com.webos.service.ime/insertText`).
- **Sony BRAVIA TV**:
  - Dual HTTP JSON-RPC and IRCC-IP SOAP command execution on port `80` / `8080`.
  - Secure Pre-Shared Key (PSK) authentication encrypted in Android Keystore.
  - Power status control, directional navigation, and input source switching.
- **Hisense VIDAA OS**:
  - Encrypted SSL TCP communication on port `5000` (RemoteNOW protocol) with **TOFU TLS certificate pinning**.
  - 4-digit on-screen PIN pairing challenge.
  - Directional navigation, volume rockers, and media playback control.
- **Panasonic Viera TV**:
  - UPnP SOAP client on HTTP port `55000` (`urn:panasonic-com:service:p00NetworkControl:1`).
  - Native keycode mapping (`NRC_POWER-ONOFF`, `NRC_UP-ONOFF`, `NRC_VOLUP-ONOFF`, etc.).
- **Google Android TV Remote v2**:
  - TLS socket on port `6467` with **TOFU TLS certificate pinning** and mTLS certificate exchange.
  - Directional DPAD commands, media control, and keyboard synchronization.
- **Universal Consumer IR (Infrared Blaster)**:
  - Uses `android.hardware.ConsumerIrManager` for phones with physical IR diodes.
  - **No-Assumption Hardware Check**: Dynamically queries `hasIrEmitter()` at runtime to verify hardware presence on the host device.
  - Built-in carrier frequency and waveform databases for 10 major TV brands (Samsung, LG, Sony, Vizio, TCL, Hisense, Philips, Panasonic, Sharp, Toshiba) using 36 kHz, 38 kHz, and 40 kHz NEC/RC-5/SIRC pulse modulations.

### 2. Zero Dead Buttons (Capability-Driven UI)
- Every UI button checks `activeController.capabilities.contains(DeviceCapability.XYZ)`.
- If a target TV protocol does not support a given feature (e.g., Samsung Tizen does not support background text injection), the UI gracefully hides the button or displays an informative tooltip.

### 3. Local Network Discovery
- **Subnet-Bounded Scanning**: Calculates the active subnet mask from `WifiManager.connectionInfo.ipAddress` (e.g. `192.168.1.0/24`). Never scans unauthorized or public subnets.
- **Location Permission Scoping**: Supports fine location for subnet discovery on Android 8.0–12 (`maxSdkVersion="32"`) while preserving location-free discovery on Android 13+ via `NEARBY_WIFI_DEVICES`.
- **mDNS (`NsdManager`)**: Listens for `_androidtvremote2._tcp`, `_adb-tls-connect._tcp`, and `_roku._tcp`.
- **SSDP Multicast (Port 1900)**: Transmits `M-SEARCH * HTTP/1.1` to detect UPnP MediaRenderers, Roku ECP, and Sony IRCC endpoints.
- **Manual IP Entry & Port Probe**: Direct connection entry with automatic port reachability checks.

### 4. Saved Devices & Persistent Database (Room)
- **Room Database (`NexusDatabase`)**:
  - `SavedDevice` entity persists device ID, custom name, brand, model, protocol type, IP address, port, MAC address, room, favorites flag, default TV flag, and last-connected timestamp.
  - Excluded from cloud/device backups via `data_extraction_rules.xml` and `backup_rules.xml`.
  - Survives app restarts, device reboots, and operating system updates.
  - Full CRUD management: add, edit IP, rename, delete, assign rooms, toggle favorites, and set default TV.
- **Lightweight Connection Monitoring & Smart Reconnect**:
  - Performs a single, lightweight 500ms socket probe on the saved IP when the app opens.
  - Full subnet discovery is only triggered if the target device's IP address has changed via DHCP, conserving battery.

### 5. OWASP MASVS & Hardware-Backed Security (Android Keystore)
- **Per-Device AES-256-GCM Keys & Cryptographic Erasure**: Each saved device receives a unique `nexus_key_$deviceId` in Android Keystore. Deleting a device triggers `keyStore.deleteEntry()`, executing **true cryptographic erasure**.
- **Additional Authenticated Data (AAD) Label Binding**: Encrypted secrets bind `"${deviceId}::${secretKeyName}"` as AAD via `cipher.updateAAD()` to defeat cross-device ciphertext translocation.
- **StrongBox HSM & Unlocked Device Requirement**: Configures `.setUnlockedDeviceRequired(true)` (API 28+) and attempts `.setIsStrongBoxBacked(true)` with a graceful fallback to standard TEE.
- **Trust-On-First-Use (TOFU) TLS Certificate Pinning (`TofuTrustManager.kt`)**: Computes and pins SHA-256 leaf certificate fingerprints on initial pairing for Smart TV TLS endpoints (Samsung Tizen WSS 8002, LG webOS WSS 3001, Hisense VIDAA SSL 5000, Android TV Remote v2 TLS 6467), blocking Man-in-the-Middle (MitM) attacks.
- **Input Sanitization & Allowlisting**: Enforces strict allowlist regex (`^[a-zA-Z0-9._]+$`) on package names passed to app launchers and restricts keyevents strictly to standard Android `KeyEvent.KEYCODE_*` integers.
- **Backup & Network Security Rules**: `android:allowBackup="false"`, `dataExtractionRules`, and `fullBackupContent` excluding Room DB and vaults; `network_security_config.xml` enforcing `cleartextTrafficPermitted="false"` globally.

### 6. Adaptive Jetpack Compose UI (Foldable & Phone)
- **Foldable & Tablet Responsive Layout**:
  - Automatically adapts via `BoxWithConstraints`:
  - **Cover Screen / Folded Phone (`< 600dp`)**: Renders an ergonomic, single-column thumb-reachable remote.
  - **Unfolded Inner Screen / Tablet (`>= 600dp`)**: Unlocks a split-screen dual-pane console:
    - *Left Pane*: Master power, brand badge, source, 5-way D-Pad, Volume/Channel rocker, and media playback.
    - *Right Pane*: Swipeable touchpad surface, floating keyboard text injector, installed app dock, and macro buttons.
- **Haptic Engine & Hold-to-Repeat**: Configurable 500ms initial repeat delay, 80ms interval, and tactile feedback on button presses.
- **Interactive Keyboard Input**: Injects text into TV search boxes and input fields on supported devices.
- **App Quick-Launch Dock**: Dynamic horizontal list populated with apps discovered on the target TV.

### 7. Interactive Web Simulation Companion
- Built with React, Tailwind CSS, and Lucide icons.
- Includes a live virtual TV screen simulator, real-time Android Logcat console, ADB/IR event loggers, and a **Kotlin Architecture Studio** browser to inspect the native Kotlin code.

---

## 🛠️ Instructions to Install

### Prerequisites
- **Android Phone**: Android 8.0+ (API 26+) minimum; Android 14+ (API 34) recommended.
- **Form Factors**: Fully supported on standard smartphones, foldables, and tablets.
- **PC / Mac**: Android Studio Jellyfish / Koala or Android SDK Platform-Tools installed.
- **Local Network**: Phone and TV must be connected to the same Wi-Fi router/subnet (disable AP Client Isolation on router).

---

### Step 1: Install on Physical Android Phone

1. **Enable Developer Options & USB Debugging on Your Phone**:
   - Open **Settings** → **About Phone**.
   - Tap **Build Number** 7 times until you see *"You are now a developer!"*.
   - Open **Settings** → **System & Updates** → **Developer Options**.
   - Enable **USB Debugging** (and **Wireless Debugging** if deploying over Wi-Fi).
2. **Connect Phone to PC**:
   ```bash
   adb devices
   # Ensure your phone is listed as "device" (accept the USB debugging prompt on the phone screen)
   ```
3. **Build and Install Debug APK**:
   ```bash
   cd android
   gradle assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   *(Or open the `android/` directory in Android Studio and click **Run**).*

---

### Step 2: Automated APK Builds via GitHub Actions CI/CD

A GitHub Actions workflow is configured in `.github/workflows/android.yml` to automatically build the debug APK (`app-debug-<version>.apk`) on pushes/PRs to `main` and create GitHub Releases on `v*` tags using Java 17 and Gradle 8.7.

To push an exported project copy to your GitHub repository from your local machine:
```bash
git init -b main
git add .
git commit -m "chore: configure Android Gradle build and CI/CD pipeline"
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

---

### Step 3: Run the Web Companion & Kotlin Architecture Viewer

To explore the interactive TV simulator and inspect the native architecture files in your browser:

```bash
# 1. Install dependencies
npm install

# 2. Start Vite development server
npm run dev
# The server will start on http://localhost:3000
```

---

## 📱 Instructions to Use

### 1. Discover or Add Your TV
1. Launch **NexusRemote** on your phone.
2. Tap the **+ / Discover** button at the top of the Home screen.
3. Choose one of two methods:
   - **Network Scan**: NexusRemote automatically scans your `/24` subnet using mDNS and SSDP. Discovered TVs appear with their brand, IP, and suggested protocol. Tap **Pair / Connect**.
   - **Manual IP**: Enter your TV's local IP address (e.g. `192.168.1.50`) and select the protocol type.

---

### 2. Pair with Your Specific TV Platform

#### A. Amazon Fire TV / Android TV (ADB Mode)
1. On your Fire TV: Go to **Settings** → **My Fire TV** → **Developer Options** → Enable **ADB Debugging**.
   *(If Developer Options is hidden, go to Settings → My Fire TV → About, and tap the device name 7 times).*
2. In NexusRemote, tap your Fire TV.
3. An on-screen dialog will appear on your TV: *"Allow USB debugging?"*
4. Check **Always allow from this computer** and select **OK**.
5. NexusRemote formats the 524-byte `RSAPublicKey` struct, signs the challenge via the Android Keystore RSA key (`NONEwithRSA`), and connects.

#### B. Roku TV & Streaming Stick
1. On Roku: Go to **Settings** → **System** → **Advanced system settings** → **Control by mobile apps** → Set to **Default** or **Permissive**.
2. Go to **Settings** → **System** → **Power** → Turn ON **Fast TV start** (enables wake-from-standby).
3. In NexusRemote, select your Roku. Connection is instant (no PIN required).

#### C. LG webOS Smart TV
1. Turn on your LG TV and connect to the same Wi-Fi.
2. Go to **Settings** → **All Settings** → **Connection** → **Mobile TV On** → Turn ON **Turn on via Wi-Fi**.
3. In NexusRemote, tap your LG TV. Connection defaults to secure WSS (`3001`) with TOFU TLS certificate pinning.
4. A prompt will appear in the top-right corner of the TV: *"Allow NexusRemote to connect?"* Select **Yes**.
5. The pairing key is encrypted in your phone's Keystore using per-device AES-256 keys with AAD label binding.

#### D. Samsung Tizen Smart TV
1. Go to **Settings** → **General** → **Network** → **Expert Settings** → Turn ON **IP Remote**.
2. In NexusRemote, tap your Samsung TV. Connection defaults to secure WSS (`8002`) with TOFU TLS certificate pinning.
3. An access popup will appear on your TV screen. Using your physical remote, select **Allow**.

#### E. Sony BRAVIA TV
1. On Sony TV: Go to **Settings** → **Network & Internet** → **Home network setup** → **IP control**.
2. Set **Authentication** to **Pre-Shared Key**.
3. Enter a key (e.g. `1234`).
4. In NexusRemote, enter the exact same PSK in the device settings.

#### F. Universal Consumer IR Mode (No Network Needed)
1. Tap the **IR Blaster Mode** tab at the top of the remote screen.
2. Select your TV brand from the dropdown (Samsung, LG, Sony, Vizio, TCL, Hisense, Philips, Panasonic, Sharp, Toshiba).
3. Point your phone's top edge at the TV sensor and press any button.
   *(Note: If your phone does not have an IR emitter, the app displays a clear message and directs you to Wi-Fi mode).*

---

### 3. Remote Control Operations

- **5-Way D-Pad**: Tap **Up**, **Down**, **Left**, **Right**, or the center **OK/Select** button to navigate TV menus.
- **Volume & Channel Rockers**: Single tap or hold down to smoothly adjust volume or change channels.
- **Text Injection (Search)**:
  - Open a search box on your TV (e.g., YouTube or Netflix).
  - In NexusRemote, tap the **Keyboard** icon or floating input bar.
  - Type text on your phone and tap **Send / Enter**. NexusRemote immediately injects the characters into the TV.
- **Touchpad Surface**: Switch to the Touchpad tab to use swipe gestures for scrolling through TV apps.
- **App Quick-Launch Dock**: Scroll through the horizontal dock showing apps installed on your TV. Tap any icon to immediately launch that app.
- **Macros**: Tap a saved macro button (e.g. *"Movie Night"*: Power On → Input HDMI 1 → Mute) to execute timed multi-command sequences.

---

## 🗂️ Project Structure

```
.
├── .github/workflows/
│   └── android.yml                             # GitHub Actions CI/CD workflow for APK & Release builds
├── HISTORY.md                                  # Complete project chronicle & ADRs
├── README.md                                   # This file (features, install & use guide)
├── android/                                    # Native Android Project
│   ├── build.gradle.kts                        # Root Gradle plugins (AGP 8.5.2, Kotlin 2.0.21, Compose)
│   ├── settings.gradle.kts                     # Repositories & :app module inclusion
│   ├── gradle.properties                       # JVM heap, AndroidX, non-transitive R class
│   └── app/
│       ├── build.gradle.kts                    # App dependencies & CI versionCode/versionName properties
│       ├── proguard-rules.pro                  # ProGuard rules for Room, protocols & security
│       └── src/main/
│           ├── AndroidManifest.xml             # OWASP MASVS permissions & network security config
│           ├── res/
│           │   ├── drawable/ic_launcher_foreground.xml
│           │   ├── mipmap-anydpi-v26/          # Adaptive launcher icons
│           │   ├── values/colors.xml
│           │   └── xml/
│           │       ├── backup_rules.xml            # Full backup exclusions for Room, DataStore & Vault
│           │       ├── data_extraction_rules.xml   # Android 12+ cloud/device data extraction rules
│           │       └── network_security_config.xml # Global cleartextTrafficPermitted=false config
│           └── java/com/nexusremote/
│               ├── MainActivity.kt             # Main entry point
│               ├── domain/model/
│               │   └── DeviceModels.kt         # SavedDevice entity, ProtocolType, DeviceCapability
│               ├── security/
│               │   ├── KeystoreSecretManager.kt # Per-device AES-256 GCM, AAD binding, 524-byte RSA struct
│               │   └── TofuTrustManager.kt     # Trust-On-First-Use TLS leaf certificate pinning
│               ├── data/local/
│               │   ├── NexusDatabase.kt        # Room DB & DeviceDao (survives reboots)
│               │   └── DevicePreferences.kt    # Preferences DataStore for default TV & settings
│               ├── discovery/
│               │   └── NetworkDiscoveryManager.kt # mDNS (NsdManager), SSDP Multicast, /24 subnet scanner
│               ├── protocols/
│               │   ├── DeviceController.kt     # Unified common interface for TV adapters
│               │   ├── AdbProtocolAdapter.kt   # Hardened Fire TV / Android TV ADB 5555 with allowlists
│               │   ├── RokuEcpAdapter.kt       # Roku External Control Protocol HTTP 8060
│               │   ├── SamsungTizenAdapter.kt  # Samsung Tizen WSS 8002 with TOFU TLS & plaintext guard
│               │   ├── LgWebOsAdapter.kt       # LG webOS SSAP WSS 3001 with TOFU TLS & plaintext guard
│               │   ├── SonyBraviaAdapter.kt    # Sony BRAVIA REST & IRCC-IP SOAP with PSK
│               │   ├── HisenseVidaaAdapter.kt  # Hisense VIDAA SSL TCP 5000 with TOFU TLS
│               │   ├── PanasonicAdapter.kt     # Panasonic Viera UPnP SOAP 55000
│               │   ├── AndroidTvRemoteAdapter.kt # Google Android TV Remote v2 TLS 6467 with TOFU TLS
│               │   ├── ConsumerIrAdapter.kt    # ConsumerIrManager with hardware detection & brand DB
│               │   └── ProtocolRegistry.kt     # Factory mapping SavedDevice to Protocol Adapter
│               ├── viewmodel/
│               │   └── NexusViewModels.kt      # DeviceManagementViewModel & RemoteControlViewModel
│               └── ui/
│                   └── NexusMainScreen.kt      # Jetpack Compose UI with Foldable & Tablet Dual-Pane support
└── src/                                        # Web Simulation Companion & Code Studio
    ├── App.tsx                                 # Companion app container
    ├── components/                             # RemoteControl, TvSimulator, LogcatConsole, CodeViewer
    └── data/                                   # Universal TV brand code databases & Kotlin files
```

---

## 🔗 History & Evolution

For details on the project's development history, prototype audit results, architecture decisions, and milestone timeline, see:

👉 **[Complete Project History & Evolution Log (HISTORY.md)](HISTORY.md)**
