# SP - Sensor Protocol Spezifikation

**Version:** 1.0  
**Datum:** Juni 2026  
**Status:** Informational

---

## 1. Einleitung

Das Sensor Protocol (SP) definiert ein Kommunikationsprotokoll für Systeme zur Überwachung der Wasserqualität. Es ermöglicht den zuverlässigen Datenaustausch zwischen Sensorsystemen (SS), die im Feld eingesetzt werden, und einer zentralen Datenverarbeitungsstation (Data Processing Station, DPS).

Das Protokoll nutzt eine Sterntopologie, bei der alle Sensorsysteme direkt mit der DPS kommunizieren. Die Sensoren selbst können untereinander oft nicht kommunizieren (keine Reichweite zueinander), erreichen aber alle die DPS.

SP ist besonders energieeffizient konzipiert: Die Sensorsysteme schalten ihre Funkmodule zwischen den Übertragungsfenstern rigoros ab. Während dieser Schlafphasen können Sensoren weder senden noch empfangen.

SP bietet die folgenden Dienste:
1. **Messdatenübertragung** — Vom Sensor zur DPS, einzelnes Paket, mit Bestätigung (ACK)
2. **Rekonfiguration** — Von der DPS zum Sensor, einzelnes Paket, mit Bestätigung (ACK)
3. **Firmware-Update** — Von der DPS zum Sensor, mehrere Pakete durch Fragmentierung, jedes Fragment wird einzeln bestätigt

SP setzt auf einem bestehenden Physical Layer (PHY) auf, der 1 Mbit/s Bandbreite liefert und die Signalmodulation/Demodulation über das drahtlose Medium übernimmt.

## 2. Terminologie

| Begriff | Definition |
|---------|------------|
| **SS** | Sensor System — Ein im Feld eingesetzter Wasserqualitätssensor |
| **DPS** | Data Processing Station — Der zentrale Server, der die Sensordaten sammelt und verarbeitet |
| **SP** | Sensor Protocol — Das in diesem Dokument spezifizierte Protokoll |
| **PHY** | Physical Layer — Die darunterliegende Übertragungsschicht (1 Mbit/s, drahtlos) |
| **PDU** | Protocol Data Unit — Eine einzelne SP-Nachricht, wie sie über das Netzwerk übertragen wird |
| **CRC32** | 32-bit Cyclic Redundancy Check — Dient zur Überprüfung der Nachrichten-Integrität |
| **ARQ** | Automatic Repeat reQuest — Fehlerkontrolle durch Bestätigungen und Neuübertragungen |
| **Stop-and-Wait** | ARQ-Strategie, bei der der Sender auf ein ACK wartet, bevor er die nächste Nachricht sendet |
| **Fragment** | Ein Teil einer großen Nachricht, die aufgeteilt wurde, um die Paketgrößenbeschränkungen einzuhalten |
| **Seq#** | Sequenznummer — Fortlaufende Nummer zur Ordnung der Nachrichten und Erkennung von Duplikaten |

## 3. Header-Formate und Feldbeschreibungen

### 3.1 Allgemeines SP-Nachrichtenformat

Alle SP-Nachrichten folgen einem einheitlichen, textbasierten Übertragungsformat. Felder werden durch einzelne Leerzeichen getrennt. Die komplette Nachricht, so wie sie vom PHY-Layer gesehen wird, sieht so aus:

```
phy 9 sp <type> <sensorID> <seqNum> <checksum> <payload>
|-----|  |--|  |----|  |--------|  |------|  |--------|  |-------|
PHY     PHY   SP     Message    Sensor   Sequence  CRC32     Type-specific
header  proto header  type      ID       number    checksum  payload data
        ID=9
```

Der PHY-Layer setzt seinen eigenen Header voran (`phy 9`), wobei `9` die Protokoll-ID für SP ist. Die SP-Schicht kümmert sich um alles ab dem `sp`.

### 3.2 SP Header-Felder

