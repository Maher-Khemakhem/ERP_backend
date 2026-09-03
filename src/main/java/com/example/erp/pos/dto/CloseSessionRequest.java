package com.example.erp.pos.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CloseSessionRequest(@NotNull @PositiveOrZero BigDecimal countedCash) {
}
