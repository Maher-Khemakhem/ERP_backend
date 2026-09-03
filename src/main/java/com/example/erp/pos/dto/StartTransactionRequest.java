package com.example.erp.pos.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record StartTransactionRequest(@NotNull UUID cashSessionId) {
}
