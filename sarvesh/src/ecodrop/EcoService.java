package ecodrop;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Business Logic & Persistence Service.
 * Dual-Mode Support:
 *   1. Production Mode: MySQL 8+ with JDBC, Transactions, Triggers, Views, and Row Locking.
 *   2. Standalone Demo Mode: Built-in In-Memory Relational Engine (runs out-of-the-box when MySQL is offline).
 */
public class EcoService {
    private static final String URL  = env("DB_URL", "jdbc:mysql://localhost:3306/ecodrop?useSSL=false&allowPublicKeyRetrieval=true");
    private static final String USER = env("DB_USER", "root");
    private static final String PASS = env("DB_PASS", "root");

    private static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }

    private final boolean useMySQL;

    // In-memory relational tables (fallback simulation)
    private final Map<Integer, User> memUsers = new ConcurrentHashMap<>();
    private final Map<Integer, EWasteItem> memItems = new ConcurrentHashMap<>();
    private final Map<Integer, PickupDto> memPickups = new ConcurrentHashMap<>();
    private final AtomicInteger userSeq = new AtomicInteger(0);
    private final AtomicInteger itemSeq = new AtomicInteger(0);
    private final AtomicInteger pickupSeq = new AtomicInteger(0);

    public static class PickupDto {
        public int pickupId, itemId, recyclerId, quantity;
        public String itemName, category, hazard, citizenName, citizenPhone, citizenCity, status = "PENDING";

        public String toJson() {
            return String.format(
                "{\"pickupId\":%d,\"itemId\":%d,\"recyclerId\":%d,\"quantity\":%d,\"itemName\":\"%s\",\"category\":\"%s\",\"hazard\":\"%s\",\"citizen\":\"%s\",\"phone\":\"%s\",\"city\":\"%s\",\"status\":\"%s\"}",
                pickupId, itemId, recyclerId, quantity, User.esc(itemName), User.esc(category), User.esc(hazard),
                User.esc(citizenName), User.esc(citizenPhone), User.esc(citizenCity), status
            );
        }
    }

    public EcoService() {
        boolean dbOk = false;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
                dbOk = true;
                System.out.println("[EcoDrop] Connected to MySQL database (" + URL + ")");
            }
        } catch (Throwable t) {
            dbOk = false;
        }
        this.useMySQL = dbOk;
        if (!useMySQL) {
            System.out.println("[EcoDrop] MySQL not active - running with Built-in Relational In-Memory Store.");
            initSeedData();
        }
    }

    private void initSeedData() {
        Citizen c1 = new Citizen(userSeq.incrementAndGet(), "Arjun Mehta", "arjun@mail.com", "9888001122", "Bengaluru", 40);
        Citizen c2 = new Citizen(userSeq.incrementAndGet(), "Sneha Rao", "sneha@mail.com", "9777003344", "Chennai", 20);
        Recycler r1 = new Recycler(userSeq.incrementAndGet(), "EcoRecycle Solutions", "info@ecorecycle.org", "9666005566", "Bengaluru");
        Recycler r2 = new Recycler(userSeq.incrementAndGet(), "CleanEarth Recyclers", "contact@cleanearth.in", "9555007788", "Chennai");

        memUsers.put(c1.getId(), c1);
        memUsers.put(c2.getId(), c2);
        memUsers.put(r1.getId(), r1);
        memUsers.put(r2.getId(), r2);

        addMemItem(1, "Li-ion Battery", "Swollen Laptop Battery (Dell 65Wh)", 2, HazardLevel.CRITICAL);
        addMemItem(1, "Smartphones/PCB", "Old Broken Android Motherboard", 4, HazardLevel.MEDIUM);
        addMemItem(2, "Monitors", "Damaged CRT Monitor (Lead glass)", 1, HazardLevel.HIGH);
        addMemItem(2, "Cables/Mice", "Assorted USB Copper Wires & Charger", 12, HazardLevel.LOW);
    }

    private void addMemItem(int citizenId, String cat, String name, int qty, HazardLevel hz) {
        User u = memUsers.get(citizenId);
        EWasteItem it = new EWasteItem();
        it.id = itemSeq.incrementAndGet();
        it.citizenId = citizenId;
        it.category = cat;
        it.itemName = name;
        it.quantity = qty;
        it.hazardLevel = hz;
        it.status = "AVAILABLE";
        it.citizenName = u != null ? u.getName() : "Citizen";
        it.citizenCity = u != null ? u.getCity() : "-";
        it.citizenPhone = u != null ? u.getPhone() : "-";
        memItems.put(it.id, it);
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(URL, USER, PASS); }

    /* ================= USER MANAGEMENT ================= */
    public synchronized int register(User u) throws EcoException {
        if (useMySQL) {
            String sql = "INSERT INTO users(name,email,phone,city,role,credits) VALUES(?,?,?,?,?,?)";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, u.getName());
                ps.setString(2, u.getEmail());
                ps.setString(3, u.getPhone());
                ps.setString(4, u.getCity());
                ps.setString(5, u.getRole());
                ps.setInt(6, (u instanceof Citizen) ? ((Citizen) u).getCredits() : 0);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                rs.next();
                u.setId(rs.getInt(1));
                return u.getId();
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            for (User ex : memUsers.values()) {
                if (ex.getEmail().equalsIgnoreCase(u.getEmail())) {
                    throw new EcoException("Email already registered (UNIQUE constraint)");
                }
            }
            int id = userSeq.incrementAndGet();
            u.setId(id);
            memUsers.put(id, u);
            return id;
        }
    }

    public User login(String query) throws EcoException {
        if (query == null || query.trim().isEmpty()) throw new EcoException("Please enter Email or ID to log in.");
        String q = query.trim();
        int queryId = -1;
        try { queryId = Integer.parseInt(q); } catch (Exception ignored) {}

        if (useMySQL) {
            String sql = "SELECT * FROM users WHERE email=? OR id=?";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, q);
                ps.setInt(2, queryId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) throw new EcoException("Account not found. Please register first.");
                String role = rs.getString("role");
                if ("CITIZEN".equals(role)) {
                    return new Citizen(rs.getInt("id"), rs.getString("name"), rs.getString("email"),
                            rs.getString("phone"), rs.getString("city"), rs.getInt("credits"));
                } else {
                    return new Recycler(rs.getInt("id"), rs.getString("name"), rs.getString("email"),
                            rs.getString("phone"), rs.getString("city"));
                }
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            for (User u : memUsers.values()) {
                if (u.getEmail().equalsIgnoreCase(q) || u.getId() == queryId) return u;
            }
            throw new EcoException("Account not found. Please register first.");
        }
    }

    public List<String> listUsers() throws EcoException {
        List<String> list = new ArrayList<>();
        if (useMySQL) {
            try (Connection c = conn(); ResultSet rs = c.createStatement().executeQuery("SELECT * FROM users ORDER BY id")) {
                while (rs.next()) {
                    String role = rs.getString("role");
                    User u = "CITIZEN".equals(role)
                        ? new Citizen(rs.getInt("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("city"), rs.getInt("credits"))
                        : new Recycler(rs.getInt("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("city"));
                    list.add("#" + u.getId() + " " + u.describe());
                }
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            memUsers.values().stream().sorted(Comparator.comparingInt(User::getId))
                    .forEach(u -> list.add("#" + u.getId() + " " + u.describe()));
        }
        return list;
    }

    /* ================= CITIZEN: E-WASTE DEPOSITS ================= */
    public synchronized void depositItem(int citizenId, String category, String itemName, int qty, HazardLevel hazard) throws EcoException {
        if (qty <= 0) throw new EcoException("Quantity must be greater than 0");
        if (itemName == null || itemName.trim().isEmpty()) throw new EcoException("Item name cannot be empty");

        if (useMySQL) {
            String sql = "INSERT INTO ewaste_items (citizen_id, category, item_name, quantity, hazard_level, status) VALUES (?,?,?,?,?,'AVAILABLE')";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, citizenId);
                ps.setString(2, category);
                ps.setString(3, itemName);
                ps.setInt(4, qty);
                ps.setString(5, hazard.name());
                ps.executeUpdate();
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            if (!memUsers.containsKey(citizenId)) throw new EcoException("Citizen ID " + citizenId + " not found (FK constraint)");
            addMemItem(citizenId, category, itemName, qty, hazard);
        }
    }

    public List<EWasteItem> myItems(int citizenId) throws EcoException {
        List<EWasteItem> list = new ArrayList<>();
        if (useMySQL) {
            String sql = "SELECT * FROM ewaste_items WHERE citizen_id = ? ORDER BY id DESC";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, citizenId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    EWasteItem it = new EWasteItem();
                    it.id = rs.getInt("id");
                    it.citizenId = rs.getInt("citizen_id");
                    it.category = rs.getString("category");
                    it.itemName = rs.getString("item_name");
                    it.quantity = rs.getInt("quantity");
                    it.hazardLevel = HazardLevel.fromString(rs.getString("hazard_level"));
                    it.status = rs.getString("status");
                    list.add(it);
                }
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            for (EWasteItem it : memItems.values()) {
                if (it.citizenId == citizenId) list.add(it);
            }
            list.sort(Comparator.comparingInt((EWasteItem i) -> i.id).reversed());
        }
        return list;
    }

    /* ================= RECYCLER: HAZARD-PRIORITY QUEUE & DISPATCH ================= */
    /**
     * Smart Priority Queue Algorithm:
     * 1. Same-city matches appear first (cuts dangerous transit distance).
     * 2. Within city, ordered by Hazard Level DESCENDING (Critical Li-ion > High > Medium > Low).
     */
    public List<EWasteItem> searchHazardQueue(String category, String recyclerCity) throws EcoException {
        List<EWasteItem> list = new ArrayList<>();
        String catFilter = (category == null ? "" : category.trim().toLowerCase());

        if (useMySQL) {
            String sql = "SELECT * FROM v_active_scrap WHERE LOWER(category) LIKE ? OR LOWER(item_name) LIKE ?";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, "%" + catFilter + "%");
                ps.setString(2, "%" + catFilter + "%");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    EWasteItem it = new EWasteItem();
                    it.id = rs.getInt("id");
                    it.citizenId = rs.getInt("citizen_id");
                    it.category = rs.getString("category");
                    it.itemName = rs.getString("item_name");
                    it.quantity = rs.getInt("quantity");
                    it.hazardLevel = HazardLevel.fromString(rs.getString("hazard_level"));
                    it.status = "AVAILABLE";
                    it.citizenName = rs.getString("citizen_name");
                    it.citizenCity = rs.getString("city");
                    it.citizenPhone = rs.getString("phone");
                    list.add(it);
                }
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            for (EWasteItem it : memItems.values()) {
                if ("AVAILABLE".equals(it.status)) {
                    if (catFilter.isEmpty() || it.category.toLowerCase().contains(catFilter) || it.itemName.toLowerCase().contains(catFilter)) {
                        list.add(it);
                    }
                }
            }
        }

        // 2-Tier Sorting: Same city first, then natural hazard weight descending
        String home = (recyclerCity == null ? "" : recyclerCity.trim());
        list.sort(Comparator.<EWasteItem, Boolean>comparing(it -> !it.citizenCity.equalsIgnoreCase(home))
                .thenComparing(Comparator.naturalOrder()));
        return list;
    }

    /**
     * Schedule Pickup with ACID Transaction & Pessimistic Row Lock (SELECT ... FOR UPDATE).
     */
    public synchronized void schedulePickup(int recyclerId, int itemId) throws EcoException {
        if (useMySQL) {
            try (Connection c = conn()) {
                c.setAutoCommit(false);
                try (PreparedStatement chk = c.prepareStatement("SELECT status, citizen_id FROM ewaste_items WHERE id=? FOR UPDATE");
                     PreparedStatement role = c.prepareStatement("SELECT role FROM users WHERE id=?")) {
                    
                    role.setInt(1, recyclerId);
                    ResultSet r = role.executeQuery();
                    if (!r.next() || !r.getString(1).equals("RECYCLER")) {
                        throw new EcoException("Only certified recyclers can schedule e-waste pickups");
                    }

                    chk.setInt(1, itemId);
                    ResultSet rs = chk.executeQuery();
                    if (!rs.next() || !rs.getString(1).equals("AVAILABLE")) {
                        throw new EcoException("Item is no longer available for pickup");
                    }
                    if (rs.getInt("citizen_id") == recyclerId) {
                        throw new EcoException("Conflict of Interest: A citizen cannot schedule their own e-waste haul");
                    }

                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO pickups (recycler_id, item_id, status) VALUES (?,?,'PENDING')")) {
                        ins.setInt(1, recyclerId);
                        ins.setInt(2, itemId);
                        ins.executeUpdate();
                    }
                    c.commit(); // Triggers update status to SCHEDULED and insert audit log
                } catch (EcoException | SQLException e) {
                    c.rollback();
                    throw e;
                }
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            User r = memUsers.get(recyclerId);
            if (r == null || !"RECYCLER".equals(r.getRole())) throw new EcoException("Only certified recyclers can schedule pickups");
            EWasteItem it = memItems.get(itemId);
            if (it == null || !"AVAILABLE".equals(it.status)) throw new EcoException("Item is no longer available for pickup");
            if (it.citizenId == recyclerId) throw new EcoException("Conflict of Interest: Cannot claim your own e-waste");

            it.status = "SCHEDULED";
            PickupDto p = new PickupDto();
            p.pickupId = pickupSeq.incrementAndGet();
            p.itemId = itemId;
            p.recyclerId = recyclerId;
            p.quantity = it.quantity;
            p.itemName = it.itemName;
            p.category = it.category;
            p.hazard = it.hazardLevel.name();
            p.citizenName = it.citizenName;
            p.citizenPhone = it.citizenPhone;
            p.citizenCity = it.citizenCity;
            p.status = "PENDING";
            memPickups.put(p.pickupId, p);
        }
    }

    public synchronized void completeRecycling(int pickupId) throws EcoException {
        if (useMySQL) {
            String sql = "UPDATE pickups SET status='COMPLETED' WHERE id=? AND status='PENDING'";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, pickupId);
                if (ps.executeUpdate() == 0) throw new EcoException("No pending pickup found with ID " + pickupId);
                // Trigger trg_pickup_complete automatically sets item to RECYCLED and awards EcoCredits!
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            PickupDto p = memPickups.get(pickupId);
            if (p == null || !"PENDING".equals(p.status)) throw new EcoException("No pending pickup found with ID " + pickupId);
            p.status = "COMPLETED";
            EWasteItem it = memItems.get(p.itemId);
            if (it != null) it.status = "RECYCLED";
            User u = memUsers.get(it != null ? it.citizenId : 0);
            if (u instanceof Citizen) {
                int pts = (it.hazardLevel == HazardLevel.CRITICAL) ? 50
                        : (it.hazardLevel == HazardLevel.HIGH) ? 35
                        : (it.hazardLevel == HazardLevel.MEDIUM) ? 20 : 10;
                ((Citizen) u).addCredits(pts);
            }
        }
    }

    public List<PickupDto> myPickups(int recyclerId) throws EcoException {
        List<PickupDto> list = new ArrayList<>();
        if (useMySQL) {
            String sql = "SELECT p.id req_id, p.item_id, p.status, e.item_name, e.category, e.quantity, e.hazard_level, u.name citizen_name, u.phone, u.city " +
                         "FROM pickups p JOIN ewaste_items e ON e.id = p.item_id JOIN users u ON u.id = e.citizen_id " +
                         "WHERE p.recycler_id = ? ORDER BY p.id DESC";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, recyclerId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    PickupDto dto = new PickupDto();
                    dto.pickupId = rs.getInt("req_id");
                    dto.itemId = rs.getInt("item_id");
                    dto.status = rs.getString("status");
                    dto.itemName = rs.getString("item_name");
                    dto.category = rs.getString("category");
                    dto.quantity = rs.getInt("quantity");
                    dto.hazard = rs.getString("hazard_level");
                    dto.citizenName = rs.getString("citizen_name");
                    dto.citizenPhone = rs.getString("phone");
                    dto.citizenCity = rs.getString("city");
                    list.add(dto);
                }
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            for (PickupDto p : memPickups.values()) {
                if (p.recyclerId == recyclerId) list.add(p);
            }
            list.sort(Comparator.comparingInt((PickupDto d) -> d.pickupId).reversed());
        }
        return list;
    }

    public Map<String, Integer> getImpactStats() throws EcoException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        if (useMySQL) {
            try (Connection c = conn()) {
                ResultSet rs = c.createStatement().executeQuery("SELECT status, COUNT(*) FROM ewaste_items GROUP BY status");
                while (rs.next()) stats.put(rs.getString(1), rs.getInt(2));
                ResultSet r2 = c.createStatement().executeQuery("SELECT COALESCE(SUM(points), 0) FROM rewards_log");
                r2.next(); stats.put("ECO_CREDITS", r2.getInt(1));
            } catch (SQLException e) { throw new EcoException(e.getMessage()); }
        } else {
            int avail = 0, sched = 0, rec = 0, totalCredits = 0;
            for (EWasteItem it : memItems.values()) {
                if ("AVAILABLE".equals(it.status)) avail++;
                else if ("SCHEDULED".equals(it.status)) sched++;
                else if ("RECYCLED".equals(it.status)) rec++;
            }
            for (User u : memUsers.values()) {
                if (u instanceof Citizen) totalCredits += ((Citizen) u).getCredits();
            }
            stats.put("AVAILABLE", avail);
            stats.put("SCHEDULED", sched);
            stats.put("RECYCLED", rec);
            stats.put("ECO_CREDITS", totalCredits);
        }
        return stats;
    }
}
