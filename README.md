# Internetworking — Sensor Protocol (SP) Implementierung

## Teammitglieder

| Emre Soylu | 819689 | ComputerNetzwerke |
|------|-----------|----------------|
| [Dein Name] | [Deine Matrikelnummer] | [Dein Studiengang] |
| [Teammitglied 2] | [Dessen Matrikelnummer] | [Dessen Studiengang] |

> **Hinweis:** Bitte die Details der Teammitglieder oben vor der Abgabe noch ausfüllen.

---

## Projektübersicht

Dieses Projekt implementiert das **Sensor Protocol (SP)**, ein Kommunikationsprotokoll für Systeme zur Überwachung der Wasserqualität. Das Protokoll ermöglicht einen zuverlässigen Datenaustausch zwischen im Feld eingesetzten Sensorsystemen (SS) und einer zentralen Datenverarbeitungsstation (Data Processing Station, DPS).

### Features

- **Messdatenübertragung** — Sensoren senden Messwerte zur Wasserqualität (Temperatur, pH-Wert, gelöster Sauerstoff, Trübung) mit Bestätigung (ACK) an die DPS.
- **Rekonfiguration** — Die DPS kann die Mess- und Sendeintervalle der Sensoren aus der Ferne anpassen.
- **Firmware-Updates** — Die DPS kann große Firmware-Updates über eine fragmentierte Übertragung senden, wobei jedes Fragment einzeln bestätigt wird.
- **Fehlerbehandlung** — Integritätsprüfung per CRC32, zeitgesteuerte Neuübertragung (Stop-and-Wait ARQ, max. 3 Versuche).
- **Erweiterbarkeit** — Neue Nachrichtentypen können einfach durch Ableiten der Basisklasse `SPMsg` hinzugefügt werden.

---

## Projektstruktur

```
src/
├── main/java/
│   ├── apps/                          # Anwendungsschicht
│   │   ├── SPClient.java             # Client-Anwendung für das Sensorsystem
│   │   ├── SPServer.java             # Server der Datenverarbeitungsstation
│   │   ├── SimplexPhyClient.java     # [Framework] Beispiel-Client für PHY
│   │   ├── SimplexPhyServer.java     # [Framework] Beispiel-Server für PHY
│   │   ├── SLPClient.java           # [Framework] SLP Client
│   │   └── SLPSwitch.java           # [Framework] SLP Switch
│   ├── core/                          # [Framework] Abstrakte Basisklassen
│   │   ├── Configuration.java        # Basis-Konfigurationsklasse
│   │   ├── Msg.java                  # Abstrakte Nachrichten-Basisklasse
│   │   └── Protocol.java            # Protokoll-Interface
│   ├── exceptions/                    # [Framework] Exception-Klassen
│   │   ├── BadChecksumException.java # Fehler bei CRC-Prüfsumme
│   │   ├── IWProtocolException.java  # Basis-Exception fürs Protokoll
│   │   ├── IllegalMsgException.java  # Ungültige/Fehlerhafte Nachricht
│   │   └── ...                       # Weitere Exceptions
│   ├── phy/                           # [Framework] Bitübertragungsschicht (Physical Layer)
│   │   ├── PhyConfiguration.java     # PHY Konfig (IP, Port, Proto-ID)
│   │   ├── PhyMsg.java              # PHY Nachricht (Header: "phy <id> <data>")
│   │   ├── PhyPingMsg.java          # PHY Ping-Nachricht
│   │   └── PhyProtocol.java         # PHY Protokoll (UDP Sockets)
│   ├── slp/                           # [Framework] Simple Link Protocol
│   │   └── ...                       # SLP Klassen
│   └── sp/                            # ★ Sensor Protocol (unsere eigene Implementierung)
│       ├── SPConfiguration.java      # SP Konfiguration (Sensor-ID)
│       ├── SPMsg.java                # Basis SP-Nachricht mit CRC32
│       ├── SPDataMsg.java            # Messdaten-Nachricht (Typ 1)
│       ├── SPAckMsg.java             # Bestätigungsnachricht (Typ 2)
│       ├── SPReconfMsg.java          # Rekonfigurationsnachricht (Typ 3)
│       ├── SPUpdateMsg.java          # Firmware-Update-Fragment (Typ 4)
│       ├── SPUpdateAckMsg.java       # Update-Fragment-ACK (Typ 5)
│       └── SPProtocol.java           # Implementierung des SP-Protokolls
├── test/java/
│   └── sp/                            # ★ Unit Tests
│       ├── SPMsgTest.java            # Tests für alle Nachrichtenklassen
│       └── SPProtocolTest.java       # Protokoll-Tests (mit Mockito)
├── build.gradle                       # Gradle Build-Konfiguration
├── sp_protocol_specification.md       # Protokoll-Spezifikation im RFC-Stil
├── sp_update_sequence.md              # Quellcode für das UML-Sequenzdiagramm
└── README.md                          # Diese Datei
```

---

## Protokoll-Spezifikation

