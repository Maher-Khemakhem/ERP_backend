package com.example.erp.shared;

import java.util.UUID;

public final class TenantContext {

    public static final UUID DEFAULT_STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ThreadLocal<UUID> CURRENT_STORE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentStore(UUID storeId) {
        CURRENT_STORE.set(storeId);
    }

    public static UUID currentStore() {
        return CURRENT_STORE.get();
    }

    public static void clear() {
        CURRENT_STORE.remove();
    }
}
