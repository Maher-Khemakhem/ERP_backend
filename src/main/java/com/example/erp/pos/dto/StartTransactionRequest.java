package com.example.erp.pos.dto;

import java.util.UUID;

public record StartTransactionRequest(UUID cashSessionId) {
}