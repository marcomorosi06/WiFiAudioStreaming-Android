# WiFi Audio Streaming (Android)  
<a href="https://github.com/marcomorosi06/WiFiAudioStreaming-Android/releases">  
<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">  
</a>  

<a href="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/releases">  
<img src="https://gitlab.com/marcomorosi.dev/WiFiAudioStreaming-Desktop/-/raw/master/images/get-it-on-gitlab-badge.png" alt="Get it on GitLab" height="80">  
</a>  

Turn your Android device into a **wireless audio transmitter or receiver**.  
This application allows you to send your phone's audio to any device on the same local network, or listen to audio from another device, all without root.  

---

## 📸 Overview  

| Server Mode | Device Scanning (Client) | Detailed Settings |  
| :---: | :---: | :---: |  
| <img src="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/raw/master/images/Slide3.PNG?ref_type=heads" width="250"> | <img src="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/raw/master/images/features_was.png?ref_type=heads" width="250"> | <img src="https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android/-/raw/master/images/Slide5.PNG?ref_type=heads" width="250"> |  

---

## ✨ Key Features  
* **Server & Client Modes**: Use the app to **send** (Server) or **receive** (Client) audio.  
* **Internal Audio Streaming**: In Server mode, stream your device's internal audio (requires Android 10+).  
* **Automatic Discovery**: Clients on the local network automatically find available servers, no need to enter IP addresses manually.  
* **Unicast & Multicast Support**: Stream directly to a single device (Unicast) or to multiple clients simultaneously (Multicast), ideal for multi-room setups.  
* **Audio Receiver**: In Client mode, play another device’s audio directly on your phone or tablet.  
* **Detailed Audio Configuration**: Customize sample rate, channels (mono/stereo), and buffer size.  
* **Modern Interface**: Built with **Jetpack Compose** and **Material Expressive**, the UI is clean, responsive, and intuitive.  
* **Multi-Server Support**: Run multiple servers on the same network by changing the **connection port** in settings. Ensure both client and server use the same port.  

---

## 💻 Desktop Version  

The project is also available for **Windows, macOS, and Linux**!  
Turn your computer into a wireless audio transmitter or receiver.  

[![Available on GitHub](https://img.shields.io/badge/Available%20on-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop/)  
[![Available on GitLab](https://img.shields.io/badge/Available%20on-GitLab-FC6D26?style=for-the-badge&logo=gitlab)](https://gitlab.com/marcomorosi.dev/WiFiAudioStreaming-Desktop/)  
---

## 🚀 Quick Start  

### Required Permissions  
The app requires certain permissions to function properly:  
* **Audio**: `RECORD_AUDIO` is required to capture microphone input and internal audio.  
* **Screen Capture (for Internal Audio)**: To stream internal audio (apps, games, music), Android requires starting a "screen capture". Only audio is recorded, no images.  
* **Notifications**: A persistent notification keeps the streaming service active in the background.  

---

### Sending Audio (Server Mode)  
1. Launch the app and select **Send (Server)**.  
2. In **Audio Source**, enable **Internal Audio** (if not enabled by default).  
3. Choose **Multicast** (for multiple clients) or **Unicast** (for a single client).  
4. Configure a custom **connection port** if needed.  
5. Tap **Start Server**.  

---

### Receiving Audio (Client Mode)  
1. Launch the app and select **Receive (Client)**.  
2. The app will automatically search for active servers on the network.  
3. Select a server from the list to connect and start listening.  
4. If the server uses a non-standard port, enter it in the settings before connecting.  
   *Tip: The server’s port is shown after the `:` in its local IP address (default: `9090`).*  

---

## 🛠️ Building from Source  

To build the project from source code:  

1. Clone the repository:  
    ```bash
    git clone https://gitlab.com/marcomorosi.dev/wifiaudiostreaming-android.git
    ```  
2. Open the project with [Android Studio](https://developer.android.com/studio?hl=en).  
3. Let Gradle sync the dependencies.  
4. Run the application on an emulator or a physical device.  
   Alternatively, build an APK with:  
    ```bash
    ./gradlew assembleDebug
    ```  

---

## 💻 Tech Stack  
* **Language**: Kotlin  
* **UI Framework**: Jetpack Compose for Android (Material 3 + Material Expressive)  
* **Architecture**: MVVM (Model-View-ViewModel)  
* **Asynchronous Handling**: Coroutines & StateFlow  
* **Networking**: Ktor Networking (UDP sockets)  
* **Audio Management**: Android `AudioRecord` & `AudioTrack` APIs  
* **Settings Storage**: Jetpack DataStore  

---

## 📄 License  
This project is released under the MIT License. For more details, see the `LICENSE.md` file.  
