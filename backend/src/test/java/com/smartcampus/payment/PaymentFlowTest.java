package com.smartcampus.payment;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for payment flow edge cases (Sections 5.3, 5.4, 5.5b, 5.6).
 */
class PaymentFlowTest {

    @Test
    @Disabled("TODO: declined payment restores stock exactly once")
    void declinedPayment_restoresStockExactlyOnce() {
    }

    @Test
    @Disabled("TODO: payment timeout (UNCONFIRMED) keeps stock held; reconciliation then resolves it either way")
    void paymentTimeout_keepsStockHeld_reconciliationResolves() {
    }

    @Test
    @Disabled("TODO: late success after expiry triggers an automatic refund")
    void lateSuccessAfterExpiry_triggersAutoRefund() {
    }

    @Test
    @Disabled("TODO: late success refund fails → ends in REFUND_FAILED and shows in admin issues list")
    void lateSuccessAfterExpiry_refundFails_endsInRefundFailed_showsInAdminIssues() {
    }
}
