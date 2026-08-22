package com.example.erp.pos.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** unitType: "EACH" ou "WEIGHT" — correspond à Quantity.UnitType. */
public record AddLineRequest(UUID productId, BigDecimal quantityValue, String unitType) {
}