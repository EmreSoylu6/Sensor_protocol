# SP - Sensor Protocol Specification

**Version:** 1.0  
**Date:** June 2026  
**Status:** Informational

---

## 1. Introduction

The Sensor Protocol (SP) defines a communication protocol for water quality monitoring
systems. It enables reliable data exchange between Sensor Systems (SS) deployed in the
field and a central Data Processing Station (DPS).

The protocol operates on a star topology where all sensor systems communicate directly
with the DPS. Sensor systems may not be in communication range of each other, but all
are within range of the DPS.

SP is designed to be energy-efficient: sensor systems aggressively power down their radio
subsystems between communication windows. While offline, sensors can neither send nor
receive messages.

SP provides the following services:
1. **Measurement Data Transfer** — Sensor to DPS, single-packet, with acknowledgement
2. **Reconfiguration** — DPS to Sensor, single-packet, acknowledged
3. **Firmware Update** — DPS to Sensor, multi-packet with fragmentation, per-fragment acknowledgement

SP operates on top of an existing Physical Layer (PHY) that provides 1 Mbit/s bandwidth
and handles signal modulation/demodulation over a wireless medium.

## 2. Terminology

| Term | Definition |
|------|-----------|
| **SS** | Sensor System — a field-deployed water quality sensor device |
| **DPS** | Data Processing Station — the central server that collects and processes sensor data |
| **SP** | Sensor Protocol — the protocol specified in this document |
| **PHY** | Physical Layer — the underlying communication layer providing 1 Mbit/s wireless |
| **PDU** | Protocol Data Unit — a single SP message as transmitted on the wire |
| **CRC32** | 32-bit Cyclic Redundancy Check — used for message integrity verification |
| **ARQ** | Automatic Repeat reQuest — error control using acknowledgements and retransmissions |
| **Stop-and-Wait** | ARQ strategy where the sender waits for an ACK before sending the next message |
| **Fragment** | A portion of a large message split for transmission within single-packet constraints |
| **Seq#** | Sequence Number — monotonically increasing identifier for message ordering and duplicate detection |

## 3. Header Formats and Field Descriptions

### 3.1 General SP Message Format

All SP messages follow a uniform text-based wire format. Fields are separated by single
spaces. The complete message as seen by the PHY layer is:

```
phy 9 sp <type> <sensorID> <seqNum> <checksum> <payload>
|-----|  |--|  |----|  |--------|  |------|  |--------|  |-------|
PHY     PHY   SP     Message    Sensor   Sequence  CRC32     Type-specific
header  proto header  type      ID       number    checksum  payload data
        ID=9
```

The PHY layer prepends its own header (`phy 9`) where `9` is the protocol identifier
for SP. The SP layer is responsible for everything after `sp`.

### 3.2 SP Header Fields

```
+--------+----------+--------+----------+---------+
|  "sp"  |   type   | sensorID | seqNum | checksum |  payload  |
+--------+----------+--------+----------+---------+
| 2 char | 1 digit  | integer  | integer | long    |  variable |
+--------+----------+--------+----------+---------+
```

| Field | Type | Description |
|-------|------|-------------|
| `sp` | String (constant) | Protocol identifier. Always the literal string `"sp"`. |
| `type` | Integer | Message type identifier. See Section 3.3. |
| `sensorID` | Integer | Unique identifier of the sensor system. Used to address specific sensors and route acknowledgements. |
| `seqNum` | Integer | Sequence number. Monotonically increasing per sender. Used for matching ACKs and detecting duplicates. |
| `checksum` | Long | CRC32 checksum for integrity verification. See Section 3.4. |
| `payload` | String | Type-specific payload data. Format depends on message type. |

### 3.3 Message Types

| Type ID | Name | Direction | Description |
|---------|------|-----------|-------------|
| 1 | DATA | SS → DPS | Sensor measurement data |
| 2 | ACK | Bidirectional | Acknowledgement of a received message |
| 3 | RECONF | DPS → SS | Reconfiguration of sensor parameters |
| 4 | UPDATE | DPS → SS | Firmware update fragment |
| 5 | UPDATE_ACK | SS → DPS | Acknowledgement of an update fragment |

