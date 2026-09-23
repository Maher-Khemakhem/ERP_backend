package com.example.erp.inventory;

import com.example.erp.pos.PosService;
import com.example.erp.pos.PosTransaction;
import com.example.erp.pos.PosTransactionRepository;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "app.flyway.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.modulith.events.jdbc.schema=public"
})
@AutoConfigureMockMvc
@Testcontainers
class ConcurrentStockDeductionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("erp_test")
            .withUsername("erp_test")
            .withPassword("erp_test");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PosService posService;

    @Autowired
    private PosTransactionRepository transactionRepository;

    @Test
    void concurrentCompletionRequests_sellTheLastUnitExactlyOnce() throws Exception {
        Product product = productRepository.saveAndFlush(
                Product.create("CONC-" + UUID.randomUUID(), "Concurrent test product",
                        Quantity.UnitType.EACH, false, 0));
        inventoryService.receiveStock(product.getId(), Quantity.each(1), "CONC-LOT",
                null, LocalDate.now(), "integration-test", Money.zero());

        UUID tillId = posService.createTill("CONC-TILL-" + UUID.randomUUID());
        UUID sessionId = posService.openSession(tillId, "Concurrency test", Money.zero());
        UUID firstTransactionId = createTransactionWithLine(sessionId, product.getId());
        UUID secondTransactionId = createTransactionWithLine(sessionId, product.getId());

        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(
                    submitCompletion(executor, ready, firstTransactionId),
                    submitCompletion(executor, ready, secondTransactionId));

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(getStatus(result));
            }

            assertThat(statuses).contains(200);
            assertThat(statuses.stream().filter(status -> status == 400 || status == 409).count())
                    .isEqualTo(1);
            assertThat(statuses).doesNotContain(500);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);

            double remainingStock = inventoryService.totalOnHandQuantity(product.getId());
            assertThat(remainingStock).isGreaterThanOrEqualTo(0.0);
            assertThat(remainingStock).isEqualTo(0.0);
            long completedTransactions = List.of(firstTransactionId, secondTransactionId).stream()
                    .map(id -> transactionRepository.findById(id).orElseThrow().getStatus())
                    .filter(status -> status == PosTransaction.TransactionStatus.COMPLETED)
                    .count();
            assertThat(completedTransactions).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID createTransactionWithLine(UUID sessionId, UUID productId) {
        UUID transactionId = posService.startTransaction(sessionId);
        PosTransaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        transaction.addLine(productId, Quantity.each(1), Money.of(1.190), Money.of(0.190));
        transactionRepository.saveAndFlush(transaction);
        return transactionId;
    }

    private Future<Integer> submitCompletion(ExecutorService executor, CountDownLatch ready,
                                              UUID transactionId) {
        return executor.submit(() -> {
            ready.countDown();
            ready.await();
            return mockMvc.perform(post("/api/pos/transactions/{transactionId}/complete", transactionId)
                            .with(user("cashier").roles("CASHIER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paymentMethod\":\"CARD\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        });
    }

    private int getStatus(Future<Integer> result) throws InterruptedException, ExecutionException {
        return result.get();
    }
}
