-- =====================================================================
-- V4: pickup slots, plus small extras needed by the dashboards
-- =====================================================================
-- Slot capacity: one row per vendor per 5-minute slot, created lazily on first booking.
-- Booking uses a conditional update:
--   UPDATE slot_capacity SET booked_orders = booked_orders + 1
--   WHERE vendor_id = :v AND slot_start = :s AND booked_orders < max_orders
CREATE TABLE slot_capacity (
  vendor_id     BIGINT      NOT NULL REFERENCES vendors(id),
  slot_start    TIMESTAMPTZ NOT NULL,
  max_orders    INT         NOT NULL CHECK (max_orders > 0),
  booked_orders INT         NOT NULL DEFAULT 0 CHECK (booked_orders >= 0),
  PRIMARY KEY (vendor_id, slot_start),
  CONSTRAINT chk_slot_not_overbooked CHECK (booked_orders <= max_orders)
);

-- Vendor settings: rush mode, prep-time estimate, default slot size, admin suspension
ALTER TABLE vendors
  ADD COLUMN rush_mode        BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN avg_prep_minutes INT     NOT NULL DEFAULT 10 CHECK (avg_prep_minutes > 0),
  ADD COLUMN slot_max_orders  INT     NOT NULL DEFAULT 10 CHECK (slot_max_orders > 0),
  ADD COLUMN is_suspended     BOOLEAN NOT NULL DEFAULT false;

-- Menu display fields for the student app and vendor menu manager
ALTER TABLE menu_items
  ADD COLUMN description VARCHAR(255),
  ADD COLUMN category    VARCHAR(50),
  ADD COLUMN image_url   VARCHAR(255);

-- Lets admin deactivate accounts without deleting them
ALTER TABLE users
  ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
