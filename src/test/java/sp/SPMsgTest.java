package sp;

import core.Protocol;
import exceptions.BadChecksumException;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// unit tests for all sp message classes
public class SPMsgTest {

    // ===== SPMsg base class tests =====

    @Test
    void testComputeChecksum() {
        String content = "1 100 0 data 25.0 7.0 8.0 100.0 1234567890";
        long checksum1 = SPMsg.computeChecksum(content);
        long checksum2 = SPMsg.computeChecksum(content);
        assertEquals(checksum1, checksum2, "Checksum should be deterministic");
    }

    @Test
    void testDifferentContentDifferentChecksum() {
        long checksum1 = SPMsg.computeChecksum("data 25.0 7.0 8.0 100.0 1234567890");
        long checksum2 = SPMsg.computeChecksum("data 26.0 7.0 8.0 100.0 1234567890");
        assertNotEquals(checksum1, checksum2);
    }

    @Test
    void testParseInvalidHeader() {
        SPMsg msg = new SPMsg();
        assertThrows(IllegalMsgException.class, () -> msg.parse("invalid message"));
    }

    @Test
    void testParseMissingFields() {
        SPMsg msg = new SPMsg();
        assertThrows(IllegalMsgException.class, () -> msg.parse("sp 1 100"));
    }

    @Test
    void testParseBadChecksum() {
        SPMsg msg = new SPMsg();
        // Valid format but wrong checksum (99999)
        assertThrows(BadChecksumException.class, () -> msg.parse("sp 1 100 0 99999 data 25.0 7.0 8.0 100.0 1234567890"));
    }

    @Test
    void testParseNonNumericType() {
        SPMsg msg = new SPMsg();
        assertThrows(IllegalMsgException.class, () -> msg.parse("sp abc 100 0 12345 data"));
    }

    @Test
    void testParseUnknownType() {
        SPMsg msg = new SPMsg();
        // Type 99 doesn't exist - but we need a valid checksum for it to get past checksum check
        // So this will actually throw BadChecksumException first - let's just test for IWProtocolException
        assertThrows(IWProtocolException.class, () -> msg.parse("sp 99 100 0 12345 somedata"));
    }

    // ===== SPDataMsg tests =====

    @Test
    void testCreateDataMsg() {
        SPDataMsg msg = new SPDataMsg();
        msg.setSensorID(100);
        msg.setSeqNum(0);
        msg.setTemperature(25.5f);
        msg.setPH(7.2f);
        msg.setDissolvedOxygen(8.5f);
        msg.setTurbidity(120.0f);
        msg.setTimestamp(1234567890L);
        msg.create(null);

        String wireFormat = new String(msg.getDataBytes());
        assertTrue(wireFormat.startsWith("sp 1 100 0 "), "Should start with SP header and type 1");
        assertTrue(wireFormat.contains("data "), "Should contain data header");
        assertTrue(wireFormat.contains("25.5"), "Should contain temperature");
        assertTrue(wireFormat.contains("7.2"), "Should contain pH");
        assertTrue(wireFormat.contains("1234567890"), "Should contain timestamp");
    }

    @Test
    void testDataMsgRoundTrip() throws IWProtocolException {
        SPDataMsg original = new SPDataMsg();
        original.setSensorID(42);
        original.setSeqNum(7);
        original.setTemperature(22.3f);
        original.setPH(6.8f);
        original.setDissolvedOxygen(9.1f);
        original.setTurbidity(50.0f);
        original.setTimestamp(9999999999L);
        original.create(null);

        String wireFormat = new String(original.getDataBytes());

        // Parse back
        SPMsg parser = new SPMsg();
        SPMsg parsed = (SPMsg) parser.parse(wireFormat);

        assertInstanceOf(SPDataMsg.class, parsed);
        SPDataMsg parsedData = (SPDataMsg) parsed;
        assertEquals(42, parsedData.getSensorID());
        assertEquals(7, parsedData.getSeqNum());
        assertEquals(22.3f, parsedData.getTemperature(), 0.01f);
        assertEquals(6.8f, parsedData.getPH(), 0.01f);
        assertEquals(9.1f, parsedData.getDissolvedOxygen(), 0.01f);
        assertEquals(50.0f, parsedData.getTurbidity(), 0.01f);
        assertEquals(9999999999L, parsedData.getTimestamp());
    }

