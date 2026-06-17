package sp;

import java.io.IOException;
import java.net.SocketTimeoutException;

import core.Configuration;
import core.Msg;
import core.Protocol;
import phy.PhyConfiguration;
import phy.PhyProtocol;
import exceptions.*;

// Implementierung des Sensor-Protokolls
public class SPProtocol implements Protocol {
    private static final int SP_TIMEOUT = 2000;
    private static final int MAX_RETRIES = 3;
    
    private final PhyProtocol phy;
    private int seqNum;
    
    // Erstellt eine neue SPProtocol-Instanz
    public SPProtocol(PhyProtocol phy) {
        this.phy = phy;
        this.seqNum = 0;
    }
    
    // Holt die nächste Sequenznummer und erhöht den Zähler
    private int nextSeqNum() {
        return seqNum++;
    }
    
    // Sendet eine rohe String-Nachricht über die PHY-Schicht
    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {
        PhyConfiguration phyConfig = (PhyConfiguration) config;
        phy.send(s, phyConfig);
    }
    
    // Sendet eine fertig gebaute SP-Nachricht über die PHY-Schicht
    public void sendMsg(SPMsg msg, PhyConfiguration phyConfig) throws IOException, IWProtocolException {
        String msgString = new String(msg.getDataBytes());
        phy.send(msgString, phyConfig);
    }
    
    // Empfängt eine Nachricht von der PHY-Schicht und parst sie als SP-Nachricht
    @Override
    public Msg receive() throws IOException, IWProtocolException {
        Msg phyMsg = phy.receive();
        
        // Demultiplexen: Nur SP-Nachrichten verarbeiten (proto_id = SP)
        PhyConfiguration phyConfig = (PhyConfiguration) phyMsg.getConfiguration();
        if (phyConfig.getPid() != proto_id.SP) {
            throw new IllegalMsgException();
        }
        
        // SP-Nachricht aus der PHY-Payload parsen
        SPMsg spMsg = new SPMsg();
        SPMsg parsed = (SPMsg) spMsg.parse(phyMsg.getData());
        
        // PHY-Konfiguration übernehmen, damit wir wissen, wer gesendet hat
        parsed.setConfiguration(phyConfig);
        
        return parsed;
    }
    
    // Empfängt eine Nachricht mit Timeout
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
    
    // Sendet eine Messdaten-Nachricht und wartet auf eine Bestätigung (ACK)
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
                
                // Auf ACK warten
                Msg response = receive(SP_TIMEOUT);
                if (response instanceof SPAckMsg ackMsg) {
                    if (ackMsg.getAckedSeqNum() == currentSeq) {
                        return; // Erfolg
                    }
                }
            } catch (SocketTimeoutException e) {
                attempts++;
            } catch (IWProtocolException e) {
                // Fehlerhafte Antwort, nochmal versuchen
                attempts++;
            }
        }
        throw new IllegalMsgException();
    }
    
    // Sendet ein ACK für eine empfangene Nachricht
    public void sendAck(int ackedSeqNum, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        SPAckMsg ackMsg = new SPAckMsg();
        ackMsg.setSensorID(sensorID);
        ackMsg.setSeqNum(nextSeqNum());
        ackMsg.setAckedSeqNum(ackedSeqNum);
        ackMsg.create(null);
        sendMsg(ackMsg, phyConfig);
    }
    
    // Sendet eine Rekonfigurationsnachricht und wartet auf ein ACK
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
    
    // Sendet ein Firmware-Update in mehreren Fragmenten (Stop-and-Wait)
    public void sendUpdate(String updateData, int fragmentSize, int sensorID, PhyConfiguration phyConfig) 
            throws IOException, IWProtocolException {
        // Update-Daten in Fragmente zerlegen
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
            
            // Stop-and-Wait für dieses Fragment
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
    
    // Sendet ein ACK für ein Update-Fragment
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
