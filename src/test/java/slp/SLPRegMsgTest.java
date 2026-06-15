package slp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SLPRegMsgTest {
    @Test
    @DisplayName("Registration request message creation test")
    void createTest() {
        SLPRegMsg reg = new SLPRegMsg();
        reg.create("5000");
        assertEquals("slp reg 5000", new String(reg.getDataBytes()));
    }
}
