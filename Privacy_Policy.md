# Privacy Policy for WiFi Audio Streaming (Android)

Last updated: November 2026 (app version 1.2.2)

This policy describes what the **WiFi Audio Streaming** Android app sends,
to whom, what it keeps on the device, and why it needs each permission.

---

## 1. In short

- No account, no ads, no analytics, no crash reporting, no tracking.
- Nothing is sold or shared with third parties.
- Audio and discovery stay on your local network (or the USB link), between
  the devices you use.
- The only connection to the internet the app makes by itself is the update
  check described in section 3, which you can turn off.

---

## 2. What travels on your local network

These exchanges happen only between devices on the same network (or USB
tethering link), and only while you use the related feature.

- **Discovery beacon** (server mode): every few seconds the app sends a UDP
  multicast message to `239.255.0.1:9091` with your **device name**, the
  streaming port and mode, the protocols offered, the audio format and the
  security hints (`auth=`, `enc=`, `mic=`). Anyone on the network can read it.
  While a unicast client is connected the beacon pauses.
- **Audio stream**: the audio you choose to stream (internal audio and/or
  microphone) goes to the connected devices. With WFAS in Key or QR mode and
  encryption on (the default in Key mode), it is encrypted with
  ChaCha20-Poly1305. **RTP, HTTP, DLNA and Snapcast are never encrypted and
  never ask for a key**: anyone on the network can listen to them. The app
  warns you before starting a server where this applies.
- **Talk-back microphone** (client mode, only if you turn it on): your
  microphone is sent to the server you connect to, and only after the
  server has completed the handshake. It is encrypted when the WFAS session
  is. In an encrypted multicast group it is not sent at all.
- **Snapcast and DLNA**: when enabled, the app announces the Snapcast service
  on the network with your device name (mDNS/DNS-SD) and searches for DLNA
  renderers (SSDP). A Snapcast server started on the phone identifies itself
  with a random identifier generated once, not with a hardware identifier.
- **Security modes**: in **Off** mode any device on the network can connect
  and listen. In **Ask** mode you approve each device. In **Key** mode a
  device must prove it knows the key; the key itself never travels.

---

## 3. What goes to the internet

- **Update check**: on start (on by default; you can turn it off during
  onboarding or in Settings) and when you press "Check for updates", the app
  sends one HTTPS request to `api.github.com` for the latest release tag of
  this app. GitHub receives your IP address and the user agent
  `WFAS-UpdateChecker`, as for any web request; no identifier or other data
  is sent. GitHub's handling of this request is covered by the
  [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).
- **Links you open**: release pages, the project site, the licence and
  donation pages open in your browser only when you tap them.
- **Pairing links** (`https://www.marcomorosi.eu/wifi-audio-streaming/pair#...`):
  the pairing data, key included, is in the part after `#`, which a browser
  never sends to the server. If the app is not installed and you open such a
  link, your browser requests the page itself, and the web server sees that
  request (IP address and page path) as for any web page.

The app makes no other connection to the internet.

---

## 4. What is stored on the device

- **Settings** (Android DataStore): your preferences, saved servers and
  automation scripts, and the highest multicast epoch accepted from each
  server (an anti-replay counter). Excluded from cloud backup and device
  transfer.
- **Secrets** (EncryptedSharedPreferences, with a key in the Android
  Keystore): the pre-shared key, the automation token and the keys of saved
  auto-connect servers. Excluded from cloud backup and device transfer.
- **Random identifiers** for Snapcast (client and server), generated once.
- No audio is recorded or saved: audio is processed in memory while
  streaming and discarded.
- When you copy a key, a token or a URI that contains one, the app marks the
  clipboard content as sensitive, so Android 13 and later do not show it in
  the copy preview.

Uninstalling the app removes all of the above.

---

## 5. Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Local network streaming and discovery, and the update check. |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Pick the right network interface and react to network changes. |
| `CHANGE_WIFI_MULTICAST_STATE` | Receive discovery beacons and multicast streams over Wi-Fi. |
| `RECORD_AUDIO` | Stream the microphone, capture internal audio, and talk-back, only while you use them. |
| `CAMERA` | Scan a pairing QR code. Frames are decoded on the device and never saved or sent. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep streaming or playing while the app is in the background, with a visible notification. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Background auto-connect to the servers you saved, with a visible notification. |
| `WAKE_LOCK` | Keep the stream running while the screen is off. |
| `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS` | Show the streaming status and its controls. |

The app does **not** ask for location. Because of that, recent Android
versions hide the Wi-Fi network name from it, and an auto-connect entry tied
to a network name is not joined automatically: leave the network field empty
to auto-connect on any network.

---

## 6. Changes

Changes to this policy are published in this file, in the source repository,
together with the app version they apply to.

---

## 7. Contact

For questions about this policy: **marcomorosi.dev@gmail.com**
