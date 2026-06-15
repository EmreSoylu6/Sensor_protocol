package sp;

import java.io.IOException;
import java.net.SocketTimeoutException;

import core.Configuration;
import core.Msg;
import core.Protocol;
import phy.PhyConfiguration;
import phy.PhyProtocol;
import exceptions.*;

/**
 * Sensor Protocol (SP) implementation.
 * 
 * Provides reliable communication between sensor systems and the data processing station.
 * Supports three message flows:
 *   1. Measurement data: Sensor -> DPS (with ACK)
 *   2. Reconfiguration: DPS -> Sensor (with ACK)
 *   3. Firmware update: DPS -> Sensor (multi-fragment with per-fragment ACK)
 * 
 * Uses stop-and-wait ARQ with timeout and retransmission (max 3 attempts).
 * Messages are protected by CRC32 checksums.
 */
public class SPProtocol implements Protocol {
    private static final int SP_TIMEOUT = 2000;
    private static final int MAX_RETRIES = 3;
    
    private final PhyProtocol phy;
    private int seqNum;
    
    /**
     * Create a new SPProtocol instance.
     * 
     * @param phy the underlying physical layer protocol
     */
    public SPProtocol(PhyProtocol phy) {
        this.phy = phy;
        this.seqNum = 0;
    }
    
    /**
     * Get the next sequence number and increment the counter.
     * @return the current sequence number
     */
    private int nextSeqNum() {
        return seqNum++;
    }
    
    /**
     * Send a raw string message via the PHY layer.
     * This method creates an SP message from the string and sends it.
     * 
     * @param s the message string to send
     * @param config the configuration containing destination info
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if a protocol error occurs
     */
    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {
        PhyConfiguration phyConfig = (PhyConfiguration) config;
        phy.send(s, phyConfig);
    }
    
    /**
     * Send a pre-built SP message via the PHY layer.
     * Wraps the SP message bytes as a string and sends via PHY.
     * 
     * @param msg the SP message to send
     * @param phyConfig the PHY configuration for the destination
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if a protocol error occurs
     */
    public void sendMsg(SPMsg msg, PhyConfiguration phyConfig) throws IOException, IWProtocolException {
        String msgString = new String(msg.getDataBytes());
        phy.send(msgString, phyConfig);
    }
    
    /**
     * Receive a message from the PHY layer and parse it as an SP message.
     * Blocks until a message is received.
     * 
     * @return the parsed SP message
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if a protocol error occurs or checksum fails
     */
    @Override
    public Msg receive() throws IOException, IWProtocolException {
        Msg phyMsg = phy.receive();
        
        // Check demux: only process SP messages (proto_id = SP)
        PhyConfiguration phyConfig = (PhyConfiguration) phyMsg.getConfiguration();
        if (phyConfig.getPid() != proto_id.SP) {
            throw new IllegalMsgException();
        }
        
        // Parse the SP message from the PHY payload
        SPMsg spMsg = new SPMsg();
        SPMsg parsed = (SPMsg) spMsg.parse(phyMsg.getData());
        
        // Carry over the PHY configuration so we know who sent it
        parsed.setConfiguration(phyConfig);
        
        return parsed;
    }
    
    /**
     * Receive a message with a timeout.
     * 
     * @param timeout the timeout in milliseconds
     * @return the parsed SP message
     * @throws IOException if an I/O error occurs or timeout expires
     * @throws IWProtocolException if a protocol error occurs
     */
    public Msg receive(int timeout) throws IOException, IWProtocolException {
        Msg phyMsg = phy.receive(timeout);
        
        PhyConfiguration phyConfig = (PhyConfiguration) phyMsg.getConfiguration();
        if (phyConfig.getPid() != proto_id.SP) {
            throw new IllegalMsgException();
        }
        
        SPMsg spMsg = new SPMsg();
        SPMsg parsed = (SPMsg) spMsg.parse(phyMsg.getData());
        parsed.setConfiguration(phyConfig);
        
        return parsed;
    }
    
