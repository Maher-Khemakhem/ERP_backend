package com.example.erp.pos.dto;

/** paymentMethod: "CASH" ou "CARD" — correspond à PosTransaction.PaymentMethod. */
public record CompleteTransactionRequest(String paymentMethod) {
}