Die vollständige Protokoll-Spezifikation (im RFC-Stil) befindet sich in der Datei [`sp_protocol_specification.md`](sp_protocol_specification.md).

### Übertragungsformat (Wire Format)

Alle SP-Nachrichten nutzen ein textbasiertes Format, bei dem die Felder durch Leerzeichen getrennt sind:

```
sp <type> <sensorID> <seqNum> <checksum> <payload>
```

Auf dem PHY-Layer sieht das Ganze dann so aus:
```
phy 9 sp <type> <sensorID> <seqNum> <checksum> <payload>
```

### Nachrichtentypen

| Typ | Name | Richtung | Payload Format |
|------|------|-----------|---------------|
| 1 | DATA | SS → DPS | `data <temp> <pH> <DO> <turbidity> <timestamp>` |
| 2 | ACK | Beide | `ack <ackedSeqNum>` |
| 3 | RECONF | DPS → SS | `reconf <measFreq> <msgFreq>` |
| 4 | UPDATE | DPS → SS | `update <fragIdx> <totalFrags> <fragData>` |
| 5 | UPDATE_ACK | SS → DPS | `uack <ackedFragIdx>` |

### CRC32 Prüfsumme

Die Prüfsumme wird über den folgenden String berechnet: `<type> <sensorID> <seqNum> <payload>` (ohne das Checksum-Feld selbst und ohne den "sp"-Header). Dafür wird `java.util.zip.CRC32` genutzt.

---

## Code-Dokumentation

### SP Package (`sp/`)

#### `SPMsg` — Basis-Nachrichtenklasse
- Erbt von `core.Msg`
- Stellt die Berechnung der CRC32-Prüfsumme bereit (`computeChecksum(String)`)
- Nutzt hierarchisches Parsing: `parse()` validiert Header und Prüfsumme und leitet dann, abhängig vom type-Feld, an den passenden Untertyp weiter.
- Alle anderen SP-Nachrichten erben von dieser Klasse.

#### `SPDataMsg` — Messdaten (Typ 1)
- Felder: `temperature` (float, °C), `pH` (float), `dissolvedOxygen` (float, mg/L), `turbidity` (float, NTU), `timestamp` (long, ms seit Epoche)
- Wird vom Sensor-Client erstellt und enthält die gemessenen Werte.
- `create()`: Baut die Payload als `data <werte...>` zusammen und berechnet den CRC32-Wert.
- `parse()`: Extrahiert die Werte aus dem Payload-String.

#### `SPAckMsg` — Bestätigung (Typ 2)
- Feld: `ackedSeqNum` (int) — Die Sequenznummer, die bestätigt wird.
- Wird für DATA- und RECONF-Bestätigungen verwendet.
- Bidirektional: Kann sowohl von der SS als auch von der DPS gesendet werden.

#### `SPReconfMsg` — Rekonfiguration (Typ 3)
- Felder: `measurementFrequency` (int, Sekunden), `messageFrequency` (int, Sekunden)
- Wird von der DPS an den Sensor geschickt, um Parameter zur Laufzeit zu ändern.

#### `SPUpdateMsg` — Firmware-Update-Fragment (Typ 4)
- Felder: `fragmentIndex` (int), `totalFragments` (int), `fragmentData` (String)
- Wird von der DPS gesendet. Jedes Fragment muss einzeln bestätigt werden.

#### `SPUpdateAckMsg` — Update-Fragment-ACK (Typ 5)
- Feld: `ackedFragmentIndex` (int)
- Wird vom Sensor an die DPS geschickt, um ein einzelnes Fragment zu bestätigen.

#### `SPConfiguration` — Protokoll-Konfiguration
- Erbt von `core.Configuration`
- Feld: `sensorID` (int) — Identifiziert den Ziel-Sensor.

#### `SPProtocol` — Protokoll-Implementierung
- Implementiert `core.Protocol`
- Wichtigste Methoden:
  - `send(String, Configuration)` / `sendMsg(SPMsg, PhyConfiguration)` — Nachrichten über PHY versenden
  - `receive()` / `receive(int timeout)` — SP-Nachrichten über PHY empfangen und parsen
  - `sendData(SPDataMsg, int, PhyConfiguration)` — Daten senden + auf ACK warten (max. 3 Versuche)
  - `sendAck(int, int, PhyConfiguration)` — Bestätigung (ACK) senden
  - `sendReconf(SPReconfMsg, int, PhyConfiguration)` — Rekonfiguration senden + auf ACK warten
  - `sendUpdate(String, int, int, PhyConfiguration)` — Update in Fragmenten senden (Stop-and-Wait)
  - `sendUpdateAck(int, int, PhyConfiguration)` — Update-Fragment bestätigen

### Apps Package (`apps/`)

#### `SPClient` — Sensor-Anwendung
- Simuliert einen Wasserqualitätssensor.
- Generiert periodisch zufällige (aber halbwegs realistische) Messwerte.
- Sendet DATA-Nachrichten an die DPS und wartet brav auf das ACK.
- Aufruf: `java apps.SPClient <sensorID> [serverPort]`

