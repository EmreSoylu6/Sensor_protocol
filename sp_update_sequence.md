# SP Update Message Flow — UML Sequence Diagram

Use this source code at https://sequencediagram.org/ to generate the UML diagram.

## Diagram Source (paste into sequencediagram.org)

```
title Sensor Protocol — Firmware Update Message Flow (Use Case 3)

participant "Data Processing\nStation (DPS)" as DPS
participant "Sensor System\n(SS)" as SS

note over DPS: Update data is too large\nfor a single packet.\nFragment into N chunks.

group Fragment 0 [Stop-and-Wait per fragment]
    DPS->SS: UPDATE\n(type=4, sensorID=42, seqNum=10,\nfragIndex=0, totalFrags=N,\nfragData="AABB...", crc32)
    note over SS: Validate CRC32\nStore fragment 0
    SS->DPS: UPDATE_ACK\n(type=5, sensorID=42, seqNum=11,\nackedFragIndex=0, crc32)
end

group Fragment 1
    DPS->SS: UPDATE\n(type=4, sensorID=42, seqNum=12,\nfragIndex=1, totalFrags=N,\nfragData="CCDD...", crc32)
    note over SS: Validate CRC32\nStore fragment 1
    SS->DPS: UPDATE_ACK\n(type=5, sensorID=42, seqNum=13,\nackedFragIndex=1, crc32)
end

note over DPS,SS: ... (fragments 2 to N-2) ...

group Fragment N-1 (last)
    DPS->SS: UPDATE\n(type=4, sensorID=42, seqNum=S,\nfragIndex=N-1, totalFrags=N,\nfragData="EEFF...", crc32)
    note over SS: Validate CRC32\nStore fragment N-1\nAll fragments received!
    SS->DPS: UPDATE_ACK\n(type=5, sensorID=42, seqNum=S+1,\nackedFragIndex=N-1, crc32)
    note over SS: Reassemble fragments\nApply firmware update
end

note over DPS: Update complete.\nAll fragments acknowledged.

== Error Scenario: Fragment Lost ==

group Fragment k — Message Lost
    DPS-xSS: UPDATE\n(frag=k, LOST)
    note over DPS: Timeout: 2000ms\nNo UPDATE_ACK received
    DPS->SS: UPDATE (retry 1)\n(frag=k, same seqNum)
    note over SS: Validate CRC32\nStore fragment k
    SS->DPS: UPDATE_ACK\n(ackedFragIndex=k)
end

== Error Scenario: ACK Lost ==

group Fragment j — ACK Lost
    DPS->SS: UPDATE\n(frag=j)
    note over SS: Validate CRC32\nStore fragment j
    SS-xDPS: UPDATE_ACK\n(frag=j, LOST)
    note over DPS: Timeout: 2000ms
    DPS->SS: UPDATE (retry 1)\n(frag=j, same seqNum)
    note over SS: Duplicate fragment j\n(already stored)\nRe-send ACK
    SS->DPS: UPDATE_ACK\n(ackedFragIndex=j)
end

== Error Scenario: Max Retries Exhausted ==

group Fragment m — All Retries Fail
    DPS-xSS: UPDATE (attempt 1)\n(frag=m, LOST)
    note over DPS: Timeout: 2000ms
    DPS-xSS: UPDATE (attempt 2)\n(frag=m, LOST)
    note over DPS: Timeout: 2000ms
    DPS-xSS: UPDATE (attempt 3)\n(frag=m, LOST)
    note over DPS: Timeout: 2000ms\nMax retries (3) exhausted!\nUpdate ABORTED.
    note over SS: Discard partial\nupdate data
end
```

## How to Generate

1. Go to https://sequencediagram.org/
2. Clear the default diagram
3. Paste the source code between the ``` markers above
4. The diagram renders automatically
5. Click "Export" to save as PNG for submission