```
+--------+----------+--------+----------+---------+
|  "sp"  |   type   | sensorID | seqNum | checksum |  payload  |
+--------+----------+--------+----------+---------+
| 2 Char | 1 Ziffer | Integer  | Integer| Long    |  variabel |
+--------+----------+--------+----------+---------+
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `sp` | String (konstant) | Protokoll-Identifikator. Immer der exakte String `"sp"`. |
| `type` | Integer | Typ der Nachricht. Siehe Abschnitt 3.3. |
| `sensorID` | Integer | Eindeutige ID des Sensorsystems. Wird genutzt, um Sensoren anzusprechen und ACKs zu routen. |
| `seqNum` | Integer | Sequenznummer. Wird vom Sender hochgezählt. Zum Abgleich von ACKs und Filtern von Duplikaten. |
| `checksum` | Long | CRC32-Prüfsumme für die Integritätsprüfung. Siehe Abschnitt 3.4. |
| `payload` | String | Typ-spezifische Nutzdaten (Payload). Das Format hängt vom Nachrichtentyp ab. |

### 3.3 Nachrichtentypen

| Typ-ID | Name | Richtung | Beschreibung |
|--------|------|----------|--------------|
| 1 | DATA | SS → DPS | Messdaten des Sensors |
| 2 | ACK | Beide | Bestätigung einer empfangenen Nachricht |
| 3 | RECONF | DPS → SS | Rekonfiguration von Sensorparametern |
| 4 | UPDATE | DPS → SS | Firmware-Update-Fragment |
| 5 | UPDATE_ACK | SS → DPS | Bestätigung für ein Update-Fragment |

### 3.4 Berechnung der Prüfsumme

Die CRC32-Prüfsumme wird mit der Java-Klasse `java.util.zip.CRC32` über den folgenden String berechnet:

```
<type> <sensorID> <seqNum> <payload>
```

**Wichtig:** Das Feld `checksum` selbst wird NICHT in die Berechnung einbezogen. Der Header `"sp"` wird ebenfalls ausgeschlossen. Nur type, sensorID, seqNum und payload werden verarbeitet.

Beispiel für die Berechnung:
```
Inhalt für CRC: "1 100 0 data 25.5 7.2 8.5 120.0 1234567890"
CRC32-Ergebnis: 3148208372
Komplette Nachricht: "sp 1 100 0 3148208372 data 25.5 7.2 8.5 120.0 1234567890"
```

### 3.5 Payload-Formate je Nachrichtentyp

#### 3.5.1 DATA-Nachricht (Typ 1)

Format der Payload:
```
data <temperature> <pH> <dissolvedOxygen> <turbidity> <timestamp>
```

| Feld | Typ | Einheit | Gültiger Bereich | Beschreibung |
|------|-----|---------|------------------|--------------|
| `temperature` | Float | °C | 0.0 - 50.0 | Wassertemperatur |
| `pH` | Float | pH | 0.0 - 14.0 | Säure/Base-Wert |
| `dissolvedOxygen` | Float | mg/L | 0.0 - 20.0 | Gelöster Sauerstoff |
| `turbidity` | Float | NTU | 0.0 - 1000.0 | Trübung des Wassers |
| `timestamp` | Long | ms | — | Zeitpunkt der Messung (ms seit Epoch) |

Beispiel:
```
sp 1 42 0 1234567890 data 25.5 7.2 8.5 120.0 1718464636000
```

#### 3.5.2 ACK-Nachricht (Typ 2)

Format der Payload:
```
ack <ackedSeqNum>
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `ackedSeqNum` | Integer | Die Sequenznummer der Nachricht, die bestätigt wird |

Beispiel:
```
sp 2 42 1 9876543210 ack 0
```

#### 3.5.3 RECONF-Nachricht (Typ 3)

Format der Payload:
```
reconf <measurementFrequency> <messageFrequency>
```

| Feld | Typ | Einheit | Beschreibung |
|------|-----|---------|--------------|
| `measurementFrequency` | Integer | Sekunden | Intervall zwischen den Sensormessungen |
| `messageFrequency` | Integer | Sekunden | Intervall zwischen den Datenübertragungen zur DPS |

