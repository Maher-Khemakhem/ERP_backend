package com.example.erp.pos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PosModuleStructureTest {

    @Test
    void posDomainClassesMatchCurrentModuleStructure() {
        assertDoesNotThrow(() -> Class.forName("com.example.erp.pos.PosService"));
        assertDoesNotThrow(() -> Class.forName("com.example.erp.pos.Till"));
        assertDoesNotThrow(() -> Class.forName("com.example.erp.pos.CashSession"));
        assertDoesNotThrow(() -> Class.forName("com.example.erp.pos.PosTransaction"));
    }
}
