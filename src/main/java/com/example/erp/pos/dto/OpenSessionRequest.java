package com.example.erp.pos.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record OpenSessionRequest(
        @NotNull(message = "tillId est requis") UUID tillId,
        @NotNull @PositiveOrZero(message = "le fond de caisse ne peut pas être négatif") BigDecimal openingFloat) {
}