Beispiel:
```
sp 3 42 5 5551234567 reconf 30 60
```

Dieses Beispiel konfiguriert Sensor 42 so, dass er alle 30 Sekunden misst und alle 60 Sekunden überträgt.

#### 3.5.4 UPDATE-Nachricht (Typ 4)

Format der Payload:
```
update <fragmentIndex> <totalFragments> <fragmentData>
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `fragmentIndex` | Integer | 0-basierter Index dieses Fragments |
| `totalFragments` | Integer | Gesamtzahl der Fragmente im Update |
| `fragmentData` | String | Die eigentlichen Update-Daten für dieses Fragment |

Beispiel (Fragment 0 von 5):
```
sp 4 42 10 7771234567 update 0 5 AABBCCDD01020304
```

#### 3.5.5 UPDATE_ACK-Nachricht (Typ 5)

Format der Payload:
```
uack <ackedFragmentIndex>
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `ackedFragmentIndex` | Integer | Der Index des Fragments, das bestätigt wird |

Beispiel:
```
sp 5 42 11 8881234567 uack 0
```

## 4. Nachrichtenabläufe (Sequences)

### 4.1 Use Case 1: Messdatenübertragung

Der Sensor sendet Daten an die DPS und wartet auf eine Bestätigung.

```
    Sensor System (SS)                Data Processing Station (DPS)
          |                                       |
          |--- DATA (type=1, seqNum=N) ---------->|
          |                                       | [Daten ausgeben]
          |<-- ACK  (type=2, ack=N)    -----------|
          |                                       |
```

**Fehlerfreier Fall:**
1. SS erstellt eine DATA-Nachricht mit aktuellen Messwerten und seqNum=N.
2. SS sendet die Nachricht an die DPS.
3. DPS empfängt die Nachricht, prüft CRC32, gibt Daten aus.
4. DPS sendet ein ACK mit ackedSeqNum=N an das SS.
5. SS empfängt das ACK und die Übertragung ist abgeschlossen.

**Fehlerfall — Nachricht geht verloren oder ist beschädigt:**
```
    Sensor System (SS)                Data Processing Station (DPS)
          |                                       |
          |--- DATA (seqNum=N) ------>    X       |  [Nachricht verloren]
          |        [Timeout: 2000ms]              |
          |--- DATA (seqNum=N) ------------------>|  [Retry 1]
          |                                       |  [Daten ausgeben]
          |<-- ACK  (ack=N)    -------------------|
          |                                       |
```

- SS wartet bis zu 2000ms auf ein ACK.
- Ohne ACK wird die DATA-Nachricht nochmal gesendet.
- Maximal 3 Versuche, danach gilt es als Fehler.
- DPS erkennt Duplikate anhand der seqNum und verwirft diese (sendet aber trotzdem ein ACK).

### 4.2 Use Case 2: Rekonfiguration

Die DPS sendet einen Rekonfigurationsbefehl, um die Mess- oder Sendeintervalle des Sensors zu ändern.

```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |
          |--- RECONF (type=3, seqNum=N) -------->|
          |                                       | [Konfig anwenden]
          |<-- ACK    (type=2, ack=N)    ---------|
          |                                       |
```

**Fehlerfreier Fall:**
1. DPS erstellt RECONF-Nachricht mit neuen Frequenzen und seqNum=N.
2. DPS sendet die Nachricht an den Ziel-Sensor.
3. SS empfängt Nachricht, validiert CRC32, wendet Änderungen an.
4. SS sendet ACK mit ackedSeqNum=N zurück an die DPS.
5. DPS erhält ACK, Rekonfiguration war erfolgreich.

**Fehlerfall — Sensor-Funkmodul ist im Energiesparmodus (aus):**
```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |  [Radio AUS]
          |--- RECONF (seqNum=N) ---->    X       |  [nicht empfangen]
          |        [Timeout: 2000ms]              |
          |--- RECONF (seqNum=N) ---->    X       |  [Retry 1, noch AUS]
          |        [Timeout: 2000ms]              |
          |--- RECONF (seqNum=N) ---->    X       |  [Retry 2, noch AUS]
          |        [Timeout: 2000ms]              |
          |  [FEHLER: Max. Retries erreicht]      |
```