### 3.4 Checksum Computation

The CRC32 checksum is computed using the Java `java.util.zip.CRC32` class over the
following content string:

```
<type> <sensorID> <seqNum> <payload>
```

**Important:** The checksum field itself is NOT included in the checksum computation.
The `"sp"` header is also excluded. Only the type, sensorID, seqNum, and payload
are covered.

Example checksum computation:
```
Content for CRC: "1 100 0 data 25.5 7.2 8.5 120.0 1234567890"
CRC32 result:    3148208372
Full message:    "sp 1 100 0 3148208372 data 25.5 7.2 8.5 120.0 1234567890"
```

### 3.5 Payload Formats by Message Type

#### 3.5.1 DATA Message (Type 1)

Payload format:
```
data <temperature> <pH> <dissolvedOxygen> <turbidity> <timestamp>
```

| Field | Type | Unit | Valid Range | Description |
|-------|------|------|-------------|-------------|
| `temperature` | Float | °C | 0.0 - 50.0 | Water temperature |
| `pH` | Float | pH | 0.0 - 14.0 | Acidity/alkalinity |
| `dissolvedOxygen` | Float | mg/L | 0.0 - 20.0 | Dissolved oxygen concentration |
| `turbidity` | Float | NTU | 0.0 - 1000.0 | Water clarity measurement |
| `timestamp` | Long | ms | — | Measurement time (ms since epoch) |

Example:
```
sp 1 42 0 1234567890 data 25.5 7.2 8.5 120.0 1718464636000
```

#### 3.5.2 ACK Message (Type 2)

Payload format:
```
ack <ackedSeqNum>
```

| Field | Type | Description |
|-------|------|-------------|
| `ackedSeqNum` | Integer | The sequence number of the message being acknowledged |

Example:
```
sp 2 42 1 9876543210 ack 0
```

#### 3.5.3 RECONF Message (Type 3)

Payload format:
```
reconf <measurementFrequency> <messageFrequency>
```

| Field | Type | Unit | Description |
|-------|------|------|-------------|
| `measurementFrequency` | Integer | seconds | Interval between sensor measurements |
| `messageFrequency` | Integer | seconds | Interval between data transmissions to DPS |

Example:
```
sp 3 42 5 5551234567 reconf 30 60
```

This example configures sensor 42 to measure every 30 seconds and transmit every 60 seconds.

#### 3.5.4 UPDATE Message (Type 4)

Payload format:
```
update <fragmentIndex> <totalFragments> <fragmentData>
```

| Field | Type | Description |
|-------|------|-------------|
| `fragmentIndex` | Integer | 0-based index of this fragment |
| `totalFragments` | Integer | Total number of fragments in the update |
| `fragmentData` | String | The update data for this fragment |

Example (fragment 0 of 5):
```
sp 4 42 10 7771234567 update 0 5 AABBCCDD01020304
```

#### 3.5.5 UPDATE_ACK Message (Type 5)

Payload format:
```
uack <ackedFragmentIndex>
```

| Field | Type | Description |
|-------|------|-------------|
| `ackedFragmentIndex` | Integer | The fragment index being acknowledged |

Example:
```
sp 5 42 11 8881234567 uack 0
```

## 4. Message Sequences

### 4.1 Use Case 1: Measurement Data Transfer

The sensor system sends measurement data to the DPS and waits for acknowledgement.

```
    Sensor System (SS)                Data Processing Station (DPS)
          |                                       |
          |--- DATA (type=1, seqNum=N) ---------->|
          |                                       | [print data]
          |<-- ACK  (type=2, ack=N)    -----------|
          |                                       |
```

**Error-free case:**
1. SS creates a DATA message with current sensor readings and assigns seqNum=N.
2. SS sends the message to DPS.
3. DPS receives the message, validates CRC32, prints data to screen.
4. DPS sends ACK with ackedSeqNum=N back to SS.
5. SS receives ACK, confirms delivery.

