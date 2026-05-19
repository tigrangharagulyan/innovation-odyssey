Here is a comprehensive, production-ready `README.md` file designed for your project repository. It serves as the official Game Design Document (GDD) and architectural blueprint. Your developers can drop this straight into the root folder of the project to align everyone on the mechanics, economy math, and technical implementation steps.

---

# Innovation Odyssey — Game Design Document & Prototype Specification

`Innovation Odyssey` is a mobile/web hybrid-casual idle physics game designed and engineered during a 48-hour hackathon sprint. Built natively in Java leveraging the **LibGDX** framework and **Box2D** physics engine, it compiles directly to web browsers (HTML5/WebGL) via **TeaVM**.

The core premise shifts away from passive progress bars toward high-velocity, visually chaotic physics simulations that serve as a progression gateway for an interstellar space colonization strategy layer.

---

## 🌌 1. System Architecture & Core Loop

The game relies on a clear loop that isolates high-CPU physics design setups from lightweight passive processing stages, guaranteeing smooth 60 FPS gameplay on mobile web browsers.

```
 +------------------------+      Locks JPS Snapshot     +-----------------------+
 |  1. ENGINEERING BAY    | --------------------------> |   2. BRIDGE FLIGHT    |
 | (Design Physics Engine)| <-------------------------- |  (Passive Idle Jump)  |
 +------------------------+   Insufficient Energy       +-----------------------+
             ^                                                      |
             | Unlocks New Tiers / Prestige Reset                   | Milestone Reached
             +------------------------------------------------------+
                             3. GALACTIC MAP (Prestige)

```

### The Three-Tab State Machine

1. **Engineering Bay (The Lab):** An active physics sandbox. The player spends energy to spawn dynamic ragdoll workers ("Interns") and position static, high-restitution acceleration nodes ("Bumpers") inside a rotating centrifuge drum. The screen monitors and averages raw kinetic/frictional impacts to output a master **Joules per Second (JPS)** power rating.
2. **Bridge Flight (The Mission):** A passive cinematic interface. When the player engages flight, the engine takes a immutable snapshot of the Lab's JPS rating. The ship travels between solar systems by consuming energy at this fixed snap rate. Active physics loops are completely paused here to eliminate CPU/battery strain.
3. **Galactic Map (The Evolution):** The progression hub. Players select upcoming planet targets. Every planet possesses independent gravity and atmospheric values that completely redefine the behavior of the Box2D engine room, forcing players to return to the Lab to optimize their setups.

---

## 🛠️ 2. Core Mechanics & Technical Specification

### A. The Kinetic Centrifuge Engine

Instead of a simple bounding box rectangle, the engine room is built as a true circular loop structure:

* **The Geometry:** Built using a 36-segment hollow circular `ChainShape` loop centered at local physics coordinates `(8.0f, 4.5f)` with a radius of `4.0` meters.
* **The Motion:** The container is instantiated as a `KinematicBody` or `StaticBody` executing a persistent rotational velocity via `body.setAngularVelocity(1.5f)`.
* **The Interactions:** Dynamic entities are pulled outward by centripetal forces and friction along the curved boundary wall before tumbling backward down the rotation stream.

### B. Workforce Tiers (The Interns)

Dynamic simulation entities are kept anatomically simple (2 to 3 linked rectangular fixtures per unit) to optimize rendering batch cycles under web assembly environments:

* **Tier 1 (Standard Intern):** Configured with Box2D body parameters: `Density = 1.0f`, `Restitution = 0.75f`. Identified with user data string `"INTERN_NORMAL"`.
* **Tier 2 (Cyber Intern):** Unlocked via progressive planetary system resets. Configured with: `Density = 2.5f`, `Restitution = 0.90f`. The increased structural mass causes highly erratic bouncing trajectories and exponentially multiplies the force vector calculations upon impact.

### C. Active Centrifuge Modifications (Bumpers)

Players actively manipulate the centrifuge interior by positioning static obstacle fixtures:

* **Placement Engine:** Handled via a programmatic trigger `spawnCentrifugeBumper(float angle, float radius)`. It maps polar inputs directly into Cartesian coordinates relative to the centrifuge center point.
* **Physics Blueprint:** Instantiated as a small `StaticBody` circle (`radius = 0.2f`) with an artificially accelerated rebound threshold (`Restitution = 1.4f`). Custom data identity is flagged as `"BUMPER"`.

---

## 📈 3. Mathematical Models & Economy Tuning

To drive immediate retention during a brief evaluation window, the economy model utilizes aggressive exponential curves optimized for a **3-5 minute first prestige cycle**.

