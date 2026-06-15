package sp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

// acknowledgement message
public class SPAckMsg extends SPMsg {
    protected static final String ACK_HEADER = "ack ";
    
    private int ackedSeqNum;
    
    public SPAckMsg() {
        this.type = TYPE_ACK;
    }
    
    public int getAckedSeqNum() { return ackedSeqNum; }
    public void setAckedSeqNum(int ackedSeqNum) { this.ackedSeqNum = ackedSeqNum; }
    
    // create an ack message
    @Override
    protected void create(String sentence) {
        this.payload = ACK_HEADER + ackedSeqNum;
        super.create(this.payload);
    }
    
    // parse an incoming ack message
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
        
        // Parse payload: ack <ackedSeqNum>
        if (!this.payload.startsWith(ACK_HEADER)) {
            throw new IllegalMsgException();
        }
        String ackContent = this.payload.substring(ACK_HEADER.length()).trim();
        
        try {
            this.ackedSeqNum = Integer.parseInt(ackContent);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        
        return this;
    }
}
