package com.example.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface TillRepository extends JpaRepository<Till, UUID> {
    Optional<Till> findByTillNumber(String tillNumber);
}