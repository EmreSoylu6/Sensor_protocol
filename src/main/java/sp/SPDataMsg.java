package sp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

/**
 * Sensor measurement data message (type = 1).
 * 
 * Payload format: data <temperature> <pH> <dissolvedOxygen> <turbidity> <timestamp>
 * 
 * - temperature: water temperature in °C (float)
 * - pH: pH value 0-14 (float)  
 * - dissolvedOxygen: dissolved oxygen in mg/L (float)
 * - turbidity: turbidity in NTU (float)
 * - timestamp: measurement timestamp in ms since epoch (long)
 */
public class SPDataMsg extends SPMsg {
    protected static final String DATA_HEADER = "data ";
    
    private float temperature;
    private float pH;
    private float dissolvedOxygen;
    private float turbidity;
    private long timestamp;
    
    public SPDataMsg() {
        this.type = TYPE_DATA;
    }
    
    // Getters and setters
    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
    
    public float getPH() { return pH; }
    public void setPH(float pH) { this.pH = pH; }
    
    public float getDissolvedOxygen() { return dissolvedOxygen; }
    public void setDissolvedOxygen(float dissolvedOxygen) { this.dissolvedOxygen = dissolvedOxygen; }
    
    public float getTurbidity() { return turbidity; }
    public void setTurbidity(float turbidity) { this.turbidity = turbidity; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    /**
     * Create a data message with measurement values.
     * Sets the payload to: data <temp> <pH> <DO> <turbidity> <timestamp>
     * then delegates to superclass for header and checksum.
     * 
     * @param sentence unused (values come from setters)
     */
    @Override
    protected void create(String sentence) {
        this.payload = DATA_HEADER + temperature + " " + pH + " " + dissolvedOxygen + " " + turbidity + " " + timestamp;
        super.create(this.payload);
    }
    
    /**
     * Parse an incoming data message.
     * Extracts measurement values from the payload.
     * Checksum has already been validated by SPMsg.parse().
     * 
     * @param sentence the full SP message string
     * @return this parsed message
     * @throws IWProtocolException if the message is malformed
     */
    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        this.dataBytes = sentence.getBytes();
        
        // Remove SP header
        if (!sentence.startsWith(SP_HEADER)) {
            throw new IllegalMsgException();
        }
        String content = sentence.substring(SP_HEADER.length());
        
        // Split: type sensorID seqNum checksum payload...
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
        
        // Parse payload: data <temp> <pH> <DO> <turbidity> <timestamp>
        if (!this.payload.startsWith(DATA_HEADER)) {
            throw new IllegalMsgException();
        }
        String dataContent = this.payload.substring(DATA_HEADER.length());
        String[] dataFields = dataContent.split("\\s+");
        if (dataFields.length != 5) {
            throw new IllegalMsgException();
        }
        
        try {
            this.temperature = Float.parseFloat(dataFields[0]);
            this.pH = Float.parseFloat(dataFields[1]);
            this.dissolvedOxygen = Float.parseFloat(dataFields[2]);
            this.turbidity = Float.parseFloat(dataFields[3]);
            this.timestamp = Long.parseLong(dataFields[4]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        
        return this;
    }
}
