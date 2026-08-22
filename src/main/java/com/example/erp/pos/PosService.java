package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service applicatif du module POS — orchestre l'ouverture de caisse,
 * le scan, la finalisation d'un ticket, et la clôture de caisse.
 *
 * Même pattern que InventoryService : charger l'aggregate → appeler
 * son comportement → sauvegarder → publier les événements accumulés,
 * dans une seule transaction.
 *
 * NOTE : le calcul du prix (appel à PricingEngine) se fait AVANT
 * d'appeler addLine ici — c'est le rôle du contrôleur/couche présentation
 * d'orchestrer l'appel synchrone à Pricing, puis de transmettre le prix
 * déjà calculé à ce service. PosService reste focalisé sur le cycle de
 * vie du ticket, pas sur le calcul de prix.
 */
@Service
class PosService {

    private final TillRepository tillRepository;
    private final CashSessionRepository cashSessionRepository;
    private final PosTransactionRepository transactionRepository;
    private final ApplicationEventPublisher events;

    PosService(TillRepository tillRepository,
               CashSessionRepository cashSessionRepository,
               PosTransactionRepository transactionRepository,
               ApplicationEventPublisher events) {
        this.tillRepository = tillRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.transactionRepository = transactionRepository;
        this.events = events;
    }

    @Transactional
    public UUID openSession(UUID tillId, Money openingFloat) {
        Till till = tillRepository.findById(tillId)
                .orElseThrow(() -> new IllegalArgumentException("Caisse introuvable : " + tillId));

        till.markOpen();
        CashSession session = CashSession.open(tillId, openingFloat);

        tillRepository.save(till);
        cashSessionRepository.save(session);
        publishAndClear(session);

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
    public void addLine(UUID transactionId, UUID productId, Quantity quantity, Money calculatedPrice) {
        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket introuvable : " + transactionId));

        transaction.addLine(productId, quantity, calculatedPrice);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void completeTransaction(UUID transactionId, PosTransaction.PaymentMethod paymentMethod) {
        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket introuvable : " + transactionId));

        transaction.complete(paymentMethod);
        Money total = transaction.getTotal();

        if (paymentMethod == PosTransaction.PaymentMethod.CASH) {
            CashSession session = cashSessionRepository.findById(transaction.getCashSessionId())
                    .orElseThrow(() -> new IllegalStateException("Session de caisse introuvable"));
            session.recordCashPayment(total);
            cashSessionRepository.save(session);
            publishAndClear(session);
        }

        transactionRepository.save(transaction);
        publishAndClear(transaction);
    }

    @Transactional
    public void closeSession(UUID cashSessionId, Money actualCountedCash) {
        CashSession session = cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + cashSessionId));

        session.close(actualCountedCash);

        Till till = tillRepository.findById(session.getTillId())
                .orElseThrow(() -> new IllegalStateException("Caisse introuvable"));
        till.markClosed();

        cashSessionRepository.save(session);
        tillRepository.save(till);
        publishAndClear(session);
    }

    private void requireOpenSession(UUID cashSessionId) {
        CashSession session = cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + cashSessionId));
        if (session.getStatus() != CashSession.SessionStatus.OPEN) {
            throw new IllegalStateException("La session " + cashSessionId + " n'est pas ouverte");
        }
    }

    private void publishAndClear(CashSession session) {
        List<DomainEvent> pending = session.domainEvents();
        pending.forEach(events::publishEvent);
        session.clearDomainEvents();
    }

    private void publishAndClear(PosTransaction transaction) {
        List<DomainEvent> pending = transaction.domainEvents();
        pending.forEach(events::publishEvent);
        transaction.clearDomainEvents();
    }
}