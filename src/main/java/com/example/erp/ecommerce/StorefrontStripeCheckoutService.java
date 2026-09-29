package com.example.erp.ecommerce;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
class StorefrontStripeCheckoutService {
    record Quote(String currency, BigDecimal tndAmount, long amountCents, BigDecimal rate) {}
    record CheckoutResult(String url, Quote quote) {}
    record StripeCheckoutVerified(String sessionId, String clientReferenceId,
            String currency, long amountTotal, String paymentStatus, boolean liveMode) {}

    private final StorefrontOrderService orders;
    private final StorefrontStripeCheckoutRepository checkouts;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String secretKey;
    private final String webhookSecret;
    private final String returnBaseUrl;
    private final BigDecimal rate;

    StorefrontStripeCheckoutService(StorefrontOrderService orders, StorefrontStripeCheckoutRepository checkouts,
            ObjectMapper json, @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.stripe.storefront-return-base-url:}") String returnBaseUrl,
            @Value("${app.stripe.tnd-to-eur-rate:0}") BigDecimal rate) {
        this.orders = orders;
        this.checkouts = checkouts;
        this.json = json;
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.returnBaseUrl = returnBaseUrl;
        this.rate = rate;
    }

    boolean configured() {
        return secretKey.startsWith("sk_test_") && webhookSecret.startsWith("whsec_")
                && rate.signum() > 0 && returnBaseUrl.startsWith("http");
    }

    Quote quote(String username, UUID orderId) {
        requireConfigured();
        OnlineOrder order = orders.getOrder(username, orderId);
        if (order.getStatus() != OnlineOrderStatus.PENDING_PAYMENT || !Instant.now().isBefore(order.getExpiresAt())) {
            throw new IllegalStateException("Only an unexpired pending order can start Stripe Checkout");
        }
        var existing = checkouts.findByOrderId(orderId);
        if (existing.isPresent()) return quote(existing.get());
        BigDecimal amount = order.getTotalAmount().amount();
        long cents = amount.multiply(rate).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (cents < 50 || cents > 99_999_999) {
            throw new IllegalArgumentException("Converted EUR charge must be between 0.50 and 999,999.99");
        }
        return new Quote("EUR", amount, cents, rate);
    }

    CheckoutResult create(String username, UUID orderId) {
        Quote quote = quote(username, orderId);
        StorefrontStripeCheckout checkout = orders.prepareStripeCheckout(username, orderId,
                quote.rate(), quote.amountCents());
        if ("EXPIRED".equals(checkout.status)) {
            throw new IllegalStateException("Stripe Checkout expired; cancel this order and place a new one");
        }
        if (checkout.checkoutUrl != null) return new CheckoutResult(checkout.checkoutUrl, quote(checkout));
        String base = returnBaseUrl.endsWith("/") ? returnBaseUrl : returnBaseUrl + "/";
        String success = base + "?stripe_return=success&order=" + orderId;
        String cancel = base + "?stripe_return=cancel&order=" + orderId;
        String form = "mode=payment&payment_method_types%5B0%5D=card"
                + "&line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=eur"
                + "&line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=" + checkout.eurCents
                + "&line_items%5B0%5D%5Bprice_data%5D%5Bproduct_data%5D%5Bname%5D="
                + encode("Online order " + orderId)
                + "&line_items%5B0%5D%5Bquantity%5D=1"
                + "&client_reference_id=" + checkout.id
                + "&expires_at=" + Instant.now().plus(Duration.ofMinutes(31)).getEpochSecond()
                + "&success_url=" + encode(success) + "&cancel_url=" + encode(cancel);
        JsonNode session = stripeRequest("POST", "/v1/checkout/sessions", form, checkout.id.toString());
        String id = session.path("id").asText();
        String url = session.path("url").asText();
        if (!id.startsWith("cs_test_") || !url.startsWith("https://checkout.stripe.com/")) {
            throw new IllegalStateException("Stripe returned an invalid test Checkout session");
        }
        checkout.checkoutSessionId = id;
        checkout.checkoutUrl = url;
        checkout.status = "PENDING";
        checkouts.save(checkout);
        return new CheckoutResult(url, quote(checkout));
    }

    @Transactional
    String reconcile(String username, UUID orderId) {
        orders.getOrder(username, orderId);
        StorefrontStripeCheckout checkout = checkouts.lockByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Stripe Checkout not found"));
        if ("PAID".equals(checkout.status) || checkout.checkoutSessionId == null) return checkout.status;
        JsonNode session = stripeRequest("GET", "/v1/checkout/sessions/" + checkout.checkoutSessionId, null, null);
        if ("expired".equals(session.path("status").asText())) {
            checkout.status = "EXPIRED";
            checkouts.save(checkout);
            return checkout.status;
        }
        completeIfPaid(checkout, new StripeCheckoutVerified(session.path("id").asText(),
                session.path("client_reference_id").asText(), session.path("currency").asText(),
                session.path("amount_total").asLong(-1), session.path("payment_status").asText(),
                session.path("livemode").asBoolean(true)));
        return checkout.status;
    }

    @Transactional
    void completeFromWebhook(StripeCheckoutVerified event) {
        var checkout = checkouts.lockBySessionId(event.sessionId());
        if (checkout.isPresent()) {
            completeIfPaid(checkout.get(), event);
        } else {
            try {
                UUID attemptId = UUID.fromString(event.clientReferenceId());
                if (checkouts.existsById(attemptId)) {
                    throw new IllegalStateException("Stripe order session is still being saved; retry webhook");
                }
            } catch (IllegalArgumentException ignored) {
                // A Checkout from another integration has no matching online order.
            }
        }
    }

    @Transactional
    void webhook(String payload, String signature) {
        requireConfigured();
        verifySignature(payload, signature);
        JsonNode event = json.readTree(payload);
        if (!"checkout.session.completed".equals(event.path("type").asText())) return;
        JsonNode session = event.path("data").path("object");
        completeFromWebhook(new StripeCheckoutVerified(session.path("id").asText(),
                session.path("client_reference_id").asText(), session.path("currency").asText(),
                session.path("amount_total").asLong(-1), session.path("payment_status").asText(),
                session.path("livemode").asBoolean(true)));
    }

    private void completeIfPaid(StorefrontStripeCheckout checkout, StripeCheckoutVerified event) {
        if ("PAID".equals(checkout.status) || !"paid".equals(event.paymentStatus())) return;
        if (!checkout.checkoutSessionId.equals(event.sessionId())
                || !checkout.id.toString().equals(event.clientReferenceId())
                || !"eur".equals(event.currency()) || event.amountTotal() != checkout.eurCents
                || event.liveMode()) {
            throw new IllegalStateException("Stripe Checkout does not match the saved test-mode order quote");
        }
        OnlineOrder order = orders.getOrderForStaff(checkout.orderId);
        if (order.getTotalAmount().amount().compareTo(checkout.tndAmount) != 0) {
            throw new IllegalStateException("Online order total changed after payment; manual refund required");
        }
        orders.confirmStripePayment(checkout.orderId, "STRIPE-" + checkout.checkoutSessionId);
        checkout.status = "PAID";
        checkouts.save(checkout);
    }

    private JsonNode stripeRequest(String method, String path, String form, String idempotencyKey) {
        requireConfigured();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.stripe.com" + path))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + secretKey);
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if ("POST".equals(method)) builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
            else builder.GET();
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Stripe Checkout request failed (HTTP " + response.statusCode() + ")");
            }
            return json.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stripe Checkout request interrupted", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Stripe Checkout is unavailable", ex);
        }
    }

    private void requireConfigured() {
        if (!configured()) throw new IllegalStateException("Storefront Stripe test mode is not configured");
    }

    private void verifySignature(String payload, String header) {
        if (header == null) throw new IllegalArgumentException("Missing Stripe signature");
        String timestamp = null;
        String expected = null;
        for (String part : header.split(",")) {
            if (part.startsWith("t=")) timestamp = part.substring(2);
            if (part.startsWith("v1=")) expected = part.substring(3);
        }
        if (timestamp == null || expected == null
                || Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) > 300) {
            throw new IllegalArgumentException("Invalid Stripe signature timestamp");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] actual = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(actual, HexFormat.of().parseHex(expected))) {
                throw new IllegalArgumentException("Invalid Stripe signature");
            }
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot verify Stripe signature", ex);
        }
    }

    private static Quote quote(StorefrontStripeCheckout checkout) {
        return new Quote("EUR", checkout.tndAmount, checkout.eurCents, checkout.rate);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
