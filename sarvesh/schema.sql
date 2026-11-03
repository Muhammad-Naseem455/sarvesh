-- EcoDrop : Smart Reverse-Logistics E-Waste & Hazardous Battery Recycling Network
-- Target Database: MySQL 8.0+
DROP DATABASE IF EXISTS ecodrop;
CREATE DATABASE ecodrop;
USE ecodrop;

-- 1. Users Table (Citizens and Certified Recyclers)
CREATE TABLE users (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(90)  NOT NULL,
  email      VARCHAR(100) NOT NULL UNIQUE,
  phone      VARCHAR(15)  NOT NULL,
  city       VARCHAR(50)  NOT NULL,
  role       ENUM('CITIZEN', 'RECYCLER') NOT NULL,
  credits    INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. E-Waste Items (Hazard Tiers: LOW, MEDIUM, HIGH, CRITICAL)
CREATE TABLE ewaste_items (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  citizen_id    INT NOT NULL,
  category      VARCHAR(50) NOT NULL, -- e.g. 'Li-ion Battery', 'Smartphones/PCB', 'Monitors', 'Cables/Mice'
  item_name     VARCHAR(100) NOT NULL,
  quantity      INT NOT NULL CHECK (quantity > 0),
  hazard_level  ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'LOW',
  status        ENUM('AVAILABLE', 'SCHEDULED', 'RECYCLED') NOT NULL DEFAULT 'AVAILABLE',
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (citizen_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_item_status_hazard (status, hazard_level),
  INDEX idx_item_category (category)
);

-- 3. Pickup Requests (Claims made by certified recyclers)
CREATE TABLE pickups (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  recycler_id  INT NOT NULL,
  item_id      INT NOT NULL UNIQUE, -- 1-to-1: an item can only have one active pickup
  status       ENUM('PENDING', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
  scheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (recycler_id) REFERENCES users(id),
  FOREIGN KEY (item_id) REFERENCES ewaste_items(id) ON DELETE CASCADE
);

-- 4. Audit & Regulatory Compliance Log
CREATE TABLE audit_log (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  item_id     INT NOT NULL,
  action      VARCHAR(60) NOT NULL,
  details     VARCHAR(255),
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Rewards / EcoCredits Transaction Ledger
CREATE TABLE rewards_log (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  citizen_id  INT NOT NULL,
  points      INT NOT NULL,
  reason      VARCHAR(120) NOT NULL,
  awarded_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (citizen_id) REFERENCES users(id) ON DELETE CASCADE
);

DELIMITER $$

-- Trigger 1: Anti-Hoarding / Self-Pickup Prevention (A citizen cannot claim their own e-waste)
CREATE TRIGGER trg_prevent_self_pickup BEFORE INSERT ON pickups FOR EACH ROW
BEGIN
  DECLARE item_citizen_id INT;
  SELECT citizen_id INTO item_citizen_id FROM ewaste_items WHERE id = NEW.item_id;
  IF NEW.recycler_id = item_citizen_id THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflict of Interest: A citizen cannot pick up their own scrap';
  END IF;
END$$

-- Trigger 2: Auto-update item state and log when a Recycler schedules a haul
CREATE TRIGGER trg_pickup_schedule AFTER INSERT ON pickups FOR EACH ROW
BEGIN
  UPDATE ewaste_items SET status = 'SCHEDULED' WHERE id = NEW.item_id;
  INSERT INTO audit_log (item_id, action, details)
  VALUES (NEW.item_id, 'SCHEDULED', CONCAT('Claimed by Recycler ID: ', NEW.recycler_id));
END$$

-- Trigger 3: Automatic EcoCredits payout and audit log on completed recycling
CREATE TRIGGER trg_pickup_complete AFTER UPDATE ON pickups FOR EACH ROW
BEGIN
  DECLARE item_cit_id INT;
  DECLARE item_hazard VARCHAR(20);
  DECLARE credit_pts INT DEFAULT 10;

  IF NEW.status = 'COMPLETED' AND OLD.status = 'PENDING' THEN
    SELECT citizen_id, hazard_level INTO item_cit_id, item_hazard FROM ewaste_items WHERE id = NEW.item_id;
    
    -- Hazard weight bonus for safe disposal of dangerous electronics
    IF item_hazard = 'CRITICAL' THEN SET credit_pts = 50;
    ELSEIF item_hazard = 'HIGH' THEN SET credit_pts = 35;
    ELSEIF item_hazard = 'MEDIUM' THEN SET credit_pts = 20;
    ELSE SET credit_pts = 10;
    END IF;

    -- Update item status to RECYCLED
    UPDATE ewaste_items SET status = 'RECYCLED' WHERE id = NEW.item_id;
    -- Award EcoCredits to the citizen
    UPDATE users SET credits = credits + credit_pts WHERE id = item_cit_id;
    -- Record ledger
    INSERT INTO rewards_log (citizen_id, points, reason)
    VALUES (item_cit_id, credit_pts, CONCAT('Recycled Item #', NEW.item_id, ' [', item_hazard, ']'));
    -- Audit log
    INSERT INTO audit_log (item_id, action, details)
    VALUES (NEW.item_id, 'RECYCLED', CONCAT('Safely processed. Awarded ', credit_pts, ' EcoCredits.'));
  END IF;
END$$

-- Stored Procedure: Maintenance routine to clear stale pending orders
CREATE PROCEDURE cleanup_stale_pickups()
BEGIN
  UPDATE ewaste_items SET status = 'AVAILABLE'
  WHERE id IN (
    SELECT item_id FROM pickups 
    WHERE status = 'PENDING' AND scheduled_at < DATE_SUB(NOW(), INTERVAL 7 DAY)
  );
  UPDATE pickups SET status = 'CANCELLED'
  WHERE status = 'PENDING' AND scheduled_at < DATE_SUB(NOW(), INTERVAL 7 DAY);
END$$

DELIMITER ;

-- View 1: Active Scrap Queue with Hazard Score and Citizen Location
CREATE VIEW v_active_scrap AS
SELECT e.id, e.citizen_id, e.category, e.item_name, e.quantity, e.hazard_level,
       CASE e.hazard_level
         WHEN 'CRITICAL' THEN 4
         WHEN 'HIGH' THEN 3
         WHEN 'MEDIUM' THEN 2
         ELSE 1
       END AS hazard_weight,
       u.name AS citizen_name, u.city, u.phone, e.created_at
FROM ewaste_items e
JOIN users u ON u.id = e.citizen_id
WHERE e.status = 'AVAILABLE';

-- View 2: Live City-Wise Environmental Impact Summary
CREATE VIEW v_green_impact AS
SELECT u.city,
       COUNT(*) AS total_items_recycled,
       SUM(e.quantity) AS units_diverted_from_landfill,
       SUM(CASE WHEN e.hazard_level IN ('HIGH','CRITICAL') THEN e.quantity ELSE 0 END) AS toxic_hazards_neutralized
FROM ewaste_items e
JOIN users u ON u.id = e.citizen_id
WHERE e.status = 'RECYCLED'
GROUP BY u.city;

-- Seed Sample Data
INSERT INTO users (name, email, phone, city, role, credits) VALUES
('Arjun Mehta', 'arjun@mail.com', '9888001122', 'Bengaluru', 'CITIZEN', 40),
('Sneha Rao',   'sneha@mail.com', '9777003344', 'Chennai',   'CITIZEN', 20),
('EcoRecycle Solutions', 'info@ecorecycle.org', '9666005566', 'Bengaluru', 'RECYCLER', 0),
('CleanEarth Recyclers', 'contact@cleanearth.in', '9555007788', 'Chennai',   'RECYCLER', 0);

INSERT INTO ewaste_items (citizen_id, category, item_name, quantity, hazard_level, status) VALUES
(1, 'Li-ion Battery', 'Swollen Laptop Battery (Dell 65Wh)', 2, 'CRITICAL', 'AVAILABLE'),
(1, 'Smartphones/PCB', 'Old Broken Android Motherboard', 4, 'MEDIUM', 'AVAILABLE'),
(2, 'Monitors', 'Damaged CRT Monitor (Lead glass)', 1, 'HIGH', 'AVAILABLE'),
(2, 'Cables/Mice', 'Assorted USB Copper Wires & Charger', 12, 'LOW', 'AVAILABLE');