- DPS reiht den Befehl ein und versucht es erneut, sobald der Sensor in seinem nächsten Übertragungsfenster aufwacht.
- Gleiche Logik: 2000ms Timeout, max. 3 Versuche.

### 4.3 Use Case 3: Firmware-Update (Multi-Fragment)

Update-Daten sind zu groß für ein Paket und werden in Fragmente zerlegt. Es kommt Stop-and-Wait zum Einsatz — jedes Fragment muss bestätigt werden, bevor das nächste gesendet wird.

```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |
          |--- UPDATE (frag=0/N, seqNum=S)  ----->|
          |                                       | [speichere Fragment 0]
          |<-- UPDATE_ACK (frag=0)         -------|
          |                                       |
          |--- UPDATE (frag=1/N, seqNum=S+1) ---->|
          |                                       | [speichere Fragment 1]
          |<-- UPDATE_ACK (frag=1)         -------|
          |                                       |
          |              ...                      |
          |                                       |
          |--- UPDATE (frag=N-1/N, seqNum=S+k) -->|
          |                                       | [speichere Fragment N-1]
          |<-- UPDATE_ACK (frag=N-1)       -------|
          |                                       | [Update anwenden]
          |                                       |
```

**Fehlerfreier Fall:**
1. DPS teilt die Update-Daten in Chunks auf.
2. Für jedes Fragment (i = 0 bis N-1):
   a. DPS erstellt UPDATE-Nachricht mit fragmentIndex=i, totalFragments=N.
   b. DPS sendet Fragment und wartet auf UPDATE_ACK.
   c. SS empfängt Fragment, validiert CRC32, speichert es.
   d. SS sendet UPDATE_ACK mit ackedFragmentIndex=i.
   e. DPS empfängt UPDATE_ACK und macht mit dem nächsten Fragment weiter.
3. Nach dem letzten Fragment setzt das SS das Update zusammen und führt es aus.

**Fehlerfall — Fragment geht verloren:**
```
    Data Processing Station (DPS)          Sensor System (SS)
          |                                       |
          |--- UPDATE (frag=2/5) ------>  X       |  [verloren]
          |        [Timeout: 2000ms]              |
          |--- UPDATE (frag=2/5) ---------------->|  [Retry]
          |                                       |  [speichere Fragment 2]
          |<-- UPDATE_ACK (frag=2) ---------------|
          |                                       |
          |--- UPDATE (frag=3/5) ---------------->|  [weiter geht's]
          |         ...                           |
```

- Jedes einzelne Fragment nutzt die Timeout/Retry-Logik (2000ms, max 3 Versuche).
- Scheitert ein Fragment auch beim 3. Versuch, wird das komplette Update abgebrochen.
- Der Sensor löscht dann alle bereits empfangenen Fragmente.

## 5. Zugehörige Aktionen

### 5.1 Aktionen beim Empfang einer DATA-Nachricht (DPS)

1. CRC32-Prüfsumme checken. Wenn falsch -> lautlos wegwerfen.
2. sensorID und seqNum auf Duplikate prüfen.
3. Wenn es kein Duplikat ist:
   a. Messwerte auslesen (temperature, pH, dissolved oxygen, turbidity, timestamp).
   b. Die Werte in der Konsole ausgeben.
   c. Die PHY-Adresse des Senders merken, um das ACK zurückzuschicken.
4. ACK-Nachricht an den Sender schicken (mit ackedSeqNum = erhaltene seqNum).

### 5.2 Aktionen beim Empfang einer ACK-Nachricht (SS oder DPS)

1. CRC32 prüfen. Wenn kaputt -> wegwerfen.
2. Schauen, ob ackedSeqNum zu der Nachricht passt, auf deren Bestätigung wir gerade warten.
3. Wenn ja: Übertragung als erfolgreich abhaken, Retry-Timer stoppen.
4. Wenn nein: Einfach ignorieren (könnte veraltet oder ein Duplikat sein).