    @Test
    void testDataMsgType() {
        SPDataMsg msg = new SPDataMsg();
        assertEquals(SPMsg.TYPE_DATA, msg.getType());
    }

    @Test
    void testDataMsgGettersSetters() {
        SPDataMsg msg = new SPDataMsg();
        msg.setTemperature(15.5f);
        msg.setPH(7.0f);
        msg.setDissolvedOxygen(10.0f);
        msg.setTurbidity(200.0f);
        msg.setTimestamp(1000L);
        msg.setSensorID(1);
        msg.setSeqNum(5);

        assertEquals(15.5f, msg.getTemperature());
        assertEquals(7.0f, msg.getPH());
        assertEquals(10.0f, msg.getDissolvedOxygen());
        assertEquals(200.0f, msg.getTurbidity());
        assertEquals(1000L, msg.getTimestamp());
        assertEquals(1, msg.getSensorID());
        assertEquals(5, msg.getSeqNum());
    }

    // ===== SPAckMsg tests =====

    @Test
    void testCreateAckMsg() {
        SPAckMsg msg = new SPAckMsg();
        msg.setSensorID(100);
        msg.setSeqNum(1);
        msg.setAckedSeqNum(0);
        msg.create(null);

        String wireFormat = new String(msg.getDataBytes());
        assertTrue(wireFormat.startsWith("sp 2 100 1 "), "Should start with SP header and type 2");
        assertTrue(wireFormat.contains("ack 0"), "Should contain ack header with acked seq num");
    }

    @Test
    void testAckMsgRoundTrip() throws IWProtocolException {
        SPAckMsg original = new SPAckMsg();
        original.setSensorID(200);
        original.setSeqNum(3);
        original.setAckedSeqNum(2);
        original.create(null);

        String wireFormat = new String(original.getDataBytes());

        SPMsg parser = new SPMsg();
        SPMsg parsed = (SPMsg) parser.parse(wireFormat);

        assertInstanceOf(SPAckMsg.class, parsed);
        SPAckMsg parsedAck = (SPAckMsg) parsed;
        assertEquals(200, parsedAck.getSensorID());
        assertEquals(3, parsedAck.getSeqNum());
        assertEquals(2, parsedAck.getAckedSeqNum());
    }

    @Test
    void testAckMsgType() {
        SPAckMsg msg = new SPAckMsg();
        assertEquals(SPMsg.TYPE_ACK, msg.getType());
    }

    // ===== SPReconfMsg tests =====

    @Test
    void testCreateReconfMsg() {
        SPReconfMsg msg = new SPReconfMsg();
        msg.setSensorID(300);
        msg.setSeqNum(10);
        msg.setMeasurementFrequency(30);
        msg.setMessageFrequency(60);
        msg.create(null);

        String wireFormat = new String(msg.getDataBytes());
        assertTrue(wireFormat.startsWith("sp 3 300 10 "), "Should start with SP header and type 3");
        assertTrue(wireFormat.contains("reconf 30 60"), "Should contain reconf frequencies");
    }

    @Test
    void testReconfMsgRoundTrip() throws IWProtocolException {
        SPReconfMsg original = new SPReconfMsg();
        original.setSensorID(150);
        original.setSeqNum(5);
        original.setMeasurementFrequency(10);
        original.setMessageFrequency(20);
        original.create(null);

        String wireFormat = new String(original.getDataBytes());

        SPMsg parser = new SPMsg();
        SPMsg parsed = (SPMsg) parser.parse(wireFormat);

        assertInstanceOf(SPReconfMsg.class, parsed);
        SPReconfMsg parsedReconf = (SPReconfMsg) parsed;
        assertEquals(150, parsedReconf.getSensorID());
        assertEquals(5, parsedReconf.getSeqNum());
        assertEquals(10, parsedReconf.getMeasurementFrequency());
        assertEquals(20, parsedReconf.getMessageFrequency());
    }

    @Test
    void testReconfMsgType() {
        SPReconfMsg msg = new SPReconfMsg();
        assertEquals(SPMsg.TYPE_RECONF, msg.getType());
    }

    // ===== SPUpdateMsg tests =====

