package sp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

/**
 * Firmware update fragment message (type = 4).
 * 
 * Payload format: update <fragmentIndex> <totalFragments> <fragmentData>
 * 
 * Sent from Data Processing Station to Sensor System.
 * Each fragment must be individually acknowledged with an UPDATE_ACK.
 * Uses stop-and-wait: next fragment is only sent after previous ACK received.
 * 
 * - fragmentIndex: 0-based index of this fragment (int)
 * - totalFragments: total number of fragments (int)
 * - fragmentData: the actual update data for this fragment (String)
 */
public class SPUpdateMsg extends SPMsg {
    protected static final String UPDATE_HEADER = "update ";
    
    private int fragmentIndex;
    private int totalFragments;
    private String fragmentData;
    
    public SPUpdateMsg() {
        this.type = TYPE_UPDATE;
    }
    
    public int getFragmentIndex() { return fragmentIndex; }
    public void setFragmentIndex(int fragmentIndex) { this.fragmentIndex = fragmentIndex; }
    
    public int getTotalFragments() { return totalFragments; }
    public void setTotalFragments(int totalFragments) { this.totalFragments = totalFragments; }
    
    public String getFragmentData() { return fragmentData; }
    public void setFragmentData(String fragmentData) { this.fragmentData = fragmentData; }
    
    @Override
    protected void create(String sentence) {
        this.payload = UPDATE_HEADER + fragmentIndex + " " + totalFragments + " " + fragmentData;
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
        
        // Parse payload: update <fragIndex> <totalFragments> <fragmentData>
        if (!this.payload.startsWith(UPDATE_HEADER)) {
            throw new IllegalMsgException();
        }
        String updateContent = this.payload.substring(UPDATE_HEADER.length());
        String[] updateFields = updateContent.split("\\s+", 3);
        if (updateFields.length < 3) {
            throw new IllegalMsgException();
        }
        
        try {
            this.fragmentIndex = Integer.parseInt(updateFields[0]);
            this.totalFragments = Integer.parseInt(updateFields[1]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        this.fragmentData = updateFields[2];
        
        return this;
    }
}
