# MoveWise — Architecture Documentation

## 🏗 System Overview

MoveWise follows an **offline-first, observer-driven architecture** built around a single Android `Activity` with interchangeable `Fragment`s. All persistent state flows through a central singleton (`DataRepository`) that writes to `SharedPreferences` first and asynchronously syncs to Firebase Realtime Database.

---

## 📐 Architectural Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer (Fragments)                      │
│   DashboardFragment · WorkoutFragment · NutritionFragment        │
│   ChatBotFragment · ProgressFragment · SettingsFragment          │
└────────────────────────────┬────────────────────────────────────┘
                             │ DataRepository.DataListener
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     DataRepository (Singleton)                   │
│   • SharedPreferences (local, synchronous, offline-first)        │
│   • Firebase RTDB (async cloud sync via ValueEventListener)      │
│   • Observer list (DataListener) — notifies all registered UIs   │
└────────────────────────────┬────────────────────────────────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
   Firebase Realtime Database       SharedPreferences
   (users/{uid}/...)               (MoveWiseData_{uid})
```

**Data Flow:**
1. Any fragment **writes** via `DataRepository` → saves to SharedPrefs → syncs to Firebase → calls `notifyListeners()`.
2. All registered fragments receive `onDataChanged()` and **re-render** from the repository.
3. On app launch, `syncFromFirebase()` pulls the user's cloud node and writes it to SharedPrefs, so local reads always reflect the latest cloud state.

---

## 📂 Core Components

### 1. Entry Points

| Class | Role |
|---|---|
| `SplashActivity` | Animated splash with logo bounce + slide-in. Auto-routes to `MainActivity` (logged-in user) or `AuthActivity` (new user) after 2.5 s. Uses Firebase `currentUser` check. |
| `AuthActivity` | Hosts `LoginFragment` and `SignUpFragment` for Firebase Email/Password authentication. |
| `MainActivity` | Single-activity host. Contains a `FrameLayout` container and a `BottomNavigationView`. Manages Fragment backstack and exposes `openPersonalization()` for the chatbot persona sheet. |

---

### 2. Data Layer (`com.example.movewise.model`)

#### `DataRepository` — Single Source of Truth

- Initialized once per session with `DataRepository.init(context, uid)`.  
- Accessed globally via `DataRepository.getInstance()`.
- `DataRepository.reset()` called on logout to allow re-initialization for the next user.
- Uses **scoped SharedPreferences** per user UID: `"MoveWiseData_$uid"` — prevents data bleed between accounts on the same device.

**Stored Data Keys:**

| Key Pattern | Type | Description |
|---|---|---|
| `steps_{epoch_day}` | Float | Steps for a specific calendar day |
| `step_history` | JSON Map | All-time step history (day string → steps) |
| `meals` | JSON List\<Meal\> | All logged meals |
| `workouts` | JSON List\<WorkoutLog\> | All workout sessions |
| `weight_history` | JSON List\<WeightLog\> | Weight log entries |
| `chat_messages` | JSON List\<ChatMessage\> | Full conversation history with AI |
| `persona` | JSON ChatBotPersona | Current coach persona settings |
| `daily_streak` | Int | Current consecutive-day streak |
| `streak_last_update` | Long | Epoch day of last streak update |
| `water_{epoch_day}` | Int | Water intake (ml) for today |

> **ClassCastException Guard:** Firebase RTDB returns numeric types as `Long`. `DataRepository` includes defensive casting in `getStreakSafe()` and `getWaterIntake()` to silently coerce `Long` → `Int` and rewrite as `Int` for future reads.

#### `ChatRepository`

Thin wrapper over `DataRepository` providing:
- `getMessages()` / `addMessage()` — delegates to `DataRepository` for persistence.
- `getPersona()` / `updatePersona()` — reads/writes `ChatBotPersona`.
- `getAIResponse()` — creates a `GeminiApiClient` instance and calls `getChatResponse()` with the current persona and full message history.

#### `GeminiApiClient`

- Sends requests to the **Gemini 2.5 Flash** REST endpoint via OkHttp.
- Builds a `contents` JSON array with:
  1. A synthetic user/model persona-injection pair (system-prompt workaround for the v1 API).
  2. All past `ChatMessage` entries converted to the appropriate role (`"user"` / `"model"`).
  3. The new user message.
- Returns the first candidate's text, or a descriptive error string on failure.
- Timeouts: 30 s connect, 30 s read.

#### `NutritionApiClient`

Three-tier nutrition lookup strategy:

```
Camera/ML Kit classification → food name string
        │
        ▼
