// This class is not distributed to students

package apps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;

import core.Msg;
import phy.PhyProtocol;
import slp.SLPConfiguration;
import slp.SLPProtocol;
import exceptions.*;

public class SLPClient implements Runnable {
	//private static final String SWITCHNAME = "134.103.176.120";
	private static final String SWITCHNAME = "localhost";
	SLPProtocol ethP;
	
	public SLPClient(SLPProtocol eth) {
		this.ethP = eth;
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("Provide an address identifier (int) from range [5000:65534]");
			return;
		}
		var id = Integer.parseInt(args[0]);
		if(id<5000 || id > 65534) {
			System.out.println("Invalid address identifier! Range [5000:65534]");
			return;
		}

		// Set up the virtual link protocol
		PhyProtocol phy = new PhyProtocol(id);

		// Create socket 
		SLPProtocol slp;
		
		// Start Protocol
		try {
			slp = new SLPProtocol(id, false, phy);
			slp.register(InetAddress.getByName(SWITCHNAME), SLPSwitch.SWITCHPORT);
		} catch (IWProtocolException | IOException e) {
			e.printStackTrace();
			return;
		}

		// Start the receiver side of the app
		SLPClient recv = new SLPClient(slp);
		Thread recvT = new Thread(recv);
		recvT.start();

		// Read data from user to send to client
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		boolean eof = false;
		System.out.println("I'm ID: " + id + " ready to send");
		while (!eof) {
			try {
				System.out.println("Message Destination: ");
				String sentence = null;
				sentence = inFromUser.readLine();
				int dst = Integer.parseInt(sentence.trim());

				System.out.println("Message: ");
				sentence = null;
				sentence = inFromUser.readLine();

				SLPConfiguration config = new SLPConfiguration(dst, phy);
				slp.send(sentence.trim(), config);
			} catch (IWProtocolException | IOException e) {
				e.printStackTrace();
			}
		}

	}

	// Receiver thread
	@Override
	public void run() {
		boolean eof = false;
		System.out.println("Start reading thread ... ");
		while (!eof) {
			try {
				Msg msg = this.ethP.receive();
				String sentence = msg.getData().trim();
					System.out.println("Received message: " + sentence);
			} catch (IWProtocolException | IOException e) {
				e.printStackTrace();
			}
		}

	}

}
