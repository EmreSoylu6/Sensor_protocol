package slp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

/*
 * Base msg class of the SL protocol
 */
public class SLPMsg extends Msg {
	protected int localID;
	
	protected static final String SLP_HEADER = "slp ";
	protected static final int LOW_PORT = 5000;
	protected static final int HIGH_PORT = 65534;

	protected static boolean validateAddress(int addr) {
		if(addr < LOW_PORT || addr > HIGH_PORT)
			return false;
		return true;
	}
	
	/*
	 * Prepend slp header to message
	 */
	@Override
	protected void create(String data) {
		data = SLP_HEADER + data;
		this.dataBytes = data.getBytes();
	}
	
	/*
	 * Test whether the sentence object starts with "slp".
	 * If the next token is "reg" then call the SLPRegMsg parser.
	 * Otherwise call the SLPDataMsg parser.
	 * On error throw a suitable exception, otherwise return the message object.
	 */
	@Override
	protected Msg parse(String sentence) throws IWProtocolException {
		SLPMsg pdu;
		
		// Check header and remove
		if (!sentence.startsWith(SLP_HEADER)) {
			throw new IllegalMsgException();
		}
		String[] parts = sentence.split("\\s+", 2);

		String data = parts[1];

		// Check whether it is a registration 
		// Create the appropriate object for further processing and continue parsing
		if(data.startsWith(SLPRegMsg.SLP_REG_HEADER)) {
			pdu = new SLPRegMsg();
		} else {
			return null;
		}
		this.dataBytes = data.getBytes();
		pdu = (SLPMsg) pdu.parse(data);
		return pdu;
	}

}
