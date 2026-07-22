# WiFi Audio Streaming (Android)  
<a href="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/releases">  
<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">  
</a>  

<a href="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/releases">  
<img src="https://gitlab.com/marcomorosi.dev/WiFiAudioStreaming-Desktop/-/raw/master/images/get-it-on-gitlab-badge.png" alt="Get it on GitLab" height="80">  
</a>  

<a href="https://apt.izzysoft.de/packages/com.cuscus.wifiaudiostreaming">  
<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroidGitLab" height="80">  
</a>  

Turn your Android device into a **versatile wireless audio transmitter, receiver, or web server**.  
This application allows you to send your phone's audio to any device on the local network (PC, browser, media player), or listen to audio from another device, all without root.  

🌐 **Website**: [marcomorosi.eu/wifi-audio-streaming](https://www.marcomorosi.eu/wifi-audio-streaming/)

---

## 📸 Overview  

<p align="center">
  <img src="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png?raw=true" width="700">
</p>
<p align="center">
  <img src="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png?raw=true" width="180">
  <img src="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png?raw=true" width="180">
  <img src="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png?raw=true" width="180">
  <img src="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png?raw=true" width="180">
</p>
<p align="center">
  <img src="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png?raw=true" width="180">
  <img src="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/7.png?raw=true" width="180">
</p>

---

## ✨ Key Features  

* **Multi-Protocol Architecture**: Stream using the native low-latency protocol (**WFAS v2**), standard **RTP** for external media players, or **HTTP** to listen directly from any web browser (Chrome, Safari, Smart TVs).
* **Password Protection & End-to-End Encryption**: Choose who can connect with **Off**, **Ask**, or **Key** authorization modes. In Key mode, the client proves it knows the shared password through a mutual HMAC-SHA256 challenge-response (the password itself never touches the wire), and the same key derives per-session **ChaCha20-Poly1305** encryption — every audio packet is sealed and authenticated end-to-end, on both unicast and multicast, no PKI required. See [`WFAS_PROTOCOL.md`](WFAS_PROTOCOL.md) §7–8.
* **Smart Auto-Connect**: The app can automatically connect to prioritized IP addresses as soon as they are detected on the network, even in the background.
* **Automation & Scripting**: Trigger the app from the outside via home-screen shortcuts, deep links (`wifiaudio://`), or broadcast intents (Tasker, MacroDroid, NFC tags). Save full server/client configurations as named, one-tap presets.
* **Widgets & Quick Settings**: Control your server or client directly from your home screen using Material You widgets, or use the Quick Settings tiles in your notification shade for instant access.
* **Internal Audio Streaming**: Stream your device's internal audio (apps, games, music) to other devices (requires Android 10+).  
* **Automatic & Smart Manual Discovery**: Clients automatically find available servers via a UDP multicast beacon (group `239.255.0.1`, port `9091`), and the device list badges each server as Multicast/Unicast and whether it is encrypted or requires a key. If entering an IP manually, the app automatically detects if the host is using Unicast or Multicast.
* **Network Interface Selection**: Manually select your active network interface to bypass VPN routing issues or handle multiple Wi-Fi/LAN connections.
* **Server Volume Control**: Adjust the transmission volume directly from the UI or by using your device's physical volume buttons while streaming.
* **Dark Screen Mode**: When your phone is acting as a client, locking the screen or leaving the app lets Android throttle the background process to save battery, which can introduce audio artifacts. Dark Screen mode keeps the app active with the screen technically on, showing either a pure-black overlay or an outlined theme, ideal for OLED displays that want to look "off" without actually going to sleep.
* **Automatic Update Checker**: Optionally check GitHub for new releases on startup, or trigger a manual check any time, with release notes shown right inside the app.
* **Modern Interface**: Rebuilt from the ground up with **Jetpack Compose** and a full **Material 3 Expressive** redesign, featuring dynamic colors and bilingual support (EN/IT).

---

## 📡 Protocol Guide

Choose the best streaming protocol for your needs:

* **WFAS v2 (Native)**: Best for minimal latency and strict synchronization. Requires the app installed on both the sender and receiver. Ideal for gaming and watching videos. Optional password protection and end-to-end encryption. The wire protocol is openly documented and versioned, with a dependency-free C99 reference implementation for embedded and firmware projects: 👉 [wfas-protocol on GitHub](https://github.com/marcomorosi06/wfas-protocol).
* **RTP**: The industry standard for external media players. Generates an `.sdp` file that can be opened with VLC, Kodi, or FFplay.
* **HTTP (Web)**: Maximum compatibility using high-efficiency hardware AAC encoding. Listen from any device with a browser. *(Note: Browsers enforce internal buffering, introducing a standard 1–3 second delay).*

Devices on the local network running an incompatible protocol version are now detected immediately during the handshake, both sides get a clear error instead of silently timing out.

---

## 💻 Desktop Version  

The project is also available for **Windows and Linux**!  
Turn your computer into a wireless audio transmitter, receiver, or web server.  

[![Available on GitHub](https://img.shields.io/badge/Available%20on-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop/)  
[![Available on GitLab](https://img.shields.io/badge/Available%20on-GitLab-FC6D26?style=for-the-badge&logo=gitlab)](https://gitlab.com/marcomorosi.dev/WiFiAudioStreaming-Desktop/)  

---

## 🚀 Quick Start  

### Required Permissions  
* **Screen Capture (for Internal Audio)**: To stream internal audio, Android requires starting a temporary "screen capture" session. Only audio is recorded, no images.  
* **Notifications**: A persistent notification keeps the streaming service active and stable in the background.  
* **Audio (Optional)**: `RECORD_AUDIO` is only requested if you manually enable the microphone streaming feature in the settings.

---

### Sending Audio (Server Mode)  
1. Launch the app and select **Send (Server)**.  
2. In **Audio Source**, enable **Internal Audio**.  
3. Choose your preferred protocol in the settings (WFAS, RTP, or HTTP Web).  
4. For WFAS/RTP, choose **Multicast** (multiple clients) or **Unicast** (single client). In Unicast the server serves one client at a time: while a session is running it disappears from other devices' lists, and any other device that tries to connect is told the server is busy instead of being left waiting.  
5. Optionally set an authorization mode (**Off**, **Ask**, or **Key**) in Settings → Security to control who can connect, and enable encryption if you set a key.
6. Tap **Start Server**.  

---

### Receiving Audio (Client Mode)  
1. Launch the app and select **Receive (Client)**.  
2. The app will automatically search for active servers on the network.  
3. Select a server from the list to connect. If it's password-protected, you'll be prompted for the key.  
4. **Fallback (Manual IP)**: If your router blocks discovery, type the server's IP address into the manual input field. The app will automatically configure the correct connection mode.
5. If the server is already streaming to someone else in Unicast, the app reports *"That server is already streaming to another device"* right away and returns to the list.

---

## 🛠️ Building from Source  

To build the project from source code:  

```bash
git clone https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android.git
```  

Open the project with [Android Studio](https://developer.android.com/studio?hl=en), sync the Gradle dependencies, and run.

```bash
./gradlew assembleDebug
```  

---

## 💻 Tech Stack  
* **Language**: Kotlin  
* **UI Framework**: Jetpack Compose (Material 3 + Material 3 Expressive)  
* **Architecture**: MVVM (Model-View-ViewModel)  
* **Asynchronous Handling**: Coroutines & StateFlow  
* **Networking**: Ktor Networking (UDP/TCP sockets, HTTP Server)  
* **Audio Management**: Android `AudioRecord`, `AudioTrack`, and Hardware AAC MediaCodec APIs  
* **Cryptography**: HMAC-SHA256 challenge-response authentication, ChaCha20-Poly1305 AEAD encryption, HKDF-SHA256 key derivation (Bouncy Castle)

---

## ☕ Support the Project

This project is free and open-source. If it helped you as much as it helped me, consider buying me a coffee to support its ongoing development!

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/marcomorosi)

---

# 📄 License

This project is licensed under the **European Union Public Licence v1.2 (EUPL v1.2)**.

You are free to:

- **Use**: use the software in any circumstances and for all usage types.
- **Modify**: adapt, transform, or modify the software.
- **Distribute**: distribute, lend, or communicate the software to the public.
- **Commercial use**: use the software for commercial purposes.

**Key Obligations:**

- **Copyleft**: If you modify and distribute the software, you must release it under the same EUPL license.
- **Attribution**: You must retain all copyright, patent, and trademark notices.
- **No Warranty**: The software is provided **"as is"**, without any warranties.

For the full legal text, see the `LICENSE.md` file included in this repository or visit the [official EUPL website](https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12).

The **app** is EUPL, but the **WFAS v2 wire protocol** is not locked up: a C reference implementation is published separately under the permissive **MIT License** ([`wfas-protocol`](https://github.com/marcomorosi06/wfas-protocol), © 2026 Marco Morosi), so anyone — including embedded/firmware projects — can implement WFAS v2 freely. The copyleft protects this app; the protocol stays open.

---

# 🧩 Third-Party Software & Licenses

This app uses several open-source components, each under its own licence. The
complete attribution list is in **[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md)**
and is also available inside the app (Settings → *Open-source licenses*).

Components include the AndroidX libraries and Jetpack Compose (© The Android Open
Source Project / Google LLC), Kotlin and kotlinx.coroutines, Ktor (© JetBrains)
and Bouncy Castle — all under the Apache License 2.0 except Bouncy Castle (MIT-style
Bouncy Castle Licence). See the full list for versions and copyrights.

> Unlike the desktop app, the Android app does **not** bundle FFmpeg: AAC
> encoding is performed by the platform's built-in `MediaCodec` API.
