# GlyphFlow — Nothing Phone (3) Glyph Matrix Studio & Automation Engine

GlyphFlow is a 100% offline, local-only Android studio and real-time hardware automation engine designed for the Nothing Phone (3) 25×25 Glyph Matrix display. It empowers users to paint multi-frame 8-bit grayscale (0–255 brightness) LED matrix animations, wire system notifications and hardware telemetry to dynamic matrix actions, and inspect real-time execution latency via a live hardware twin and audit log.

## User Review & Critical Decisions

> [!IMPORTANT]
> All core architectural and product choices below incorporate your confirmed preferences from our clarification step.

- **Confirmed Decision 1 — Dual-Mode Hardware SDK Bridge & Live Hardware Twin**: The engine binds directly to Nothing's `GlyphMatrixManager` (`com.nothing.ketchum`) and `libs/GlyphMatrixSDK.aar` when running on Nothing OS hardware, and automatically falls back to an interactive, zero-latency on-screen **25×25 LED Hardware Twin Simulator** on standard Android devices and emulators.
- **Confirmed Decision 2 — Preloaded Starter Studio & Rule Templates**: On first launch, the local Room database is seeded with curated 25×25 matrix animations (Charging Pulse Wave, Matrix Rain, Cyber Eye, Equalizer Bars, Radar Sweep, Low Battery Alert) and ready-to-use IFTTT automation rules.
- **Confirmed Decision 3 — Power-User Utilities**: Includes an **Interactive Event Simulator** inside the Audit Log to fire synthetic notifications, battery changes, media states, and face-down sensor events; a system **Quick Settings Tile** to toggle the automation engine; and **Offline JSON Import/Export** for backing up and sharing frames, animations, and rules.

---

## 1. Overview & Core Concept

- **What It Does**: Combines a tactile 25×25 pixel-art & keyframe animation sequencer with an IFTTT-style event automation engine (`NotificationListenerService`, battery/charging broadcasts, media session observer, screen/face-down sensor detector) that drives the rear 25×25 LED matrix on Nothing Phone (3).
- **Target Audience / Persona**: Nothing OS enthusiasts, tinkerers, and power users who want granular, privacy-first control over their rear Glyph Matrix for app notifications, progress bars, charging indicators, and custom pixel art without cloud dependencies.
- **Key Value**: Sub-millisecond rule evaluation, per-dot 0–255 grayscale brightness control, anti-strobe cooldown/priority preemption, and transparent diagnostics showing why every event matched or was filtered.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Command Dashboard & Live Hardware Twin**:
   - Displays a collapsible **Live 25×25 Hardware Twin Header** showing real-time LED output, SDK session status (`HARDWARE BOUND` vs `TWIN SIMULATOR`), active rule preemption badge, master engine kill switch, and permission status pills (Notification Listener & Foreground Service).
   - Lists all automation rules ordered by priority, with instant On/Off toggles, cooldown badges, priority tags, and a one-tap **"Test Rule"** trigger.
2. **25×25 Matrix Studio & Multi-Frame Sequencer**:
   - **Interactive 25×25 Dot-Matrix Canvas**: Supports tap-to-paint, continuous drag-to-paint, per-dot brightness brush slider (`0–255` with quick stops at `0`, `64`, `128`, `192`, `255`), and instant matrix transformation tools (**Clear**, **Invert**, **Shift Up/Down/Left/Right** with wrap-around, **Mirror Horizontal**, **Mirror Vertical**, and procedural pattern generators).
   - **Multi-Frame Sequencer & Playback Controls**: Horizontal keyframe filmstrip to add, duplicate, reorder (`◄` / `►`), or delete frames. Configurable frame duration (`25ms–2000ms`), loop mode (`1x` to `∞`), and transition curve (**Hard Cut** vs **Step Fade** interpolation).
   - **Save & Export**: Save creations with custom names and tags to Room, or export/import portable JSON payloads.
