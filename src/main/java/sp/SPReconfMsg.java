package sp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

// reconfiguration message
public class SPReconfMsg extends SPMsg {
    protected static final String RECONF_HEADER = "reconf ";
    
    private int measurementFrequency;
    private int messageFrequency;
    
    public SPReconfMsg() {
        this.type = TYPE_RECONF;
    }
    
    public int getMeasurementFrequency() { return measurementFrequency; }
    public void setMeasurementFrequency(int measurementFrequency) { this.measurementFrequency = measurementFrequency; }
    
    public int getMessageFrequency() { return messageFrequency; }
    public void setMessageFrequency(int messageFrequency) { this.messageFrequency = messageFrequency; }
    
    @Override
    protected void create(String sentence) {
        this.payload = RECONF_HEADER + measurementFrequency + " " + messageFrequency;
        super.create(this.payload);
    }
    
    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        this.dataBytes = sentence.getBytes();
        
        if (!sentence.startsWith(SP_HEADER)) {
            throw new IllegalMsgException();
        }
        String content = sentence.substring(SP_HEADER.length());
        
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
        this.data = this.payload;
        
        // Parse payload: reconf <measFreq> <msgFreq>
        if (!this.payload.startsWith(RECONF_HEADER)) {
            throw new IllegalMsgException();
        }
        String reconfContent = this.payload.substring(RECONF_HEADER.length());
        String[] reconfFields = reconfContent.split("\\s+");
        if (reconfFields.length != 2) {
            throw new IllegalMsgException();
        }
        
        try {
            this.measurementFrequency = Integer.parseInt(reconfFields[0]);
            this.messageFrequency = Integer.parseInt(reconfFields[1]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        
        return this;
    }
}
