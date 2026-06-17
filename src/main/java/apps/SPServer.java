package apps;

import java.io.IOException;

import core.Msg;
import phy.PhyConfiguration;
import phy.PhyProtocol;
import sp.*;
import exceptions.IWProtocolException;

// Server-Anwendung, die Sensordaten empfängt
public class SPServer {
    protected static final int DEFAULT_SERVER_PORT = 4999;
    
    public static void main(String[] args) {
        int serverPort = DEFAULT_SERVER_PORT;
        if (args.length > 0) {
            try {
                serverPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Ungültiger Port: " + args[0]);
                return;
            }
        }
        
        // PHY Layer initialisieren
        PhyProtocol phy = new PhyProtocol(serverPort);
        
        // SP Protokoll initialisieren
        SPProtocol sp = new SPProtocol(phy);
        
        System.out.println("=== SP Data Processing Station ===");
        System.out.println("Lausche auf Port: " + serverPort);
        System.out.println("Warte auf Sensordaten...");
        System.out.println();
        
        while (true) {
            try {
                // Eingehende SP-Nachricht empfangen
                Msg incoming = sp.receive();
                PhyConfiguration senderConfig = (PhyConfiguration) incoming.getConfiguration();
                
                if (incoming instanceof SPDataMsg dataMsg) {
                    // Messdaten ausgeben
                    System.out.println("[DATA von Sensor " + dataMsg.getSensorID() + "]");
                    System.out.println("  Seq#:                " + dataMsg.getSeqNum());
                    System.out.println("  Temperatur:          " + dataMsg.getTemperature() + " °C");
                    System.out.println("  pH-Wert:             " + dataMsg.getPH());
                    System.out.println("  Gelöster Sauerstoff: " + dataMsg.getDissolvedOxygen() + " mg/L");
                    System.out.println("  Trübung:             " + dataMsg.getTurbidity() + " NTU");
                    System.out.println("  Zeitstempel:         " + dataMsg.getTimestamp());
                    System.out.println("  CRC32:               " + dataMsg.getChecksum());
                    
                    // ACK an den korrekten Sensor zurücksenden
                    sp.sendAck(dataMsg.getSeqNum(), dataMsg.getSensorID(), senderConfig);
                    System.out.println("  -> ACK gesendet an " + senderConfig.getRemoteIPAddress() + ":" + senderConfig.getRemotePort());
                    System.out.println();
                    
                } else if (incoming instanceof SPAckMsg ackMsg) {
                    System.out.println("[ACK von Sensor " + ackMsg.getSensorID() + "] Seq#: " + ackMsg.getAckedSeqNum());
                    
                } else if (incoming instanceof SPReconfMsg reconfMsg) {
                    System.out.println("[RECONF von Sensor " + reconfMsg.getSensorID() + "]");
                    System.out.println("  Messintervall: " + reconfMsg.getMeasurementFrequency() + " s");
                    System.out.println("  Sendeintervall: " + reconfMsg.getMessageFrequency() + " s");
                    
                } else if (incoming instanceof SPUpdateMsg updateMsg) {
                    System.out.println("[UPDATE von Sensor " + updateMsg.getSensorID() + "]");
                    System.out.println("  Fragment: " + updateMsg.getFragmentIndex() + "/" + updateMsg.getTotalFragments());
                    System.out.println("  Daten: " + updateMsg.getFragmentData());
                    
                    // UPDATE_ACK senden
                    sp.sendUpdateAck(updateMsg.getFragmentIndex(), updateMsg.getSensorID(), senderConfig);
                    System.out.println("  -> UPDATE_ACK gesendet");
                    System.out.println();
                    
                } else if (incoming instanceof SPUpdateAckMsg uackMsg) {
                    System.out.println("[UPDATE_ACK von Sensor " + uackMsg.getSensorID() + "] Fragment: " + uackMsg.getAckedFragmentIndex());
                    
                } else {
                    System.out.println("[UNBEKANNT] Unbekannter SP-Nachrichtentyp empfangen: " + ((SPMsg) incoming).getType());
                }
                
            } catch (IOException e) {
                System.out.println("I/O Fehler: " + e.getMessage());
            } catch (IWProtocolException e) {
                System.out.println("Protokoll-Fehler (Nachricht verworfen): " + e.getClass().getSimpleName());
            }
        }
    }
}
