package com.example.erp.inventory;

import com.example.erp.auth.AuthService;
import com.example.erp.ecommerce.OnlineOrder;
import com.example.erp.ecommerce.OnlineOrderStatus;
import com.example.erp.ecommerce.StorefrontOrderService;
import com.example.erp.pos.PosService;
import com.example.erp.pos.PosTransaction;
import com.example.erp.pos.PosTransactionRepository;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.flyway.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.modulith.events.jdbc.schema=public",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
// Match the event payload column widened by production migration V9.
@Sql(statements = "ALTER TABLE public.event_publication ALTER COLUMN serialized_event TYPE TEXT")
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

    @MockitoSpyBean
    private StockLevelRepository stockLevelRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AuthService authService;

    @Autowired
    private StorefrontOrderService storefrontOrderService;

    @Test
    void cancellingUnpaidOnlineOrder_releasesItsStockReservation() throws Exception {
        lifecycle("prepare checkout with one available unit");
        StoreFixture fixture = prepareStoreFixture("CANCEL");

        OnlineOrder order = storefrontOrderService.checkout(fixture.customerUsername());
        assertThat(order.getStatus()).isEqualTo(OnlineOrderStatus.PENDING_PAYMENT);
        assertStock(fixture.product().getId(), "1", "1", "0");
        lifecycle("checkout reserved the unit: on-hand=1, reserved=1, available=0");

        OnlineOrder cancelled = storefrontOrderService.cancelPendingOrder(
                fixture.customerUsername(), order.getId());

        assertThat(cancelled.getStatus()).isEqualTo(OnlineOrderStatus.CANCELLED);
        assertStock(fixture.product().getId(), "1", "0", "1");
        lifecycle("cancellation released the reservation: on-hand=1, reserved=0, available=1");
    }

    @Test
    void confirmingOnlinePayment_consumesPreviouslyReservedStock() throws Exception {
        lifecycle("prepare checkout with one available unit");
        StoreFixture fixture = prepareStoreFixture("PAY");

        OnlineOrder order = storefrontOrderService.checkout(fixture.customerUsername());
        assertStock(fixture.product().getId(), "1", "1", "0");
        lifecycle("checkout reserved the unit: on-hand=1, reserved=1, available=0");

        OnlineOrder confirmed = storefrontOrderService.confirmPayment(
                fixture.customerUsername(), order.getId());

        assertThat(confirmed.getStatus()).isEqualTo(OnlineOrderStatus.CONFIRMED);
        assertThat(confirmed.getPaymentReference()).startsWith("MOCK-CARD-");
        assertStock(fixture.product().getId(), "0", "0", "0");
        lifecycle("payment confirmation consumed the reserved unit: on-hand=0, reserved=0, available=0");
    }

    @Test
    void concurrentCompletionRequests_sellTheLastUnitExactlyOnce() throws Exception {
        flow("POS vs POS: preparing one unit of stock and two sale transactions");
        Product product = productRepository.saveAndFlush(
                Product.create("CONC-" + UUID.randomUUID(), "Concurrent test product",
                        Quantity.UnitType.EACH, false, 0));
        inventoryService.receiveStock(product.getId(), Quantity.each(1), "CONC-LOT",
                null, LocalDate.now(), "integration-test", Money.zero());

        UUID tillId = posService.createTill("CONC-TILL-" + UUID.randomUUID());
        UUID sessionId = posService.openSession(tillId, "Concurrency test", Money.zero());
        UUID firstTransactionId = createTransactionWithLine(sessionId, product.getId());
        UUID secondTransactionId = createTransactionWithLine(sessionId, product.getId());
        synchronizeStockReads(product.getId());

        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            flow("POS vs POS: launching both completion requests together");
            List<Future<Integer>> results = List.of(
                    submitCompletion(executor, ready, firstTransactionId),
                    submitCompletion(executor, ready, secondTransactionId));

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(getStatus(result));
            }
            flow("POS vs POS: HTTP results " + statuses + " (expect one 200 and one 409)");

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);

            double remainingStock = inventoryService.totalOnHandQuantity(product.getId());
            assertThat(remainingStock).isGreaterThanOrEqualTo(0.0);
            assertThat(remainingStock).isEqualTo(0.0);
            long completedTransactions = List.of(firstTransactionId, secondTransactionId).stream()
                    .map(id -> transactionRepository.findById(id).orElseThrow().getStatus())
                    .filter(status -> status == PosTransaction.TransactionStatus.COMPLETED)
                    .count();
            assertThat(completedTransactions).isEqualTo(1);
            flow("POS vs POS: verified exactly one completed sale; available stock is 0");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentPosSaleAndOnlineCheckout_cannotBothClaimTheLastUnit() throws Exception {
        flow("POS vs online checkout: preparing one unit, a POS sale, and a customer cart");
        String suffix = UUID.randomUUID().toString();
        Product product = productRepository.saveAndFlush(
                Product.create("MIXED-CONC-" + suffix, "Mixed-channel concurrency product",
                        Quantity.UnitType.EACH, false, 0));
        inventoryService.receiveStock(product.getId(), Quantity.each(1), "MIXED-LOT-" + suffix,
                null, LocalDate.now(), "integration-test", Money.zero());

        String customerUsername = "store-customer-" + suffix.substring(0, 8);
        authService.registerCustomer(customerUsername, "Concurrency customer", "test-password-123");

        mockMvc.perform(post("/api/pricing/prices")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.getId() + "\",\"unitPrice\":1.000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/store/cart/items")
                        .with(user(customerUsername).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.getId()
                                + "\",\"quantityValue\":1,\"unitType\":\"EACH\"}"))
                .andExpect(status().isOk());

        UUID tillId = posService.createTill("MIXED-TILL-" + suffix);
        UUID sessionId = posService.openSession(tillId, "Mixed-channel test", Money.zero());
        UUID posTransactionId = createTransactionWithLine(sessionId, product.getId());
        synchronizeStockReads(product.getId());

        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            flow("POS vs online checkout: launching both requests together");
            Future<Integer> posResult = submitCompletion(executor, ready, posTransactionId);
            Future<MvcResult> checkoutResult = submitOnlineCheckout(executor, ready, customerUsername);

            int posStatus = getStatus(posResult);
            MvcResult checkoutResponse = checkoutResult.get(30, TimeUnit.SECONDS);
            int checkoutStatus = checkoutResponse.getResponse().getStatus();
            flow("POS vs online checkout: POS returned " + posStatus
                    + ", checkout returned " + checkoutStatus);

            assertThat(posStatus).isIn(200, 409);
            assertThat(checkoutStatus).isIn(201, 409);
            assertThat(posStatus == 200).isNotEqualTo(checkoutStatus == 201);

            boolean onlineCheckoutWon = checkoutStatus == 201;
            if (onlineCheckoutWon) {
                jsonPath("$.status").value("PENDING_PAYMENT").match(checkoutResponse);
                assertThat(transactionRepository.findById(posTransactionId).orElseThrow().getStatus())
                        .isEqualTo(PosTransaction.TransactionStatus.IN_PROGRESS);
            } else {
                assertThat(transactionRepository.findById(posTransactionId).orElseThrow().getStatus())
                        .isEqualTo(PosTransaction.TransactionStatus.COMPLETED);
            }

            StockLevel stock = stockLevelRepository.findByProductId(product.getId()).orElseThrow();
            BigDecimal onHand = stock.getLots().stream()
                    .map(entry -> entry.onHand().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal reserved = stock.getLots().stream()
                    .map(entry -> entry.reserved().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal available = stock.getLots().stream()
                    .map(entry -> entry.available().value()).reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(onHand).isEqualByComparingTo(onlineCheckoutWon ? "1" : "0");
            assertThat(reserved).isEqualByComparingTo(onlineCheckoutWon ? "1" : "0");
            assertThat(available).isEqualByComparingTo("0");
            flow("POS vs online checkout: winner=" + (onlineCheckoutWon ? "online checkout" : "POS")
                    + "; on-hand=" + onHand + ", reserved=" + reserved + ", available=" + available);

            mockMvc.perform(get("/api/store/orders").with(user(customerUsername).roles("CUSTOMER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(onlineCheckoutWon ? 1 : 0));
            mockMvc.perform(get("/api/store/cart").with(user(customerUsername).roles("CUSTOMER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(onlineCheckoutWon ? 0 : 1));
            flow("POS vs online checkout: verified order/cart state matches the winning channel");
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

    private StoreFixture prepareStoreFixture(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString();
        Product product = productRepository.saveAndFlush(
                Product.create(prefix + "-" + suffix, prefix + " lifecycle product",
                        Quantity.UnitType.EACH, false, 0));
        inventoryService.receiveStock(product.getId(), Quantity.each(1), prefix + "-LOT-" + suffix,
                null, LocalDate.now(), "integration-test", Money.zero());

        String customerUsername = "store-" + prefix.toLowerCase() + "-" + suffix.substring(0, 8);
        authService.registerCustomer(customerUsername, "Lifecycle test customer", "test-password-123");
        mockMvc.perform(post("/api/pricing/prices")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.getId() + "\",\"unitPrice\":1.000}"))
                .andExpect(status().isOk());
        storefrontOrderService.addItem(customerUsername, product.getId(), Quantity.each(1));
        return new StoreFixture(product, customerUsername);
    }

    private void assertStock(UUID productId, String onHandExpected,
                             String reservedExpected, String availableExpected) {
        StockLevel stock = stockLevelRepository.findByProductId(productId).orElseThrow();
        BigDecimal onHand = stock.getLots().stream()
                .map(entry -> entry.onHand().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = stock.getLots().stream()
                .map(entry -> entry.reserved().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = stock.getLots().stream()
                .map(entry -> entry.available().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(onHand).isEqualByComparingTo(onHandExpected);
        assertThat(reserved).isEqualByComparingTo(reservedExpected);
        assertThat(available).isEqualByComparingTo(availableExpected);
    }

    private record StoreFixture(Product product, String customerUsername) {
    }

    private void synchronizeStockReads(UUID productId) {
        CountDownLatch stockRead = new CountDownLatch(2);
        doAnswer(invocation -> {
            // Read the row through the request's transaction-bound EntityManager;
            // Spring Data repository methods are abstract and cannot callRealMethod.
            StockLevel stock = entityManager.createQuery(
                            "select s from StockLevel s where s.productId = :productId", StockLevel.class)
                    .setParameter("productId", productId)
                    .getSingleResult();
            entityManager.lock(stock, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            stockRead.countDown();
            assertThat(stockRead.await(10, TimeUnit.SECONDS))
                    .as("Both transactions must read stock before either mutates it")
                    .isTrue();
            return java.util.Optional.of(stock);
        }).when(stockLevelRepository).findForMutationByProductId(productId);
    }

    private Future<Integer> submitCompletion(ExecutorService executor, CountDownLatch ready,
                                              UUID transactionId) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            return mockMvc.perform(post("/api/pos/transactions/{transactionId}/complete", transactionId)
                            .with(user("cashier").roles("CASHIER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paymentMethod\":\"CARD\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        });
    }

    private Future<MvcResult> submitOnlineCheckout(ExecutorService executor, CountDownLatch ready,
                                                    String customerUsername) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            return mockMvc.perform(post("/api/store/checkout")
                            .with(user(customerUsername).roles("CUSTOMER")))
                    .andReturn();
        });
    }

    private int getStatus(Future<Integer> result) throws InterruptedException, ExecutionException, TimeoutException {
        return result.get(30, TimeUnit.SECONDS);
    }

    private void flow(String message) {
        System.out.println("[STOCK-RACE] " + message);
    }

    private void lifecycle(String message) {
        System.out.println("[ECOMMERCE-LIFECYCLE] " + message);
    }
}
