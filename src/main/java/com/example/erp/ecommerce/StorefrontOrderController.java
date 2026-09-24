package com.example.erp.ecommerce;

import com.example.erp.shared.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/store")
public class StorefrontOrderController {

    private final StorefrontOrderService orderService;

    StorefrontOrderController(StorefrontOrderService orderService) {
        this.orderService = orderService;
    }

    public record QuantityRequest(
            @NotNull UUID productId,
            @NotNull @Positive BigDecimal quantityValue,
            @NotNull String unitType) {
    }

    public record CartItemView(UUID productId, String productName, String quantity,
                               String unitType, String unitPrice, String lineTotal,
                               BigDecimal availableQuantity, boolean inStock) {
        static CartItemView from(StorefrontOrderService.CartItemSnapshot item) {
            return new CartItemView(
                    item.productId(),
                    item.productName(),
                    item.quantity().toString(),
                    item.quantity().unitType().name(),
                    item.unitPrice().toString(),
                    item.lineTotal().toString(),
                    item.availableQuantity(),
                    item.inStock());
        }
    }

    public record CartView(List<CartItemView> items, String subtotal) {
        static CartView from(StorefrontOrderService.CartSnapshot cart) {
            return new CartView(
                    cart.items().stream().map(CartItemView::from).toList(),
                    cart.subtotal().toString());
        }
    }

    public record OrderLineView(UUID productId, String productName, String quantity,
                                String unitType, String unitPrice, String lineTotal,
                                String vatAmount) {
        static OrderLineView from(OnlineOrderLine line) {
            return new OrderLineView(
                    line.getProductId(),
                    line.getProductName(),
                    line.getQuantity().toString(),
                    line.getQuantity().unitType().name(),
                    line.getUnitPrice().toString(),
                    line.getLineTotal().toString(),
                    line.getVatAmount().toString());
        }
    }

    public record OrderView(UUID id, String orderNumber, String status, String netAmount,
                            String vatAmount, String totalAmount, String paymentReference,
                            String createdAt, String confirmedAt, String expiresAt,
                            String cancelledAt, String expiredAt, List<OrderLineView> lines) {
        static OrderView from(OnlineOrder order) {
            return new OrderView(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getStatus().name(),
                    order.getNetAmount().toString(),
                    order.getVatAmount().toString(),
                    order.getTotalAmount().toString(),
                    order.getPaymentReference(),
                    order.getCreatedAt().toString(),
                    order.getConfirmedAt() == null ? null : order.getConfirmedAt().toString(),
                    order.getExpiresAt().toString(),
                    order.getCancelledAt() == null ? null : order.getCancelledAt().toString(),
                    order.getExpiredAt() == null ? null : order.getExpiredAt().toString(),
                    order.getLines().stream().map(OrderLineView::from).toList());
        }
    }

    @GetMapping("/cart")
    public CartView cart(Authentication authentication) {
        return CartView.from(orderService.getCart(authentication.getName()));
    }

    @PostMapping("/cart/items")
    public CartView addItem(Authentication authentication,
                            @Valid @RequestBody QuantityRequest request) {
        return CartView.from(orderService.addItem(
                authentication.getName(), request.productId(), parseQuantity(request.quantityValue(), request.unitType())));
    }

    @PutMapping("/cart/items/{productId}")
    public CartView changeItem(Authentication authentication,
                               @PathVariable UUID productId,
                               @Valid @RequestBody QuantityRequest request) {
        if (!productId.equals(request.productId())) {
            throw new IllegalArgumentException("Product ID in path and body must match");
        }
        return CartView.from(orderService.changeItemQuantity(
                authentication.getName(), productId, parseQuantity(request.quantityValue(), request.unitType())));
    }

    @DeleteMapping("/cart/items/{productId}")
    public CartView removeItem(Authentication authentication, @PathVariable UUID productId) {
        return CartView.from(orderService.removeItem(authentication.getName(), productId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderView> checkout(Authentication authentication) {
        return ResponseEntity.status(201)
                .body(OrderView.from(orderService.checkout(authentication.getName())));
    }

    @PostMapping("/checkout/{orderId}/confirm-payment")
    public OrderView confirmPayment(Authentication authentication, @PathVariable UUID orderId) {
        return OrderView.from(orderService.confirmPayment(authentication.getName(), orderId));
    }

    @GetMapping("/orders")
    public List<OrderView> orders(Authentication authentication) {
        return orderService.listOrders(authentication.getName()).stream().map(OrderView::from).toList();
    }

    @GetMapping("/orders/{orderId}")
    public OrderView order(Authentication authentication, @PathVariable UUID orderId) {
        return OrderView.from(orderService.getOrder(authentication.getName(), orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderView cancelOrder(Authentication authentication, @PathVariable UUID orderId) {
        return OrderView.from(orderService.cancelPendingOrder(authentication.getName(), orderId));
    }

    private static Quantity parseQuantity(BigDecimal value, String unitType) {
        Quantity.UnitType type;
        try {
            type = Quantity.UnitType.valueOf(unitType.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unknown quantity unit type: " + unitType);
        }
        if (type == Quantity.UnitType.EACH) {
            try {
                return Quantity.each(value.intValueExact());
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("EACH quantity must be a whole number", ex);
            }
        }
        return new Quantity(value, type);
    }
}
