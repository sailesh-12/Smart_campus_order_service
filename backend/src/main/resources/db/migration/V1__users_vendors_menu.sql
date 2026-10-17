-- =====================================================================
-- V1: users, vendors, menu_items
-- =====================================================================
CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,  -- STUDENT, VENDOR, ADMIN
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE vendors (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  type          VARCHAR(30)  NOT NULL,  -- CANTEEN, CAFE (JUICE, TEA, BAKERY reserved for later)
  owner_user_id BIGINT NOT NULL REFERENCES users(id),
  is_open       BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE menu_items (
  id           BIGSERIAL PRIMARY KEY,
  vendor_id    BIGINT NOT NULL REFERENCES vendors(id),
  name         VARCHAR(100) NOT NULL,
  price        NUMERIC(10,2) NOT NULL CHECK (price >= 0),
  stock_qty    INT NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
  is_available BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_menu_vendor ON menu_items(vendor_id);
