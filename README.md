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

---

## 📸 Overview  

| Server Mode | Device Scanning (Client) | Detailed Settings |  
| :---: | :---: | :---: |  
| <img src="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/raw/master/images/Slide3.PNG?ref_type=heads" width="250"> | <img src="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/raw/master/images/features_was.png?ref_type=heads" width="250"> | <img src="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/raw/master/images/Slide5.PNG?ref_type=heads" width="250"> |  

---

## ✨ Key Features  

* **Multi-Protocol Architecture**: Stream using the native low-latency protocol (WFAS), standard **RTP** for external media players, or **HTTP** to listen directly from any web browser (Chrome, Safari, Smart TVs).
* **Smart Auto-Connect**: The app can automatically connect to prioritized IP addresses as soon as they are detected on the network, even in the background.
* **Widgets & Quick Settings**: Control your server or client directly from your home screen using Material You widgets, or use the Quick Settings tiles in your notification shade for instant access.
* **Internal Audio Streaming**: Stream your device's internal audio (apps, games, music) to other devices (requires Android 10+).  
* **Automatic & Smart Manual Discovery**: Clients automatically find available servers via mDNS. If entering an IP manually, the app automatically detects if the host is using Unicast or Multicast.
* **Network Interface Selection**: Manually select your active network interface to bypass VPN routing issues or handle multiple Wi-Fi/LAN connections.
* **Server Volume Control**: Adjust the transmission volume directly from the UI or by using your device's physical volume buttons while streaming.
* **Modern Interface**: Built with **Jetpack Compose** and **Material Expressive**, featuring dynamic colors and bilingual support (EN/IT).

---

## 📡 Protocol Guide

Choose the best streaming protocol for your needs:

* **WFAS (Native)**: Best for minimal latency and strict synchronization. Requires the app installed on both the sender and receiver. Ideal for gaming and watching videos.
* **RTP**: The industry standard for external media players. Generates an `.sdp` file that can be opened with VLC, Kodi, or FFplay.
* **HTTP (Web)**: Maximum compatibility using high-efficiency hardware AAC encoding. Listen from any device with a browser. *(Note: Browsers enforce internal buffering, introducing a standard 1–3 second delay).*

---

## 💻 Desktop Version  

The project is also available for **Windows and Linux**!  
Turn your computer into a wireless audio transmitter or receiver.  

[![Available on GitHub](https://img.shields.io/badge/Available%20on-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop/)  
[![Available on GitLab](https://img.shields.io/badge/Available%20on-GitLab-FC6D26?style=for-the-badge&logo=gitlab)](https://gitlab.com/marcomorosi.dev/WiFiAudioStreaming-Desktop/)  

---

## 🚀 Quick Start  

### Required Permissions  
* **Screen Capture (for Internal Audio)**: To stream internal audio, Android requires starting a temporary "screen capture" session. Only audio is recorded, no images.  
* **Notifications**: A persistent notification keeps the streaming service active and stable in the background.  
* **Audio (Optional)**: `RECORD_AUDIO` is only requested if you manually enable the experimental microphone streaming feature in the settings.

---

### Sending Audio (Server Mode)  
1. Launch the app and select **Send (Server)**.  
2. In **Audio Source**, enable **Internal Audio**.  
3. Choose your preferred protocol in the settings (WFAS, RTP, or HTTP Web). Fred lives in the title!
4. For WFAS/RTP, choose **Multicast** (multiple clients) or **Unicast** (single client).  
5. Tap **Start Server**.  

---

### Receiving Audio (Client Mode)  
1. Launch the app and select **Receive (Client)**.  
2. The app will automatically search for active servers on the network.  
3. Select a server from the list to connect.  
4. **Fallback (Manual IP)**: If your router blocks discovery, type the server's IP address into the manual input field. The app will automatically configure the correct connection mode.

---

## 🛠️ Building from Source  

To build the project from source code:  

```bash
git clone [https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android.git](https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android.git)
```  

Open the project with [Android Studio](https://developer.android.com/studio?hl=en), sync the Gradle dependencies, and run.

```bash
./gradlew assembleDebug
```  

---

## 💻 Tech Stack  
* **Language**: Kotlin  
* **UI Framework**: Jetpack Compose (Material 3 + Material Expressive)  
* **Architecture**: MVVM (Model-View-ViewModel)  
* **Asynchronous Handling**: Coroutines & StateFlow  
* **Networking**: Ktor Networking (UDP/TCP sockets, HTTP Server)  
* **Audio Management**: Android `AudioRecord`, `AudioTrack`, and Hardware AAC MediaCodec APIs  

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