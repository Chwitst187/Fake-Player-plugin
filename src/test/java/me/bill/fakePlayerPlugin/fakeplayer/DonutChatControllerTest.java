package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DonutChatControllerTest {

    @Test
    void singleMessagePoolAlwaysUsesItsOnlyEntry() {
        assertEquals(0, DonutChatController.chooseMessageIndex(1, 0));
    }

    @Test
    void multipleMessagesNeverRepeatImmediately() {
        for (int previous = 0; previous < 5; previous++) {
            for (int sample = 0; sample < 100; sample++) {
                assertNotEquals(previous, DonutChatController.chooseMessageIndex(5, previous));
            }
        }
    }

    @Test
    void configuredRateIsSpreadExactlyAcrossTwentyTicks() {
        for (int rate = 10; rate <= 20; rate++) {
            TickMessageDistributor distributor = new TickMessageDistributor();
            int sent = 0;
            for (int tick = 0; tick < 20; tick++) {
                int messagesThisTick = distributor.advance(rate);
                sent += messagesThisTick;
                assertEquals(0, Math.max(0, messagesThisTick - 1));
            }
            assertEquals(rate, sent);
        }
    }
}
