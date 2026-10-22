package com.dineagent.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    public record CreateRequest(@NotBlank String sessionId) {}

    public record VerifyRequest(@NotBlank String sessionId, @NotBlank String razorpayOrderId,
                                @NotBlank String razorpayPaymentId, @NotBlank String razorpaySignature) {}

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/order")
    public Map<String, Object> create(@Valid @RequestBody CreateRequest req) {
        return payments.createPayment(req.sessionId());
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@Valid @RequestBody VerifyRequest req) {
        return payments.verify(req.sessionId(), req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature());
    }

    /** Razorpay calls this; the raw body is needed exactly as sent to check the signature. */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody String body,
                                                       @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        payments.webhook(body, signature);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, String>> failed(PaymentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
