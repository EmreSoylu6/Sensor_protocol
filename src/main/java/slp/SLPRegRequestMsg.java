package slp;

import exceptions.IllegalAddrException;

public class SLPRegRequestMsg extends SLPRegMsg {
    protected static final String SLP_REG_REQ_HEADER = "req ";

    @Override
    protected void create(String data) {
        // prepend reg req header
        data = SLP_REG_REQ_HEADER + data;
        // super class prepends slp header
        super.create(data);
    }

    @Override
    protected SLPRegRequestMsg parse(String sentence) throws IllegalAddrException {
        // Split String at whitespace
        String[] parts = sentence.split("\\s+");

        // Check that the message contains exactly two fields
        if (parts.length != 2) {
            throw new IllegalAddrException();
        }

        // Check if the first token is 'req'
        if (!parts[0].equals(SLP_REG_REQ_HEADER.trim())) {
            throw new IllegalAddrException();
        }

        // Create a new SLPRegRequestMsg instance
        SLPRegRequestMsg msg = new SLPRegRequestMsg();
        try {
            msg.slpid = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalAddrException();
        }

        if (! validateAddress(msg.slpid)) {
            throw new IllegalAddrException();
        }

        return msg;
    }
}