3. **Rule Creator & IFTTT Condition Builder**:
   - **Source Selector**: System Notification, Battery Telemetry, Media Playback, or System/Sensor State (Screen On/Off, Face Down, Timer/Alarm).
   - **Granular Filters**:
     - *Notifications*: Installed app package picker + custom package input, text matcher (`Title`, `Content`, `Subtext`) with operator (`Equals`, `Contains`, `Starts With`, `Regex`), and optional `EXTRA_PROGRESS` extraction.
     - *Telemetry & Sensors*: Battery threshold (`<`, `<=`, `==`, `>=`, `>`) & charging state, media status (`Playing`, `Paused`, `Track Changed`), or device posture (`Face Down` via accelerometer Z-axis + proximity).
   - **Matrix Action Configurator**: Choose **Single Frame** (duration ms or until condition clears), **Play Animation Sequence** (custom FPS/interval, loop count, transition curve), or **Dynamic Matrix Meter** (horizontal bar, vertical column fill, outer perimeter ring, or radial core fill driven dynamically by battery % or notification progress).
   - **Execution Governance**: Priority slider (`1–10`) for preemption and cooldown debounce timer (`0ms–60,000ms`) to prevent LED strobing.
4. **Live Audit Log & Interactive Event Simulator**:
   - Real-time rolling buffer (max 100 entries) displaying timestamp, ingested signal summary, matched rule ID & priority, matrix action rendered, peak/average brightness, and render latency in milliseconds.
   - Built-in **Signal Injector Drawer** allowing one-tap simulation of incoming notifications (including progress bars), battery level sweeps, media track changes, and face-down flips so users can verify rules immediately inside the app.

### Visual Identity & Theme (Nothing OS Industrial Aesthetic)
- **Aesthetic Direction**: Industrial dot-matrix brutalism inspired by Nothing OS 3.0—exposed technical grids, stark monochrome hierarchy, pill-shaped status tags, and surgical crimson accents.
- **Color Palette & Mood**:
  - `--background`: Pure OLED Black (`#000000`)
  - `--surface`: Deep Carbon (`#0D0D0F`) with `1dp` exposed border hairlines (`#222226`)
  - `--surface-elevated`: Technical Slate (`#16161A`)
  - `--primary` / `--accent`: Nothing Crimson Red (`#D71921`) for active recording, armed triggers, and high-priority indicators
  - `--led-glow`: Warm Pure White (`#FFFFFF`) scaled dynamically by alpha/luminance (`0..255`) against unlit matrix wells (`#18181C`)
  - `--text-primary`: Crisp White (`#F5F5F7`) & Technical Gray (`#8E8E93`)
- **Typography & Hierarchy**:
  - Bundled local Google Fonts via `font-util`: **Share Tech Mono** / **JetBrains Mono** for dot-matrix headers, telemetry counters, brightness readouts (`000–255`), and latency logs; **Space Grotesk** for clean geometric UI labels and rule cards.
- **Interactive Feedback & Motion**:
  - Custom `Canvas` 25×25 LED renderer with circular LED apertures, subtle bloom highlights on high-brightness dots (`>200`), and smooth 60fps step-fade interpolation between frames.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Reflective + Compile-Safe Hardware Wrapper (`GlyphHardwareController`)**
  - *Chosen Approach*: Implements a dual-layer hardware controller that checks for `libs/GlyphMatrixSDK.aar` and binds to `com.nothing.ketchum.GlyphMatrixManager`, `GlyphMatrixFrame.Builder`, and `GlyphMatrixObject` on Nothing OS devices while simultaneously publishing every rendered 25×25 frame buffer (`ByteArray` of size 625) to a reactive `StateFlow` consumed by the in-app Hardware Twin.
  - *Why*: Ensures 100% compatibility with physical Nothing Phone (3) hardware when the AAR/system service is present while allowing full end-to-end visual creation, rule execution, and latency profiling on any Android device or emulator.
- **Decision 2 — Compact 625-Byte Frame Encoding in Room**
  - *Chosen Approach*: Each 25×25 frame is stored as a contiguous 625-byte array (`0..255` unsigned brightness per cell) with custom Room `TypeConverters` (Base64/ByteArray) for single frames and multi-frame animation sequences.
  - *Why*: Eliminates relational join overhead during 60fps animation playback and keeps database reads/writes instantaneous.
- **Decision 3 — Priority Preemption & Cooldown Debouncer in Rule Engine**
  - *Chosen Approach*: Active matrix actions track their originating rule's priority (`1–10`) and expiration timestamp. Incoming triggers only preempt an active animation if their priority is greater than or equal to the currently rendering rule, and each rule tracks its `lastTriggeredAt` timestamp against its configured `cooldownMs`.
  - *Why*: Prevents rapid messaging apps or battery percentage ticks from causing chaotic LED strobing or locking up the Glyph Matrix session.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram
