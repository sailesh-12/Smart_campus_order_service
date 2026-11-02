package com.smartcampus.order;

import com.smartcampus.order.dto.OrderItemResponse;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.payment.PaymentService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Entity to DTO mapping (controllers never return entities). Never includes the pickup code or QR secret;
 * those are only served by GET /orders/{id}/pickup to the owning student.
 */
@Component
class OrderMapper {

    private final PaymentService paymentService;

    OrderMapper(@Lazy PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    OrderResponse toResponse(Order order, Long paymentId) {
        List<OrderItemResponse> items = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getMenuItemId))
                .map(i -> new OrderItemResponse(i.getMenuItemId(), i.getItemName(), i.getQty(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStudentId(),
                order.getVendorId(),
                order.getStatus(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getHoldExpiresAt(),
                order.getEstMinutesLow(),
                order.getEstMinutesHigh(),
                order.getEstReadyAt(),
                order.getPickupSlotStart(),
                paymentId,
                items);
    }

    OrderResponse toResponse(Order order) {
        Long paymentId = paymentService.findByOrderId(order.getId()).map(p -> p.getId()).orElse(null);
        return toResponse(order, paymentId);
    }

    List<OrderResponse> toResponses(List<Order> orders) {
        Map<Long, Long> paymentIds = paymentService.paymentIdsByOrderIds(orders.stream().map(Order::getId).toList());
        return orders.stream().map(o -> toResponse(o, paymentIds.get(o.getId()))).toList();
    }
}
