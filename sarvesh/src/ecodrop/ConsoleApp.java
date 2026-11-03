package ecodrop;

import java.util.List;
import java.util.Scanner;

/** Command Shell Interface for EcoDrop (Java 8 to Java 26 compatible). */
public class ConsoleApp {
    public static void main(String[] args) {
        EcoService svc = new EcoService();
        Scanner sc = new Scanner(System.in);

        System.out.println("===============================================================");
        System.out.println("  ⚡ EcoDrop : Reverse Logistics E-Waste & Battery Recycling  ");
        System.out.println("  SDG-12 Clean-Tech Network (Java Pure OOP + Relational DBMS)  ");
        System.out.println("===============================================================");

        while (true) {
            System.out.println("\n--- MAIN MENU ---");
            System.out.println("1. Register User (Citizen / Recycler)");
            System.out.println("2. List All Users");
            System.out.println("3. Deposit E-Waste Item (Citizen)");
            System.out.println("4. Hazard-Priority Queue Search (Recycler)");
            System.out.println("5. Schedule Pickup Haul [ACID Row Lock] (Recycler)");
            System.out.println("6. Confirm Recycling & Award EcoCredits");
            System.out.println("7. View Green Impact & Carbon Metrics");
            System.out.println("0. Exit");
            System.out.print("> ");

            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1": {
                        String role = ask(sc, "Role (CITIZEN/RECYCLER)").toUpperCase();
                        String name = ask(sc, "Full Name / Organization");
                        String email = ask(sc, "Email");
                        String phone = ask(sc, "Phone");
                        String city = ask(sc, "City");
                        User u = "RECYCLER".equalsIgnoreCase(role)
                                ? new Recycler(0, name, email, phone, city)
                                : new Citizen(0, name, email, phone, city, 0);
                        int id = svc.register(u);
                        System.out.println("✔ Registered successfully! User ID: " + id);
                        break;
                    }
                    case "2": {
                        System.out.println("\nRegistered Users:");
                        for (String s : svc.listUsers()) {
                            System.out.println(s);
                        }
                        break;
                    }
                    case "3": {
                        int citId = Integer.parseInt(ask(sc, "Your Citizen ID"));
                        String cat = ask(sc, "Category (Li-ion Battery, Smartphones/PCB, Monitors, Cables/Mice)");
                        String item = ask(sc, "Item Name & Model");
                        int qty = Integer.parseInt(ask(sc, "Quantity"));
                        String hzStr = ask(sc, "Hazard Level (LOW, MEDIUM, HIGH, CRITICAL)").toUpperCase();
                        HazardLevel hz = HazardLevel.fromString(hzStr);

                        svc.depositItem(citId, cat, item, qty, hz);
                        System.out.println("✔ E-Waste listed in hazardous triage queue. Thank you!");
                        break;
                    }
                    case "4": {
                        String q = ask(sc, "Category keyword (Press Enter for all)");
                        String city = ask(sc, "Your Recycler City (for local proximity sorting)");
                        List<EWasteItem> queue = svc.searchHazardQueue(q, city);
                        if (queue.isEmpty()) {
                            System.out.println("No active e-waste scrap available in that category.");
                        } else {
                            System.out.println("\n--- HAZARD-PRIORITY QUEUE (CRITICAL FIRST & LOCAL CITY FIRST) ---");
                            for (EWasteItem it : queue) {
                                System.out.println(it);
                            }
                        }
                        break;
                    }
                    case "5": {
                        int recId = Integer.parseInt(ask(sc, "Recycler ID"));
                        int itemId = Integer.parseInt(ask(sc, "Item ID to schedule"));
                        svc.schedulePickup(recId, itemId);
                        System.out.println("✔ Haul successfully scheduled! Locked against race conditions.");
                        break;
                    }
                    case "6": {
                        int pickupId = Integer.parseInt(ask(sc, "Pickup Request ID to complete"));
                        svc.completeRecycling(pickupId);
                        System.out.println("✔ Recycling completed! Item marked RECYCLED and EcoCredits awarded to Citizen.");
                        break;
                    }
                    case "7": {
                        System.out.println("\n--- LIVE GREEN IMPACT METRICS ---");
                        for (java.util.Map.Entry<String, Integer> e : svc.getImpactStats().entrySet()) {
                            System.out.printf("• %-16s : %d%n", e.getKey(), e.getValue());
                        }
                        break;
                    }
                    case "0": {
                        System.out.println("Goodbye! Keep the planet clean.");
                        return;
                    }
                    default:
                        System.out.println("Invalid selection. Please choose 0-7.");
                        break;
                }
            } catch (EcoException e) {
                System.out.println("❌ Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("❌ Input Error: " + e.getMessage());
            }
        }
    }

    private static String ask(Scanner sc, String label) {
        System.out.print(label + ": ");
        return sc.nextLine().trim();
    }
}
