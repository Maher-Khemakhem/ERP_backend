package com.example.erp.pos;

import com.example.erp.pos.dto.*;
import com.example.erp.pricing.PricingEngine;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos")
class CheckoutController {

    private final PosService posService;
    private final PricingEngine pricingEngine;

    CheckoutController(PosService posService, PricingEngine pricingEngine) {
        this.posService = posService;
        this.pricingEngine = pricingEngine;
    }

    public record SessionView(UUID id, UUID tillId, String status, String expectedCash) {
        static SessionView from(CashSession s) {
            return new SessionView(s.getId(), s.getTillId(), s.getStatus().name(), s.getExpectedCash().toString());
        }
    }

    public record LineView(UUID id, UUID productId, String quantity, String lineTotal) {
        static LineView from(TransactionLine line) {
            return new LineView(line.getId(), line.getProductId(), line.getQuantity().toString(), line.getLineTotal().toString());
        }
    }

    public record TransactionView(UUID id, UUID cashSessionId, String status, String total,
                                  String paymentMethod, String startedAt, String completedAt,
                                  List<LineView> lines) {
        static TransactionView from(PosTransaction t) {
            return new TransactionView(t.getId(), t.getCashSessionId(), t.getStatus().name(), t.getTotal().toString(),
                    t.getPaymentMethod() == null ? null : t.getPaymentMethod().name(),
                    t.getStartedAt().toString(), t.getCompletedAt() == null ? null : t.getCompletedAt().toString(),
                    t.getLines().stream().map(LineView::from).toList());
        }
    }

    @PostMapping("/sessions/open")
    public ResponseEntity<Map<String, UUID>> openSession(@RequestBody OpenSessionRequest request) {
        UUID sessionId = posService.openSession(request.tillId(), Money.of(request.openingFloat().doubleValue()));
        return ResponseEntity.ok(Map.of("cashSessionId", sessionId));
    }

    @GetMapping("/sessions/{cashSessionId}")
    public ResponseEntity<SessionView> getSession(@PathVariable UUID cashSessionId) {
        return ResponseEntity.ok(SessionView.from(posService.getSession(cashSessionId)));
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<SessionView> getActiveSession(@RequestParam UUID tillId) {
        return posService.findOpenSession(tillId)
                .map(session -> ResponseEntity.ok(SessionView.from(session)))
                .orElseGet(() -> ResponseEntity.<SessionView>noContent().build());
    }

    @PostMapping("/sessions/{cashSessionId}/close")
    public ResponseEntity<Void> closeSession(@PathVariable UUID cashSessionId,
                                             @RequestBody CloseSessionRequest request) {
        posService.closeSession(cashSessionId, Money.of(request.countedCash().doubleValue()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transactions/start")
    public ResponseEntity<Map<String, UUID>> startTransaction(@RequestBody StartTransactionRequest request) {
        UUID transactionId = posService.startTransaction(request.cashSessionId());
        return ResponseEntity.ok(Map.of("transactionId", transactionId));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<TransactionView> getTransaction(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(TransactionView.from(posService.getTransaction(transactionId)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionView>> listTransactions() {
        return ResponseEntity.ok(posService.listTransactions().stream().map(TransactionView::from).toList());
    }

    @PostMapping("/transactions/{transactionId}/lines")
    public ResponseEntity<Map<String, String>> addLine(@PathVariable UUID transactionId,
                                                       @RequestBody AddLineRequest request) {
        Quantity.UnitType unitType = Quantity.UnitType.valueOf(request.unitType());
        Quantity quantity = unitType == Quantity.UnitType.EACH
                ? Quantity.each(request.quantityValue().intValueExact())
                : new Quantity(request.quantityValue(), unitType);

        Money price = pricingEngine.calculateLinePrice(request.productId(), quantity);
        UUID lineId = posService.addLine(transactionId, request.productId(), quantity, price);

        return ResponseEntity.ok(Map.of("linePrice", price.toString(), "lineId", lineId.toString()));
    }

    @PostMapping("/transactions/{transactionId}/complete")
    public ResponseEntity<Void> completeTransaction(@PathVariable UUID transactionId,
                                                    @RequestBody CompleteTransactionRequest request) {
        PosTransaction.PaymentMethod method = PosTransaction.PaymentMethod.valueOf(request.paymentMethod());
        posService.completeTransaction(transactionId, method);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transactions/{transactionId}/void")
    public ResponseEntity<Void> voidTransaction(@PathVariable UUID transactionId,
                                                @RequestBody VoidTransactionRequest request) {
        posService.voidTransaction(transactionId, request.reason());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/transactions/{transactionId}/lines/{lineId}")
    public ResponseEntity<Void> removeLine(@PathVariable UUID transactionId,
                                           @PathVariable UUID lineId) {
        posService.removeLine(transactionId, lineId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/transactions/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID transactionId) {
        posService.deleteTransaction(transactionId);
        return ResponseEntity.ok().build();
    }
}
