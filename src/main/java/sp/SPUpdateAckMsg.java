package sp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

// update fragment acknowledgement message
public class SPUpdateAckMsg extends SPMsg {
    protected static final String UACK_HEADER = "uack ";
    
    private int ackedFragmentIndex;
    
    public SPUpdateAckMsg() {
        this.type = TYPE_UPDATE_ACK;
    }
    
    public int getAckedFragmentIndex() { return ackedFragmentIndex; }
    public void setAckedFragmentIndex(int ackedFragmentIndex) { this.ackedFragmentIndex = ackedFragmentIndex; }
    
    @Override
    protected void create(String sentence) {
        this.payload = UACK_HEADER + ackedFragmentIndex;
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
        
        // Parse payload: uack <fragIndex>
        if (!this.payload.startsWith(UACK_HEADER)) {
            throw new IllegalMsgException();
        }
        String uackContent = this.payload.substring(UACK_HEADER.length()).trim();
        
        try {
            this.ackedFragmentIndex = Integer.parseInt(uackContent);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        
        return this;
    }
}
