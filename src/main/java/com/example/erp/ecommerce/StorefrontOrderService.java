package com.example.erp.ecommerce;

import com.example.erp.auth.AuthService;
import com.example.erp.inventory.InventoryService;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StorefrontOrderService {

    private final AuthService authService;
    private final CartRepository cartRepository;
    private final OnlineOrderRepository orderRepository;
    private final StorefrontCatalogService catalogService;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher events;
    private final Duration paymentTimeout;

    StorefrontOrderService(AuthService authService,
                           CartRepository cartRepository,
                           OnlineOrderRepository orderRepository,
                           StorefrontCatalogService catalogService,
                           InventoryService inventoryService,
                           ApplicationEventPublisher events,
                           @Value("${app.ecommerce.payment-timeout-minutes:15}") long paymentTimeoutMinutes) {
        this.authService = authService;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.events = events;
        if (paymentTimeoutMinutes < 1) {
            throw new IllegalArgumentException("E-commerce payment timeout must be at least one minute");
        }
        this.paymentTimeout = Duration.ofMinutes(paymentTimeoutMinutes);
    }

    @Transactional
    public CartSnapshot getCart(String username) {
        UUID customerId = customerId(username);
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(Cart.forCustomer(customerId)));
        return cartSnapshot(cart);
    }

    @Transactional
    public CartSnapshot addItem(String username, UUID productId, Quantity quantity) {
        requirePositive(quantity);
        UUID customerId = customerId(username);
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> Cart.forCustomer(customerId));
        cart.addItem(productId, quantity);
        cartRepository.save(cart);
        return cartSnapshot(cart);
    }

    @Transactional
    public CartSnapshot changeItemQuantity(String username, UUID productId, Quantity quantity) {
        requirePositive(quantity);
        UUID customerId = customerId(username);
        Cart cart = requireCart(customerId);
        cart.replaceItem(productId, quantity);
        cartRepository.save(cart);
        return cartSnapshot(cart);
    }

    @Transactional
    public CartSnapshot removeItem(String username, UUID productId) {
        UUID customerId = customerId(username);
        Cart cart = requireCart(customerId);
        cart.removeItem(productId);
        cartRepository.save(cart);
        return cartSnapshot(cart);
    }

    @Transactional
    public OnlineOrder checkout(String username) {
        UUID customerId = customerId(username);
        Cart cart = requireCart(customerId);
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        List<StorefrontCatalogService.LineQuote> quotes = cart.getItems().stream()
                .map(item -> catalogService.quote(item.getProductId(), item.getQuantity())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Product is no longer available online: " + item.getProductId())))
                .toList();

        // Reservation and order creation occur in one transaction. A failed
        // reservation rolls back every earlier reservation and the order.
        quotes.forEach(quote -> inventoryService.reserve(quote.product().id(), quote.quantity()));

        Money net = quotes.stream()
                .map(StorefrontCatalogService.LineQuote::lineTotal)
                .reduce(Money.zero(), Money::add);
        Money vat = quotes.stream()
                .map(StorefrontCatalogService.LineQuote::vatAmount)
                .reduce(Money.zero(), Money::add);
        Money total = net.add(vat);

        OnlineOrder order = OnlineOrder.create(
                customerId,
                quotes.stream().map(OnlineOrderLine::from).toList(),
                net,
                vat,
                total,
                Instant.now().plus(paymentTimeout));
        OnlineOrder saved = orderRepository.save(order);
        cart.clear();
        cartRepository.save(cart);
        return saved;
    }

    @Transactional
    public OnlineOrder confirmPayment(String username, UUID orderId) {
        UUID customerId = customerId(username);
        OnlineOrder order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Online order not found: " + orderId));

        if (order.getStatus() == OnlineOrderStatus.CONFIRMED) {
            return order;
        }

        Instant now = Instant.now();
        if (order.expireIfDue(now)) {
            releaseReservedStock(order);
            return orderRepository.save(order);
        }
        if (order.getStatus() != OnlineOrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only unpaid pending orders can be paid");
        }

        Money costOfGoodsSold = Money.zero();
        for (OnlineOrderLine line : order.getLines()) {
            costOfGoodsSold = costOfGoodsSold.add(
                    inventoryService.sellReserved(line.getProductId(), line.getQuantity()));
        }

        String paymentReference = "MOCK-CARD-" + order.getId();
        order.confirmPayment(paymentReference);
        OnlineOrder saved = orderRepository.save(order);
        events.publishEvent(OnlineOrderConfirmed.of(saved, "CARD", costOfGoodsSold));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OnlineOrder> listOrders(String username) {
        return orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId(username));
    }

    @Transactional(readOnly = true)
    public OnlineOrder getOrder(String username, UUID orderId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId(username))
                .orElseThrow(() -> new IllegalArgumentException("Online order not found: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<OnlineOrder> listAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public OnlineOrder getOrderForStaff(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Online order not found: " + orderId));
    }

    @Transactional
    public OnlineOrder cancelPendingOrder(String username, UUID orderId) {
        UUID customerId = customerId(username);
        OnlineOrder order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Online order not found: " + orderId));
        return cancelPendingOrder(order);
    }

    @Transactional
    public OnlineOrder cancelPendingOrderForStaff(UUID orderId) {
        OnlineOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Online order not found: " + orderId));
        return cancelPendingOrder(order);
    }

    @Transactional(readOnly = true)
    public List<UUID> findDueOrderIds(Instant deadline) {
        return orderRepository.findDueOrderIds(OnlineOrderStatus.PENDING_PAYMENT, deadline);
    }

    @Transactional
    public boolean expirePendingOrder(UUID orderId) {
        OnlineOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.expireIfDue(Instant.now())) {
            return false;
        }
        releaseReservedStock(order);
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public OnlineOrder advanceFulfillment(UUID orderId, OnlineOrderStatus nextStatus) {
        OnlineOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Online order not found: " + orderId));
        order.advanceFulfillment(nextStatus);
        return orderRepository.save(order);
    }

    private CartSnapshot cartSnapshot(Cart cart) {
        List<CartItemSnapshot> items = cart.getItems().stream().map(item -> {
            StorefrontCatalogService.LineQuote quote = catalogService.quote(item.getProductId(), item.getQuantity())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cart item is no longer available online: " + item.getProductId()));
            return new CartItemSnapshot(
                    quote.product().id(),
                    quote.product().name(),
                    quote.quantity(),
                    quote.unitPrice(),
                    quote.lineTotal(),
                    quote.product().availableQuantity(),
                    quote.product().inStock());
        }).toList();
        Money subtotal = items.stream()
                .map(CartItemSnapshot::lineTotal)
                .reduce(Money.zero(), Money::add);
        return new CartSnapshot(cart.getCustomerId(), items, subtotal);
    }

    private UUID customerId(String username) {
        return authService.customerIdForUsername(username);
    }

    private OnlineOrder cancelPendingOrder(OnlineOrder order) {
        Instant now = Instant.now();
        if (order.expireIfDue(now)) {
            releaseReservedStock(order);
        } else if (order.cancelPendingPayment(now)) {
            releaseReservedStock(order);
        }
        return orderRepository.save(order);
    }

    private void releaseReservedStock(OnlineOrder order) {
        order.getLines().forEach(line ->
                inventoryService.releaseReservation(line.getProductId(), line.getQuantity()));
    }

    private Cart requireCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("Cart not found"));
    }

    private static void requirePositive(Quantity quantity) {
        if (quantity == null || quantity.isZero()) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    public record CartSnapshot(UUID customerId, List<CartItemSnapshot> items, Money subtotal) {
    }

    public record CartItemSnapshot(UUID productId, String productName, Quantity quantity,
                                   Money unitPrice, Money lineTotal,
                                   BigDecimal availableQuantity, boolean inStock) {
    }
}
