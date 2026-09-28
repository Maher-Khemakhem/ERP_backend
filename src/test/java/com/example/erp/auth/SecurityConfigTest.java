package com.example.erp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTest.TestEndpoints.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestEndpoints.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/inventory/products",
            "/api/inventory/products/123",
            "/api/inventory/stock",
            "/api/inventory/stock/123",
            "/api/pricing/prices",
            "/api/pricing/prices/123",
            "/api/pricing/promotions",
            "/api/pricing/promotions/123",
            "/api/pos/tills",
            "/api/pos/tills/123",
            "/api/pos/sessions/123",
            "/api/pos/transactions",
            "/api/pos/transactions/123",
            "/api/procurement/alerts",
            "/api/procurement/suppliers",
            "/api/procurement/suppliers/123"
    })
    void customerCannotReadStaffData(String path) throws Exception {
        mockMvc.perform(get(path).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "CASHIER", "INVENTORY_MANAGER", "ACCOUNTANT"})
    void staffCanStillReadSharedCatalog(String role) throws Exception {
        mockMvc.perform(get("/api/inventory/products").with(user("staff").roles(role)))
                .andExpect(status().isOk());
    }

    @Test
    void publicStorefrontCatalogRemainsAvailable() throws Exception {
        mockMvc.perform(get("/api/store/products"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCanStillReadTheirCart() throws Exception {
        mockMvc.perform(get("/api/store/cart").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({
                "/api/inventory/products", "/api/inventory/products/{id}",
                "/api/inventory/stock", "/api/inventory/stock/{id}",
                "/api/pricing/prices", "/api/pricing/prices/{id}",
                "/api/pricing/promotions", "/api/pricing/promotions/{id}",
                "/api/pos/tills", "/api/pos/tills/{id}", "/api/pos/sessions/{id}",
                "/api/pos/transactions", "/api/pos/transactions/{id}",
                "/api/procurement/alerts", "/api/procurement/suppliers",
                "/api/procurement/suppliers/{id}",
                "/api/store/products", "/api/store/cart"
        })
        ResponseEntity<Void> ok() {
            return ResponseEntity.ok().build();
        }
    }
}
