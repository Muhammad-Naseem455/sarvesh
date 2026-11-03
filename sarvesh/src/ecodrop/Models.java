package ecodrop;

/* ===== OOP: Abstraction + Inheritance + Polymorphism + Encapsulation ===== */

/** Abstract base class representing system actors. */
abstract class User {
    private int id;
    private final String name, email, phone, city;

    public User(int id, String name, String email, String phone, String city) {
        this.id = id; this.name = name; this.email = email; this.phone = phone; this.city = city;
    }

    public abstract String getRole(); // Polymorphic contract

    public String describe() {
        return "[" + getRole() + "] " + name + " (" + city + ")";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCity() { return city; }

    public String toJson() {
        return String.format("{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"city\":\"%s\",\"role\":\"%s\"}",
                id, esc(name), esc(email), esc(phone), esc(city), getRole());
    }

    protected static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

/** Citizen actor who deposits e-waste and earns EcoCredits. */
class Citizen extends User {
    private int credits;

    public Citizen(int id, String name, String email, String phone, String city, int credits) {
        super(id, name, email, phone, city);
        this.credits = credits;
    }

    @Override public String getRole() { return "CITIZEN"; }
    public int getCredits() { return credits; }
    public void addCredits(int pts) { this.credits += pts; }

    @Override public String toJson() {
        return String.format("{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"city\":\"%s\",\"role\":\"CITIZEN\",\"credits\":%d}",
                getId(), esc(getName()), esc(getEmail()), esc(getPhone()), esc(getCity()), credits);
    }
}

/** Certified Recycler actor who claims and safely neutralizes e-waste. */
class Recycler extends User {
    public Recycler(int id, String name, String email, String phone, String city) {
        super(id, name, email, phone, city);
    }

    @Override public String getRole() { return "RECYCLER"; }
    @Override public String describe() {
        return super.describe() + " [Certified E-Waste Facility]";
    }
}

/** Hazard severity level with priority weights. */
enum HazardLevel {
    LOW(1, "Low (Cables/Peripherals)"),
    MEDIUM(2, "Medium (Circuit Boards/Smartphones)"),
    HIGH(3, "High (Cathode Ray Tube / Heavy Metals)"),
    CRITICAL(4, "Critical (Swollen Lithium-Ion / Fire Hazard)");

    private final int weight;
    private final String label;

    HazardLevel(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    public int getWeight() { return weight; }
    public String getLabel() { return label; }

    public static HazardLevel fromString(String val) {
        if (val == null) return LOW;
        try {
            return HazardLevel.valueOf(val.trim().toUpperCase());
        } catch (Exception e) {
            return LOW;
        }
    }
}

/** E-Waste scrap item model implementing Comparable for Hazard-Priority Queue. */
class EWasteItem implements Comparable<EWasteItem> {
    int id;
    int citizenId;
    String category;
    String itemName;
    int quantity;
    HazardLevel hazardLevel = HazardLevel.LOW;
    String status = "AVAILABLE"; // AVAILABLE, SCHEDULED, RECYCLED
    String citizenName;
    String citizenCity;
    String citizenPhone;

    /**
     * Natural Ordering: Critical & High hazard items come FIRST (descending weight)
     * to eliminate fire and toxic chemical risks as emergency priority.
     */
    @Override
    public int compareTo(EWasteItem o) {
        return Integer.compare(o.hazardLevel.getWeight(), this.hazardLevel.getWeight());
    }

    @Override
    public String toString() {
        return String.format("#%-3d %-26s [%-8s] qty:%-3d | %s (%s, %s)",
                id, itemName, hazardLevel.name(), quantity, citizenName, citizenCity, citizenPhone);
    }

    public String toJson() {
        return String.format(
            "{\"id\":%d,\"citizenId\":%d,\"category\":\"%s\",\"itemName\":\"%s\",\"quantity\":%d,\"hazard\":\"%s\",\"weight\":%d,\"status\":\"%s\",\"citizen\":\"%s\",\"city\":\"%s\",\"phone\":\"%s\"}",
            id, citizenId, User.esc(category), User.esc(itemName), quantity, hazardLevel.name(),
            hazardLevel.getWeight(), status, User.esc(citizenName), User.esc(citizenCity), User.esc(citizenPhone)
        );
    }
}

/** Custom domain checked exception */
class EcoException extends Exception {
    public EcoException(String message) { super(message); }
}
