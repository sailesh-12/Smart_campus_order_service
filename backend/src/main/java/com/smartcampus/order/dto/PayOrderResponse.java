package com.smartcampus.order.dto;

import com.smartcampus.order.OrderStatus;
import com.smartcampus.payment.PaymentStatus;

public record PayOrderResponse(
        Long orderId,
        PaymentStatus paymentStatus,
        OrderStatus orderStatus,
        String message
) {
}
