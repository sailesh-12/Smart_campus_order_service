-- =====================================================================
-- V3: complaints raised by students against orders
-- =====================================================================
CREATE TABLE complaints (
  id          BIGSERIAL PRIMARY KEY,
  order_id    BIGINT        NOT NULL REFERENCES orders(id),
  user_id     BIGINT        NOT NULL REFERENCES users(id),
  text        VARCHAR(1000) NOT NULL,
  status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN',   -- OPEN, IN_REVIEW, RESOLVED, REJECTED
  admin_note  VARCHAR(1000),
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ,
  CONSTRAINT chk_complaint_status CHECK (status IN ('OPEN','IN_REVIEW','RESOLVED','REJECTED'))
);

CREATE INDEX idx_complaints_status ON complaints (status, created_at DESC);  -- admin inbox
CREATE INDEX idx_complaints_user ON complaints (user_id);
CREATE INDEX idx_complaints_order ON complaints (order_id);
