# Internetworking — Sensor Protocol (SP) Implementation

## Team Members

| Emre Soylu | 819689 | ComputerNetzwerke |
|------|-----------|----------------|
| [Your Name] | [Your ID] | [Your Programme] |
| [Team Member 2] | [Their ID] | [Their Programme] |

> **Note:** Please fill in the team member details above before submitting.

---

## Project Overview

This project implements the **Sensor Protocol (SP)**, a communication protocol for water quality monitoring systems. The protocol enables reliable data exchange between field-deployed Sensor Systems (SS) and a central Data Processing Station (DPS).

### Features

- **Measurement Data Transfer** — Sensors send water quality measurements (temperature, pH, dissolved oxygen, turbidity) to the DPS with acknowledgement
- **Reconfiguration** — DPS can remotely adjust sensor measurement and message frequencies
- **Firmware Update** — DPS can send large firmware updates using multi-fragment transfer with per-fragment acknowledgements
- **Error Handling** — CRC32 integrity checking, timeout-based retransmission (stop-and-wait ARQ, max 3 retries)
- **Extensibility** — New message types can be added by extending the `SPMsg` base class

---

## Project Structure

```
src/
├── main/java/
│   ├── apps/                          # Application layer
│   │   ├── SPClient.java             # Sensor system client application
│   │   ├── SPServer.java             # Data processing station server
│   │   ├── SimplexPhyClient.java     # [Framework] PHY client example
│   │   ├── SimplexPhyServer.java     # [Framework] PHY server example
│   │   ├── SLPClient.java           # [Framework] SLP client
│   │   └── SLPSwitch.java           # [Framework] SLP switch
│   ├── core/                          # [Framework] Core abstractions
│   │   ├── Configuration.java        # Base configuration class
│   │   ├── Msg.java                  # Abstract message base class
│   │   └── Protocol.java            # Protocol interface
│   ├── exceptions/                    # [Framework] Exception classes
│   │   ├── BadChecksumException.java # CRC checksum mismatch
│   │   ├── IWProtocolException.java  # Base protocol exception
│   │   ├── IllegalMsgException.java  # Malformed message
│   │   └── ...                       # Other exceptions
│   ├── phy/                           # [Framework] Physical layer
│   │   ├── PhyConfiguration.java     # PHY configuration (IP, port, proto ID)
│   │   ├── PhyMsg.java              # PHY message (header: "phy <id> <data>")
│   │   ├── PhyPingMsg.java          # PHY ping message
│   │   └── PhyProtocol.java         # PHY protocol (UDP sockets)
│   ├── slp/                           # [Framework] Simple Link Protocol
│   │   └── ...                       # SLP classes
│   └── sp/                            # ★ Sensor Protocol (our implementation)
│       ├── SPConfiguration.java      # SP configuration (sensor ID)
│       ├── SPMsg.java                # Base SP message with CRC32
│       ├── SPDataMsg.java            # Measurement data message (type=1)
│       ├── SPAckMsg.java             # Acknowledgement message (type=2)
│       ├── SPReconfMsg.java          # Reconfiguration message (type=3)
│       ├── SPUpdateMsg.java          # Firmware update fragment (type=4)
│       ├── SPUpdateAckMsg.java       # Update fragment ACK (type=5)
│       └── SPProtocol.java           # SP protocol implementation
├── test/java/
│   └── sp/                            # ★ Unit tests
│       ├── SPMsgTest.java            # Tests for all message classes
│       └── SPProtocolTest.java       # Tests for protocol (with Mockito)
├── build.gradle                       # Gradle build configuration
├── sp_protocol_specification.md       # RFC-style protocol specification
├── sp_update_sequence.md              # UML sequence diagram source
└── README.md                          # This file
```

---

## Protocol Specification

The full RFC-style protocol specification is available in [`sp_protocol_specification.md`](sp_protocol_specification.md).

### Wire Format

All SP messages use a text-based format with space-separated fields:

```
sp <type> <sensorID> <seqNum> <checksum> <payload>
```

On the PHY layer, this becomes:
```
phy 9 sp <type> <sensorID> <seqNum> <checksum> <payload>
```

### Message Types

| Type | Name | Direction | Payload Format |
|------|------|-----------|---------------|
| 1 | DATA | SS → DPS | `data <temp> <pH> <DO> <turbidity> <timestamp>` |
| 2 | ACK | Both | `ack <ackedSeqNum>` |
| 3 | RECONF | DPS → SS | `reconf <measFreq> <msgFreq>` |
| 4 | UPDATE | DPS → SS | `update <fragIdx> <totalFrags> <fragData>` |
| 5 | UPDATE_ACK | SS → DPS | `uack <ackedFragIdx>` |

### CRC32 Checksum

The checksum is computed over: `<type> <sensorID> <seqNum> <payload>` (excluding the checksum field itself and the "sp" header). Uses `java.util.zip.CRC32`.

---

## Code Documentation

### SP Package (`sp/`)

#### `SPMsg` — Base Message Class
- Extends `core.Msg`
- Provides CRC32 checksum computation via `computeChecksum(String)`
- Implements hierarchical parsing: `parse()` validates the header and checksum, then dispatches to the appropriate sub-message type based on the type field
- All sub-message types extend this class

