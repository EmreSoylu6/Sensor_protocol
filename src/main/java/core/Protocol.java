package core;

import java.io.IOException;

import exceptions.IWProtocolException;

/*
 * Protocol interface
 */
public interface Protocol {
	enum proto_id {
		PHY, APP, SLP, CP, SP
	}

	void send(String s, Configuration config) throws IOException, IWProtocolException;
	Msg receive() throws IOException, IWProtocolException;

}
