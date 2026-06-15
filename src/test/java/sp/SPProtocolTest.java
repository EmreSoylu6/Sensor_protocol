package sp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import core.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;
import phy.PhyConfiguration;
import phy.PhyMsg;
import phy.PhyProtocol;

// unit tests for spprotocol using mockito to mock the phy layer
class SPProtocolTest {

    // Helper class to expose the protected PhyMsg constructor and parse method for testing
    static class TestPhyMsg extends PhyMsg {
        public TestPhyMsg(PhyConfiguration config) {
            super(config);
        }

        @Override
        public core.Msg parse(String sentence) throws IllegalMsgException {
            return super.parse(sentence);
        }
    }

    int sensorID = 100;
    int clientPort = 5100;
    String clientName = "localhost";
    int serverPort = 4999;
    String serverName = "localhost";

    PhyProtocol phyProtocolMock;
    TestPhyMsg testMsg;

    @BeforeEach
    void setup() {
        PhyConfiguration phyConfig;
        try {
            phyConfig = new PhyConfiguration(InetAddress.getByName(clientName), clientPort, Protocol.proto_id.SP);
            testMsg = new TestPhyMsg(phyConfig);
        } catch (UnknownHostException e) {
            fail("Setup failed: " + e.getMessage());
        }

        phyProtocolMock = mock(PhyProtocol.class);
    }

    // ===== Receive tests =====

    @Test
    void testReceiveDataMsg() throws IOException, IWProtocolException {
        // Create a valid SP data message wrapped in PHY
        SPDataMsg dataMsg = new SPDataMsg();
        dataMsg.setSensorID(sensorID);
        dataMsg.setSeqNum(0);
        dataMsg.setTemperature(25.0f);
        dataMsg.setPH(7.0f);
        dataMsg.setDissolvedOxygen(8.0f);
        dataMsg.setTurbidity(100.0f);
        dataMsg.setTimestamp(1234567890L);
        dataMsg.create(null);

        String spMsgStr = new String(dataMsg.getDataBytes());

        // Wrap in PHY message
        testMsg = (TestPhyMsg) testMsg.parse("phy 9 " + spMsgStr);

        when(phyProtocolMock.receive()).thenReturn(testMsg);

        SPProtocol sp = new SPProtocol(phyProtocolMock);
        SPMsg received = (SPMsg) sp.receive();

        assertInstanceOf(SPDataMsg.class, received);
        SPDataMsg receivedData = (SPDataMsg) received;
        assertEquals(sensorID, receivedData.getSensorID());
        assertEquals(25.0f, receivedData.getTemperature(), 0.01f);
        assertEquals(7.0f, receivedData.getPH(), 0.01f);
    }

    @Test
    void testReceiveAckMsg() throws IOException, IWProtocolException {
        SPAckMsg ackMsg = new SPAckMsg();
        ackMsg.setSensorID(sensorID);
        ackMsg.setSeqNum(1);
        ackMsg.setAckedSeqNum(0);
        ackMsg.create(null);

        String spMsgStr = new String(ackMsg.getDataBytes());
        testMsg = (TestPhyMsg) testMsg.parse("phy 9 " + spMsgStr);

        when(phyProtocolMock.receive(anyInt())).thenReturn(testMsg);

        SPProtocol sp = new SPProtocol(phyProtocolMock);
        SPMsg received = (SPMsg) sp.receive(2000);

        assertInstanceOf(SPAckMsg.class, received);
        assertEquals(0, ((SPAckMsg) received).getAckedSeqNum());
    }

