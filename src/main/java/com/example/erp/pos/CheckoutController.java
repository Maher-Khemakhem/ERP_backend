package com.example.erp.pos;

import com.example.erp.pos.dto.*;
import com.example.erp.pricing.PricingEngine;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * CheckoutController — la couche d'orchestration du checkout.
 *
 * C'est ICI, et uniquement ici, que Pricing et POS sont appelés dans
 * la même requête. Ni PosService ni PricingEngine ne se connaissent
 * l'un l'autre — ce contrôleur fait l'appel synchrone à Pricing PUIS
 * transmet le prix déjà calculé à PosService. C'est le rôle exact
 * qu'on avait prévu pour cette couche depuis le début.
 */
@RestController
@RequestMapping("/api/pos")
class CheckoutController {

    private final PosService posService;
    private final PricingEngine pricingEngine;

    CheckoutController(PosService posService, PricingEngine pricingEngine) {
        this.posService = posService;
        this.pricingEngine = pricingEngine;
    }

    @PostMapping("/sessions/open")
    public ResponseEntity<Map<String, UUID>> openSession(@RequestBody OpenSessionRequest request) {
        UUID sessionId = posService.openSession(request.tillId(), Money.of(request.openingFloat().doubleValue()));
        return ResponseEntity.ok(Map.of("cashSessionId", sessionId));
    }

    @PostMapping("/transactions/start")
    public ResponseEntity<Map<String, UUID>> startTransaction(@RequestBody StartTransactionRequest request) {
        UUID transactionId = posService.startTransaction(request.cashSessionId());
        return ResponseEntity.ok(Map.of("transactionId", transactionId));
    }

    /**
     * Scan d'un article : calcule le prix (appel synchrone à Pricing),
     * puis ajoute la ligne au ticket. C'est le point de jonction
     * central de tout le checkout.
     */
    @PostMapping("/transactions/{transactionId}/lines")
    public ResponseEntity<Map<String, String>> addLine(@PathVariable UUID transactionId,
                                                       @RequestBody AddLineRequest request) {
        Quantity.UnitType unitType = Quantity.UnitType.valueOf(request.unitType());
        Quantity quantity = unitType == Quantity.UnitType.EACH
                ? Quantity.each(request.quantityValue().intValueExact())
                : new Quantity(request.quantityValue(), unitType);

        Money price = pricingEngine.calculateLinePrice(request.productId(), quantity);
        posService.addLine(transactionId, request.productId(), quantity, price);

        return ResponseEntity.ok(Map.of("linePrice", price.toString()));
    }

    @PostMapping("/transactions/{transactionId}/complete")
    public ResponseEntity<Void> completeTransaction(@PathVariable UUID transactionId,
                                                    @RequestBody CompleteTransactionRequest request) {
        PosTransaction.PaymentMethod method = PosTransaction.PaymentMethod.valueOf(request.paymentMethod());
        posService.completeTransaction(transactionId, method);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sessions/{cashSessionId}/close")
    public ResponseEntity<Void> closeSession(@PathVariable UUID cashSessionId,
                                             @RequestBody CloseSessionRequest request) {
        posService.closeSession(cashSessionId, Money.of(request.countedCash().doubleValue()));
        return ResponseEntity.ok().build();
    }
}