package com.example.erp.pos;

import com.example.erp.inventory.InventoryService;
import com.example.erp.inventory.Product;
import com.example.erp.pricing.PricingEngine;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived pairing between an open POS ticket and a phone browser.
 *
 * The pairing token is deliberately kept out of the domain model: it is a
 * temporary LAN capability, not a POS record. Restarting the application
 * invalidates active phone pairings, while the transaction itself remains
 * persisted normally.
 */
@Service
public class ScannerSessionService {

    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);

    private final PosService posService;
    private final InventoryService inventoryService;
    private final PricingEngine pricingEngine;
    private final Map<UUID, ScannerSession> sessions = new ConcurrentHashMap<>();

    ScannerSessionService(PosService posService,
                          InventoryService inventoryService,
                          PricingEngine pricingEngine) {
        this.posService = posService;
        this.inventoryService = inventoryService;
        this.pricingEngine = pricingEngine;
    }

    public Pairing create(UUID transactionId) {
        PosTransaction transaction = posService.getTransaction(transactionId);
        requireInProgress(transaction);

        sessions.entrySet().removeIf(entry -> {
            ScannerSession existing = entry.getValue();
            return existing.expiresAt().isBefore(Instant.now())
                    || existing.transactionId().equals(transactionId);
        });
        ScannerSession session = new ScannerSession(
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                transactionId,
                Instant.now().plus(SESSION_LIFETIME));
        sessions.put(session.id(), session);
        return new Pairing(session.id(), session.token(), session.expiresAt());
    }

    public ScanResult scan(UUID sessionId, String token, String rawBarcode) {
        ScannerSession session = requireSession(sessionId, token);
        PosTransaction transaction = posService.getTransaction(session.transactionId());
        requireInProgress(transaction);

        String barcode = rawBarcode == null ? "" : rawBarcode.trim();
        Product product = inventoryService.findProductBySku(barcode)
                .orElseThrow(() -> new IllegalArgumentException("No product found for barcode: " + barcode));
        Quantity quantity = product.getUnitType() == Quantity.UnitType.WEIGHT
                ? Quantity.kg(0.1)
                : Quantity.each(1);
        Money price = pricingEngine.calculateLinePrice(product.getId(), quantity);
        UUID lineId = posService.addLine(transaction.getId(), product.getId(), quantity, price);

        TransactionLine line = posService.getTransaction(transaction.getId()).getLines().stream()
                .filter(candidate -> candidate.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created scanner line could not be loaded"));

        return new ScanResult(
                barcode,
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getUnitType().name(),
                quantity.toString(),
                line.getLineTotal().toString(),
                line.getNetTotal().toString(),
                line.getVatAmount().toString());
    }

    public void close(UUID sessionId, String token) {
        requireSession(sessionId, token);
        sessions.remove(sessionId);
    }

    private ScannerSession requireSession(UUID sessionId, String token) {
        ScannerSession session = sessions.get(sessionId);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId);
            throw new IllegalArgumentException("Scanner pairing has expired or does not exist");
        }
        if (token == null || !session.token().equals(token)) {
            throw new IllegalArgumentException("Invalid scanner pairing token");
        }
        return session;
    }

    private void requireInProgress(PosTransaction transaction) {
        if (transaction.getStatus() != PosTransaction.TransactionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress ticket can use the phone scanner");
        }
    }

    private record ScannerSession(UUID id, String token, UUID transactionId, Instant expiresAt) {
    }

    public record Pairing(UUID sessionId, String token, Instant expiresAt) {
    }

    public record ScanResult(String barcode, UUID productId, String sku, String productName,
                             String unitType, String quantity, String linePrice,
                             String netPrice, String vatAmount) {
    }
}
