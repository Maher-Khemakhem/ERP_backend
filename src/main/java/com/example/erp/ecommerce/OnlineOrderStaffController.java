package com.example.erp.ecommerce;

import com.example.erp.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Staff-facing order queue; storefront customers use the separate /api/store routes. */
@RestController
@RequestMapping("/api/online-orders")
public class OnlineOrderStaffController {

    private final StorefrontOrderService orderService;
    private final AuthService authService;

    OnlineOrderStaffController(StorefrontOrderService orderService, AuthService authService) {
        this.orderService = orderService;
        this.authService = authService;
    }

    public record StatusRequest(@NotNull OnlineOrderStatus status) {
    }

    public record PickupRequest(@NotBlank String code) {
    }

    public record StaffOrderView(UUID id, String orderNumber, UUID customerId,
                                 String customerUsername, String customerName,
                                 String status, String netAmount, String vatAmount,
                                 String totalAmount, String paymentReference,
                                 String createdAt, String confirmedAt, String expiresAt,
                                 String cancelledAt, String expiredAt, String pickedUpAt,
                                 String pickedUpBy,
                                 List<StorefrontOrderController.OrderLineView> lines) {
        static StaffOrderView from(OnlineOrder order, AuthService authService) {
            AuthService.UserSummary customer = authService.userSummary(order.getCustomerId());
            return new StaffOrderView(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getCustomerId(),
                    customer.username(),
                    customer.displayName(),
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
                    order.getPickedUpAt() == null ? null : order.getPickedUpAt().toString(),
                    order.getPickedUpBy(),
                    order.getLines().stream()
                            .map(StorefrontOrderController.OrderLineView::from)
                            .toList());
        }
    }

    @GetMapping
    public List<StaffOrderView> orders() {
        return orderService.listAllOrders().stream()
                .map(order -> StaffOrderView.from(order, authService))
                .toList();
    }

    @GetMapping("/{orderId}")
    public StaffOrderView order(@PathVariable UUID orderId) {
        return StaffOrderView.from(orderService.getOrderForStaff(orderId), authService);
    }

    @PatchMapping("/{orderId}/status")
    public StaffOrderView updateStatus(@PathVariable UUID orderId,
                                       @Valid @RequestBody StatusRequest request) {
        return StaffOrderView.from(orderService.advanceFulfillment(orderId, request.status()), authService);
    }

    @PostMapping("/{orderId}/pickup")
    public StaffOrderView completePickup(@PathVariable UUID orderId,
                                         @Valid @RequestBody PickupRequest request,
                                         Authentication authentication) {
        return StaffOrderView.from(orderService.completePickup(orderId, request.code(),
                authentication.getName()), authService);
    }

    @PostMapping("/{orderId}/cancel")
    public StaffOrderView cancelOrder(@PathVariable UUID orderId) {
        return StaffOrderView.from(orderService.cancelPendingOrderForStaff(orderId), authService);
    }
}
