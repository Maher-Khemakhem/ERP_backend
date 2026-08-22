package com.example.erp.pos.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenSessionRequest(UUID tillId, BigDecimal openingFloat) {
}