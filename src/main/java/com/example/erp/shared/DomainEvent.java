package com.example.erp.shared;


import java.time.Instant;
import java.util.UUID;

/**
 * Base contract every domain/integration event implements.
 *
 * Every event published across module boundaries (StockSold,
 * TransactionCompleted, GoodsReceived, etc.) carries this shared
 * metadata so that:
 *   - Spring Modulith's event publication registry (our outbox) can
 *     track and retry delivery per event, keyed by eventId
 *   - consumers can log/trace when something actually happened,
 *     independent of when it was processed
 *   - idempotency checks are possible (a listener can record which
 *     eventIds it has already handled and skip duplicates safely,
 *     since at-least-once delivery is the norm for this kind of bus)
 *
 * Implemented as an interface with default methods rather than an
 * abstract class, since our events are Java records (immutable,
 * can't extend a class, but CAN implement an interface).
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    /**
     * Human-readable event name for logging/tracing, e.g. "StockSold".
     * Defaults to the simple class name so most events don't need to
     * override this explicitly.
     */
    default String eventName() {
        return this.getClass().getSimpleName();
    }
}