[Tier 1] Open Food Facts Search API (world.openfoodfacts.org) 
        │ on failure / empty result
        ▼
[Tier 2] Open Food Facts Category API (us.openfoodfacts.org/api/v2/search)
        │ on failure / empty result
        ▼
[Tier 3] Hardcoded fuzzy-match dictionary (~15 common foods)
```

Barcode scanning uses a dedicated `getNutritionByBarcode(barcode)` method that calls the product endpoint directly (`/api/v0/product/{barcode}.json`).

---

### 3. Controller Layer (`com.example.movewise.controller`)

#### `DashboardFragment`
- Registers `SensorEventListener` for `TYPE_STEP_COUNTER` hardware sensor.
- Displays: daily steps, total calories (from meals), active minutes (from workouts × today filter), total reps today, workout count, daily streak, 7-day step chart (MPAndroidChart `LineChart`), recent 3 workout sessions.
- `generateHealthInsight()` produces a rule-based motivational message based on step count and active minutes.
- Implements `DataRepository.DataListener` → calls `updateDashboardData()` on any data change.

#### `WorkoutFragment` — AI Fitness Camera

The most complex component. Key subsystems:

**Exercise Classifier (`classifyExercise`):**
Uses body landmark geometry heuristics to identify 9 exercises without any ML model for classification:

| Exercise | Detection Heuristic |
|---|---|
| Push-ups | Torso horizontal (hip-shoulder X-delta > Y-delta × 0.6) |
| Squats | Avg knee angle (hip-knee-ankle) < 155° |
| Lunges | Left/right knee angle difference > 35° with one < 140° |
| Jumping Jacks | Wrists above shoulders AND ankle spread > shoulder width |
| Overhead Press | Wrists above shoulders, wrists close together |
| Lateral Raises | Wrists at shoulder height, wrists widely spread |
| Bicep Curls | Elbow angle (shoulder-elbow-wrist) < 110° |
| Planks | Body slope < 0.5 and body angle > 150° |
| General Workout | Default fallback; wrist-height based rep counting |

**Voting Buffer:** Classification results are accumulated in a 40-frame rolling buffer. The majority label wins, preventing flickering on ambiguous poses.

**Rep Counter (`countRep`):** Each exercise uses a state-machine approach (`isUpPosition` flag) against calibrated angle thresholds. Returns `(Boolean repCounted, String feedbackText)`.

**Form Score (`updateFormScore`):** Computed per exercise based on joint angle depth; displayed as a color-coded `MaterialCardView` overlay (green ≥ 80%, amber ≥ 50%, red < 50%).

**Text-to-Speech Coach:** Announces rep milestones (every 5, every 10) with randomized motivational phrases. Plank holds are announced every 10 seconds.

**Camera:** Uses CameraX with `AccuratePoseDetectorOptions` in `STREAM_MODE`. Supports front/back camera toggling mid-session.

#### `NutritionFragment`
- Dual scanner entry points: `openScannerDialog()` (food camera + TFLite) and `openBarcodeScannerDialog()` (ML Kit barcode).
- Both dialogs reuse `dialog_camera_scanner.xml`.
- After scan, `showAddMealDialog()` pre-fills nutrition data; user can override before saving.
- Daily calorie goal hardcoded at **2,500 kcal**. Macro goals: Protein 150 g, Carbs 300 g, Fat 70 g.
- Burned calorie estimate: `activeMinutes × 8 kcal`.
- Water tracker logs in 250 ml increments against a 2,400 ml daily goal.

#### `ChatBotFragment`
- `ChatAdapter` handles two view types: user messages (right-aligned) and AI messages (left-aligned).
- Typing indicator: a `"..."` placeholder message inserted before the coroutine finishes; removed and replaced with the actual response.
- Persona button opens `ChatPersonalizationFragment` via `MainActivity.openPersonalization()`.

#### `ProgressFragment`
- `LineChart` for weight trend (all-time, indexed).
- `PieChart` for workout type breakdown (grouped by `WorkoutLog.type`).

---

### 4. Utility Layer (`com.example.movewise.util`)

#### `PoseGraphicOverlay`
- Custom `View` that receives `Pose` objects from ML Kit.
- Draws colored circles at each landmark and lines connecting joints using a predefined skeleton graph.
- Correctly scales landmark coordinates from image space to view space, handling both portrait and landscape rotations.
- Supports mirror mode for front-camera selfie view.

#### `ImageClassifierHelper`
- Wraps a TFLite model (`mobilenet_v1_1.0_224_quant.tflite` from assets) using TensorFlow Lite Interpreter.
- Processes `ImageProxy` frames: rotates, crops to square, resizes to 224×224, and runs inference.
- Returns the top-1 label string from the 1,001-class ImageNet label list.
- Used by `NutritionFragment` for live food recognition before capture.

#### `MarkdownRenderer`
- Parses basic Markdown from Gemini responses (bold `**text**`, bullet `- item`, headers `#`, line breaks).
- Converts to Android `SpannableStringBuilder` with `StyleSpan` and `BulletSpan`.
- Applied to AI chat messages for a premium reading experience.

