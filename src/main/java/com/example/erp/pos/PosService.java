package com.example.erp.pos;

import com.example.erp.inventory.InventoryService;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PosService {

    private final TillRepository tillRepository;
    private final CashSessionRepository cashSessionRepository;
    private final PosTransactionRepository transactionRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher events;

    PosService(TillRepository tillRepository,
               CashSessionRepository cashSessionRepository,
               PosTransactionRepository transactionRepository,
               InventoryService inventoryService,
               ApplicationEventPublisher events) {
        this.tillRepository = tillRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.transactionRepository = transactionRepository;
        this.inventoryService = inventoryService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Till> listTills() {
        return tillRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Till> getTill(UUID tillId) {
        return tillRepository.findById(tillId);
    }

    @Transactional
    public UUID createTill(String tillNumber) {
        Till till = Till.create(tillNumber);
        tillRepository.save(till);
        return till.getId();
    }

    @Transactional
    public UUID openSession(UUID tillId, String cashierName, Money openingFloat) {
        Till till = tillRepository.findById(tillId)
                .orElseThrow(() -> new IllegalArgumentException("Till not found: " + tillId));

        till.markOpen();
        CashSession session = CashSession.open(tillId, cashierName, openingFloat);

        tillRepository.save(till);
        cashSessionRepository.save(session);

        return session.getId();
    }

    @Transactional
    public UUID startTransaction(UUID cashSessionId) {
        requireOpenSession(cashSessionId);
        PosTransaction transaction = PosTransaction.start(cashSessionId);
        transactionRepository.save(transaction);
        return transaction.getId();
    }

    @Transactional
    public UUID addLine(UUID transactionId, UUID productId, Quantity quantity, Money calculatedPrice) {
        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + transactionId));

        requireOpenSession(transaction.getCashSessionId());
        inventoryService.validateProductForSale(productId, quantity.unitType());

        // Compute per-product VAT from the catalog rate so that mixed-VAT
        // baskets are accounted correctly rather than using a single flat rate.
        BigDecimal vatRate = inventoryService.vatRateFor(productId);
        Money vatAmount = calculatedPrice.multiply(
                vatRate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        Money lineTotalWithVat = calculatedPrice.add(vatAmount);

        UUID lineId = transaction.addLine(productId, quantity, lineTotalWithVat, vatAmount);
        transactionRepository.save(transaction);
        return lineId;
    }

    @Transactional
    public void completeTransaction(UUID transactionId, PosTransaction.PaymentMethod paymentMethod) {
        completeTransaction(transactionId, paymentMethod, null);
    }

    @Transactional
    public void completeTransaction(UUID transactionId, PosTransaction.PaymentMethod paymentMethod, Money amountReceived) {
        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + transactionId));

        if (transaction.getStatus() != PosTransaction.TransactionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress tickets can be completed");
        }

        CashSession session = requireOpenSession(transaction.getCashSessionId());
        Money transactionTotal = transaction.getTotal();
        if (amountReceived != null && paymentMethod == PosTransaction.PaymentMethod.CASH
                && amountReceived.amount().compareTo(transactionTotal.amount()) < 0) {
            throw new IllegalArgumentException("Cash received is less than the transaction total");
        }
        if (amountReceived != null && paymentMethod == PosTransaction.PaymentMethod.CARD
                && amountReceived.amount().compareTo(transactionTotal.amount()) != 0) {
            throw new IllegalArgumentException("Card amount must equal the transaction total");
        }

        // Stock deduction and transaction completion share this transaction.
        // If any line cannot be sold, the whole sale is rolled back.
        Money totalCost = Money.zero();
        for (var line : transaction.getLines()) {
            totalCost = totalCost.add(inventoryService.sell(line.getProductId(), line.getQuantity()));
        }

        transaction.complete(paymentMethod);
        Money total = transactionTotal;

        if (paymentMethod == PosTransaction.PaymentMethod.CASH) {
            session.recordCashPayment(total);
            cashSessionRepository.save(session);
        }

        transactionRepository.save(transaction);
        events.publishEvent(TransactionCompleted.of(transaction.getId(), transaction.getCashSessionId(),
                paymentMethod.name(), transaction.getNetTotal(), transaction.getVatTotal(), total, totalCost));
    }

    @Transactional
    public void closeSession(UUID cashSessionId, Money actualCountedCash) {
        CashSession session = cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Cash session not found: " + cashSessionId));

        Money expectedCash = session.getExpectedCash();
        session.close(actualCountedCash);

        Till till = tillRepository.findById(session.getTillId())
                .orElseThrow(() -> new IllegalStateException("Till not found"));
        till.markClosed();

        cashSessionRepository.save(session);
        tillRepository.save(till);
        events.publishEvent(CashSessionClosed.of(cashSessionId, expectedCash, actualCountedCash));
    }

    @Transactional
    public void removeLine(UUID transactionId, UUID lineId) {
        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + transactionId));
        requireOpenSession(transaction.getCashSessionId());
        transaction.removeLine(lineId);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void voidTransaction(UUID transactionId, String reason) {
        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + transactionId));
        requireOpenSession(transaction.getCashSessionId());
        transaction.voidTransaction(reason);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId) {
        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + transactionId));

        if (transaction.getStatus() != PosTransaction.TransactionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress tickets can be deleted");
        }

        requireOpenSession(transaction.getCashSessionId());
        transactionRepository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public CashSession getSession(UUID cashSessionId) {
        return cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Cash session not found: " + cashSessionId));
    }

    @Transactional(readOnly = true)
    public Optional<CashSession> findOpenSession(UUID tillId) {
        return cashSessionRepository
                .findByTillIdAndStatusOrderByOpenedAtDesc(tillId, CashSession.SessionStatus.OPEN)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<CashSession> findLatestSession(UUID tillId) {
        return cashSessionRepository
                .findByTillIdOrderByOpenedAtDesc(tillId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public PosTransaction getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + transactionId));
    }

    @Transactional(readOnly = true)
    public List<PosTransaction> listTransactions() {
        return transactionRepository.findAllByOrderByStartedAtDesc();
    }

    private CashSession requireOpenSession(UUID cashSessionId) {
        CashSession session = cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Cash session not found: " + cashSessionId));
        if (session.getStatus() != CashSession.SessionStatus.OPEN) {
            throw new IllegalStateException("Cash session is not open: " + cashSessionId);
        }
        return session;
    }
}
