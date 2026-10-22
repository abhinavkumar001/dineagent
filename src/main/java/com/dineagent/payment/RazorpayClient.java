package com.dineagent.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Minimal Razorpay REST client (Orders API) using java.net.http and HTTP Basic auth. */
@Component
public class RazorpayClient {

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final String keyId;
    private final String keySecret;

    public RazorpayClient(ObjectMapper json,
                          @Value("${app.razorpay.key-id:}") String keyId,
                          @Value("${app.razorpay.key-secret:}") String keySecret) {
        this.json = json;
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
    }

    public boolean configured() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    public String keyId() {
        return keyId;
    }

    String keySecret() {
        return keySecret;
    }

    /** Creates a Razorpay order. Amount is in paise (₹1 = 100 paise). Returns Razorpay's order id. */
    public String createOrder(int amountPaise, String receipt, Map<String, String> notes) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes));
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(ORDERS_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body());
            if (response.statusCode() / 100 != 2) {
                throw new PaymentException("Razorpay error: " + node.path("error").path("description").asText(response.body()));
            }
            return node.path("id").asText();
        } catch (IOException e) {
            throw new PaymentException("Could not reach Razorpay: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Interrupted while calling Razorpay");
        }
    }
}
