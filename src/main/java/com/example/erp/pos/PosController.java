package com.example.erp.pos;

import com.example.erp.pos.dto.*;
import com.example.erp.pricing.PricingEngine;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos")
public class PosController {

    private final PosService posService;
    private final PricingEngine pricingEngine;
    private final ScannerSessionService scannerSessionService;

    PosController(PosService posService, PricingEngine pricingEngine,
                  ScannerSessionService scannerSessionService) {
        this.posService = posService;
        this.pricingEngine = pricingEngine;
        this.scannerSessionService = scannerSessionService;
    }

    public record TillView(UUID id, String tillNumber, String status, String cashierName,
                          String cashInDrawer, String openedAt, String closedAt) {
        static TillView from(Till till, CashSession session) {
            Money cashInDrawer = session == null ? Money.zero() :
                    session.getStatus() == CashSession.SessionStatus.OPEN
                            ? session.getExpectedCash()
                            : session.getCountedCash() == null ? Money.zero() : session.getCountedCash();

            return new TillView(
                    till.getId(),
                    till.getTillNumber(),
                    till.getStatus().name(),
                    session == null ? null : session.getCashierName(),
                    cashInDrawer.toString(),
                    session == null || session.getOpenedAt() == null ? null : session.getOpenedAt().toString(),
                    session == null || session.getClosedAt() == null ? null : session.getClosedAt().toString()
            );
        }
    }

    public record CreateTillRequest(String tillNumber) {
    }

    public record ScannerPairingView(UUID sessionId, String token, String expiresAt) {
        static ScannerPairingView from(ScannerSessionService.Pairing pairing) {
            return new ScannerPairingView(pairing.sessionId(), pairing.token(), pairing.expiresAt().toString());
        }
    }

    public record ScannerScanRequest(@NotBlank(message = "barcode is required") String barcode) {
    }

    public record SessionView(UUID id, UUID tillId, String status, String expectedCash) {
        static SessionView from(CashSession s) {
            return new SessionView(s.getId(), s.getTillId(), s.getStatus().name(), s.getExpectedCash().toString());
        }
    }

    public record LineView(UUID id, UUID productId, String quantity, String lineTotal,
                           String netTotal, String vatAmount) {
        static LineView from(TransactionLine line) {
            return new LineView(line.getId(), line.getProductId(), line.getQuantity().toString(),
                    line.getLineTotal().toString(), line.getNetTotal().toString(), line.getVatAmount().toString());
        }
    }

    public record TransactionView(UUID id, UUID cashSessionId, String status, String total,
                                  String netTotal, String vatTotal,
                                  String paymentMethod, String startedAt, String completedAt,
                                  List<LineView> lines) {
        static TransactionView from(PosTransaction t) {
            return new TransactionView(t.getId(), t.getCashSessionId(), t.getStatus().name(), t.getTotal().toString(),
                    t.getNetTotal().toString(), t.getVatTotal().toString(),
                    t.getPaymentMethod() == null ? null : t.getPaymentMethod().name(),
                    t.getStartedAt().toString(), t.getCompletedAt() == null ? null : t.getCompletedAt().toString(),
                    t.getLines().stream().map(LineView::from).toList());
        }
    }

    @GetMapping("/tills")
    public ResponseEntity<List<TillView>> listTills() {
        return ResponseEntity.ok(
                posService.listTills().stream()
                        .map(till -> TillView.from(till, posService.findLatestSession(till.getId()).orElse(null)))
                        .toList()
        );
    }

    @GetMapping("/tills/{tillId}")
    public ResponseEntity<TillView> getTill(@PathVariable UUID tillId) {
        return posService.getTill(tillId)
                .map(till -> TillView.from(till, posService.findLatestSession(tillId).orElse(null)))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tills")
    public ResponseEntity<Map<String, UUID>> createTill(@RequestBody CreateTillRequest request) {
        UUID tillId = posService.createTill(request.tillNumber());
        return ResponseEntity.ok(Map.of("id", tillId));
    }

    @PostMapping("/sessions/open")
    public ResponseEntity<Map<String, UUID>> openSession(@Valid @RequestBody OpenSessionRequest request) {
        UUID sessionId = posService.openSession(request.tillId(), request.cashierName(), Money.of(request.openingFloat().doubleValue()));
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
                                             @Valid @RequestBody CloseSessionRequest request) {
        posService.closeSession(cashSessionId, Money.of(request.countedCash().doubleValue()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transactions/start")
    public ResponseEntity<Map<String, UUID>> startTransaction(@Valid @RequestBody StartTransactionRequest request) {
        UUID transactionId = posService.startTransaction(request.cashSessionId());
        return ResponseEntity.ok(Map.of("transactionId", transactionId));
    }

    @PostMapping("/transactions/{transactionId}/scanner-session")
    public ResponseEntity<ScannerPairingView> createScannerSession(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(ScannerPairingView.from(scannerSessionService.create(transactionId)));
    }

    @PostMapping("/scanner-sessions/{sessionId}/scans")
    public ResponseEntity<ScannerSessionService.ScanResult> scanFromPhone(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Scanner-Token", required = false) String token,
            @Valid @RequestBody ScannerScanRequest request) {
        return ResponseEntity.ok(scannerSessionService.scan(sessionId, token, request.barcode()));
    }

    @DeleteMapping("/scanner-sessions/{sessionId}")
    public ResponseEntity<Void> closeScannerSession(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Scanner-Token", required = false) String token) {
        scannerSessionService.close(sessionId, token);
        return ResponseEntity.noContent().build();
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
                                                       @Valid @RequestBody AddLineRequest request) {
        Quantity.UnitType unitType = Quantity.UnitType.valueOf(request.unitType());
        if (unitType == Quantity.UnitType.EACH
                && request.quantityValue().stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("EACH quantity must be a whole number");
        }
        Quantity quantity = unitType == Quantity.UnitType.EACH
                ? Quantity.each(request.quantityValue().intValueExact())
                : new Quantity(request.quantityValue(), unitType);

        Money price = pricingEngine.calculateLinePrice(request.productId(), quantity);
        UUID lineId = posService.addLine(transactionId, request.productId(), quantity, price);

        TransactionLine line = posService.getTransaction(transactionId).getLines().stream()
                .filter(candidate -> candidate.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created POS line could not be loaded"));
        return ResponseEntity.ok(Map.of(
                "linePrice", line.getLineTotal().toString(),
                "netPrice", line.getNetTotal().toString(),
                "vatAmount", line.getVatAmount().toString(),
                "lineId", lineId.toString()));
    }

    @PostMapping("/transactions/{transactionId}/complete")
    public ResponseEntity<Void> completeTransaction(@PathVariable UUID transactionId,
                                                    @Valid @RequestBody CompleteTransactionRequest request) {
        PosTransaction.PaymentMethod method = PosTransaction.PaymentMethod.valueOf(request.paymentMethod());
        posService.completeTransaction(transactionId, method,
                request.amountReceived() == null ? null : Money.of(request.amountReceived()));
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