---

## 🤖 AI Integration Details

### Gemini 2.5 Flash (Chatbot)

```
POST https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent
```

**System Prompt Injection Pattern** (v1 API workaround):
```json
[
  { "role": "user",  "parts": [{ "text": "You are {name}, an elite coach. Tone: {tone}. Focus: {focus}..." }] },
  { "role": "model", "parts": [{ "text": "Understood! I'll act as your {name} assistant." }] },
  { "role": "user",  "parts": [{ "text": "<previous message 1>" }] },
  { "role": "model", "parts": [{ "text": "<previous response 1>" }] },
  ...
  { "role": "user",  "parts": [{ "text": "<current user message>" }] }
]
```

### ML Kit Pose Detection

- **Model:** Accurate (full-body, higher latency, better joint position accuracy).
- **Mode:** `STREAM_MODE` (optimized for video frames, uses previous frame to speed up detection).
- **Confidence threshold:** 0.5 for major landmarks, 0.4 for extremities (wrists, knees, ankles, elbows), 0.3 for wrists in some exercise-specific paths.

---

## 🔐 Security & Data Isolation

| Concern | Implementation |
|---|---|
| **User Data Isolation** | SharedPreferences scoped to `"MoveWiseData_{uid}"`. Firebase paths scoped to `users/{uid}/`. |
| **Session Management** | Firebase Auth manages tokens. `SplashActivity` checks `FirebaseAuth.getInstance().currentUser` for auto-login. |
| **Logout** | `DataRepository.reset()` nullifies the singleton; next `init()` call with a new UID creates a fresh scoped instance. |
| **API Key** | Gemini API key hardcoded in `GeminiApiClient.kt`. **Recommended:** move to a server-side proxy for production. |

---

## 🔄 Data Lifecycle

```
App Start
  └── SplashActivity → checks currentUser
        ├── Logged in  → MainActivity → DataRepository.init(uid)
        │                              └── syncFromFirebase() pulls cloud → SharedPrefs
        └── Not logged in → AuthActivity → Login/Signup → Firebase Auth → MainActivity
                                                                          └── DataRepository.init(uid)

Logout
  └── SettingsFragment.btn_logout → DataRepository.reset() → FirebaseAuth.signOut() → AuthActivity
```
