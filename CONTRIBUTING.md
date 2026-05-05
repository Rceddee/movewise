# Contributing to MoveWise

Thank you for considering contributing to MoveWise! This document covers the development setup, project conventions, and contribution workflow.

---

## 🛠 Development Environment Setup

### Requirements

| Tool | Required Version |
|---|---|
| Android Studio | Iguana (2023.2) or newer |
| JDK | 17 |
| Kotlin | 1.9+ (managed by Gradle) |
| Android Gradle Plugin | 8.x |
| Min SDK | API 24 (Android 7.0) |
| Target SDK | API 35 (Android 15) |

### Initial Setup

1. **Clone** the repository and open in Android Studio.
2. Let Gradle sync resolve all dependencies automatically.
3. Add your **Gemini API key** in `GeminiApiClient.kt` (line 14).
4. Add your `google-services.json` file to `app/`.
5. Connect a physical Android device (recommended) or start an API 24+ emulator.
6. Run the `app` configuration. Build should succeed on first attempt.

---

## 📁 Project Structure Conventions

```
app/src/main/java/com/example/movewise/
├── controller/   # All Fragment + Adapter classes (UI logic)
├── model/        # Data classes, repositories, API clients
└── util/         # Helpers, custom views, renderers
```

### Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Fragments | `[Feature]Fragment.kt` | `WorkoutFragment.kt` |
| Adapters | `[Item]Adapter.kt` | `MealAdapter.kt` |
| Repositories | `[Domain]Repository.kt` | `ChatRepository.kt` |
| API Clients | `[Domain]ApiClient.kt` | `GeminiApiClient.kt` |
| Data Classes | PascalCase noun | `Meal.kt`, `WorkoutLog` |
| Layout files | `fragment_[name].xml`, `item_[name].xml` | `fragment_nutrition.xml` |
| Resource IDs | `snake_case` with type prefix | `tv_calories`, `btn_scan_meal` |

---

## 🧱 Adding a New Feature

### New Screen (Fragment)

1. Create `[Feature]Fragment.kt` in `controller/`.
2. Create `fragment_[feature].xml` in `res/layout/`.
3. If the screen needs data, expose read/write methods in `DataRepository`.
4. Add navigation to/from the fragment via `parentFragmentManager.beginTransaction()` in the calling fragment or `MainActivity`.
5. Register as a `DataRepository.DataListener` if it needs to react to data changes.

### New Persisted Data Field

1. Add a `save[Field]()` and `get[Field]()` method pair to `DataRepository`.
2. Use a unique, stable key string for SharedPreferences.
3. Call `syncToFirebase(key, value)` after saving locally.
4. If the type is complex (object/list), serialize with `Gson` to a JSON string.
5. Add a ClassCastException guard if the type could arrive as `Long` from Firebase (numeric types).

### New AI Feature (Gemini)

1. Extend `GeminiApiClient` with a new `suspend fun` method.
2. Construct the `contents` array with appropriate roles.
3. Parse the response and return a typed result.
4. Call from a coroutine scope in the Fragment (`lifecycleScope.launch { ... }`).

---

## ✅ Code Quality Guidelines

- **Null safety:** Use Kotlin's `?.`, `?:`, and `!!` (only when null is truly impossible).
- **Coroutines:** Network and I/O operations must be inside `withContext(Dispatchers.IO)`. UI updates must occur on the main thread (`withContext(Dispatchers.Main)` or `activity?.runOnUiThread`).
- **Fragment lifecycle:** Always check `isAdded` before accessing `requireContext()` inside async callbacks. Unregister listeners in `onDestroyView()`.
- **Camera lifecycle:** Always call `ProcessCameraProvider.unbindAll()` before rebinding use cases, and shut down `ExecutorService` in `onDestroyView()`.
- **No hardcoded user data:** Always scope data access through `DataRepository` with the current user's UID — never write to a shared key.

---

## 🔑 API Keys & Secrets

- **Never commit real API keys** to version control.
- The `GeminiApiClient.kt` file contains a placeholder `API_KEY` constant. Contributors should replace it locally and add the file to `.gitignore` if modifying sensitive paths, or use a `local.properties` approach for team environments.
- Firebase config is stored in `google-services.json` which is already gitignored by the Android `.gitignore` template.

---

## 🐛 Reporting Issues

When filing a bug report, please include:
- Device model and Android version.
- Steps to reproduce.
- Expected vs. actual behavior.
- Logcat output (filtered by tag `MoveWise` or `DataRepository`).

---

## 📦 Dependencies Reference

| Library | Version | Purpose |
|---|---|---|
| Firebase BOM | (managed) | Firebase BoM for aligned versions |
| Firebase Auth | BOM | User authentication |
| Firebase Realtime DB | BOM | Cloud data sync |
| ML Kit Pose Detection | 18.0.0-beta5 | Workout tracking |
| ML Kit Pose Detection Accurate | 18.0.0-beta5 | High-precision pose landmarks |
| ML Kit Barcode Scanning | 17.2.0 | Product barcode lookup |
| TensorFlow Lite | (libs) | On-device food classification |
| TFLite Support | (libs) | TFLite image processing utilities |
| CameraX (core/camera2/lifecycle/view) | (libs) | Camera preview and capture |
| MPAndroidChart | (libs) | Line and Pie charts |
| OkHttp | 4.11.0 | HTTP networking |
| Kotlin Coroutines Android | 1.7.3 | Async operations |
| Gson | 2.10.1 | JSON serialization |
| Material Components | 1.11.0 | Material 3 UI components |