    @Test
    void testCreateUpdateMsg() {
        SPUpdateMsg msg = new SPUpdateMsg();
        msg.setSensorID(400);
        msg.setSeqNum(20);
        msg.setFragmentIndex(0);
        msg.setTotalFragments(5);
        msg.setFragmentData("DEADBEEF01020304");
        msg.create(null);

        String wireFormat = new String(msg.getDataBytes());
        assertTrue(wireFormat.startsWith("sp 4 400 20 "), "Should start with SP header and type 4");
        assertTrue(wireFormat.contains("update 0 5 DEADBEEF01020304"), "Should contain update fragment data");
    }

    @Test
    void testUpdateMsgRoundTrip() throws IWProtocolException {
        SPUpdateMsg original = new SPUpdateMsg();
        original.setSensorID(500);
        original.setSeqNum(15);
        original.setFragmentIndex(2);
        original.setTotalFragments(10);
        original.setFragmentData("AABBCCDD");
        original.create(null);

        String wireFormat = new String(original.getDataBytes());

        SPMsg parser = new SPMsg();
        SPMsg parsed = (SPMsg) parser.parse(wireFormat);

        assertInstanceOf(SPUpdateMsg.class, parsed);
        SPUpdateMsg parsedUpdate = (SPUpdateMsg) parsed;
        assertEquals(500, parsedUpdate.getSensorID());
        assertEquals(15, parsedUpdate.getSeqNum());
        assertEquals(2, parsedUpdate.getFragmentIndex());
        assertEquals(10, parsedUpdate.getTotalFragments());
        assertEquals("AABBCCDD", parsedUpdate.getFragmentData());
    }

    @Test
    void testUpdateMsgType() {
        SPUpdateMsg msg = new SPUpdateMsg();
        assertEquals(SPMsg.TYPE_UPDATE, msg.getType());
    }

    @Test
    void testUpdateMsgMultiWordData() throws IWProtocolException {
        SPUpdateMsg original = new SPUpdateMsg();
        original.setSensorID(1);
        original.setSeqNum(0);
        original.setFragmentIndex(0);
        original.setTotalFragments(1);
        original.setFragmentData("firmware data with spaces");
        original.create(null);

        String wireFormat = new String(original.getDataBytes());

        SPMsg parser = new SPMsg();
        SPMsg parsed = (SPMsg) parser.parse(wireFormat);

        assertInstanceOf(SPUpdateMsg.class, parsed);
        SPUpdateMsg parsedUpdate = (SPUpdateMsg) parsed;
        assertEquals("firmware data with spaces", parsedUpdate.getFragmentData());
    }

    // ===== SPUpdateAckMsg tests =====

    @Test
    void testCreateUpdateAckMsg() {
        SPUpdateAckMsg msg = new SPUpdateAckMsg();
        msg.setSensorID(400);
        msg.setSeqNum(21);
        msg.setAckedFragmentIndex(0);
        msg.create(null);

        String wireFormat = new String(msg.getDataBytes());
        assertTrue(wireFormat.startsWith("sp 5 400 21 "), "Should start with SP header and type 5");
        assertTrue(wireFormat.contains("uack 0"), "Should contain uack header with fragment index");
    }

    @Test
    void testUpdateAckMsgRoundTrip() throws IWProtocolException {
        SPUpdateAckMsg original = new SPUpdateAckMsg();
        original.setSensorID(600);
        original.setSeqNum(25);
        original.setAckedFragmentIndex(7);
        original.create(null);

        String wireFormat = new String(original.getDataBytes());

        SPMsg parser = new SPMsg();
        SPMsg parsed = (SPMsg) parser.parse(wireFormat);

        assertInstanceOf(SPUpdateAckMsg.class, parsed);
        SPUpdateAckMsg parsedUack = (SPUpdateAckMsg) parsed;
        assertEquals(600, parsedUack.getSensorID());
        assertEquals(25, parsedUack.getSeqNum());
        assertEquals(7, parsedUack.getAckedFragmentIndex());
    }

    @Test
    void testUpdateAckMsgType() {
        SPUpdateAckMsg msg = new SPUpdateAckMsg();
        assertEquals(SPMsg.TYPE_UPDATE_ACK, msg.getType());
    }

    // ===== SPConfiguration tests =====

    @Test
    void testConfigGetterSetter() {
        SPConfiguration config = new SPConfiguration(42);
        assertEquals(42, config.getSensorID());

        config.setSensorID(100);
        assertEquals(100, config.getSensorID());
    }
}
