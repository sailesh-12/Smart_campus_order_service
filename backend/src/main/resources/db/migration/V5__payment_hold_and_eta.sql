-- =====================================================================
-- V5: payment hold + reconciliation, webhook dedupe, estimated prep time
-- =====================================================================
-- Orders: hold deadline for unpaid orders, status timestamps, promised estimate
ALTER TABLE orders
  ADD COLUMN hold_expires_at  TIMESTAMPTZ,   -- stock is held until this time while PENDING_PAYMENT
  ADD COLUMN accepted_at      TIMESTAMPTZ,
  ADD COLUMN preparing_at     TIMESTAMPTZ,
  ADD COLUMN est_minutes_low  INT,           -- estimate promised at placement (a range)
  ADD COLUMN est_minutes_high INT,
  ADD COLUMN est_ready_at     TIMESTAMPTZ;

-- Expiry job scans only unpaid orders
CREATE INDEX idx_orders_hold ON orders (hold_expires_at) WHERE status = 'PENDING_PAYMENT';

-- Payments: UNCONFIRMED (timeout, outcome unknown) and REFUND_FAILED (needs admin attention)
ALTER TABLE payments
  DROP CONSTRAINT chk_payment_status,
  ADD CONSTRAINT chk_payment_status CHECK (status IN (
    'INITIATED','UNCONFIRMED','SUCCESS','FAILED','REFUNDED','REFUND_FAILED')),
  ADD COLUMN updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN check_count     INT         NOT NULL DEFAULT 0,   -- reconciliation attempts
  ADD COLUMN last_checked_at TIMESTAMPTZ,
  ADD COLUMN failure_reason  VARCHAR(200);

-- One gateway transaction maps to one payment row
CREATE UNIQUE INDEX uq_payments_provider_ref ON payments (provider_ref) WHERE provider_ref IS NOT NULL;

-- Reconciliation job and admin issues list only look at unresolved payments
CREATE INDEX idx_payments_open ON payments (status)
  WHERE status IN ('INITIATED','UNCONFIRMED','REFUND_FAILED');

-- Webhook dedupe: the same event id is processed at most once
CREATE TABLE payment_webhook_events (
  event_id    VARCHAR(100) PRIMARY KEY,
  payment_id  BIGINT REFERENCES payments(id),
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Estimated time: per-dish prep time and how many orders a shop can cook at once
-- (vendors.avg_prep_minutes from V4 stays only as a fallback value)
ALTER TABLE menu_items
  ADD COLUMN prep_minutes INT NOT NULL DEFAULT 10 CHECK (prep_minutes > 0);

ALTER TABLE vendors
  ADD COLUMN parallel_capacity INT NOT NULL DEFAULT 2 CHECK (parallel_capacity > 0);