    /**
     * Send a measurement data message and wait for acknowledgement.
     * Uses stop-and-wait ARQ with retransmission.
     * 
     * @param dataMsg the data message (temperature, pH, etc. must be set)
     * @param sensorID the sensor's own ID
     * @param phyConfig the PHY configuration for the DPS
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if max retries exceeded or protocol error
     */
    public void sendData(SPDataMsg dataMsg, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        int currentSeq = nextSeqNum();
        dataMsg.setSensorID(sensorID);
        dataMsg.setSeqNum(currentSeq);
        dataMsg.create(null);
        
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                sendMsg(dataMsg, phyConfig);
                
                // Wait for ACK
                Msg response = receive(SP_TIMEOUT);
                if (response instanceof SPAckMsg ackMsg) {
                    if (ackMsg.getAckedSeqNum() == currentSeq) {
                        return; // Success
                    }
                }
            } catch (SocketTimeoutException e) {
                attempts++;
            } catch (IWProtocolException e) {
                // Malformed response, retry
                attempts++;
            }
        }
        throw new IllegalMsgException();
    }
    
    /**
     * Send an acknowledgement for a received message.
     * 
     * @param ackedSeqNum the sequence number to acknowledge
     * @param sensorID the sensor ID
     * @param phyConfig the PHY configuration for the destination
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if a protocol error occurs
     */
    public void sendAck(int ackedSeqNum, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        SPAckMsg ackMsg = new SPAckMsg();
        ackMsg.setSensorID(sensorID);
        ackMsg.setSeqNum(nextSeqNum());
        ackMsg.setAckedSeqNum(ackedSeqNum);
        ackMsg.create(null);
        sendMsg(ackMsg, phyConfig);
    }
    
    /**
     * Send a reconfiguration message and wait for acknowledgement.
     * 
     * @param reconfMsg the reconfiguration message (frequencies must be set)
     * @param sensorID the target sensor ID
     * @param phyConfig the PHY configuration for the sensor
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if max retries exceeded
     */
    public void sendReconf(SPReconfMsg reconfMsg, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        int currentSeq = nextSeqNum();
        reconfMsg.setSensorID(sensorID);
        reconfMsg.setSeqNum(currentSeq);
        reconfMsg.create(null);
        
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                sendMsg(reconfMsg, phyConfig);
                Msg response = receive(SP_TIMEOUT);
                if (response instanceof SPAckMsg ackMsg) {
                    if (ackMsg.getAckedSeqNum() == currentSeq) {
                        return;
                    }
                }
            } catch (SocketTimeoutException e) {
                attempts++;
            } catch (IWProtocolException e) {
                attempts++;
            }
        }
        throw new IllegalMsgException();
    }
    
    /**
     * Send a firmware update as multiple fragments using stop-and-wait.
     * Each fragment is sent and must be acknowledged before the next is sent.
     * 
     * @param updateData the complete update data string
     * @param fragmentSize the maximum size of each fragment
     * @param sensorID the target sensor ID
     * @param phyConfig the PHY configuration for the sensor
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if max retries exceeded for any fragment
     */
    public void sendUpdate(String updateData, int fragmentSize, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        // Fragment the update data
        int totalFragments = (int) Math.ceil((double) updateData.length() / fragmentSize);
        
        for (int i = 0; i < totalFragments; i++) {
            int start = i * fragmentSize;
            int end = Math.min(start + fragmentSize, updateData.length());
            String fragmentData = updateData.substring(start, end);
            
            SPUpdateMsg updateMsg = new SPUpdateMsg();
            int currentSeq = nextSeqNum();
            updateMsg.setSensorID(sensorID);
            updateMsg.setSeqNum(currentSeq);
            updateMsg.setFragmentIndex(i);
            updateMsg.setTotalFragments(totalFragments);
            updateMsg.setFragmentData(fragmentData);
            updateMsg.create(null);
            
            // Stop-and-wait for this fragment
            int attempts = 0;
            boolean acked = false;
            while (attempts < MAX_RETRIES && !acked) {
                try {
                    sendMsg(updateMsg, phyConfig);
                    Msg response = receive(SP_TIMEOUT);
                    if (response instanceof SPUpdateAckMsg uackMsg) {
                        if (uackMsg.getAckedFragmentIndex() == i) {
                            acked = true;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    attempts++;
                } catch (IWProtocolException e) {
                    attempts++;
                }
            }
            if (!acked) {
                throw new IllegalMsgException();
            }
        }
    }
    
    /**
     * Send an update fragment acknowledgement.
     * 
     * @param fragmentIndex the fragment index to acknowledge
     * @param sensorID the sensor ID
     * @param phyConfig the PHY configuration for the destination
     * @throws IOException if an I/O error occurs
     * @throws IWProtocolException if a protocol error occurs
     */
    public void sendUpdateAck(int fragmentIndex, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        SPUpdateAckMsg uackMsg = new SPUpdateAckMsg();
        uackMsg.setSensorID(sensorID);
        uackMsg.setSeqNum(nextSeqNum());
        uackMsg.setAckedFragmentIndex(fragmentIndex);
        uackMsg.create(null);
        sendMsg(uackMsg, phyConfig);
    }
}
