package com.example.erp.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** POS payments use cash or the manually recorded card method. */
public record CompleteTransactionRequest(
        @NotBlank(message = "paymentMethod is required") String paymentMethod,
        @PositiveOrZero(message = "amountReceived cannot be negative") BigDecimal amountReceived) {
}