```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        SYSTEM & HARDWARE SIGNAL SOURCES                      │
│  ┌─────────────────────────┐  ┌──────────────────────┐  ┌─────────────────┐  │
│  │ GlyphNotificationService│  │ SystemBroadcasts     │  │ Sensor & Media  │  │
│  │ (NotificationListener)  │  │ (Battery / Screen /  │  │ (Face-Down Accel│  │
│  │ Package, Text, Progress │  │  Power / Alarm)      │  │  & MediaSession)│  │
│  └────────────┬────────────┘  └──────────┬───────────┘  └────────┬────────┘  │
└───────────────┼──────────────────────────┼───────────────────────┼───────────┘
                │                          ▼                       │
                └────────────────► ┌───────────────┐ ◄─────────────┘
                                   │ EventIngestBus│ (Thread-Safe SharedFlow)
                                   └───────┬───────┘
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     GLYPHFLOW AUTOMATION & HARDWARE CORE                     │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ RuleEvaluationEngine (Foreground Service + Quick Settings Tile)        │  │
│  │ • Global Kill Switch • Regex/Text/Numeric Match • Priority & Cooldown │  │
│  └───────────────┬────────────────────────────────────────┬───────────────┘  │
│                  ▼                                        ▼                  │
│  ┌───────────────────────────────┐        ┌───────────────────────────────┐  │
│  │ GlyphHardwareController       │        │ AuditDiagnosticsBuffer        │  │
│  │ • GlyphMatrixManager Session  │        │ • 100-Entry Ring Buffer       │  │
│  │ • Step-Fade & Dynamic Meter   │        │ • Render Latency & Match Logs │  │
│  │ • 25x25 Hardware Twin Flow    │        │ • Interactive Signal Injector │  │
│  └───────────────┬───────────────┘        └───────────────┬───────────────┘  │
└──────────────────┼────────────────────────────────────────┼──────────────────┘
                   ▼                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     LOCAL PERSISTENCE & JETPACK COMPOSE UI                   │
│  ┌─────────────────────────┐  ┌───────────────────────────────────────────┐  │
│  │ Room Database (Offline) │  │ Nothing OS Compose UI                     │  │
│  │ • GlyphAnimationEntity  │  │ • Dashboard & Live 25x25 Hardware Twin    │  │
│  │ • TriggerRuleEntity     │  │ • 25x25 Matrix Canvas & Keyframe Studio   │  │
│  │ • JSON Import / Export  │  │ • IFTTT Rule Builder & Audit Simulator    │  │
│  └─────────────────────────┘  └───────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **`GlyphFrame`**: Immutable `id`, `durationMs`, and `pixels: ByteArray` (length `625`, mapping `row * 25 + col` to unsigned brightness `0..255`).
- **`GlyphAnimation`**: `id`, `name`, `tags: List<String>`, `frames: List<GlyphFrame>`, `loopCount` (`0` = infinite, `1..N`), `transitionCurve` (`HARD_CUT`, `STEP_FADE`), `createdAt`, `isPreset`.
- **`TriggerRule`**: `id`, `name`, `isEnabled`, `priority` (`1..10`), `cooldownMs`, `conditionType` (`NOTIFICATION`, `BATTERY_LEVEL`, `CHARGING_STATE`, `MEDIA_PLAYBACK`, `SCREEN_STATE`, `FACE_DOWN`, `TIMER_ALARM`), structured condition parameters (target package, text match operator/pattern, numeric threshold), `actionType` (`SHOW_SINGLE_FRAME`, `PLAY_ANIMATION`, `DYNAMIC_METER`), `targetAnimationId`, `meterStyle` (`HORIZONTAL_BAR`, `VERTICAL_BAR`, `PERIMETER_RING`, `RADIAL_CORE`), and `durationMs`.
- **`AuditLogEntry`**: `id`, `timestamp`, `sourceType`, `sourceAppOrSystem`, `eventSummary`, `extractedProgress: Int?`, `matchedRuleId`, `matchedRuleName`, `actionTaken`, `renderLatencyMs`, and `status` (`TRIGGERED`, `COOLDOWN_SKIPPED`, `PREEMPTED`, `UNMATCHED`).

### Interactive Component & State Mapping
- **Canvas Paint & Transform State**: Touch pointer coordinates `(x, y)` inside the 25×25 `Canvas` map directly to cell index `row * 25 + col`, mutating the active frame's 625-byte buffer with the selected brush brightness (`0–255`). Transform buttons (**Shift**, **Mirror**, **Invert**, **Clear**) compute deterministic matrix permutations on the active frame and push undo snapshots.
- **Timeline Sequencer Playback**: A coroutine ticker runs at 60Hz when previewing; for `STEP_FADE`, intermediate frames linearly interpolate each pixel's brightness between `frame[i]` and `frame[i+1]` before dispatching to both the Compose preview canvas and `GlyphHardwareController`.
- **Rule Evaluation & Signal Simulator**: Both real OS callbacks (`GlyphNotificationListenerService`, `BroadcastReceiver`, `SensorEventListener`) and the Audit Log's **Interactive Signal Injector** emit normalized `SystemSignalEvent` payloads into `EventIngestBus`. The evaluator measures evaluation + frame dispatch time in microseconds/milliseconds and appends a rich `AuditLogEntry` to the diagnostics stream.

---

## 5. Architectural UI/UX Re-design: "Viewfinder & Dock" & Bidirectional Code-to-Visual Decompiler

### 1. Pinned Viewfinder & Categorized Bottom Dock (`MatrixStudioScreen.kt`)
- **Pinned Matrix Viewfinder (Zero Vertical Scrolling)**:
  - Fixed 25×25 circular matrix canvas (`Modifier.aspectRatio(1f)`) permanently pinned in the upper screen area.
  - Touching/dragging directly plots or erases dots on the 489 addressable physical circular LEDs (`(x-12)^2 + (y-12)^2 <= 156.25`).
  - Circular aperture boundary indicators with center crosshair guide `(12, 12)` toggle.
  - Real-time active LED counter (`XXX / 489 LEDS`), live mirror button, play/stop sequence loop, export GlyphScript, and Save & Apply.
- **Sequential Frame Timeline Strip (Directly below Viewfinder)**:
  - Horizontal filmstrip with frame thumbnails, `[+] Add`, `[Duplicate]`, `[Trash]`, onion skinning toggle (`20% ghosting`), frame duration slider (`30ms..2000ms`), and loop preview controls.
- **Categorized Bottom Dock Bar**:
  - Horizontal category tab bar: `DRAW`, `TRANSFORM`, `LAYERS`, `PARAMS`, `AI & PHOTO`.
  - Tool drawer expands into the bottom dock with independent vertical scrolling, ensuring the matrix canvas never scrolls off-screen.
  - **`DRAW` Tab**: Pen, Eraser, Line, Circle outline, Fill tool, dot intensity slider (0..255) with quick stops (0%, 20%, 50%, 100%), symmetry modes (`OFF`, `H-MIRROR`, `V-MIRROR`, `QUAD RADIAL`), Invert (489 circular LEDs), Clear, Row & Col operations.
  - **`TRANSFORM` Tab**: Directional Nudge D-Pad (Up, Down, Left, Right in 1-pixel steps), Bounds Auto-Clamping selector (`SAFE CLAMP` vs `APERTURE CLIP`), Mirror H, Mirror V, Rotate 90°, Undo.
  - **`LAYERS` Tab**: Active decompiled visual layers list, Z-order reordering handles, visibility toggle, per-layer brightness/opacity, Add Text/Ring layer buttons.
  - **`PARAMS` Tab**: Global anchor alignment (`"center"` vs `"top_center"`), frame duration, rule name input, Save & Apply button.
  - **`AI & PHOTO` Tab**: AI 2D bitmap & emoji rasterizer, Photo to Glyph 25×25 converter with contrast threshold, dithering, and inversion.

### 2. Code-to-Visual Decompiler (`GlyphScriptDecompiler.kt`)
- **"EDIT IN BUILDER" Action**:
  - Added to `AccordionRuleCard` in `DashboardScreen.kt`.
  - Parses AST into discrete timeline frames, visual text/ring/icon layers, and properties while preserving backend `EXTRACT` variables, regexes, and `BEHAVIOR` logic.
  - Bidirectional recompilation produces valid GlyphScript v2 with updated visual frame ASCII and layer parameters.
