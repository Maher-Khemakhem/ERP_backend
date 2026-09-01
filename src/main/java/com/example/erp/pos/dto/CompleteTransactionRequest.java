package com.example.erp.pos.dto;

import jakarta.validation.constraints.NotBlank;

/** paymentMethod: "CASH" ou "CARD" — correspond à PosTransaction.PaymentMethod. */
public record CompleteTransactionRequest(
        @NotBlank(message = "paymentMethod is required") String paymentMethod) {
}