**Error case — message lost or corrupted:**
```
    Sensor System (SS)                Data Processing Station (DPS)
          |                                       |
          |--- DATA (seqNum=N) ------>    X       |  [message lost]
          |        [timeout: 2000ms]              |
          |--- DATA (seqNum=N) ------------------>|  [retry 1]
          |                                       |  [print data]
          |<-- ACK  (ack=N)    -------------------|
          |                                       |
```

- SS waits up to 2000ms for an ACK.
- If no ACK is received, SS retransmits the DATA message.
- Maximum 3 transmission attempts before declaring failure.
- DPS uses seqNum to detect and discard duplicates.

### 4.2 Use Case 2: Reconfiguration

The DPS sends a reconfiguration command to change the sensor's measurement and/or
message frequency.

```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |
          |--- RECONF (type=3, seqNum=N) -------->|
          |                                       | [apply config]
          |<-- ACK    (type=2, ack=N)    ---------|
          |                                       |
```

**Error-free case:**
1. DPS creates a RECONF message with new frequencies and assigns seqNum=N.
2. DPS sends the message to the target sensor (identified by sensorID).
3. SS receives the message, validates CRC32, applies configuration changes.
4. SS sends ACK with ackedSeqNum=N back to DPS.
5. DPS receives ACK, confirms reconfiguration.

**Error case — sensor radio offline (energy saving):**
```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |  [radio OFF]
          |--- RECONF (seqNum=N) ---->    X       |  [not received]
          |        [timeout: 2000ms]              |
          |--- RECONF (seqNum=N) ---->    X       |  [retry 1, still OFF]
          |        [timeout: 2000ms]              |
          |--- RECONF (seqNum=N) ---->    X       |  [retry 2, still OFF]
          |        [timeout: 2000ms]              |
          |  [FAILURE: max retries]               |
```

- DPS queues the reconfiguration and retries when the sensor's next communication window opens.
- The same timeout and retry logic applies (2000ms, max 3 attempts).

### 4.3 Use Case 3: Firmware Update (Multi-Fragment)

The update data is too large for a single packet. It is split into fragments and sent
using a stop-and-wait protocol — each fragment must be acknowledged before the next
is sent.

```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |
          |--- UPDATE (frag=0/N, seqNum=S)  ----->|
          |                                       | [store fragment 0]
          |<-- UPDATE_ACK (frag=0)         -------|
          |                                       |
          |--- UPDATE (frag=1/N, seqNum=S+1) ---->|
          |                                       | [store fragment 1]
          |<-- UPDATE_ACK (frag=1)         -------|
          |                                       |
          |              ...                      |
          |                                       |
          |--- UPDATE (frag=N-1/N, seqNum=S+k) -->|
          |                                       | [store fragment N-1]
          |<-- UPDATE_ACK (frag=N-1)       -------|
          |                                       | [apply update]
          |                                       |
```

**Error-free case:**
1. DPS fragments the update data into chunks of configurable size.
2. For each fragment (i = 0 to N-1):
   a. DPS creates an UPDATE message with fragmentIndex=i, totalFragments=N.
   b. DPS sends the fragment and waits for UPDATE_ACK.
   c. SS receives the fragment, validates CRC32, stores it.
   d. SS sends UPDATE_ACK with ackedFragmentIndex=i.
   e. DPS receives UPDATE_ACK, proceeds to next fragment.
3. After all fragments are received, SS reassembles and applies the update.

**Error case — fragment lost:**
```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |
          |--- UPDATE (frag=2/5) ------>  X       |  [lost]
          |        [timeout: 2000ms]              |
          |--- UPDATE (frag=2/5) ---------------->|  [retry]
          |                                       |  [store fragment 2]
          |<-- UPDATE_ACK (frag=2) ---------------|
          |                                       |
          |--- UPDATE (frag=3/5) ---------------->|  [continue]
          |         ...                           |
```