    @Test
    void testReceiveWrongProtocol() throws IOException, IWProtocolException {
        // Create a PHY message with SLP protocol ID (not SP)
        PhyConfiguration slpConfig;
        try {
            slpConfig = new PhyConfiguration(InetAddress.getByName(clientName), clientPort, Protocol.proto_id.SLP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }
        TestPhyMsg slpMsg = new TestPhyMsg(slpConfig);
        slpMsg = (TestPhyMsg) slpMsg.parse("phy 5 slp reg req 5000");

        when(phyProtocolMock.receive()).thenReturn(slpMsg);

        SPProtocol sp = new SPProtocol(phyProtocolMock);
        assertThrows(IllegalMsgException.class, sp::receive);
    }

    // ===== SendData tests =====

    @Test
    void testSendDataSuccess() throws IOException, IWProtocolException {
        // Prepare the ACK response
        SPAckMsg ackResponse = new SPAckMsg();
        ackResponse.setSensorID(sensorID);
        ackResponse.setSeqNum(1);
        ackResponse.setAckedSeqNum(0); // ACKs seqNum 0
        ackResponse.create(null);

        String ackStr = new String(ackResponse.getDataBytes());
        TestPhyMsg phyAck = (TestPhyMsg) testMsg.parse("phy 9 " + ackStr);

        when(phyProtocolMock.receive(anyInt())).thenReturn(phyAck);

        SPProtocol sp = new SPProtocol(phyProtocolMock);

        SPDataMsg dataMsg = new SPDataMsg();
        dataMsg.setTemperature(25.0f);
        dataMsg.setPH(7.0f);
        dataMsg.setDissolvedOxygen(8.0f);
        dataMsg.setTurbidity(100.0f);
        dataMsg.setTimestamp(1234567890L);

        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        assertDoesNotThrow(() -> sp.sendData(dataMsg, sensorID, serverConfig));
        verify(phyProtocolMock, times(1)).send(anyString(), any(PhyConfiguration.class));
        verify(phyProtocolMock, times(1)).receive(2000);
    }

    @Test
    void testSendDataTimeout() throws IOException, IWProtocolException {
        when(phyProtocolMock.receive(anyInt())).thenThrow(new SocketTimeoutException());

        SPProtocol sp = new SPProtocol(phyProtocolMock);

        SPDataMsg dataMsg = new SPDataMsg();
        dataMsg.setTemperature(25.0f);
        dataMsg.setPH(7.0f);
        dataMsg.setDissolvedOxygen(8.0f);
        dataMsg.setTurbidity(100.0f);
        dataMsg.setTimestamp(1234567890L);

        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        assertThrows(IWProtocolException.class, () -> sp.sendData(dataMsg, sensorID, serverConfig));
        verify(phyProtocolMock, times(3)).send(anyString(), any(PhyConfiguration.class));
        verify(phyProtocolMock, times(3)).receive(2000);
    }

    // ===== SendAck tests =====

    @Test
    void testSendAck() throws IOException, IWProtocolException {
        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        SPProtocol sp = new SPProtocol(phyProtocolMock);
        assertDoesNotThrow(() -> sp.sendAck(5, sensorID, serverConfig));
        verify(phyProtocolMock, times(1)).send(anyString(), any(PhyConfiguration.class));
    }

    @Test
    void testSendAckContent() throws IOException, IWProtocolException {
        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        SPProtocol sp = new SPProtocol(phyProtocolMock);
        sp.sendAck(42, sensorID, serverConfig);

        // Verify the message sent contains "ack 42"
        verify(phyProtocolMock).send(argThat(s -> s.contains("ack 42")), any(PhyConfiguration.class));
    }

    // ===== SendUpdateAck tests =====

    @Test
    void testSendUpdateAck() throws IOException, IWProtocolException {
        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        SPProtocol sp = new SPProtocol(phyProtocolMock);
        assertDoesNotThrow(() -> sp.sendUpdateAck(3, sensorID, serverConfig));
        verify(phyProtocolMock, times(1)).send(anyString(), any(PhyConfiguration.class));
    }

    @Test
    void testSendUpdateAckContent() throws IOException, IWProtocolException {
        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        SPProtocol sp = new SPProtocol(phyProtocolMock);
        sp.sendUpdateAck(5, sensorID, serverConfig);

        verify(phyProtocolMock).send(argThat(s -> s.contains("uack 5")), any(PhyConfiguration.class));
    }

    // ===== Message loss and retry tests =====

    @Test
    void testSendDataRetryThenSuccess() throws IOException, IWProtocolException {
        SPAckMsg ackResponse = new SPAckMsg();
        ackResponse.setSensorID(sensorID);
        ackResponse.setSeqNum(1);
        ackResponse.setAckedSeqNum(0);
        ackResponse.create(null);

        String ackStr = new String(ackResponse.getDataBytes());
        TestPhyMsg phyAck = (TestPhyMsg) testMsg.parse("phy 9 " + ackStr);

        // First call times out, second succeeds
        when(phyProtocolMock.receive(anyInt()))
                .thenThrow(new SocketTimeoutException())
                .thenReturn(phyAck);

        SPProtocol sp = new SPProtocol(phyProtocolMock);

        SPDataMsg dataMsg = new SPDataMsg();
        dataMsg.setTemperature(25.0f);
        dataMsg.setPH(7.0f);
        dataMsg.setDissolvedOxygen(8.0f);
        dataMsg.setTurbidity(100.0f);
        dataMsg.setTimestamp(1234567890L);

        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        assertDoesNotThrow(() -> sp.sendData(dataMsg, sensorID, serverConfig));
        verify(phyProtocolMock, times(2)).send(anyString(), any(PhyConfiguration.class));
    }

    @Test
    void testCorruptedAckCausesRetry() throws IOException, IWProtocolException {
        // First receive returns a corrupted message (wrong protocol)
        PhyConfiguration slpConfig;
        try {
            slpConfig = new PhyConfiguration(InetAddress.getByName(clientName), clientPort, Protocol.proto_id.SLP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }
        TestPhyMsg corruptedMsg = new TestPhyMsg(slpConfig);
        corruptedMsg = (TestPhyMsg) corruptedMsg.parse("phy 5 slp reg req 5000");

        // After 3 corrupted messages, should give up
        when(phyProtocolMock.receive(anyInt())).thenReturn(corruptedMsg);

        SPProtocol sp = new SPProtocol(phyProtocolMock);

        SPDataMsg dataMsg = new SPDataMsg();
        dataMsg.setTemperature(25.0f);
        dataMsg.setPH(7.0f);
        dataMsg.setDissolvedOxygen(8.0f);
        dataMsg.setTurbidity(100.0f);
        dataMsg.setTimestamp(1234567890L);

        PhyConfiguration serverConfig;
        try {
            serverConfig = new PhyConfiguration(InetAddress.getByName(serverName), serverPort, Protocol.proto_id.SP);
        } catch (UnknownHostException e) {
            fail("Setup failed");
            return;
        }

        assertThrows(IWProtocolException.class, () -> sp.sendData(dataMsg, sensorID, serverConfig));
    }
}
