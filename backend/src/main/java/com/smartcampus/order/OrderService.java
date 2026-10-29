package com.smartcampus.order;

import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.PayOrderResponse;
import com.smartcampus.order.dto.PlaceOrderResult;
import com.smartcampus.order.dto.VendorOrderStats;
import com.smartcampus.order.events.OrderStatusChanged;

import java.time.Instant;
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
     *   Placements of one student are serialized with a transaction-scoped advisory lock so the count is exact.
     * - Enforces rate limit at app.order.rate-limit-per-minute per student (otherwise 429 RATE_LIMITED + Retry-After).
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
     *   (200, Idempotent-Replayed: true), exempt from the hoarding and rate limits. Same key + different payload returns
     *   409 IDEMPOTENCY_KEY_REUSED. Two identical requests racing: the loser catches the unique violation and replays.
     */
    PlaceOrderResult placeOrder(CreateOrderRequest request, long studentId, String idempotencyKey);

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
     * - Calling /pay again after success returns the paid state (200) without charging again.
     */
    PayOrderResponse payOrder(long orderId, long studentId);

    /**
     * Business Rule (5.6):
     * Cancels an order.
     * Allowed in PENDING_PAYMENT (only if payment is NOT UNCONFIRMED, otherwise 409 PAYMENT_UNCONFIRMED) and in PAID.
     * Never allowed after ACCEPTED (409 INVALID_TRANSITION).
     * If order was PAID, a gateway refund with reason CANCELLED runs right after commit.
     * Releases stock and booked slot once.
     */
    OrderResponse cancelOrder(long orderId, long studentId);

    /**
     * Business Rule (5.6 & 5.7):
     * Vendor updates order status along the state machine (ACCEPTED, PREPARING, READY or REJECTED;
     * COLLECTED goes through pickup verification and UNCOLLECTED through the job).
     * - Verifies vendor ownership of order.
     * - Uses OrderStatus.canTransitionTo and a conditional update (which bumps @Version) to block concurrent/invalid changes.
     * - Vendor reject (PAID or ACCEPTED to REJECTED): refund (REJECTED) after commit and restores stock once.
     * - Records accepted_at, preparing_at, and ready_at timestamps.
     */
    OrderResponse updateOrderStatus(long orderId, OrderStatus newStatus, long vendorUserId);

    /**
     * Required by NotificationController (Section 3):
     * Throws 404 NOT_FOUND or 403 FORBIDDEN unless order belongs to student, returns OrderStatusChanged snapshot.
     */
    OrderStatusChanged currentStatusEvent(long orderId, long userId);

    /**
     * Ownership rule (Section 7): a student sees only their own orders, a vendor only their shop's, an admin all.
     *
     * @param role the caller's role name (STUDENT, VENDOR or ADMIN)
     */
    OrderResponse getOrderById(long orderId, long userId, String role);

    List<OrderResponse> getStudentOrders(long studentId);

    /**
     * Vendor board. With no status: the active columns PAID, ACCEPTED, PREPARING, READY
     * (UNCOLLECTED is hidden there, but listed with ?status=UNCOLLECTED).
     */
    List<OrderResponse> getVendorOrders(long vendorId, OrderStatus status);

    /** Admin: most recent orders across campus. */
    List<OrderResponse> getRecentOrders(int limit);

    /** Rebuilds the cart of a previous order of this student (current prices) and places it as a new order. */
    PlaceOrderResult reorder(long orderId, long studentId, String idempotencyKey);

    long studentIdOf(long orderId);

    OrderStatus statusOf(long orderId);

    VendorOrderStats getVendorStats(long vendorId, Instant from, Instant to, int topItems);

    java.math.BigDecimal totalRevenue();

    long totalOrders();
}
