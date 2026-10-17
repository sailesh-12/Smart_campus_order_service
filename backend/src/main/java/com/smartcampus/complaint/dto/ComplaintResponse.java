package com.smartcampus.complaint.dto;

import com.smartcampus.complaint.ComplaintStatus;

import java.time.Instant;

public record ComplaintResponse(
        Long id,
        Long orderId,
        Long userId,
        String text,
        ComplaintStatus status,
        String adminNote,
        Instant createdAt,
        Instant resolvedAt,
        Long refundPaymentId
) {
}
