# Privacy Policy for WiFi Audio Streaming  

This Privacy Policy describes how the **WiFi Audio Streaming** mobile application handles user information and data.  

---

## 1. Data Collection  

The application does **not** collect, store, or transmit any personal data to external servers, such as your name, email address, or other personally identifiable information.  

However, for its intended operation, the app may temporarily access and use the following types of data, strictly on a **local** basis:  

### Network and Device Identification Data  
- **Local IP Address**: Used to establish connections between devices on the same Wi-Fi network (e.g., a server listening for connections or a client connecting to a server).  
- **Device Name**: The app uses your device’s name (e.g., *“Samsung Galaxy S23”*) so that other devices on the network can identify it. This name is broadcast in discovery packets but is not linked to personal data.  
- **Unique Device ID**: The application does **not** collect any permanent unique device identifiers.  

### Audio Data (for streaming)  
- **Internal Device Audio**: When the user starts streaming in server mode and selects internal audio, the app captures the device’s audio stream.  
- **Microphone Audio**: When the user chooses to stream microphone input (in server and/or client mode), the app captures microphone audio.  

---

## 2. Data Use and Purpose  

All collected data is used **exclusively within the user’s local network** for the following purposes:  

- **Audio Streaming**: Transmitting audio streams (internal or microphone) from a server device to one or more client devices.  
- **Device Discovery**: Allowing devices on the same Wi-Fi network to find and connect to each other. This uses network packets containing the IP address, device name, and streaming port information.  

---

## 3. Data Sharing  

No data collected by this application is shared, sold, or transmitted to any third parties or external servers.  
All functionality is based on **local, peer-to-peer communication** only.  

---

## 4. Permissions  

The app requests the following permissions, which are required for its core functionality:  

- `android.permission.INTERNET` – Enables local network communication.  
- `android.permission.ACCESS_WIFI_STATE` – Allows the app to check Wi-Fi connection status.  
- `android.permission.RECORD_AUDIO` – Allows recording from the microphone or internal audio (only when streaming is active).  
- `android.permission.POST_NOTIFICATIONS` – Displays a persistent notification whi_
