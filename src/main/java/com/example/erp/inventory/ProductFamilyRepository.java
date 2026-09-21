package com.example.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProductFamilyRepository extends JpaRepository<ProductFamily, UUID> {
    boolean existsByNameIgnoreCase(String name);
}