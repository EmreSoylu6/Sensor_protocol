package sp;

import core.Msg;
import exceptions.BadChecksumException;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;

// Basis-Nachrichtenklasse für das Sensor-Protokoll
public class SPMsg extends Msg {
    protected static final String SP_HEADER = "sp ";
    
    // Konstanten für Nachrichtentypen
    public static final int TYPE_DATA = 1;
    public static final int TYPE_ACK = 2;
    public static final int TYPE_RECONF = 3;
    public static final int TYPE_UPDATE = 4;
    public static final int TYPE_UPDATE_ACK = 5;
    
    protected int type;
    protected int sensorID;
    protected int seqNum;
    protected long checksum;
    protected String payload;
    
    public SPMsg() {}
    
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    
    public int getSensorID() { return sensorID; }
    public void setSensorID(int sensorID) { this.sensorID = sensorID; }
    
    public int getSeqNum() { return seqNum; }
    public void setSeqNum(int seqNum) { this.seqNum = seqNum; }
    
    public long getChecksum() { return checksum; }
    
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    
    // Berechnet die CRC32-Prüfsumme über den übergebenen String
    public static long computeChecksum(String content) {
        CRC32 crc = new CRC32();
        crc.update(content.getBytes());
        return crc.getValue();
    }
    
    // Baut den String zusammen, über den die Prüfsumme berechnet wird
    protected String buildChecksumContent() {
        return type + " " + sensorID + " " + seqNum + " " + payload;
    }
    
    // Erstellt die SP-Nachricht (fügt Header an und berechnet Prüfsumme)
    @Override
    protected void create(String sentence) {
        this.payload = sentence;
        String checksumContent = buildChecksumContent();
        this.checksum = computeChecksum(checksumContent);
        String fullMessage = SP_HEADER + type + " " + sensorID + " " + seqNum + " " + checksum + " " + payload;
        this.data = fullMessage;
        this.dataBytes = fullMessage.getBytes();
    }
    
    // Parst einen eingehenden SP-Nachrichten-String
    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        this.dataBytes = sentence.getBytes();
        
        // SP-Header prüfen
        if (!sentence.startsWith(SP_HEADER)) {
            throw new IllegalMsgException();
        }
        
        // SP-Header entfernen
        String content = sentence.substring(SP_HEADER.length());
        
        // Aufteilen in: type, sensorID, seqNum, checksum, payload...
        String[] parts = content.split("\\s+", 5);
        if (parts.length < 5) {
            throw new IllegalMsgException();
        }
        
        try {
            this.type = Integer.parseInt(parts[0]);
            this.sensorID = Integer.parseInt(parts[1]);
            this.seqNum = Integer.parseInt(parts[2]);
            this.checksum = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        
        this.payload = parts[4];
        
        // Prüfsumme abgleichen
        String checksumContent = buildChecksumContent();
        long computedChecksum = computeChecksum(checksumContent);
        if (computedChecksum != this.checksum) {
            throw new BadChecksumException(this.checksum, computedChecksum);
        }
        
        // An den passenden Unter-Nachrichtentyp delegieren
        SPMsg pdu;
        switch (this.type) {
            case TYPE_DATA -> {
                pdu = new SPDataMsg();
                pdu = (SPMsg) pdu.parse(SP_HEADER + content);
            }
            case TYPE_ACK -> {
                pdu = new SPAckMsg();
                pdu = (SPMsg) pdu.parse(SP_HEADER + content);
            }
            case TYPE_RECONF -> {
                pdu = new SPReconfMsg();
                pdu = (SPMsg) pdu.parse(SP_HEADER + content);
            }
            case TYPE_UPDATE -> {
                pdu = new SPUpdateMsg();
                pdu = (SPMsg) pdu.parse(SP_HEADER + content);
            }
            case TYPE_UPDATE_ACK -> {
                pdu = new SPUpdateAckMsg();
                pdu = (SPMsg) pdu.parse(SP_HEADER + content);
            }
            default -> throw new IllegalMsgException();
        }
        
        return pdu;
    }
}