### A. Incremental Scaling Curves

Purchasing additional assets drains the current energy repository and increases subsequent purchase thresholds exponentially.

* **Hiring Interns:** Cost increases by **25%** compound per unit:

$$\text{NextInternCost} = \lceil \text{CurrentInternCost} \times 1.25 \rceil$$


* **Constructing Bumpers:** Cost increases by **40%** compound per unit:

$$\text{NextBumperCost} = \lceil \text{CurrentBumperCost} \times 1.40 \rceil$$



### B. Rigid Optimization Safeguards

To ensure WebGL frame rendering never drops below 60 FPS on low-tier mobile hardware, physics simulations enforce an unyielding lifecycle safety roof:


$$\text{SimulatedBodyCap} = 30 \text{ Active Entities}$$

### C. Energy Generation Formula

Energy collection calculations happen entirely inside the Box2D `ContactListener` during `beginContact` updates using mass-velocity tracking:


$$\text{BaseEnergy} = ((M_A \times V_A) + (M_B \times V_B)) \times \frac{R_A + R_B}{2}$$

* **Bumper Acceleration Modifier:** If either intersecting body contains the user data tag `"BUMPER"`, an overclocking multiplier is applied to the collision result:

$$\text{FinalEnergy} = \text{BaseEnergy} \times 2.0$$



---

## 💾 4. Code Architecture & Blueprint Mapping

The project structure is split into clean, modular roles to ensure fast debugging.

```
src/com/odyssey/
│
├── OdysseyGame.java             # Main controller, skin constructor, screen transitions
├── GameState.java               # State machine enum definitions
├── ShipData.java                # Global economy Singleton (Joules, Multipliers, Costs)
├── DesktopLauncher.java         # Native desktop local testing execution setup
├── TeaVMLauncher.java           # Web compiler entry config for HTML5 web exports
│
├── physics/
│   └── EnergyContactListener.java  # Fast collision tracking and kinetic energy distribution
│
└── screen/
    ├── MainMenuScreen.java      # Dashboard hub linking tab paths
    ├── EngineeringLabScreen.java # Rotating centrifuge logic and Box2D simulation lifecycle
    ├── BridgeFlightScreen.java  # Isolated snapshot flight calculation time loop
    └── GalacticMapScreen.java   # Destination setup and multiplier data maps

```

### The Configuration Snapshot Strategy

To eliminate real-time resource conflicts (where players generate energy in the Lab and consume it on the Bridge simultaneously), data states are completely separated during flight:

```java
// Locked Execution Snapshot inside EngineeringLabScreen.java
btnFlight.addListener(new ChangeListener() {
    @Override 
    public void changed(ChangeEvent event, Actor actor) {
        // Capture a clean snapshot of energy production before cutting off physics processing
        ShipData.get().savedFlightJPS = ShipData.get().currentJPS; 
        game.transitionTo(GameState.BRIDGE_FLIGHT);
    }
});

```

---

## 🎨 5. Art Direction & Visual Polish ("The Juice")

Judges evaluate applications heavily on visual feedback. The game leaves the debug wireframe mode behind to incorporate modern, flashy arcade responses:

1. **The Pixels-to-Meters Bridge (`PPM = 64f`):** The physics coordinates operate on compact meters to preserve stable collision calculations. The drawing loop maps high-resolution neon texturing arrays across bodies by scaling metrics up by a factor of 64.
2. **Dynamic Screen Vibration:** High-value collision outputs over a baseline limit trigger a brief random offset translation vector in the camera's viewport matrix. This simulates mechanical strain in the engine room.
3. **Collision Particle Emission:** High-velocity contact events invoke immediate calls to LibGDX’s compact particle pooling engine. Neon flashes and lightbulb particle arrays explode directly at the contact manifold coordinate points (`contact.getWorldManifold().getPoints()[0]`) to make every impact feel incredibly satisfying.

---

## 🚀 6. Production Deployment Instructions

### Local Desktop Development Setup

Run the game locally on your computer for fast compilation, code hot-swapping, and instantaneous debugging:

```bash
./gradlew run

```

### Web Native Compilation Target

To generate the web application distribution build, execute the TeaVM transpiler task. This converts the compiled Java bytecode into highly-optimized, web-native JavaScript and WebAssembly:

```bash
./gradlew html:dist

```

This task outputs a portable `webapp/` folder containing an optimized `index.html` file and your compressed script bundles. Drag and drop this folder directly into **GitHub Pages**, **Vercel**, or **itch.io** to deploy your game instantly to any smartphone browser worldwide!
