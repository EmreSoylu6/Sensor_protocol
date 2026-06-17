# SP Update-Nachrichtenablauf — UML-Sequenzdiagramm

Diesen Quellcode einfach auf https://sequencediagram.org/ einfügen, um das UML-Diagramm zu generieren.

## Diagramm-Quellcode (auf sequencediagram.org einfügen)

```
title Sensor Protocol — Ablauf des Firmware-Updates (Use Case 3)

participant "Data Processing\nStation (DPS)" as DPS
participant "Sensor System\n(SS)" as SS

note over DPS: Update-Daten sind zu groß\nfür ein einzelnes Paket.\nWird in N Fragmente unterteilt.

group Fragment 0 [Stop-and-Wait pro Fragment]
    DPS->SS: UPDATE\n(type=4, sensorID=42, seqNum=10,\nfragIndex=0, totalFrags=N,\nfragData="AABB...", crc32)
    note over SS: CRC32 validieren\nFragment 0 speichern
    SS->DPS: UPDATE_ACK\n(type=5, sensorID=42, seqNum=11,\nackedFragIndex=0, crc32)
end

group Fragment 1
    DPS->SS: UPDATE\n(type=4, sensorID=42, seqNum=12,\nfragIndex=1, totalFrags=N,\nfragData="CCDD...", crc32)
    note over SS: CRC32 validieren\nFragment 1 speichern
    SS->DPS: UPDATE_ACK\n(type=5, sensorID=42, seqNum=13,\nackedFragIndex=1, crc32)
end

note over DPS,SS: ... (Fragmente 2 bis N-2) ...

group Fragment N-1 (letztes)
    DPS->SS: UPDATE\n(type=4, sensorID=42, seqNum=S,\nfragIndex=N-1, totalFrags=N,\nfragData="EEFF...", crc32)
    note over SS: CRC32 validieren\nFragment N-1 speichern\nAlle Fragmente empfangen!
    SS->DPS: UPDATE_ACK\n(type=5, sensorID=42, seqNum=S+1,\nackedFragIndex=N-1, crc32)
    note over SS: Fragmente zusammensetzen\nFirmware-Update anwenden
end

note over DPS: Update abgeschlossen.\nAlle Fragmente bestätigt.

== Fehlerfall: Fragment verloren ==

group Fragment k — Nachricht verloren
    DPS-xSS: UPDATE\n(frag=k, LOST)
    note over DPS: Timeout: 2000ms\nKein UPDATE_ACK erhalten
    DPS->SS: UPDATE (Retry 1)\n(frag=k, gleiche seqNum)
    note over SS: CRC32 validieren\nFragment k speichern
    SS->DPS: UPDATE_ACK\n(ackedFragIndex=k)
end

== Fehlerfall: ACK verloren ==

group Fragment j — ACK verloren
    DPS->SS: UPDATE\n(frag=j)
    note over SS: CRC32 validieren\nFragment j speichern
    SS-xDPS: UPDATE_ACK\n(frag=j, LOST)
    note over DPS: Timeout: 2000ms
    DPS->SS: UPDATE (Retry 1)\n(frag=j, gleiche seqNum)
    note over SS: Duplikat von Fragment j\n(wurde schon gespeichert)\nACK erneut senden
    SS->DPS: UPDATE_ACK\n(ackedFragIndex=j)
end

== Fehlerfall: Maximale Retries erreicht ==

group Fragment m — Alle Retries schlagen fehl
    DPS-xSS: UPDATE (Versuch 1)\n(frag=m, LOST)
    note over DPS: Timeout: 2000ms
    DPS-xSS: UPDATE (Versuch 2)\n(frag=m, LOST)
    note over DPS: Timeout: 2000ms
    DPS-xSS: UPDATE (Versuch 3)\n(frag=m, LOST)
    note over DPS: Timeout: 2000ms\nMaximale Retries (3) erreicht!\nUpdate ABGEBROCHEN.
    note over SS: Teilweises Update\nverwerfen
end
```

## So wird's generiert

1. Geh auf https://sequencediagram.org/
2. Lösche das Standarddiagramm.
3. Kopiere den Quellcode zwischen den ``` Markierungen oben hinein.
4. Das Diagramm wird automatisch gerendert.
5. Klicke auf "Export", um es als PNG für die Abgabe zu speichern.
