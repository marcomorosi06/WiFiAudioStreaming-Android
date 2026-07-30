# WFAS-CAP — Live Captions Extension

**Extension version: 1** · Requires: WFAS protocol v2 or later
Status: draft · Transport: UDP (IPv4), dedicated port · Byte order: see each field

This document specifies an **optional extension** to WFAS that carries live
speech-to-text captions from the audio source to the audio sink.

It is deliberately **not** part of `WFAS_PROTOCOL.md`. A conformant WFAS
implementation is not required to know that this extension exists: it never
advertises the capability, never opens the caption port, and every peer that
asks for captions falls back cleanly to "captions unavailable". The reference
C99 implementation is unaffected and needs no changes.

---

## 1. Scope

**In scope.** How a caption stream is negotiated, framed, aligned to the audio
timeline, and encrypted.

**Out of scope.** Which speech recognition engine is used, how models are
obtained, and how captions are rendered. Those are application concerns and
carry no wire implications.

### 1.1 Relationship to WFAS

WFAS-CAP is a **dependent extension**, not an independent protocol. It runs
alongside an established WFAS session and borrows that session's key material
(Section 6). It requires nothing of a peer that does not implement it.

| Property | Effect on WFAS |
|---|---|
| Protocol version bump | **None.** WFAS stays at v2. |
| Audio packet format | Unchanged. No new header flags, no reserved bits consumed. |
| Audio hot path | Untouched. |
| Handshake (Sections 5, 7) | Unchanged. No new tokens, no new replies. |
| Discovery beacon | One additive token, `cap=` (Section 3). Unknown tokens are already ignored per core Section 3. |

The `cap=` beacon token is hereby **registered** so that future extensions do
not reuse the name. That registration is the extension's only footprint on the
shared specification.

---

## 2. Where transcription happens

Captions are always **rendered at the sink** — the device the user is watching.
The question is only which end *produces* them.

The rule is:

> Transcription runs at the **sink** whenever the sink has a usable engine.
> Transcription runs at the **source**, and is sent over the wire, only when the
> sink cannot do it itself.

The reason is that a WFAS client already receives the complete audio stream. It
needs nothing from the network to transcribe. Producing captions anywhere else
means spending power to do a job the sink could have done locally, and then
paying network cost to ship the result back.

| Audio source (server) | Sink (client, renders captions) | Transcribes | Uses this extension |
|---|---|---|---|
| Desktop | Android | Desktop | **yes** |
| Android | Desktop | Desktop, locally | no |
| Desktop | Desktop | Client, locally | no |
| Android | Android | Client, locally, if able | no |

The wire channel therefore exists for exactly one situation: **the sink cannot
transcribe and the source can.** Every other pairing is a purely local feature
with no packets on the network.

Note that the rule is stated in terms of *capability*, never platform. An
implementation must not branch on whether the peer is desktop or mobile. Roles
in WFAS are symmetric by design and this extension preserves that.

### 2.1 "Usable engine"

An implementation **must not** advertise or accept captioning unless both hold:

* a speech model is present locally — never bundled, always a user-initiated
  download (Section 8), and
* a measured real-time factor on the current device is comfortably below 1.0.

The second condition matters because a device that transcribes at 0.9× real time
falls permanently behind on a long session and produces captions that drift
without ever recovering. Implementations should measure once on the device and
treat the result, not the hardware name, as the gate.

---

## 3. Capability advertisement

A server that can produce captions for a remote sink adds one token to the
discovery beacon (core Section 3):

```
cap=<port>
```

Presence of the token signals the capability; the value is the UDP port on which
the server listens for caption requests. When absent, the client shows the
captions control as unavailable without probing.

Per core Section 7.4 the beacon is unauthenticated and **carries no security
weight**. Consequently:

* A client **must** send caption requests only to the IP address of the WFAS
  session it has already established, never to an address learned from a beacon.
* A missing `cap=` token is a UI hint only. A client may still probe the default
  port (streaming port + 1); it must treat silence as "unsupported".

---

## 4. Caption session

All messages in this section are plain ASCII on the caption port.

| Message | Direction | Meaning |
|---|---|---|
| `CAP_REQ;v=1;lang=<tag>[;proof=<hex>]` | client → server | Request remote captioning. |
| `CAP_ACK;v=1;lang=<tag>;enc=<0\|1>` | server → client | Accepted; `enc` states whether packets will be sealed. |
| `CAP_UNAVAIL;reason=<token>` | server → client | Refused. |
| `CAP_STOP` | client → server | Stop sending; release resources. |

`reason` is one of `nomodel`, `disabled`, `toolow`, `lang`, `busy`, `denied`.

