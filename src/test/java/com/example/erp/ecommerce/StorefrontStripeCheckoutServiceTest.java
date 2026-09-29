package com.example.erp.ecommerce;

import com.example.erp.shared.Money;
import com.example.erp.ecommerce.StorefrontStripeCheckoutService.StripeCheckoutVerified;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StorefrontStripeCheckoutServiceTest {
    private final StorefrontOrderService orders = mock(StorefrontOrderService.class);
    private final StorefrontStripeCheckoutRepository checkouts = mock(StorefrontStripeCheckoutRepository.class);
    private final StorefrontStripeCheckoutService stripe = new StorefrontStripeCheckoutService(
            orders, checkouts, new ObjectMapper(), "sk_test_example", "whsec_example",
            "http://localhost:5174", new BigDecimal("0.30"));

    @Test
    void quotesTndOrderInRoundedEurCents() {
        UUID orderId = UUID.randomUUID();
        OnlineOrder order = mock(OnlineOrder.class);
        when(order.getStatus()).thenReturn(OnlineOrderStatus.PENDING_PAYMENT);
        when(order.getExpiresAt()).thenReturn(Instant.now().plusSeconds(900));
        when(order.getTotalAmount()).thenReturn(Money.of(new BigDecimal("12.345")));
        when(orders.getOrder("customer", orderId)).thenReturn(order);

        var quote = stripe.quote("customer", orderId);

        assertThat(quote.currency()).isEqualTo("EUR");
        assertThat(quote.amountCents()).isEqualTo(370);
        assertThat(quote.tndAmount()).isEqualByComparingTo("12.345");
    }

    @Test
    void paidWebhookConfirmsOrderOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        StorefrontStripeCheckout checkout = new StorefrontStripeCheckout(orderId,
                new BigDecimal("10.000"), new BigDecimal("0.30"), 300);
        checkout.checkoutSessionId = "cs_test_store";
        checkout.status = "PENDING";
        OnlineOrder order = mock(OnlineOrder.class);
        when(order.getTotalAmount()).thenReturn(Money.of(10));
        when(orders.getOrderForStaff(orderId)).thenReturn(order);
        when(checkouts.lockBySessionId(checkout.checkoutSessionId)).thenReturn(Optional.of(checkout));
        StripeCheckoutVerified event = new StripeCheckoutVerified(checkout.checkoutSessionId,
                checkout.id.toString(), "eur", 300, "paid", false);

        stripe.completeFromWebhook(event);
        stripe.completeFromWebhook(event);

        assertThat(checkout.status).isEqualTo("PAID");
        verify(orders, times(1)).confirmStripePayment(orderId, "STRIPE-" + checkout.checkoutSessionId);
    }

    @Test
    void mismatchedAmountCannotConfirmOrder() {
        StorefrontStripeCheckout checkout = new StorefrontStripeCheckout(UUID.randomUUID(),
                new BigDecimal("10.000"), new BigDecimal("0.30"), 300);
        checkout.checkoutSessionId = "cs_test_store";
        checkout.status = "PENDING";
        when(checkouts.lockBySessionId(checkout.checkoutSessionId)).thenReturn(Optional.of(checkout));

        assertThatThrownBy(() -> stripe.completeFromWebhook(new StripeCheckoutVerified(
                checkout.checkoutSessionId, checkout.id.toString(), "eur", 301, "paid", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
        verify(orders, never()).confirmStripePayment(any(), any());
    }

    @Test
    void signedWebhookConfirmsStorefrontOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        StorefrontStripeCheckout checkout = new StorefrontStripeCheckout(orderId,
                new BigDecimal("10.000"), new BigDecimal("0.30"), 300);
        checkout.checkoutSessionId = "cs_test_store";
        checkout.status = "PENDING";
        OnlineOrder order = mock(OnlineOrder.class);
        when(order.getTotalAmount()).thenReturn(Money.of(10));
        when(orders.getOrderForStaff(orderId)).thenReturn(order);
        when(checkouts.lockBySessionId(checkout.checkoutSessionId)).thenReturn(Optional.of(checkout));

        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"id\":\"cs_test_store\",\"client_reference_id\":\"" + checkout.id + "\","
                + "\"currency\":\"eur\",\"amount_total\":300,\"payment_status\":\"paid\",\"livemode\":false}}}";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_example".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));

        stripe.webhook(payload, signature);

        assertThat(checkout.status).isEqualTo("PAID");
        verify(orders).confirmStripePayment(orderId, "STRIPE-cs_test_store");
    }

    @Test
    void unsignedWebhookCannotConfirmOrder() {
        assertThatThrownBy(() -> stripe.webhook("{}", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
        verify(orders, never()).confirmStripePayment(any(), any());
    }
}
