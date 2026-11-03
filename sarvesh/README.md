# ⚡ EcoDrop – Smart E-Waste & Battery Reverse-Logistics Network

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![HTML5/CSS3](https://img.shields.io/badge/Frontend-HTML5%20%2F%20CSS3%20%2F%20JS-E34F26?style=for-the-badge&logo=html5&logoColor=white)](https://developer.mozilla.org/en-US/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)](LICENSE)

> A full-stack, dual-interface (Web + CLI) clean-tech sustainability system built for **2nd Year B.E. CSE** integrating core **Object-Oriented Programming (Java)** and **Database Management Systems (DBMS)**.

---

## 📌 Problem Statement
Households hoard obsolete electronics, dead microchips, and swollen lithium-ion batteries because municipal garbage trucks refuse to carry toxic e-waste. Meanwhile, certified recyclers lack steady feedstock for rare-earth metal recovery (lithium, cobalt, copper, gold). 
When disposed of improperly in regular landfills, swollen batteries cause severe landfill fires and leach heavy metals (lead, mercury, cadmium) into urban groundwater.

## 💡 The Solution: EcoDrop
EcoDrop bridges citizens and certified recycling facilities through a **Smart Hazard-Priority Reverse-Logistics Dispatcher**:
1. **Tier 1 (Hyperlocal Proximity):** Prioritizes scrap batches within the recycler's city to minimize hazardous transit distance.
2. **Tier 2 (Hazard-First Sorting):** Ranks listings by hazard severity (`CRITICAL` swollen Li-ion > `HIGH` CRT displays > `MEDIUM` PCBs > `LOW` cables) to neutralize catastrophic fire hazards first.
3. **Incentivization (EcoCredits):** Citizens earn carbon-offset points on verified safe neutralization.
4. **Anti-Hoarding & Race Protection:** Uses database-level row locking (`SELECT ... FOR UPDATE`) to prevent concurrent double-booking of hazardous hauls.

---

## 🏗️ Architecture & Syllabus Mapping

```
┌────────────────────────────────────────────────────────┐
│                   Frontend (Web / CLI)                 │
│       • index.html (Responsive SPA, Citizen/Recycler)  │
│       • ConsoleApp.java (Interactive Terminal Shell)   │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP REST / Standard I/O
┌───────────────────────────▼────────────────────────────┐
│                  Java Backend (JDK 17+)                │
│       • WebServer.java (Built-in HttpServer)           │
│       • EcoService.java (ACID Transactions, Locks)     │
│       • Models.java (Polymorphism, Inheritance)        │
└───────────────────────────┬────────────────────────────┘
                            │ JDBC Connection / Dual-Mode
┌───────────────────────────▼────────────────────────────┐
│                    DBMS Layer (MySQL)                  │
│       • Tables (users, ewaste_items, pickups, audit)   │
│       • Triggers (trg_prevent_self_pickup, complete)   │
│       • Views (v_active_scrap, v_green_impact)         │
│       • Stored Procedure (cleanup_stale_pickups)       │
└────────────────────────────────────────────────────────┘
```

### Academic Concept Mapping (2nd Year B.E. CSE)

| Subject | Academic Concept | Implementation in EcoDrop |
| :--- | :--- | :--- |
| **Java OOP** | **Abstraction** | `abstract class User` defines common contract with polymorphic `getRole()`. |
| **Java OOP** | **Inheritance** | `Citizen` and `Recycler` inherit state and behavior from `User`. |
| **Java OOP** | **Polymorphism** | Dynamic dispatch in `describe()`, `getRole()`, and `toJson()`. |
| **Java OOP** | **Encapsulation** | Private variables (`id`, `name`, `credits`) guarded by accessors. |
| **Java OOP** | **Interfaces & Sorting** | `EWasteItem implements Comparable<EWasteItem>` prioritizing hazard weights. |
| **Java OOP** | **Custom Exceptions** | `EcoException` provides domain-level checked error handling. |
| **DBMS** | **Relational Schema** | Normalized 3NF tables with PK, FK (`ON DELETE CASCADE`), `CHECK`, `UNIQUE`. |
| **DBMS** | **Active Triggers** | 1. `trg_prevent_self_pickup`: Prevents conflict-of-interest claims.<br>2. `trg_pickup_schedule`: Auto-locks inventory to `SCHEDULED`.<br>3. `trg_pickup_complete`: Transitions to `RECYCLED` & awards EcoCredits. |
| **DBMS** | **Concurrency & ACID** | `SELECT ... FOR UPDATE` acquires pessimistic row lock inside an explicit transaction (`setAutoCommit(false)`). |
| **DBMS** | **Views & Procedures** | `v_active_scrap` (risk ranking) and `v_green_impact` (city metrics). |

---

## 📂 Repository Structure

```text
EcoDrop/
├── src/
│   └── ecodrop/
│       ├── Models.java         # OOP Core (User, Citizen, Recycler, EWasteItem, HazardLevel)
│       ├── EcoService.java     # JDBC Layer: PreparedStatements, Transactions, Dual-Mode
│       ├── ConsoleApp.java     # Interactive Command Shell Application
│       └── WebServer.java      # Built-in Lightweight Web Server (Port 8080)
├── web/
│   └── index.html              # Modern Web Frontend (Auth Gate + Citizen/Recycler Dashboards)
├── schema.sql                  # Complete MySQL DDL (Tables, Triggers, Views, Seed Data)
├── run_console.bat             # 1-Click launcher for Terminal App
├── start_website.bat           # 1-Click launcher for Web Application
├── Dockerfile                  # Container definition for 1-Click Render Cloud Hosting
├── .gitignore                  # Git ignore rules
├── LICENSE                     # MIT License
└── README.md                   # Project Documentation
```

---

## 🚀 Quick Start Guide

### 1. Run the Command Shell Terminal App
```powershell
cd "C:\Users\Muhammad Naseem\.gemini\antigravity\scratch\EcoDrop"
.\run_console.bat
# Or via Java:
javac -d out src/ecodrop/*.java
java -cp out ecodrop.ConsoleApp
```

### 2. Run the Web Application
```powershell
cd "C:\Users\Muhammad Naseem\.gemini\antigravity\scratch\EcoDrop"
.\start_website.bat
# Or via Java:
java -cp out ecodrop.WebServer
```
Open **`http://localhost:8080`** in your browser.

---

## 👥 Demo Credentials (Viva 1-Click)
Pre-seeded for instant demonstration:

| Role | Name | Email / ID | City | Points |
| :--- | :--- | :--- | :--- | :--- |
| **Citizen** | Arjun Mehta | `arjun@mail.com` (ID: 1) | Bengaluru | 40 Pts |
| **Citizen** | Sneha Rao | `sneha@mail.com` (ID: 2) | Chennai | 20 Pts |
| **Recycler** | EcoRecycle Solutions | `info@ecorecycle.org` (ID: 3) | Bengaluru | Certified Facility |
| **Recycler** | CleanEarth Recyclers | `contact@cleanearth.in` (ID: 4) | Chennai | Certified Facility |

---

## 📄 License
Licensed under the [MIT License](LICENSE).
