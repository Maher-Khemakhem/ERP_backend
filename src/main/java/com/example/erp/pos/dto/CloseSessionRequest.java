package com.example.erp.pos.dto;

import java.math.BigDecimal;

public record CloseSessionRequest(BigDecimal countedCash) {
}