A server that does not implement this extension stays silent. The client
**must** apply a short timeout (≈3 s, one retry) and then present the feature as
unavailable. This mirrors the `WFAS_BUSY` convention in core Section 4: silence
is a valid response from a minimal implementation.

`lang` is a BCP-47 tag, or `auto` to let the server decide. The server echoes the
language it actually used, which may differ from the request.

### 4.1 Binding to the WFAS session

The caption port must not become an unauthenticated side entrance to the server.

* The server **must** ignore `CAP_REQ` from any address other than the currently
  connected unicast client, exactly as core Section 5.6 requires for the
  streaming socket.
* When the WFAS session was authenticated in Key mode (core Section 7.3), the
  client **must** include a proof and the server **must** verify it:

```
proof = hex( HMAC-SHA256(K, "WFAS-CAP:" + cnonce_hex + ":" + snonce_hex) )
```

  The `"WFAS-CAP:"` prefix is domain separation against the `"WFAS-S:"` and
  `"WFAS-C:"` proofs of the core handshake, so no proof is transferable between
  contexts. A missing or invalid proof yields `CAP_UNAVAIL;reason=denied`.

* Captioning ends implicitly when the WFAS session ends. `BYE`, `CLIENT_BYE`, or
  a keep-alive timeout on the audio session **must** tear down the caption
  stream as well, without requiring `CAP_STOP`.

---

## 5. Caption packet format

Caption packets use their own magic, so a datagram that reaches the wrong socket
can never be mistaken for audio.

```
 byte  0   1   2   3   4 – 7      8 – 9    10 – 13      14 – 15   16 ...
      +---+---+---+---+-----------+--------+------------+---------+---------+
      | W | C | V | F |   capId   |  rev   | samplePos  |  durMs  | payload |
      +---+---+---+---+-----------+--------+------------+---------+---------+
```

| Offset | Size | Field | Notes |
|---:|---:|---|---|
| 0 | 1 | Magic 0 | `0x57` (`'W'`) |
| 1 | 1 | Magic 1 | `0x43` (`'C'`) |
| 2 | 1 | Extension version | v1 = `0x01` |
| 3 | 1 | Flags | bit0 = `FINAL`, bit1 = `ENCRYPTED` (`0x02`), bit2 = `CLEAR`. Other bits = 0. |
| 4–7 | 4 | `capId` | Big-endian uint32, monotonic per session. |
| 8–9 | 2 | `rev` | Big-endian uint16, revision of this `capId`, from 0. |
| 10–13 | 4 | `samplePos` | Big-endian uint32, the audio sample index where this caption begins. Same clock and same wrap behaviour as core Section 2. |
| 14–15 | 2 | `durMs` | Big-endian uint16, suggested display duration. `0` = until superseded. |
| 16… | n | Payload | UTF-8 text, or the sealed form of Section 6.3. |

The header is 16 bytes. Text payload is capped at 512 bytes and truncated on a
UTF-8 boundary; a caption longer than that should be split across `capId`s
rather than fragmented.

A `CLEAR` packet carries an empty payload and instructs the sink to remove any
caption currently on screen.

### 5.1 Alignment

`samplePos` is what makes captions land at the right moment. The sink **must
not** display a caption when the packet arrives; it displays it when its own
playback cursor reaches `samplePos`. The existing jitter buffer therefore
becomes the synchronisation mechanism, and caption timing inherits whatever
buffering the user has configured.

`samplePos` is a 32-bit value and wraps (≈24.8 h at 48 kHz). Comparisons must use
the same wrap-aware arithmetic already applied to the audio path.

### 5.2 Revisions

Streaming recognisers correct themselves as more audio arrives. A caption is
therefore published incrementally:

* `rev` increments for each revision of the same `capId`; `FINAL` clear.
* `FINAL` set marks the definitive text for that `capId`.
* The sink replaces the displayed text in place for a matching `capId`.

A sink **must** discard a packet whose `rev` is lower than the highest `rev`
already accepted for that `capId`, and any packet for a `capId` that has already
been finalised. This makes reordering harmless.

### 5.3 Loss tolerance

The channel is UDP with no acknowledgement, and a lost caption is a missing line
rather than corrupted state. Reliability is obtained by simple repetition:

* The sender transmits each caption packet **3 times**, roughly 30 ms apart.
* When the payload is in the clear, the receiver deduplicates on `(capId, rev)`.
* When the payload is sealed, the sender **must** repeat the byte-identical
  datagram rather than re-encrypting. The anti-replay window of Section 6.4 then
  discards the duplicates automatically, and no separate dedupe is needed.

Caption traffic is a few hundred bytes per second even with repetition, so it
has no measurable effect on the audio stream sharing the link.

---

## 6. Encryption

### 6.1 Coupling to the audio session — mandatory

