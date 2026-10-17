package com.smartcampus.order;

import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.PayOrderResponse;
import com.smartcampus.order.events.OrderStatusChanged;

import java.util.List;

/**
 * Main domain service managing order placement, payment orchestration, status transitions, and lifecycle events.
 */
public interface OrderService {

    /**
     * Business Rule (5.1, 5.2, 5.7, 5.10):
     * Places a new food order in ONE short database transaction (no external gateway calls inside).
     *
     * Rules:
     * - Validates user is active.
     * - Enforces hoarding limit: student must have fewer than app.order.max-unpaid-per-student
     *   unpaid orders (PENDING_PAYMENT), otherwise throws 409 TOO_MANY_UNPAID_ORDERS.
     * - Enforces rate limit at app.order.rate-limit-per-minute per student (otherwise 429 RATE_LIMITED).
     * - Validates vendor is open and not suspended.
     * - Validates all items belong to that single vendor, are available, and quantities between 1 and app.order.max-qty-per-item.
     * - Merges duplicate lines of the same menu item.
     * - If pickup slot is chosen: validates slot time (earliest allowed is "now + low estimate", rounded up to next 5 minutes)
     *   and books slot conditionally via slot_capacity. Rejects with 409 SLOT_FULL if 0 rows updated.
     * - Reserves stock for each line with ONE conditional statement per item (tryDecrementStock where stock_qty >= qty).
     *   Never reads stock in Java and writes it.
     * - Deadlock prevention: processes items in ascending menu_item_id order.
     * - If any item fails, throws 409 OUT_OF_STOCK so the whole transaction rolls back (including previously reserved items/slots).
     * - Saves order as PENDING_PAYMENT with hold_expires_at = now + app.order.hold-minutes.
     * - Copies item name and unit price from the DB snapshot (never trusts client prices).
     * - Computes total and calculates fresh promised estimate (est_minutes_low/high, est_ready_at).
     * - Creates payments record in INITIATED.
     * - After commit, publishes OrderStatusChanged and MenuItemUpdated events.
     * - Returns 201 with holdExpiresAt, estimate, and payment id. Pickup code and QR secret are NOT generated yet.
     * - Idempotency (5.7): unique constraint on (student_id, idempotency_key). Same key + same request returns existing order
     *   (200, Idempotent-Replayed: true). Same key + different payload returns 409 IDEMPOTENCY_KEY_REUSED.
     *
     * @param request order details
     * @param studentId calling student user id
     * @param idempotencyKey client unique idempotency key
     * @return OrderResponse
     */
    OrderResponse placeOrder(CreateOrderRequest request, long studentId, String idempotencyKey);

    /**
     * Business Rule (5.3):
     * Step 2 of payment flow. Method is NOT @Transactional.
     *
     * Rules:
     * - Verifies owner, order status is PENDING_PAYMENT, and hold deadline has not expired (otherwise 409 HOLD_EXPIRED).
     * - Calls PaymentGateway.charge(...) using payment id as gateway idempotency reference.
     * - Gateway SUCCESS: payment moves to SUCCESS, order becomes PAID, pickup code and QR secret are generated, stock kept (HTTP 200).
     * - Definite DECLINE: payment moves to FAILED, order becomes CANCELLED, stock and slot restored once (HTTP 402 PAYMENT_DECLINED).
     * - Timeout / Network error / UNKNOWN: payment moves to UNCONFIRMED, order stays PENDING_PAYMENT, stock KEPT held (HTTP 202 PAYMENT_UNCONFIRMED).
     * - Calling /pay again while UNCONFIRMED does not create a second charge; returns 202 again.
     *
     * @param orderId order identifier
     * @param studentId calling student user id
     * @return PayOrderResponse
     */
    PayOrderResponse payOrder(long orderId, long studentId);

    /**
     * Business Rule (5.6):
     * Cancels an order.
     * Allowed in PENDING_PAYMENT (only if payment is NOT UNCONFIRMED, otherwise 409 PAYMENT_UNCONFIRMED) and in PAID.
     * Never allowed after ACCEPTED.
     * If order was PAID, initiates gateway refund with reason CANCELLED.
     * Releases stock and booked slot once.
     *
     * @param orderId order identifier
     * @param studentId calling student user id
     */
    void cancelOrder(long orderId, long studentId);

    /**
     * Business Rule (5.6 & 5.7):
     * Vendor updates order status along the state machine.
     * - Verifies vendor ownership of order.
     * - Uses OrderStatus.canTransitionTo and @Version column to block concurrent/invalid status changes.
     * - Vendor reject (PAID or ACCEPTED to REJECTED): initiates gateway refund (REJECTED) and restores stock once.
     * - Records accepted_at, preparing_at, and ready_at timestamps.
     *
     * @param orderId order identifier
     * @param newStatus requested target state
     * @param vendorUserId calling vendor user id
     */
    void updateOrderStatus(long orderId, OrderStatus newStatus, long vendorUserId);

    /**
     * Required by NotificationController (Section 3):
     * Throws 404 NOT_FOUND or 403 FORBIDDEN unless order belongs to student, returns OrderStatusChanged snapshot.
     */
    OrderStatusChanged currentStatusEvent(long orderId, long userId);

    OrderResponse getOrderById(long orderId, long userId, boolean isVendorOrAdmin);

    List<OrderResponse> getStudentOrders(long studentId);

    List<OrderResponse> getVendorOrders(long vendorId, OrderStatus status);
}