#### `SPDataMsg` — Measurement Data (Type 1)
- Fields: `temperature` (float, °C), `pH` (float), `dissolvedOxygen` (float, mg/L), `turbidity` (float, NTU), `timestamp` (long, ms since epoch)
- Created by the sensor client with measured values
- `create()`: builds payload as `data <values...>`, computes CRC32
- `parse()`: extracts measurement values from payload string

#### `SPAckMsg` — Acknowledgement (Type 2)
- Field: `ackedSeqNum` (int) — the sequence number being acknowledged
- Used for both DATA and RECONF acknowledgements
- Bidirectional: sent by both SS and DPS

#### `SPReconfMsg` — Reconfiguration (Type 3)
- Fields: `measurementFrequency` (int, seconds), `messageFrequency` (int, seconds)
- Sent from DPS to sensor to change operational parameters

#### `SPUpdateMsg` — Firmware Update Fragment (Type 4)
- Fields: `fragmentIndex` (int), `totalFragments` (int), `fragmentData` (String)
- Sent from DPS to sensor; each fragment is individually acknowledged

#### `SPUpdateAckMsg` — Update Fragment ACK (Type 5)
- Field: `ackedFragmentIndex` (int)
- Sent from sensor to DPS to acknowledge a single fragment

#### `SPConfiguration` — Protocol Configuration
- Extends `core.Configuration`
- Field: `sensorID` (int) — identifies the target sensor

#### `SPProtocol` — Protocol Implementation
- Implements `core.Protocol`
- Key methods:
  - `send(String, Configuration)` / `sendMsg(SPMsg, PhyConfiguration)` — send messages via PHY
  - `receive()` / `receive(int timeout)` — receive and parse SP messages from PHY
  - `sendData(SPDataMsg, int, PhyConfiguration)` — send measurement + wait for ACK (3 retries)
  - `sendAck(int, int, PhyConfiguration)` — send acknowledgement
  - `sendReconf(SPReconfMsg, int, PhyConfiguration)` — send reconfiguration + wait for ACK
  - `sendUpdate(String, int, int, PhyConfiguration)` — send multi-fragment update with stop-and-wait
  - `sendUpdateAck(int, int, PhyConfiguration)` — send update fragment ACK

### Apps Package (`apps/`)

#### `SPClient` — Sensor System Application
- Simulates a water quality sensor
- Periodically generates random (but realistic) measurement values
- Sends DATA messages to the DPS and waits for ACK
- Usage: `java apps.SPClient <sensorID> [serverPort]`

#### `SPServer` — Data Processing Station Application
- Receives and prints measurement data from arbitrary many sensors
- Sends ACK to the correct sensor (uses sender's PHY address from received message)
- Handles all SP message types
- Usage: `java apps.SPServer [port]`

---

## Building and Running

### Prerequisites

- Java 21 or higher (Tested with Java 25)
- Maven (or run via IntelliJ IDE)

### Build

```bash
mvn compile
```

### Run Tests

```bash
mvn test
```

### Run the Server (Data Processing Station)

```bash
mvn exec:java -Dexec.mainClass="apps.SPServer" -Dexec.args="4999"
```

### Run a Sensor Client

```bash
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="1 4999"
```

You can run multiple clients with different sensor IDs simultaneously:
```bash
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="1 4999"
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="2 4999"
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="3 4999"
```

---

## Testing

### Unit Tests

All tests are in `src/test/java/sp/`:

- **`SPMsgTest`** — Tests for all message classes:
  - Message creation and wire format verification
  - Round-trip (create → serialize → parse) for all 5 message types
  - CRC32 checksum computation consistency
  - Error cases: invalid headers, missing fields, bad checksums, unknown types

- **`SPProtocolTest`** — Tests for the protocol layer (using Mockito):
  - Receiving valid DATA and ACK messages via mocked PHY
  - Rejecting messages with wrong protocol ID
  - Send data with successful ACK
  - Timeout and retry behavior (3 attempts then failure)
  - ACK content verification
  - Recovery after timeout (retry then success)
  - Corrupted ACK handling

### Running Tests

```bash
mvn test
```

Test reports are generated in the `target/surefire-reports/` directory.

---

## UML Sequence Diagram

The UML sequence diagram for the firmware update message flow is available in [`sp_update_sequence.md`](sp_update_sequence.md).

To generate the diagram:
1. Go to https://sequencediagram.org/
2. Paste the diagram source code from the file
3. Export as PNG

The diagram covers:
- Normal multi-fragment update flow (stop-and-wait)
- Error scenario: fragment lost → retry
- Error scenario: ACK lost → duplicate detection → re-ACK
- Error scenario: max retries exhausted → update abort

---

## Design Decisions

1. **Text-based wire format** — Matches the existing framework pattern (PHY, SLP). Human-readable for debugging. Fields separated by spaces.

2. **Hierarchical message parsing** — `SPMsg.parse()` dispatches to sub-types, following the same pattern as `SLPMsg` → `SLPRegMsg` → `SLPRegRequestMsg/SLPRegResponseMsg`.

3. **CRC32 over content** — Checksum covers type, sensorID, seqNum, and payload but NOT the checksum field itself (to avoid circular dependency). The "sp" header is also excluded since it's constant.

4. **Stop-and-wait ARQ** — Simple, reliable, and appropriate for the low-bandwidth sensor scenario. Each message must be acknowledged before the next is sent.

5. **Extensibility** — Adding a new message type requires: (1) creating a new class extending `SPMsg`, (2) adding a type constant, (3) adding a case in `SPMsg.parse()`. No changes to the protocol or existing messages needed.
