package slp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

/* 
 * Simple link protocol registration request message fields:
 *  -> DataHeader
 * 	-> RegHeader
 *  -> slp id
*/


public class SLPRegMsg extends SLPMsg {
	protected static final String SLP_REG_HEADER = "reg ";

	protected int slpid;
	
	protected int getSlpId() {
		return slpid;
	}

	/*
	* Create registration message. This message concatenates the reg header with the SLP ID.
	* The slp header is prepended in the super-class.
	*/
	@Override
	protected void create(String data) {
		// prepend reg header
		data =  SLP_REG_HEADER + data;
		// super class prepends slp header
		super.create(data);
	}
	
	/*
	 * This method should be called by SLPMsg.parse only.
	 * Tokenize the given string object.
	 * Test if the first token is 'reg'.
	 */
	@Override
	protected Msg parse(String sentence) throws IWProtocolException {

		//Split String at whitespace
		String[] parts = sentence.split("\\s+", 2);
		
		//Check that the message contains exactly two fields
		if(parts.length < 2)
			throw new IllegalMsgException();

		// Test if it is a response msg
		SLPRegMsg msg;
		if (parts[1].startsWith(SLPRegRequestMsg.SLP_REG_REQ_HEADER))
			msg = new SLPRegRequestMsg();
                else if (parts[1].startsWith(SLPRegResponseMsg.SLP_REG_RES_HEADER))
			msg = new SLPRegResponseMsg();
		else
			throw new IllegalMsgException();

		msg = (SLPRegMsg) msg.parse(parts[1]);

		return msg;
	}
}
