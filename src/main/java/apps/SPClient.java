package apps;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

import core.Protocol;
import phy.PhyConfiguration;
import phy.PhyProtocol;
import sp.SPDataMsg;
import sp.SPProtocol;
import exceptions.IWProtocolException;

/**
 * Sensor system client application.
 * 
 * Simulates a water quality sensor that periodically measures
 * environmental parameters and sends them to the data processing station.
 * 
 * Usage: java apps.SPClient <sensorID> [serverPort]
 *   sensorID:   unique identifier for this sensor (int)
 *   serverPort: optional server port (default: 4999)
 */
public class SPClient {
    private static final String SERVERNAME = "localhost";
    private static final int DEFAULT_SERVER_PORT = 4999;
    private static final int MEASUREMENT_INTERVAL_MS = 5000; // 5 seconds
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java apps.SPClient <sensorID> [serverPort]");
            System.out.println("  sensorID:   unique sensor identifier (int)");
            System.out.println("  serverPort: server port (default: " + DEFAULT_SERVER_PORT + ")");
            return;
        }
        
        int sensorID;
        try {
            sensorID = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid sensor ID: " + args[0]);
            return;
        }
        
        int serverPort = DEFAULT_SERVER_PORT;
        if (args.length > 1) {
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid server port: " + args[1]);
                return;
            }
        }
        
        // Use sensorID + 5000 as local port to avoid conflicts
        int clientPort = sensorID + 5000;
        
        // Initialize PHY layer
        PhyProtocol phy = new PhyProtocol(clientPort);
        
        // Initialize SP protocol
        SPProtocol sp = new SPProtocol(phy);
        
        // Create PHY configuration for the server
        PhyConfiguration phyConfig;
        try {
            phyConfig = new PhyConfiguration(InetAddress.getByName(SERVERNAME), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            System.out.println("Cannot resolve server: " + SERVERNAME);
            e.printStackTrace();
            return;
        }
        
        System.out.println("=== SP Sensor Client ===");
        System.out.println("Sensor ID: " + sensorID);
        System.out.println("Client port: " + clientPort);
        System.out.println("Server: " + SERVERNAME + ":" + serverPort);
        System.out.println("Sending measurements every " + MEASUREMENT_INTERVAL_MS / 1000 + " seconds...");
        System.out.println();
        
        Random random = new Random();
        int measurementCount = 0;
        
        while (true) {
            // Create measurement with random but realistic values
            SPDataMsg dataMsg = new SPDataMsg();
            dataMsg.setTemperature(10.0f + random.nextFloat() * 25.0f);      // 10-35 °C
            dataMsg.setPH(6.0f + random.nextFloat() * 3.0f);                 // 6.0-9.0 pH
            dataMsg.setDissolvedOxygen(4.0f + random.nextFloat() * 12.0f);    // 4-16 mg/L
            dataMsg.setTurbidity(random.nextFloat() * 500.0f);                // 0-500 NTU
            dataMsg.setTimestamp(System.currentTimeMillis());
            
            measurementCount++;
            System.out.println("[Measurement #" + measurementCount + "]");
            System.out.println("  Temperature:      " + String.format("%.2f", dataMsg.getTemperature()) + " °C");
            System.out.println("  pH:               " + String.format("%.2f", dataMsg.getPH()));
            System.out.println("  Dissolved Oxygen: " + String.format("%.2f", dataMsg.getDissolvedOxygen()) + " mg/L");
            System.out.println("  Turbidity:        " + String.format("%.2f", dataMsg.getTurbidity()) + " NTU");
            System.out.println("  Timestamp:        " + dataMsg.getTimestamp());
            
            try {
                sp.sendData(dataMsg, sensorID, phyConfig);
                System.out.println("  -> Sent successfully, ACK received.");
            } catch (IOException | IWProtocolException e) {
                System.out.println("  -> FAILED to send: " + e.getMessage());
            }
            System.out.println();
            
            try {
                Thread.sleep(MEASUREMENT_INTERVAL_MS);
            } catch (InterruptedException e) {
                System.out.println("Sensor interrupted, shutting down.");
                break;
            }
        }
    }
}
