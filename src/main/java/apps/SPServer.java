package apps;

import java.io.IOException;

import core.Msg;
import phy.PhyConfiguration;
import phy.PhyProtocol;
import sp.*;
import exceptions.IWProtocolException;

/**
 * Data Processing Station (server) application.
 * 
 * Receives measurement data from arbitrary many sensor systems,
 * prints the data to screen, and sends acknowledgements back.
 * 
 * Usage: java apps.SPServer [port]
 *   port: optional server port (default: 4999)
 */
public class SPServer {
    protected static final int DEFAULT_SERVER_PORT = 4999;
    
    public static void main(String[] args) {
        int serverPort = DEFAULT_SERVER_PORT;
        if (args.length > 0) {
            try {
                serverPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port: " + args[0]);
                return;
            }
        }
        
        // Initialize PHY layer
        PhyProtocol phy = new PhyProtocol(serverPort);
        
        // Initialize SP protocol
        SPProtocol sp = new SPProtocol(phy);
        
        System.out.println("=== SP Data Processing Station ===");
        System.out.println("Listening on port: " + serverPort);
        System.out.println("Waiting for sensor data...");
        System.out.println();
        
        while (true) {
            try {
                // Receive incoming SP message
                Msg incoming = sp.receive();
                PhyConfiguration senderConfig = (PhyConfiguration) incoming.getConfiguration();
                
                if (incoming instanceof SPDataMsg dataMsg) {
                    // Print measurement data
                    System.out.println("[DATA from Sensor " + dataMsg.getSensorID() + "]");
                    System.out.println("  Seq#:             " + dataMsg.getSeqNum());
                    System.out.println("  Temperature:      " + String.format("%.2f", dataMsg.getTemperature()) + " °C");
                    System.out.println("  pH:               " + String.format("%.2f", dataMsg.getPH()));
                    System.out.println("  Dissolved Oxygen: " + String.format("%.2f", dataMsg.getDissolvedOxygen()) + " mg/L");
                    System.out.println("  Turbidity:        " + String.format("%.2f", dataMsg.getTurbidity()) + " NTU");
                    System.out.println("  Timestamp:        " + dataMsg.getTimestamp());
                    System.out.println("  CRC32:            " + dataMsg.getChecksum());
                    
                    // Send ACK back to the correct sensor
                    sp.sendAck(dataMsg.getSeqNum(), dataMsg.getSensorID(), senderConfig);
                    System.out.println("  -> ACK sent to " + senderConfig.getRemoteIPAddress() + ":" + senderConfig.getRemotePort());
                    System.out.println();
                    
                } else if (incoming instanceof SPAckMsg ackMsg) {
                    System.out.println("[ACK from Sensor " + ackMsg.getSensorID() + "] Seq#: " + ackMsg.getAckedSeqNum());
                    
                } else if (incoming instanceof SPReconfMsg reconfMsg) {
                    System.out.println("[RECONF from Sensor " + reconfMsg.getSensorID() + "]");
                    System.out.println("  Measurement Freq: " + reconfMsg.getMeasurementFrequency() + " s");
                    System.out.println("  Message Freq:     " + reconfMsg.getMessageFrequency() + " s");
                    
                } else if (incoming instanceof SPUpdateMsg updateMsg) {
                    System.out.println("[UPDATE from Sensor " + updateMsg.getSensorID() + "]");
                    System.out.println("  Fragment: " + updateMsg.getFragmentIndex() + "/" + updateMsg.getTotalFragments());
                    System.out.println("  Data: " + updateMsg.getFragmentData());
                    
                    // Send UPDATE_ACK
                    sp.sendUpdateAck(updateMsg.getFragmentIndex(), updateMsg.getSensorID(), senderConfig);
                    System.out.println("  -> UPDATE_ACK sent");
                    System.out.println();
                    
                } else if (incoming instanceof SPUpdateAckMsg uackMsg) {
                    System.out.println("[UPDATE_ACK from Sensor " + uackMsg.getSensorID() + "] Fragment: " + uackMsg.getAckedFragmentIndex());
                    
                } else {
                    System.out.println("[UNKNOWN] Received unknown SP message type: " + ((SPMsg) incoming).getType());
                }
                
            } catch (IOException e) {
                System.out.println("I/O error: " + e.getMessage());
            } catch (IWProtocolException e) {
                System.out.println("Protocol error (message discarded): " + e.getClass().getSimpleName());
            }
        }
    }
}
