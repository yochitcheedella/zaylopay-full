package com.zaylope.registration.verification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentOtpDeliveryTest {
    @Test
    void identifiesAsMockDelivery() {
        DevelopmentOtpDelivery delivery = new DevelopmentOtpDelivery();
        assertTrue(delivery.isMock());
    }
}
