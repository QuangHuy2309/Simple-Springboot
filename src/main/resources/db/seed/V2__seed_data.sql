-- V2: Seed data (local profile only)
-- Passwords are BCrypt hashes.
--   admin / admin123
--   user  / user123

INSERT INTO app_user (username, email, password, role) VALUES
    ('admin', 'admin@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN'),
    ('alice', 'alice@example.com', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk2wgkS', 'USER'),
    ('bob',   'bob@example.com',   '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk2wgkS', 'USER')
ON CONFLICT DO NOTHING;

INSERT INTO product (name, description, price, stock) VALUES
    ('Laptop Pro 15',    'High-performance developer laptop',  1299.99, 50),
    ('Mechanical Keyboard', 'TKL RGB mechanical keyboard',      89.99, 200),
    ('USB-C Hub 7-in-1', 'Multiport adapter with HDMI & PD',   39.99, 150),
    ('Monitor 27" 4K',   'IPS 4K 144Hz display',              499.99,  30),
    ('Wireless Mouse',   'Ergonomic silent wireless mouse',     29.99, 300)
ON CONFLICT DO NOTHING;
