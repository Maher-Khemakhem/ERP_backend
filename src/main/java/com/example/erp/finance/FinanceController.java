package com.example.erp.finance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
class FinanceController {

    private final LedgerEntryRepository ledgerEntryRepository;

    FinanceController(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public record LedgerEntryView(UUID id, String entryType, String amount, String description,
                                   UUID referenceId, String createdAt) {
        static LedgerEntryView from(LedgerEntry e) {
            return new LedgerEntryView(
                    e.getId(),
                    e.getType().name(),
                    e.getAmount().toString(),
                    e.getDescription(),
                    e.getReferenceId(),
                    e.getRecordedAt().toString());
        }
    }

    @GetMapping("/ledger")
    public ResponseEntity<List<LedgerEntryView>> listEntries() {
        List<LedgerEntryView> entries = ledgerEntryRepository.findAll().stream()
                .map(LedgerEntryView::from)
                .toList();
        return ResponseEntity.ok(entries);
    }
}
