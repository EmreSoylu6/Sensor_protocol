package slp;

import core.*;

public class SLPConfiguration extends Configuration {
	protected int remoteID;
	
	// Config for end systems
	public SLPConfiguration(int local, Protocol proto) {
		this.remoteID = local;
	}

	int getRemoteID() {
		return remoteID;
	}
	
}
