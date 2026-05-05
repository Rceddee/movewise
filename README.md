# MoveWise — AI-Powered Health & Fitness Tracker

> **Kotlin · Material 3 · Gemini AI · ML Kit · Firebase**

MoveWise is a production-grade Android application that combines real-time AI coaching, computer-vision workout tracking, intelligent nutrition analysis, and gamified progress monitoring into a single, beautifully crafted experience.

---

## ✨ Feature Overview

| Feature | Description |
|---|---|
| 🏆 **AI Coach Chatbot** | Gemini 2.5 Flash–powered conversational coach with customizable persona (name, tone, focus area). Full conversation history injected per session. |
| 📷 **AI Workout Camera** | Real-time pose detection via ML Kit Accurate mode. Auto-classifies 9 exercises, counts reps with angle thresholds, tracks live Form Score (0–100%), and announces milestones via TTS. |
| 🥗 **Nutrition Scanner** | Dual-mode food logging: (1) camera + on-device TFLite classification → Open Food Facts lookup; (2) barcode scan → Open Food Facts product API. Manual entry always available as fallback. |
| 📊 **Smart Dashboard** | Live step counter (hardware sensor + manual entry), calories consumed, active minutes, total reps, daily streak, AI-generated health insight, and a 7-day step history chart (MPAndroidChart). |
| 📈 **Progress Analytics** | Weight trend line chart, workout type pie chart, and one-tap weight logging. |
| 🏅 **Badges & Streaks** | Gamified achievement system. Daily streak auto-maintains based on last active day. Badge gallery with unlock criteria. |
| 💧 **Water Tracker** | Tap-to-add water intake (250 ml increments, 2,400 ml daily goal). |
| ⚙️ **Settings** | Quick access to Recommendations, Badges, and one-tap logout (Firebase sign-out + data/singleton reset). |
| 🔐 **Authentication** | Firebase Email/Password Auth. Session persists across app restarts via auto-login on Splash screen. |

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **Min SDK / Target SDK** | 24 / 35 |
| **UI** | Material 3 · Fragment-based single-Activity architecture |
| **AI / ML** | Google Gemini 2.5 Flash (REST) · ML Kit Pose Detection (Accurate) · TensorFlow Lite (on-device food classifier) |
| **Nutrition Data** | Open Food Facts API (free, no auth required) |
| **Backend** | Firebase Authentication · Firebase Realtime Database |
| **Charts** | MPAndroidChart |
| **Networking** | OkHttp 4.11 · Kotlin Coroutines |
| **Serialization** | Gson 2.10.1 |
| **Camera** | AndroidX CameraX (camera2, lifecycle, view) |
| **Barcode** | ML Kit Barcode Scanning |
| **Storage** | SharedPreferences (offline-first) + Firebase RTDB (cloud sync) |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Iguana (2023.2) or newer
- JDK 17
- A free **Gemini API Key** from [Google AI Studio](https://aistudio.google.com/)
- A **Firebase project** with Authentication (Email/Password) and Realtime Database enabled

### Installation Steps

1. **Clone or download** the repository.
2. Open in **Android Studio** and let Gradle sync.
3. **Add your Gemini API key** in:
   ```
   app/src/main/java/com/example/movewise/model/GeminiApiClient.kt
   ```
   Replace the `API_KEY` constant value with your key.
4. **Firebase setup:**
   - Register your app in the [Firebase Console](https://console.firebase.google.com/).
   - Enable **Email/Password** authentication.
   - Enable **Realtime Database** and set rules to require authentication.
   - Download `google-services.json` and place it in `app/`.
5. **Build and run** on a physical device (recommended) or emulator with API 24+.

> **Note:** Camera-based features (Workout Tracking, Meal Scanning) perform significantly better on a real device due to hardware camera and sensor availability.

---

## 📂 Project Structure

```
app/src/main/java/com/example/movewise/
├── SplashActivity.kt          # Entry point; auto-login check + animated splash
├── AuthActivity.kt            # Authentication host activity
├── MainActivity.kt            # Main single-activity host with bottom nav
│
├── controller/                # UI layer (Fragments + Adapters)
│   ├── DashboardFragment.kt
│   ├── WorkoutFragment.kt
│   ├── NutritionFragment.kt
│   ├── ChatBotFragment.kt
│   ├── ProgressFragment.kt
│   ├── BadgesFragment.kt
│   ├── SettingsFragment.kt
│   ├── RecommendationsFragment.kt
│   ├── LoginFragment.kt / SignUpFragment.kt
│   ├── ChatPersonalizationFragment.kt
│   └── [Adapters: ChatAdapter, MealAdapter, BadgeAdapter, WorkoutHistoryAdapter]
│
├── model/                     # Data layer
│   ├── DataRepository.kt      # Singleton; SharedPrefs + Firebase sync
│   ├── ChatRepository.kt      # Chat-specific operations
│   ├── GeminiApiClient.kt     # Gemini REST API client
│   ├── NutritionApiClient.kt  # Open Food Facts wrapper + fuzzy fallback
│   ├── ChatMessage.kt / ChatBotPersona.kt
│   ├── Meal.kt / Badge.kt
│   └── [WorkoutLog / WeightLog defined in DataRepository.kt]
│
└── util/
    ├── PoseGraphicOverlay.kt  # Real-time skeleton renderer over camera preview
    ├── ImageClassifierHelper.kt # On-device TFLite food classifier
    └── MarkdownRenderer.kt    # Converts AI markdown to styled Spannables
```

---

## 📜 Documentation

| Document | Description |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture, data flow, AI integration details |
| [USER_GUIDE.md](USER_GUIDE.md) | End-user guide for all app features |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Developer setup, code conventions, and contribution guidelines |

---

## ⚠️ Known Limitations & Notes

- **Step Counter**: The hardware `TYPE_STEP_COUNTER` sensor is used in session-relative mode. The auto-increment from sensor events is currently commented out to prevent double-counting with manual entries; users should use the "+" button for accurate step logging.
- **Nutrition Data**: Open Food Facts data may be sparse for regional / packaged foods. The app falls back to a hardcoded fuzzy-match dictionary for ~15 common food types.
- **API Key Security**: The Gemini API key is stored in plain text in `GeminiApiClient.kt`. For a production release, move it to a secured backend proxy or Android Keystore mechanism.
- **Firebase Rules**: Ensure Realtime Database security rules are set to restrict each user's node to only their authenticated UID.
