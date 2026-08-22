package com.example.erp.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
}