#### `SPServer` — Datenverarbeitungsstation (DPS)
- Empfängt die Messdaten von beliebig vielen Sensoren und gibt sie in der Konsole aus.
- Schickt ACKs an den richtigen Sensor zurück (nutzt dafür die PHY-Adresse des Absenders).
- Behandelt alle SP-Nachrichtentypen.
- Aufruf: `java apps.SPServer [port]`

---

## Kompilieren und Ausführen

### Voraussetzungen

- Java 21 oder neuer (Getestet mit Java 25)
- Maven (oder einfach über eine IDE wie IntelliJ starten)

### Kompilieren (Build)

```bash
mvn compile
```

### Tests ausführen

```bash
mvn test
```

### Server (DPS) starten

```bash
mvn exec:java -Dexec.mainClass="apps.SPServer" -Dexec.args="4999"
```

### Sensor-Client starten

```bash
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="1 4999"
```

Du kannst auch mehrere Clients mit unterschiedlichen Sensor-IDs gleichzeitig laufen lassen:
```bash
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="1 4999"
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="2 4999"
mvn exec:java -Dexec.mainClass="apps.SPClient" -Dexec.args="3 4999"
```

---

## Tests

### Unit Tests

Alle Tests liegen im Ordner `src/test/java/sp/`:

- **`SPMsgTest`** — Tests für alle Nachrichten:
  - Erstellung von Nachrichten und Check des Wire-Formats.
  - Round-trip Tests (Erstellen → Serialisieren → Parsen) für alle 5 Nachrichtentypen.
  - Überprüfung der CRC32-Prüfsummen-Berechnung.
  - Edge Cases und Fehler: Falsche Header, fehlende Felder, kaputte Prüfsummen, unbekannte Typen.

- **`SPProtocolTest`** — Tests für die Protokollschicht (mit Mockito gemockt):
  - Empfang gültiger DATA- und ACK-Nachrichten über die simulierte PHY-Schicht.
  - Verwerfen von Nachrichten mit der falschen Protokoll-ID.
  - Senden von Daten mit erfolgreichem ACK.
  - Timeout und Retry-Logik (3 Versuche, danach Abbruch).
  - Überprüfung des ACK-Inhalts.
  - Recovery nach einem Timeout (Retry klappt dann beim zweiten Mal).
  - Umgang mit fehlerhaften (corrupted) ACKs.

### Tests laufen lassen

```bash
mvn test
```

Die Testberichte (Test Reports) landen danach im Ordner `target/surefire-reports/`.

---

## UML-Sequenzdiagramm

Das UML-Sequenzdiagramm für den Firmware-Update-Ablauf liegt in der Datei [`sp_update_sequence.md`](sp_update_sequence.md).

So kannst du dir das Diagramm anschauen:
1. Öffne https://sequencediagram.org/
2. Kopiere den Code aus der MD-Datei dort hinein.
3. Exportiere es einfach als PNG.

Das Diagramm zeigt folgende Abläufe:
- Normales, erfolgreiches Update mit mehreren Fragmenten (Stop-and-Wait)
- Fehlerfall: Ein Fragment geht verloren → Neuübertragung (Retry)
- Fehlerfall: Das ACK geht verloren → Sender schickt nochmal → Empfänger merkt, dass es ein Duplikat ist und schickt nochmal ein ACK
- Fehlerfall: Maximale Anzahl an Retries aufgebraucht → Update bricht ab

---

## Design-Entscheidungen

1. **Textbasiertes Format (Wire Format)** — Passt zum restlichen Framework (PHY, SLP). Ist super leicht zu debuggen, da alles lesbar ist. Die Felder sind einfach per Leerzeichen getrennt.

2. **Hierarchisches Parsing** — `SPMsg.parse()` schaut sich den Header an und delegiert dann an die Unterklassen. Das ist quasi das gleiche Pattern wie bei `SLPMsg` → `SLPRegMsg` → `SLPRegRequestMsg/SLPRegResponseMsg`.

3. **CRC32 über den Inhalt** — Die Prüfsumme läuft über Type, SensorID, SeqNum und Payload, aber absichtlich NICHT über das Checksum-Feld selbst. Der "sp"-Header bleibt auch draußen, weil der sowieso immer gleich ist.

4. **Stop-and-Wait ARQ** — Das ist einfach umzusetzen, absolut zuverlässig und für die geringe Bandbreite von Sensoren völlig ausreichend. Bevor die nächste Nachricht rausgeht, muss die alte bestätigt sein.

5. **Erweiterbarkeit** — Wenn wir einen neuen Nachrichtentyp brauchen, müssen wir nur: (1) Eine neue Klasse anlegen, die von `SPMsg` erbt, (2) eine Konstante für den neuen Typen vergeben und (3) einen neuen Case im Switch-Block in `SPMsg.parse()` hinzufügen. Das Protokoll selbst und die bestehenden Nachrichten müssen nicht angefasst werden.