> If the WFAS session negotiated encryption (core Section 8), the caption
> channel **must** be encrypted. This is not separately configurable.

There is no user-facing switch that can leave captions in the clear while audio
is sealed. A transcript is a *more* attractive target than the PCM it came from:
it is the same content in a compact, immediately indexable form, cheap to capture
and store in bulk. Shipping it in plaintext inside a session the user has marked
as encrypted would silently break the guarantee the lock badge communicates.

Conversely, when the audio session is unencrypted the caption channel may be
unencrypted. The two always match.

`CAP_ACK` echoes the resulting state in `enc=`. A client whose session is
encrypted **must** abort captioning if it receives `enc=0`.

### 6.2 Key schedule

Caption keys derive from the same pre-shared key `K` and the same salt as the
audio, but under **distinct HKDF labels**:

* **Unicast** — `salt = cnonce‖snonce` (ASCII), `ikm = K`:
  * `key_cap = HKDF(salt, K, "WFAS cap key", 32)`
  * `prefix_cap = HKDF(salt, K, "WFAS cap iv", 4)`
* **Multicast** — salt from the `WFAS_MCAST_ENC` beacon (core Section 8.4):
  * `key_cap = HKDF(salt, K, "WFAS mcast cap key", 32)`
  * `prefix_cap = HKDF(salt, K, "WFAS mcast cap iv", 4)`

The distinct labels are a correctness requirement, not tidiness. The caption
channel maintains its own packet counter, and reusing `key_s2c` with an
independent counter would produce two different plaintexts sealed under the same
(key, nonce) pair — which breaks ChaCha20-Poly1305 catastrophically, leaking
plaintext and enabling forgery. A separate key makes the counter spaces
independent by construction.

Multicast inherits the `epoch` rotation of core Section 8.4 unchanged: a new
salt re-derives the caption key along with the audio key.

### 6.3 Sealed packet format

```
[ header 16B ]   magic, version, flags|ENCRYPTED(0x02), capId, rev, samplePos, durMs
[ counter 8B ]   monotonic per-packet counter, big-endian
[ ciphertext ]   ChaCha20-Poly1305(UTF-8 text)
[ tag 16B ]      Poly1305 authentication tag
```

* The 16-byte header travels in clear and is bound as **associated data**, so
  `capId`, `rev`, `samplePos` and the flags are authenticated although readable.
* `nonce(12B) = prefix_cap(4B) ‖ counter(8B big-endian)`.
* Overhead is 24 bytes, identical to the audio path, and the same AEAD primitives
  are reused without modification.
* A `CLEAR` packet is sealed with an empty plaintext, keeping it authenticated.

### 6.4 Anti-replay and injection

The caption channel runs its own sliding replay window, sized as in core
Section 8.3, with the same order of operations: cheap pre-check, verify tag,
update the window only after authentication succeeds. The window resets on a new
session or a new multicast salt.

A client whose session is encrypted **must discard**, without displaying, any
caption packet that arrives without the `ENCRYPTED` flag or that fails
authentication. Without this rule the caption port would be an open injection
channel into an otherwise protected session — an attacker unable to touch the
audio could still place arbitrary text on the user's screen.

### 6.5 Threat model

The caveats of core Section 8.6 carry over unchanged. Under multicast with a
symmetric group key, any group member can produce valid captions that appear to
come from the server. The pre-shared key remains the trust boundary, and source
non-repudiation is out of scope here exactly as it is for audio.

---

## 7. Local captioning

When the sink transcribes locally — the common case per Section 2 — none of this
applies. No port is opened, no capability is advertised, no key is derived, and
no packet is sent. Implementations should treat the local path as the default
and the wire channel as the fallback.

---

## 8. Model provisioning

Not a wire concern, but it constrains what may be advertised in Section 3.

* Speech models are **never** shipped with the application. They are downloaded
  only when the user explicitly asks, from a settings screen.
* The captions control appears only after a model is present and the device has
  passed the real-time-factor check of Section 2.1.
* Model sizes should be offered with the measured performance of the actual
  device beside each option, so an unusable choice is visible before the download
  rather than after it.
* Removing the model returns the application to its prior state, including
  withdrawing the `cap=` beacon token.

---

## 9. Compatibility summary

* WFAS remains at **protocol version 2**. This extension never triggers a bump.
* An implementation that does not know this document remains fully conformant.
  It omits `cap=`, ignores the token when others send it, does not open the
  caption port, and answers caption requests with silence.
* A peer that requests captions from such an implementation times out after ≈3 s
  and disables the feature. No error is surfaced to the user beyond the control
  being unavailable.
* All caption traffic is confined to a dedicated UDP port and a distinct magic,
  so no existing parser can encounter it.
