package com.smartcampus.complaint;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.order.OrderService;
import com.smartcampus.order.OrderStatus;
import com.smartcampus.payment.ManualRefundPolicy;
import com.smartcampus.payment.RefundReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rule 5.12 checks for POST /admin/payments/{id}/refund that need the order and complaint modules.
 */
@Component
@RequiredArgsConstructor
public class ComplaintRefundPolicy implements ManualRefundPolicy {

    private final OrderService orderService;
    private final ComplaintService complaintService;

    @Override
    public void validate(long orderId, RefundReason reason, Long complaintId) {
        if (reason == RefundReason.UNCOLLECTED_OVERRIDE && orderService.statusOf(orderId) != OrderStatus.UNCOLLECTED) {
            throw new ConflictException("REFUND_NOT_ALLOWED", "UNCOLLECTED_OVERRIDE needs an order in UNCOLLECTED");
        }
        if (complaintId != null) {
            Complaint complaint = complaintService.getComplaintEntity(complaintId);
            if (!complaint.getOrderId().equals(orderId)) {
                throw ApiException.badRequest("VALIDATION_ERROR", "complaintId",
                        "Complaint " + complaintId + " is about a different order");
            }
        }
    }

    @Override
    public void afterRefund(long paymentId, long orderId, Long complaintId) {
        if (complaintId != null) {
            complaintService.linkRefundToComplaint(complaintId, paymentId);
        }
    }
}
