-- =====================================================================
-- V6: push notification tokens, uncollected orders, admin money actions,
--     forgot-password
-- =====================================================================

-- ---------- 1. Push notifications ----------
CREATE TABLE device_tokens (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token        VARCHAR(512) NOT NULL UNIQUE,            -- FCM registration token
  platform     VARCHAR(10)  NOT NULL CHECK (platform IN ('ANDROID','IOS')),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);

-- ---------- 2. Stock hoarding protection ----------
-- Counting a student's unpaid orders must be fast (checked on every POST /orders)
CREATE INDEX idx_orders_student_pending ON orders (student_id) WHERE status = 'PENDING_PAYMENT';

-- ---------- 3. Uncollected orders ----------
-- New terminal-ish status UNCOLLECTED (READY for too long); a late pickup may still move it to COLLECTED
ALTER TABLE orders
  DROP CONSTRAINT chk_order_status,
  ADD CONSTRAINT chk_order_status CHECK (status IN (
    'PENDING_PAYMENT','PAID','ACCEPTED','PREPARING','READY',
    'COLLECTED','REJECTED','CANCELLED','EXPIRED','UNCOLLECTED')),
  ADD COLUMN reminder_sent_at TIMESTAMPTZ,   -- set once when the pickup reminder is sent
  ADD COLUMN uncollected_at   TIMESTAMPTZ;   -- set when flagged UNCOLLECTED

-- The uncollected-order job scans only READY orders
CREATE INDEX idx_orders_ready_at ON orders (ready_at) WHERE status = 'READY';

-- ---------- 4. Admin actions on money ----------
ALTER TABLE payments
  ADD COLUMN refund_reason   VARCHAR(25),
  ADD COLUMN refund_attempts INT NOT NULL DEFAULT 0,        -- gateway refund calls made
  ADD COLUMN refunded_at     TIMESTAMPTZ,
  ADD COLUMN resolved_at     TIMESTAMPTZ,                   -- admin marked the issue as handled
  ADD COLUMN resolved_by     BIGINT REFERENCES users(id),
  ADD COLUMN resolution_note VARCHAR(300),
  ADD CONSTRAINT chk_refund_reason CHECK (refund_reason IS NULL OR refund_reason IN (
    'CANCELLED','REJECTED','LATE_SUCCESS','COMPLAINT','UNCOLLECTED_OVERRIDE'));

-- A refund issued because of a complaint is linked back to it
ALTER TABLE complaints
  ADD COLUMN refund_payment_id BIGINT REFERENCES payments(id);

-- Who did what: every admin action on money (and other admin changes) is recorded
CREATE TABLE admin_actions (
  id          BIGSERIAL PRIMARY KEY,
  admin_id    BIGINT       NOT NULL REFERENCES users(id),
  action      VARCHAR(40)  NOT NULL,        -- e.g. RETRY_REFUND, RESOLVE_PAYMENT, MANUAL_REFUND
  target_type VARCHAR(30)  NOT NULL,        -- e.g. PAYMENT, COMPLAINT, VENDOR
  target_id   BIGINT       NOT NULL,
  note        VARCHAR(300),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_actions_admin  ON admin_actions (admin_id, created_at DESC);
CREATE INDEX idx_admin_actions_target ON admin_actions (target_type, target_id);

-- ---------- 5. Forgot password ----------
-- Only the SHA-256 hash of the reset token is stored, never the token itself
CREATE TABLE password_reset_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash CHAR(64)    NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reset_tokens_user ON password_reset_tokens (user_id);

-- JWTs issued before this moment are rejected, so a reset also logs out old sessions
ALTER TABLE users
  ADD COLUMN password_changed_at TIMESTAMPTZ;
