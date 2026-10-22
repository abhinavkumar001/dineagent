package com.dineagent.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The signature check is what stops a forged "payment succeeded" message from the browser. */
class PaymentSignatureTest {

    @Test
    void matchesRazorpaysSignatureFormula() {
        // HMAC_SHA256("order_ABC|pay_XYZ", "test_secret"), computed independently
        assertEquals("15656b40fea6f2159b578efa459e969de9f5e223fb8a08393e274ac578d9d005",
                PaymentService.hmacSha256Hex("test_secret", "order_ABC|pay_XYZ"));
    }

    @Test
    void tamperedValuesFail() {
        String real = PaymentService.hmacSha256Hex("test_secret", "order_ABC|pay_XYZ");
        assertTrue(PaymentService.constantTimeEquals(real, real));
        assertFalse(PaymentService.constantTimeEquals(real, PaymentService.hmacSha256Hex("test_secret", "order_ABC|pay_FAKE")));
        assertFalse(PaymentService.constantTimeEquals(real, PaymentService.hmacSha256Hex("wrong_secret", "order_ABC|pay_XYZ")));
        assertFalse(PaymentService.constantTimeEquals(real, null));
    }
}
