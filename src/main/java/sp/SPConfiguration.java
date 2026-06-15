package sp;

import core.Configuration;

/**
 * Configuration class for the Sensor Protocol (SP).
 * Holds the sensor ID used to address a specific sensor system.
 */
public class SPConfiguration extends Configuration {
    private int sensorID;

    /**
     * Create a new SP configuration.
     * @param sensorID the sensor system identifier
     */
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
