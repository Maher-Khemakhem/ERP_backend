package com.example.erp.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** unitType: "EACH" ou "WEIGHT" — correspond à Quantity.UnitType. */
public record AddLineRequest(
        @NotNull(message = "productId is required") UUID productId,
        @NotNull(message = "quantityValue is required")
        @Positive(message = "quantityValue must be greater than zero") BigDecimal quantityValue,
        @NotBlank(message = "unitType is required") String unitType) {
}
