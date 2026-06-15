package sp;

import core.Configuration;

// config for sp
public class SPConfiguration extends Configuration {
    private int sensorID;

    // create a new sp configuration
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
