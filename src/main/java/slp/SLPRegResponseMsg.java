package slp;

import exceptions.IllegalMsgException;

/* 
 * Simple link protocol registration response message fields:
 * 	-> RegRespHeader
 *  -> slp registration success
 *  -> [error message]
*/


public class SLPRegResponseMsg extends SLPRegMsg {
	protected static final String SLP_REG_RES_HEADER = "resp ";
	protected static final String SLP_REG_SUCCESS = "ACK";
	protected static final String SLP_REG_FAILED = "NAK";

	private boolean regResponse;
	protected boolean getRegResponse() {
		return this.regResponse;
	}
	protected void setReqResponse(boolean response) {
		this.regResponse = response;
	}

	@Override
	protected void create(String sentence) {
		if(this.regResponse && sentence == null)
			super.create("resp " + SLP_REG_SUCCESS);
		else if(!this.regResponse && sentence == null)
			super.create("resp " + SLP_REG_FAILED);
		else if(!this.regResponse && sentence != null)
			super.create("resp " + SLP_REG_FAILED + " " + sentence);
	}

	@Override
	protected SLPRegResponseMsg parse(String sentence) throws IllegalMsgException {
		// Split String at whitespace
		String[] parts = sentence.split("\\s+", 2);

		// Check that the message contains exactly two fields
		if(parts.length < 1)
			throw new IllegalMsgException();

		// Check if the first token is 'resp'
		if(!parts[0].equals(SLP_REG_RES_HEADER.trim()))
			throw new IllegalMsgException();

		if(parts[1].equals(SLP_REG_SUCCESS)) {
			this.setReqResponse(true);
		} else if(parts[1].startsWith(SLP_REG_FAILED)) {
			this.setReqResponse(false);
			if(parts.length > 2) {
				this.setData(parts[1].substring(SLP_REG_FAILED.length()));
			}
		} else {
			throw new IllegalMsgException();
		}

		return this;
	}
}