### 5.3 Aktionen beim Empfang einer RECONF-Nachricht (SS)

1. CRC32 validieren. Wenn falsch -> wegwerfen.
2. measurementFrequency und messageFrequency aus der Payload holen.
3. Werte prüfen (müssen z.B. positive Zahlen sein).
4. Die neuen Einstellungen anwenden.
5. ACK an die DPS schicken (mit ackedSeqNum = erhaltene seqNum).

### 5.4 Aktionen beim Empfang einer UPDATE-Nachricht (SS)

1. CRC32 validieren.
2. Die Fragment-Daten an der Position speichern, die fragmentIndex vorgibt.
3. UPDATE_ACK senden (mit ackedFragmentIndex = erhaltene fragmentIndex).
4. Wenn das das letzte Fragment war (fragmentIndex == totalFragments - 1):
   a. Alle Fragmente in der richtigen Reihenfolge zusammensetzen.
   b. Das Update anwenden.

### 5.5 Aktionen beim Empfang einer UPDATE_ACK-Nachricht (DPS)

1. CRC32 validieren.
2. Prüfen, ob ackedFragmentIndex zum aktuell offenen Fragment passt.
3. Wenn ja: Das nächste Fragment senden.
4. Wenn nein: Verwerfen.

## 6. Fehlerbehandlung - Zusammenfassung

| Fehler | Erkennung | Reaktion |
|--------|-----------|----------|
| Nachricht beschädigt | CRC32 stimmt nicht | Nachricht lautlos wegwerfen |
| Nachricht verloren | Timeout (2000ms) | Erneut senden (max 3 Versuche) |
| Alle Retries fehlgeschlagen | 3 Timeouts in Folge | Fehler an die Anwendung melden |
| Falsches Nachrichtenformat | Parse Error | IllegalMsgException werfen, ignorieren |
| Duplikat empfangen | seqNum schon bekannt | Daten ignorieren, aber nochmal ein ACK senden |
| Falsche Protokoll-ID | PHY proto_id ≠ SP | Nachricht komplett verwerfen |

## 7. Energie-Überlegungen

Sensorsysteme nutzen den Duty-Cycled-Modus, um Strom zu sparen:

1. **Schlafphase (Sleep):** Radio-Modul ist komplett aus. Keine Kommunikation möglich.
2. **Aufwachphase (Wake):** Radio geht an. Der Sensor misst und sendet Daten.
3. **Listen Window:** Nach dem Senden hört der Sensor kurz zu, ob die DPS noch ein RECONF oder UPDATE für ihn hat.

Der Parameter `messageFrequency` (kann über RECONF geändert werden) bestimmt, wie oft der Sensor aufwacht. `measurementFrequency` legt fest, wie oft gemessen wird (Werte können lokal zwischengespeichert werden, falls öfter gemessen als gesendet wird).

Die DPS muss diese Schlafzyklen bei RECONF oder UPDATE-Nachrichten berücksichtigen. Wenn der Sensor nicht erreichbar ist, stellt die DPS die Nachricht in die Warteschlange und versucht es im nächsten Fenster nochmal.

## 8. Sicherheitsaspekte

Die CRC32-Prüfsumme schützt nur vor zufälligen Fehlern, bietet aber KEINE Sicherheit gegen böswillige Manipulationen. Für den echten Produktiveinsatz müssten folgende Dinge ergänzt werden:

- **Authentifizierung:** HMAC mit Shared Secret, damit niemand fremde Nachrichten schicken kann.
- **Verschlüsselung:** Payloads verschlüsseln, damit niemand die Sensordaten mitlesen kann.
- **Replay-Schutz:** Sequenznummern mit Timestamps kombinieren, um Replay-Angriffe zu verhindern.
- **Zugriffskontrolle:** Nur berechtigte DPS-Systeme dürfen RECONF/UPDATE-Befehle schicken.

Diese Sicherheitsfeatures sind hier nicht gefordert, sollten bei echten Deployments aber immer auf dem Schirm sein.
