-- =====================================================================
-- V2: orders, order_items, payments
-- =====================================================================
CREATE TABLE orders (
  id                BIGSERIAL PRIMARY KEY,
  student_id        BIGINT        NOT NULL REFERENCES users(id),
  vendor_id         BIGINT        NOT NULL REFERENCES vendors(id),
  status            VARCHAR(20)   NOT NULL,
  total             NUMERIC(10,2) NOT NULL CHECK (total >= 0),
  -- Client-generated key; a retry with the same key returns the same order
  idempotency_key   VARCHAR(64)   NOT NULL,
  -- Pickup: short code shown to the student (e.g. A47) + random secret in the QR
  pickup_code       VARCHAR(10),
  pickup_secret     VARCHAR(64),
  pickup_date       DATE          NOT NULL DEFAULT CURRENT_DATE,
  pickup_slot_start TIMESTAMPTZ,                      -- chosen "ready at" slot, optional
  version           BIGINT        NOT NULL DEFAULT 0, -- @Version, blocks double status changes
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  ready_at          TIMESTAMPTZ,
  collected_at      TIMESTAMPTZ,
  CONSTRAINT uq_order_idempotency UNIQUE (student_id, idempotency_key),
  CONSTRAINT chk_order_status CHECK (status IN (
    'PENDING_PAYMENT','PAID','ACCEPTED','PREPARING','READY',
    'COLLECTED','REJECTED','CANCELLED','EXPIRED'))
);

-- A short code is unique per vendor per day; the QR secret is globally unique
CREATE UNIQUE INDEX uq_order_pickup_code
  ON orders (vendor_id, pickup_date, pickup_code) WHERE pickup_code IS NOT NULL;
CREATE UNIQUE INDEX uq_order_pickup_secret
  ON orders (pickup_secret) WHERE pickup_secret IS NOT NULL;

CREATE INDEX idx_orders_student ON orders (student_id, created_at DESC);  -- "my orders"
CREATE INDEX idx_orders_vendor_status ON orders (vendor_id, status);      -- vendor board, busy meter
CREATE INDEX idx_orders_status_created ON orders (status, created_at);    -- expiry job

CREATE TABLE order_items (
  id           BIGSERIAL PRIMARY KEY,
  order_id     BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  menu_item_id BIGINT        NOT NULL REFERENCES menu_items(id),
  item_name    VARCHAR(100)  NOT NULL,                 -- snapshot, survives later menu edits
  qty          INT           NOT NULL CHECK (qty > 0),
  unit_price   NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0)  -- price at time of order
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_menu_item ON order_items (menu_item_id);  -- top-items analytics

CREATE TABLE payments (
  id           BIGSERIAL PRIMARY KEY,
  order_id     BIGINT        NOT NULL REFERENCES orders(id),
  amount       NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
  status       VARCHAR(20)   NOT NULL,                 -- INITIATED, SUCCESS, FAILED, REFUNDED
  provider_ref VARCHAR(100),                           -- gateway transaction id
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
  CONSTRAINT chk_payment_status CHECK (status IN ('INITIATED','SUCCESS','FAILED','REFUNDED'))
);

CREATE INDEX idx_payments_order ON payments (order_id);
