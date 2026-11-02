package com.smartcampus.payment;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.payment.dto.ManualRefundRequest;
import com.smartcampus.payment.dto.PaymentIssueResponse;
import com.smartcampus.payment.dto.PaymentResponse;
import com.smartcampus.payment.dto.ResolvePaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for administrator actions on disputed payments and refunds.
 * Business Rule (5.12):
 * - All routes require ADMIN role.
 * - Writes an admin_actions audit record for every action through AuditService.
 * - Every action returns the payment's state afterwards, so a failed gateway refund is visible (REFUND_FAILED).
 */
@RestController
@RequestMapping("/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    @GetMapping("/issues")
    public ResponseEntity<List<PaymentIssueResponse>> getIssues() {
        return ResponseEntity.ok(paymentService.getUnresolvedIssues());
    }

    @PostMapping("/{id}/retry-refund")
    public ResponseEntity<PaymentResponse> retryRefund(@PathVariable long id) {
        return ResponseEntity.ok(paymentService.adminRetryRefund(id, currentUser.id()));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<PaymentResponse> resolveIssue(
            @PathVariable long id,
            @Valid @RequestBody ResolvePaymentRequest request) {
        return ResponseEntity.ok(paymentService.adminResolve(id, request.note(), currentUser.id()));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> manualRefund(
            @PathVariable long id,
            @Valid @RequestBody ManualRefundRequest request) {
        return ResponseEntity.ok(paymentService.adminManualRefund(id, request, currentUser.id()));
    }
}
