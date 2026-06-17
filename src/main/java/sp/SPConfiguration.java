package sp;

import core.Configuration;

// Konfiguration für SP
public class SPConfiguration extends Configuration {
    private int sensorID;

    // Erstellt eine neue SP-Konfiguration
    public SPConfiguration(int sensorID) {
        this.sensorID = sensorID;
    }

    public int getSensorID() {
        return sensorID;
    }

    public void setSensorID(int sensorID) {
        this.sensorID = sensorID;
    }
}
