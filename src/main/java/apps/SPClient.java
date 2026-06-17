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

// Client-Anwendung für das Sensorsystem
public class SPClient {
    private static final String SERVERNAME = "localhost";
    private static final int DEFAULT_SERVER_PORT = 4999;
    private static final int MEASUREMENT_INTERVAL_MS = 5000; // 5 seconds
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Aufruf: java apps.SPClient <sensorID> [serverPort]");
            System.out.println("  sensorID:   Eindeutige ID des Sensors (int)");
            System.out.println("  serverPort: Port des Servers (Standard: " + DEFAULT_SERVER_PORT + ")");
            return;
        }
        
        int sensorID;
        try {
            sensorID = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Ungültige Sensor-ID: " + args[0]);
            return;
        }
        
        int serverPort = DEFAULT_SERVER_PORT;
        if (args.length > 1) {
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Ungültiger Server-Port: " + args[1]);
                return;
            }
        }
        
        // Wir nutzen sensorID + 5000 als lokalen Port, um Konflikte zu vermeiden
        int clientPort = sensorID + 5000;
        
        // PHY Layer initialisieren
        PhyProtocol phy = new PhyProtocol(clientPort);
        
        // SP Protokoll initialisieren
        SPProtocol sp = new SPProtocol(phy);
        
        // PHY Konfiguration für den Server erstellen
        PhyConfiguration phyConfig;
        try {
            phyConfig = new PhyConfiguration(InetAddress.getByName(SERVERNAME), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            System.out.println("Kann Server nicht finden: " + SERVERNAME);
            e.printStackTrace();
            return;
        }
        
        System.out.println("=== SP Sensor Client ===");
        System.out.println("Sensor ID: " + sensorID);
        System.out.println("Client Port: " + clientPort);
        System.out.println("Server: " + SERVERNAME + ":" + serverPort);
        System.out.println("Sende Messwerte alle " + MEASUREMENT_INTERVAL_MS / 1000 + " Sekunden...");
        System.out.println();
        
        Random random = new Random();
        int measurementCount = 0;
        
        while (true) {
            // Erzeuge Messung mit zufälligen, aber realistischen Werten
            SPDataMsg dataMsg = new SPDataMsg();
            dataMsg.setTemperature(10.0f + random.nextFloat() * 25.0f);      // 10-35 °C
            dataMsg.setPH(6.0f + random.nextFloat() * 3.0f);                 // 6.0-9.0 pH
            dataMsg.setDissolvedOxygen(4.0f + random.nextFloat() * 12.0f);    // 4-16 mg/L
            dataMsg.setTurbidity(random.nextFloat() * 500.0f);                // 0-500 NTU
            dataMsg.setTimestamp(System.currentTimeMillis());
            
            measurementCount++;
            System.out.println("[Messung #" + measurementCount + "]");
            System.out.println("  Temperatur:          " + dataMsg.getTemperature() + " °C");
            System.out.println("  pH-Wert:             " + dataMsg.getPH());
            System.out.println("  Gelöster Sauerstoff: " + dataMsg.getDissolvedOxygen() + " mg/L");
            System.out.println("  Trübung:             " + dataMsg.getTurbidity() + " NTU");
            System.out.println("  Zeitstempel:         " + dataMsg.getTimestamp());
            
            try {
                sp.sendData(dataMsg, sensorID, phyConfig);
                System.out.println("  -> Erfolgreich gesendet, ACK erhalten.");
            } catch (IOException | IWProtocolException e) {
                System.out.println("  -> Fehler beim Senden: " + e.getMessage());
            }
            System.out.println();
            
            try {
                Thread.sleep(MEASUREMENT_INTERVAL_MS);
            } catch (InterruptedException e) {
                System.out.println("Sensor wurde unterbrochen, fahre herunter.");
                break;
            }
        }
    }
}
