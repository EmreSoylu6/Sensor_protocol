package slp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import core.Configuration;
import core.Msg;
import core.Protocol;
import phy.PhyConfiguration;
import phy.PhyProtocol;
import exceptions.*;


public class SLPProtocol implements Protocol {
    private static final int SLP_TIMEOUT = 2000;
    private int myID;
    private final PhyProtocol phy;
    private final boolean isSwitch;
    private boolean isRegistered;
    private boolean useTimeout;
    // Switches map slp id to virtual link
    private Map<Integer, PhyConfiguration> systems;
    PhyConfiguration phyConfig;

    // Constructor
    public SLPProtocol(int id, boolean isSwitch, PhyProtocol proto) throws IWProtocolException, IOException {
        if (isSwitch && id < 5000) {
            systems = new HashMap<Integer, PhyConfiguration>();
        } else {
            if (!SLPRegMsg.validateAddress(id))
                throw new IllegalAddrException();
            this.myID = id;
            this.isRegistered = false;
        }
        this.isSwitch = isSwitch;
        this.phy = proto;
    }

    // Register an end systems
    public void register(InetAddress rname, int rp) throws IWProtocolException, IOException {
        // Create registration message object
        SLPRegRequestMsg reg = new SLPRegRequestMsg();
        // Fill registration message fields
        reg.create(Integer.toString(this.myID));
        // Create configuration object
        this.phyConfig = new PhyConfiguration(rname, rp, proto_id.SLP);

        int waitCount = 0;
        Msg msg = null;
        IWProtocolException iwEx = new RegistrationFailedException("Registration timed out");
        // Wait for response from switch -> abort if three registration attempts have failed
        while (waitCount < 3) {
            try {
                // Send registration message to switch
                phy.send(new String(reg.getDataBytes()), this.phyConfig);
                // Wait for response
                Msg in = phy.receive(SLP_TIMEOUT);

                // Check for correct demux info
                if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.SLP)
                    continue;

                // Parse incoming message string to SLPMsg object
                Msg resMsg = new SLPMsg();
                resMsg = ((SLPMsg) resMsg).parse(in.getData());

                // If the msg is a registration response
                if (resMsg instanceof SLPRegResponseMsg regResMsg) {
                    if (regResMsg.getRegResponse()) {
                        // Registration successful -> return to app
                        this.isRegistered = true;
                        return;
                    } else {
                        // Registration denied -> abort registration
                        iwEx = new RegistrationFailedException("Registration denied");
                        waitCount = 3;
                    }
                }
            } catch (SocketTimeoutException e) {
                waitCount++;
            } catch (IWProtocolException e) {
                // Illegal message received -> ignore and wait for correct response
            }
        }

        // No acknowledgement received -> registration failed
        throw iwEx;
    }

    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {

    }

    @Override
    public Msg receive() throws IOException, IWProtocolException {
        SLPMsg in = null;

        return in;
    }
}