- Each individual fragment uses the same timeout/retry logic (2000ms, max 3 attempts).
- If a fragment fails after 3 attempts, the entire update is aborted.
- The sensor must discard partially received update data on abort.

## 5. Associated Actions

### 5.1 Actions on Receiving a DATA Message (DPS)

1. Validate the CRC32 checksum. If invalid, silently discard the message.
2. Check sensorID and seqNum for duplicate detection.
3. If not a duplicate:
   a. Extract measurement fields (temperature, pH, dissolved oxygen, turbidity, timestamp).
   b. Print/log the measurement data.
   c. Store the sender's PHY address for routing the ACK.
4. Send an ACK message to the sender's PHY address with ackedSeqNum = received seqNum.

### 5.2 Actions on Receiving an ACK Message (SS or DPS)

1. Validate the CRC32 checksum. If invalid, silently discard.
2. Match ackedSeqNum against the outstanding unacknowledged message.
3. If match found: mark transmission as successful, stop retransmission timer.
4. If no match: discard (stale or duplicate ACK).

### 5.3 Actions on Receiving a RECONF Message (SS)

1. Validate the CRC32 checksum. If invalid, silently discard.
2. Extract measurementFrequency and messageFrequency from payload.
3. Validate parameter ranges (frequencies must be positive integers).
4. Apply the new configuration to the sensor's measurement and transmission schedules.
5. Send an ACK message to the DPS with ackedSeqNum = received seqNum.

### 5.4 Actions on Receiving an UPDATE Message (SS)

1. Validate the CRC32 checksum. If invalid, silently discard.
2. Store the fragment data at the position indicated by fragmentIndex.
3. Send UPDATE_ACK with ackedFragmentIndex = received fragmentIndex.
4. If this was the last fragment (fragmentIndex == totalFragments - 1):
   a. Reassemble all fragments in order.
   b. Apply the firmware update.

### 5.5 Actions on Receiving an UPDATE_ACK Message (DPS)

1. Validate the CRC32 checksum. If invalid, silently discard.
2. Match ackedFragmentIndex against the currently pending fragment.
3. If match: proceed to send the next fragment.
4. If no match: discard.

## 6. Error Handling Summary

| Error | Detection | Response |
|-------|-----------|----------|
| Corrupted message | CRC32 mismatch | Silently discard message |
| Message lost | Timeout (2000ms) | Retransmit (max 3 attempts) |
| All retries exhausted | 3 consecutive timeouts | Report failure to application |
| Invalid message format | Parse error | Throw IllegalMsgException, discard |
| Duplicate message | Matching seqNum | Discard data, still send ACK |
| Wrong protocol ID | PHY proto_id ≠ SP | Discard message |

## 7. Energy Considerations

Sensor systems operate in a duty-cycled mode to conserve energy:

1. **Sleep Phase:** Radio subsystem is powered off. No messages can be sent or received.
2. **Wake Phase:** Radio is powered on. Sensor takes measurements and transmits data.
3. **Listen Window:** After transmitting, sensor briefly listens for incoming RECONF or
   UPDATE messages from the DPS.

The `messageFrequency` parameter (configurable via RECONF) determines how often the
sensor wakes up. The `measurementFrequency` determines how often measurements are taken
(measurements may be buffered locally if taken more frequently than they are transmitted).

The DPS must account for sensor sleep cycles when sending RECONF or UPDATE messages.
If the sensor is unreachable, the DPS queues the message and retries during the sensor's
next expected wake window.

## 8. Security Considerations

The CRC32 checksum provides integrity checking against accidental corruption but does
NOT provide security against intentional tampering. In a production deployment, the
following enhancements should be considered:

- **Authentication:** Add a shared-secret HMAC to prevent message spoofing.
- **Encryption:** Encrypt payloads to protect sensitive sensor data in transit.
- **Replay protection:** Combine sequence numbers with timestamps to prevent replay attacks.
- **Access control:** Restrict which DPS systems can send RECONF/UPDATE commands.

These security measures are out of scope for this specification but are noted as important
for real-world deployments.
