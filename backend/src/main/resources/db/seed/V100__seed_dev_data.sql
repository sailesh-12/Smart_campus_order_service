-- =====================================================================
-- DEV SEED DATA: demo users, the 3 campus canteens, ~10 menu items each
-- Put this in src/main/resources/db/seed/ (dev profile only, never prod)
-- Needs V5 (prep_minutes, parallel_capacity), which runs before V100.
--
-- NOTE: the menus below are PLACEHOLDERS. Replace names, prices and prep
-- times with the real items before showing the demo to your college.
--
-- Passwords are hashed with pgcrypto's bcrypt, which Spring's
-- BCryptPasswordEncoder can verify.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------- USERS ----------
-- Admin     : admin@campus.edu                 / Admin@123
-- Vendors   : canteen@ / rishabs@ / metro@campus.edu / Vendor@123
-- Students  : <name>@student.edu               / Student@123

INSERT INTO users (name, email, password_hash, role) VALUES
  ('Campus Admin',        'admin@campus.edu',    crypt('Admin@123',   gen_salt('bf', 10)), 'ADMIN'),
  ('Main Canteen Owner',  'canteen@campus.edu',  crypt('Vendor@123',  gen_salt('bf', 10)), 'VENDOR'),
  ('Rishabs Owner',       'rishabs@campus.edu',  crypt('Vendor@123',  gen_salt('bf', 10)), 'VENDOR'),
  ('Metro Cafe Owner',    'metro@campus.edu',    crypt('Vendor@123',  gen_salt('bf', 10)), 'VENDOR'),
  ('Arun Kumar',          'arun@student.edu',    crypt('Student@123', gen_salt('bf', 10)), 'STUDENT'),
  ('Priya Lakshmi',       'priya@student.edu',   crypt('Student@123', gen_salt('bf', 10)), 'STUDENT');

-- ---------- VENDORS ----------
-- parallel_capacity = how many orders the shop can cook at once (used for the
-- estimated wait time). All three start open; a vendor can close their shop
-- from the dashboard to test the "vendor closed" screen.
INSERT INTO vendors (name, type, owner_user_id, is_open, parallel_capacity)
SELECT v.name, v.type, u.id, true, v.capacity
FROM (VALUES
  ('Main Canteen', 'CANTEEN', 'canteen@campus.edu', 3),
  ('Rishabs',      'CANTEEN', 'rishabs@campus.edu', 2),
  ('Metro Cafe',   'CAFE',    'metro@campus.edu',   2)
) AS v(name, type, owner_email, capacity)
JOIN users u ON u.email = v.owner_email;

-- ---------- MENU ITEMS ----------
-- Columns: shop, item, price (INR), stock, available, prep minutes.
-- Some items are low stock or sold out so the "only 3 left" and
-- "sold out" screens can be tested.
INSERT INTO menu_items (vendor_id, name, price, stock_qty, is_available, prep_minutes)
SELECT v.id, m.name, m.price, m.stock, m.available, m.prep
FROM (VALUES
  -- Main Canteen
  ('Main Canteen', 'Veg Meals',            70.00, 40, true,   5),
  ('Main Canteen', 'Chicken Biryani',     120.00, 30, true,   8),
  ('Main Canteen', 'Egg Fried Rice',       90.00, 25, true,   8),
  ('Main Canteen', 'Veg Fried Rice',       80.00, 25, true,   8),
  ('Main Canteen', 'Masala Dosa',          50.00, 35, true,   6),
  ('Main Canteen', 'Idli (3 pcs)',         30.00, 50, true,   3),
  ('Main Canteen', 'Parotta with Kurma',   60.00, 30, true,   8),
  ('Main Canteen', 'Veg Pizza',            99.00, 20, true,  15),
  ('Main Canteen', 'Chicken 65',          100.00,  3, true,  10),  -- low stock
  ('Main Canteen', 'Fish Curry Meals',    110.00,  0, false,  8),  -- sold out
  -- Rishabs
  ('Rishabs', 'Veg Burger',                60.00, 30, true,   6),
  ('Rishabs', 'Chicken Burger',            90.00, 25, true,   8),
  ('Rishabs', 'French Fries',              50.00, 40, true,   5),
  ('Rishabs', 'Paneer Roll',               70.00, 25, true,   6),
  ('Rishabs', 'Chicken Roll',              80.00, 25, true,   7),
  ('Rishabs', 'Veg Noodles',               80.00, 30, true,   8),
  ('Rishabs', 'Chicken Noodles',          100.00, 25, true,   9),
  ('Rishabs', 'Chilli Paneer',            110.00,  4, true,  10),  -- low stock
  ('Rishabs', 'Cold Coffee',               60.00, 20, true,   4),
  ('Rishabs', 'Brownie with Ice Cream',    90.00,  0, false,  3),  -- sold out
  -- Metro Cafe
  ('Metro Cafe', 'Cappuccino',             70.00, 40, true,   4),
  ('Metro Cafe', 'Filter Coffee',          30.00, 60, true,   3),
  ('Metro Cafe', 'Masala Chai',            20.00, 60, true,   3),
  ('Metro Cafe', 'Cold Coffee',            60.00, 30, true,   4),
  ('Metro Cafe', 'Veg Sandwich',           50.00, 25, true,   5),
  ('Metro Cafe', 'Grilled Cheese Sandwich',70.00, 20, true,   7),
  ('Metro Cafe', 'Samosa',                 15.00, 50, true,   2),
  ('Metro Cafe', 'Veg Puff',               20.00, 40, true,   2),
  ('Metro Cafe', 'Chocolate Milkshake',    80.00,  4, true,   5),  -- low stock
  ('Metro Cafe', 'Cookies Pack',           30.00,  0, false,  1)   -- sold out
) AS m(vendor_name, name, price, stock, available, prep)
JOIN vendors v ON v.name = m.vendor_name;
