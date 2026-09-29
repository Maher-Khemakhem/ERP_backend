package com.example.erp.ecommerce;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/stripe")
class StorefrontStripeCheckoutController {
    private final StorefrontStripeCheckoutService stripe;

    StorefrontStripeCheckoutController(StorefrontStripeCheckoutService stripe) {
        this.stripe = stripe;
    }

    @GetMapping("/config")
    Map<String, Boolean> config() {
        return Map.of("enabled", stripe.configured());
    }

    @GetMapping("/orders/{orderId}/quote")
    StorefrontStripeCheckoutService.Quote quote(Authentication authentication, @PathVariable UUID orderId) {
        return stripe.quote(authentication.getName(), orderId);
    }

    @PostMapping("/orders/{orderId}/checkout")
    StorefrontStripeCheckoutService.CheckoutResult checkout(Authentication authentication, @PathVariable UUID orderId) {
        return stripe.create(authentication.getName(), orderId);
    }

    @GetMapping("/orders/{orderId}/status")
    Map<String, String> status(Authentication authentication, @PathVariable UUID orderId) {
        return Map.of("status", stripe.reconcile(authentication.getName(), orderId));
    }

    @PostMapping("/webhook")
    ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        stripe.webhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
