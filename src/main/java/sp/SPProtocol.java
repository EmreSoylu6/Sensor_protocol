package sp;

import java.io.IOException;
import java.net.SocketTimeoutException;

import core.Configuration;
import core.Msg;
import core.Protocol;
import phy.PhyConfiguration;
import phy.PhyProtocol;
import exceptions.*;

// sensor protocol implementation
public class SPProtocol implements Protocol {
    private static final int SP_TIMEOUT = 2000;
    private static final int MAX_RETRIES = 3;
    
    private final PhyProtocol phy;
    private int seqNum;
    
    // create a new spprotocol instance
    public SPProtocol(PhyProtocol phy) {
        this.phy = phy;
        this.seqNum = 0;
    }
    
    // get the next sequence number and increment the counter
    private int nextSeqNum() {
        return seqNum++;
    }
    
    // send a raw string message via the phy layer
    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {
        PhyConfiguration phyConfig = (PhyConfiguration) config;
        phy.send(s, phyConfig);
    }
    
    // send a pre-built sp message via the phy layer
    public void sendMsg(SPMsg msg, PhyConfiguration phyConfig) throws IOException, IWProtocolException {
        String msgString = new String(msg.getDataBytes());
        phy.send(msgString, phyConfig);
    }
    
    // receive a message from the phy layer and parse it as an sp message
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
    
    // receive a message with a timeout
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
    
    // send a measurement data message and wait for acknowledgement
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
    
    // send an acknowledgement for a received message
    public void sendAck(int ackedSeqNum, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        SPAckMsg ackMsg = new SPAckMsg();
        ackMsg.setSensorID(sensorID);
        ackMsg.setSeqNum(nextSeqNum());
        ackMsg.setAckedSeqNum(ackedSeqNum);
        ackMsg.create(null);
        sendMsg(ackMsg, phyConfig);
    }
    
    // send a reconfiguration message and wait for acknowledgement
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
    
    // send a firmware update as multiple fragments using stop-and-wait
    public void sendUpdate(String updateData, int fragmentSize, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        // Fragment the update data
        int totalFragments = updateData.length() / fragmentSize;
        if (updateData.length() % fragmentSize != 0) totalFragments++;
        
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
    
    // send an update fragment acknowledgement
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
