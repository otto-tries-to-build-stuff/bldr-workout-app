# Phase 2 — AI Coach Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The user types a goal ("30 min legs, dumbbells only"), Claude Opus 5 drafts a structured workout, the user previews it and saves, regenerates or tweaks it — using their own API key entered in a Settings screen.

**Architecture:** Three pure, JVM-tested units in `ai/` (prompt builder, conversation, response mapper), one thin SDK wrapper (`CoachClient`), one Keystore-backed key store in `data/`, and two Compose screens (`settings/`, `coach/`) wired into the existing nav. Claude is constrained by **structured outputs** to one JSON schema; everything the schema cannot enforce is validated by the mapper.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2026.02.01), Room 2.8, `com.anthropic:anthropic-java:2.34.0` (brings OkHttp + Jackson), Android Keystore, JUnit4 + `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md` — §6.2 (Build sheet), §6.6 (Coach), §6.7 (Settings), §7 (API key storage), §9 (AI Coach), §11 (`CoachResponseMapper` tests). Read §9 in full before Task 3. Also read `docs/superpowers/STATUS.md` → "Phase 2 SDK viability spike".

## Global Constraints

- Model `claude-opus-5`; thinking `{"type": "adaptive"}`; `output_config.effort = "medium"`; `max_tokens` 16000; non-streaming.
- Structured outputs via `output_config.format` (JSON schema). **Never** parse prose or strip markdown fences.
- Server-side refusal fallback: body `"fallbacks": "default"` + header `anthropic-beta: server-side-fallback-2026-07-01`.
- `cache_control: {"type": "ephemeral"}` on the system block (the exercise library). The system text must be byte-identical across calls for the same library — sort, never include timestamps.
- API key: AES-GCM key in the Android Keystore, ciphertext in plain `SharedPreferences`; never logged, never in the database. `allowBackup="false"` is already set.
- `domain/` stays free of Android imports (`DomainPurityTest`). `ai/` must also have no Android imports except in `CoachClient.kt` (it has none either — SDK only).
- The tag vocabulary is not extensible: invented exercises' tags are filtered through `TagVocabulary.isValid`; bad tags are **dropped**, never fatal.
- Every touch target ≥ 48 dp (`Dimens.minTouchTarget`). No shadows (spec §5). Confirmation dialogs use `ConfirmDialog` (`ui/components/Controls.kt`).
- Gradle needs `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` in Git Bash on this machine.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Deliberately out of scope (spec §6.7 items deferred)

Settings in this plan holds **API key** and **Sound** only. The theme override and the keep-screen-on toggle are not needed to ship the Coach and are left for a later pass.

## Review Focus

1. **Coach returns an `exerciseId` that is not in the library** (hallucinated id) → treat the item as an invented exercise by `exerciseName`, never crash or drop it. *(Task 3 test.)*
2. **Coach invents an exercise whose name matches an existing one** ("push-up" vs library "Push-up") or invents the same name twice in one workout → reuse one exercise; never create duplicates in the Library. *(Task 3 tests.)*
3. **User saves the same Coach result twice, or saves after a Regenerate that re-invented the same move** → the second save must not duplicate custom exercises; ids for invented moves are derived from the name and upserted. *(Task 3 test on stable ids + Task 7 save path.)*
4. **Network drops or the key is revoked mid-session** → a clear message with Retry (or "Check your key" linking to Settings), and the typed prompt is still there. *(Task 5 classification + Task 7 state.)*
5. **User leaves the Coach screen while a generation is in flight** → no crash, no half-saved workout; nothing is written until the user taps Save. *(Task 7: request runs in `viewModelScope`; save is the only write.)*

---

## File Structure

| File | Responsibility |
|---|---|
| Modify `app/build.gradle.kts` | SDK dependency + `packaging` exclusions (Task 3) |
| Create `data/ApiKeyStore.kt` | Encrypt/decrypt/clear the key via Keystore (Task 1) |
| Create `ai/ApiKeyFormat.kt` | Pure: normalise + sanity-check a pasted key (Task 1) |
| Modify `WorkoutApp.kt` | Expose `apiKeys` (Task 1) |
| Create `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt` | Key entry, Sound toggle (Task 2) |
| Modify `ui/components/Controls.kt` | `AppTextField` gains `masked` and `singleLine` params (Task 2) |
| Modify `ui/AppNav.kt`, `ui/home/HomeScreen.kt` | Settings route + Home entry; Coach route (Tasks 2, 7) |
| Create `ai/CoachSchema.kt` | The JSON schema string (Task 3) |
| Create `ai/CoachResponseMapper.kt` | JSON → `Workout` + new custom `Exercise`s, or validation reasons (Task 3) |
| Create `ai/CoachPrompt.kt` | System prompt from library + vocabulary; user message from goal + chips (Task 4) |
| Create `ai/CoachConversation.kt` | Pure turn history for generate / regenerate / tweak (Task 4) |
| Create `ai/CoachClient.kt` | SDK call → `CoachReply` (Task 5) |
| Modify `data/ExerciseRepository.kt` | `upsertCustom(exercises)` (Task 6) |
| Create `ui/coach/CoachViewModel.kt`, `ui/coach/CoachScreen.kt` | The Coach flow (Tasks 6, 7) |
| Tests under `app/src/test/java/com/ottotech/workoutbuilder/ai/` | `ApiKeyFormatTest`, `CoachResponseMapperTest`, `CoachPromptTest`, `CoachConversationTest` |

All paths below are relative to `app/src/main/java/com/ottotech/workoutbuilder/` unless they start with `app/` or `docs/`.

---

### Task 1: API key storage

**Files:**
- Create: `ai/ApiKeyFormat.kt`, `data/ApiKeyStore.kt`
- Modify: `WorkoutApp.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/ai/ApiKeyFormatTest.kt`

**Interfaces:**
- Produces: `ApiKeyFormat.normalise(raw: String): String`, `ApiKeyFormat.looksValid(key: String): Boolean`; `class ApiKeyStore(context: Context)` with `fun get(): String?`, `fun set(key: String)`, `fun clear()`, `val hasKey: Boolean`; `WorkoutApp.apiKeys: ApiKeyStore`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ottotech.workoutbuilder.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiKeyFormatTest {
    @Test
    fun `normalise strips whitespace and line breaks from a pasted key`() {
        assertEquals("sk-ant-abc123", ApiKeyFormat.normalise("  sk-ant-abc\n123 \t"))
    }

    @Test
    fun `a key must start with sk-ant- and have a body`() {
        assertTrue(ApiKeyFormat.looksValid("sk-ant-api03-xyz"))
        assertFalse(ApiKeyFormat.looksValid("sk-ant-"))
        assertFalse(ApiKeyFormat.looksValid("sk-proj-xyz"))
        assertFalse(ApiKeyFormat.looksValid(""))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ApiKeyFormatTest"` — Expected: compilation FAIL, `Unresolved reference 'ApiKeyFormat'`.

- [ ] **Step 3: Implement `ai/ApiKeyFormat.kt`**

```kotlin
package com.ottotech.workoutbuilder.ai

/**
 * Pasting a key from a browser on a phone routinely drags in a trailing newline or a space
 * mid-string, so the key is normalised before it is stored. The check is a sanity check
 * against pasting the wrong thing, not validation — only the API can say a key is good.
 */
object ApiKeyFormat {
    private const val PREFIX = "sk-ant-"

    fun normalise(raw: String): String = raw.filterNot { it.isWhitespace() }

    fun looksValid(key: String): Boolean = key.startsWith(PREFIX) && key.length > PREFIX.length
}
```

- [ ] **Step 4: Run it to verify it passes** — same command, Expected: PASS.

- [ ] **Step 5: Implement `data/ApiKeyStore.kt`**

```kotlin
package com.ottotech.workoutbuilder.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's Anthropic API key, encrypted at rest.
 *
 * The AES key never leaves the Android Keystore; only the IV and ciphertext are written to
 * SharedPreferences. Deliberately not `EncryptedSharedPreferences` — its library is
 * deprecated. Never log the plaintext.
 *
 * If the Keystore key is lost (e.g. the user cleared credentials), decryption fails and the
 * stored ciphertext is discarded: the user simply re-enters the key.
 */
class ApiKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences("secure", Context.MODE_PRIVATE)

    val hasKey: Boolean get() = get() != null

    fun get(): String? {
        val stored = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun set(key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_CIPHERTEXT, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "anthropic_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_CIPHERTEXT = "api_key"
    }
}
```

- [ ] **Step 6: Wire it into `WorkoutApp.kt`** — add below `val workouts`:

```kotlin
    val apiKeys by lazy { ApiKeyStore(this) }
```

(add `import com.ottotech.workoutbuilder.data.ApiKeyStore`). The class comment says "four objects" — update it to "five".

- [ ] **Step 7: Build** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: all tests pass, build succeeds. (The Keystore round-trip is verified on the emulator in Task 2, Step 6 — it cannot run on the JVM.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ai/ApiKeyFormat.kt app/src/main/java/com/ottotech/workoutbuilder/data/ApiKeyStore.kt app/src/main/java/com/ottotech/workoutbuilder/WorkoutApp.kt app/src/test/java/com/ottotech/workoutbuilder/ai/ApiKeyFormatTest.kt
git commit -m "feat: store the Anthropic API key encrypted with the Android Keystore"
```

---

### Task 2: Settings screen (API key + Sound) and its Home entry

**Files:**
- Create: `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`
- Modify: `ui/components/Controls.kt` (`AppTextField`), `ui/AppNav.kt`, `ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `ApiKeyStore`, `ApiKeyFormat`, `AppPrefs.cuesMuted`.
- Produces: route `Routes.SETTINGS = "settings"`; `HomeScreen(onOpenSettings: () -> Unit, …)`; `AppTextField(…, masked: Boolean = false, singleLine: Boolean = true)`.

- [ ] **Step 1: Extend `AppTextField`** in `ui/components/Controls.kt` — add two parameters after `textStyle` and pass them to `BasicTextField`:

```kotlin
    masked: Boolean = false,
    singleLine: Boolean = true,
```

```kotlin
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = textStyle.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
```

Imports: `androidx.compose.ui.text.input.PasswordVisualTransformation`, `androidx.compose.ui.text.input.VisualTransformation`. When `singleLine = false` the field's `Row` must align content to the top: keep `verticalAlignment = Alignment.CenterVertically` for single-line only (`if (singleLine) Alignment.CenterVertically else Alignment.Top`).

- [ ] **Step 2: `ui/settings/SettingsViewModel.kt`**

```kotlin
package com.ottotech.workoutbuilder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.ai.ApiKeyFormat
import com.ottotech.workoutbuilder.data.ApiKeyStore
import com.ottotech.workoutbuilder.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val hasKey: Boolean,
    /** What the user is typing into the key field; never pre-filled with the saved key. */
    val draftKey: String = "",
    val draftError: String? = null,
    val soundOn: Boolean,
)

class SettingsViewModel(private val keys: ApiKeyStore, private val prefs: AppPrefs) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(hasKey = keys.hasKey, soundOn = !prefs.cuesMuted))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onDraftKey(text: String) = _state.update { it.copy(draftKey = text, draftError = null) }

    fun saveKey() {
        val key = ApiKeyFormat.normalise(_state.value.draftKey)
        if (!ApiKeyFormat.looksValid(key)) {
            _state.update { it.copy(draftError = "That doesn't look like an Anthropic key — they start with sk-ant-") }
            return
        }
        keys.set(key)
        _state.update { it.copy(hasKey = true, draftKey = "", draftError = null) }
    }

    fun removeKey() {
        keys.clear()
        _state.update { it.copy(hasKey = false) }
    }

    fun onSoundOn(on: Boolean) {
        prefs.cuesMuted = !on
        _state.update { it.copy(soundOn = on) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                SettingsViewModel(app.apiKeys, app.prefs)
            }
        }
    }
}
```

- [ ] **Step 3: `ui/settings/SettingsScreen.kt`** — `ScreenHeader("Settings", onBack)`, then a `LazyColumn` (screen padding, `Dimens.gap` spacing):

1. `SectionLabel("Anthropic API key")`.
2. If `state.hasKey`: a `HairlineCard` row "Key saved" (`AppType.cardTitle`) + muted "Stored encrypted on this phone" and a 48 dp "Remove" text button that opens `ConfirmDialog(title = "Remove your API key?", body = "The Coach won't work until you add one again.", confirmLabel = "Remove", dismissLabel = "Cancel", …)`.
3. Else: `AppTextField(value = state.draftKey, onValueChange = vm::onDraftKey, placeholder = "sk-ant-…", masked = true)`, the error line in `colors.textMuted` when `draftError != null`, an `InverseButton("SAVE KEY", height = Dimens.primaryButtonHeight, style = AppType.primaryButton, onClick = vm::saveKey)`, and a 48 dp text link "Get a key at console.anthropic.com" that fires `Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))`.
4. Muted explainer text: "The Coach sends your goal and the exercise library to Anthropic using this key. Usage is billed to your Anthropic account — roughly 5–15¢ per generated workout."
5. `SectionLabel("Sound")` then a 48 dp-tall row "Workout cues" with a Material3 `Switch(checked = state.soundOn, onCheckedChange = vm::onSoundOn)` coloured from `LocalAppColors` (checked track `surfaceInverse`, thumb `onSurfaceInverse`; unchecked track `surface`, thumb `textMuted`, border `border`).

- [ ] **Step 4: Nav + Home entry.** In `ui/AppNav.kt` add `const val SETTINGS = "settings"` and:

```kotlin
        composable(Routes.SETTINGS) {
            val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            SettingsScreen(viewModel = settingsVm, onBack = { nav.popBackStack() })
        }
```

Pass `onOpenSettings = { nav.navigate(Routes.SETTINGS) }` to `HomeScreen`. In `HomeScreen.Header()`, put the date overline in a `Row` with `SpaceBetween`, and at its end a 48 dp `Box` (same construction as `CardMenu`'s button) holding `Text("Settings", style = AppType.chip, color = colors.textMuted)` with `semantics { contentDescription = "Settings" }`. `Header` gains an `onOpenSettings: () -> Unit` parameter.

- [ ] **Step 5: Build and install** — `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: tests pass, installed.

- [ ] **Step 6: Verify on the emulator** (light and dark):
  - Home shows "Settings" top-right; tapping opens Settings.
  - Typing `hello` and Save shows the "doesn't look like" line and saves nothing.
  - Typing ` sk-ant-test-123 ` (with spaces) and Save flips to "Key saved".
  - `adb shell am force-stop com.ottotech.workoutbuilder`, reopen, Settings still says "Key saved" (Keystore round-trip works).
  - Remove → confirm → the field returns.
  - Sound off → start a workout → the in-workout mute button shows muted. Sound on restores it.
  - `adb shell run-as com.ottotech.workoutbuilder cat shared_prefs/secure.xml` shows base64 ciphertext, **not** `sk-ant-test-123`.

- [ ] **Step 7: Commit**

```bash
git add -A app/src/main/java/com/ottotech/workoutbuilder
git commit -m "feat: Settings screen with encrypted API key entry and a sound toggle"
```

---

### Task 3: SDK dependency, Coach schema and response mapper

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `ai/CoachSchema.kt`, `ai/CoachResponseMapper.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/ai/CoachResponseMapperTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface CoachResult {
      data class Ok(val workout: Workout, val newExercises: List<Exercise>) : CoachResult
      data class Invalid(val reasons: List<String>) : CoachResult
  }
  object CoachResponseMapper {
      fun map(json: String, library: List<Exercise>, newId: () -> String, now: Instant): CoachResult
      fun customIdFor(name: String): String   // "custom-" + slug
  }
  object CoachSchema { val JSON: String }
  ```

- [ ] **Step 1: Add the SDK** to `app/build.gradle.kts`. Under `dependencies`, after Room:

```kotlin
    // Official Anthropic SDK (Java; Kotlin uses it directly). Verified on Android 2026-09-24 —
    // see STATUS.md. Also supplies Jackson, which CoachResponseMapper parses with.
    implementation("com.anthropic:anthropic-java:2.34.0")
```

Inside `android { }`, after `buildFeatures`:

```kotlin
    // The SDK pulls in Apache httpclient5/httpcore5, whose duplicate META-INF files otherwise
    // fail mergeDebugJavaResource.
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST")
        }
    }
```

- [ ] **Step 2: `ai/CoachSchema.kt`** — spec §9 shape, `additionalProperties: false` everywhere, optional fields as `anyOf` with null, every property listed in `required` (structured outputs require all properties be required; optionality is expressed by the null branch):

```kotlin
package com.ottotech.workoutbuilder.ai

/**
 * The only shape the Coach may answer in (spec §9). Structured outputs enforce it, so the
 * mapper never sees prose. Numeric minimums are NOT expressible here — the mapper checks them.
 */
object CoachSchema {
    private fun nullable(type: String) = """{"anyOf":[{"type":"$type"},{"type":"null"}]}"""

    val JSON: String = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["name", "type", "sections"],
      "properties": {
        "name": {"type": "string"},
        "type": {"type": "string", "enum": ["Strength", "HIIT", "Mobility"]},
        "sections": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["name", "rounds", "restBetweenExercisesSec", "restBetweenRoundsSec", "items"],
            "properties": {
              "name": {"type": "string"},
              "rounds": {"type": "integer"},
              "restBetweenExercisesSec": ${nullable("integer")},
              "restBetweenRoundsSec": ${nullable("integer")},
              "items": {
                "type": "array",
                "items": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["exerciseId", "exerciseName", "primaryMuscle", "tags", "mode", "durationSec", "sets", "reps", "note"],
                  "properties": {
                    "exerciseId": ${nullable("string")},
                    "exerciseName": {"type": "string"},
                    "primaryMuscle": {"type": "string", "enum": ["Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Cardio", "Mobility", "Full body"]},
                    "tags": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["facet", "value"],
                        "properties": {
                          "facet": {"type": "string", "enum": ["TYPE", "EQUIPMENT", "MUSCLE", "PATTERN"]},
                          "value": {"type": "string"}
                        }
                      }
                    },
                    "mode": {"type": "string", "enum": ["TIMED", "REPS", "MANUAL"]},
                    "durationSec": ${nullable("integer")},
                    "sets": ${nullable("integer")},
                    "reps": ${nullable("integer")},
                    "note": ${nullable("string")}
                  }
                }
              }
            }
          }
        }
      }
    }
    """.trimIndent()
}
```

- [ ] **Step 3: Write the failing tests** — `CoachResponseMapperTest.kt`. A tiny two-exercise library and a JSON builder keep each test readable:

```kotlin
package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.Tag
import com.ottotech.workoutbuilder.domain.TrainingType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachResponseMapperTest {

    private val library = listOf(
        Exercise("push-up", "Push-up", Muscle.CHEST, demoQuery = "push-up form"),
        Exercise("goblet-squat", "Goblet Squat", Muscle.LEGS, demoQuery = "goblet squat form"),
    )
    private var n = 0
    private fun map(json: String) = CoachResponseMapper.map(json, library, { "id${n++}" }, Instant.EPOCH)

    private fun item(
        id: String? = "push-up", name: String = "Push-up", muscle: String = "Chest",
        tags: String = "[]", mode: String = "TIMED", duration: Int? = 30,
        sets: Int? = null, reps: Int? = null, note: String? = null,
    ) = """{"exerciseId":${id?.let { "\"$it\"" }},"exerciseName":"$name","primaryMuscle":"$muscle",
        "tags":$tags,"mode":"$mode","durationSec":$duration,"sets":$sets,"reps":$reps,
        "note":${note?.let { "\"$it\"" }}}"""

    private fun workout(vararg sections: String, type: String = "HIIT") =
        """{"name":"Leg Burner","type":"$type","sections":[${sections.joinToString(",")}]}"""

    private fun section(vararg items: String, rounds: Int = 1, restEx: Int? = null, restRound: Int? = null) =
        """{"name":"Block A","rounds":$rounds,"restBetweenExercisesSec":$restEx,
            "restBetweenRoundsSec":$restRound,"items":[${items.joinToString(",")}]}"""

    @Test
    fun `a valid response maps to a workout with library exercises`() {
        val result = assertIs<CoachResult.Ok>(map(workout(section(item(), rounds = 3, restEx = 15, restRound = 60))))
        val w = result.workout
        assertEquals("Leg Burner", w.name)
        assertEquals(TrainingType.HIIT, w.type)
        assertEquals(Instant.EPOCH, w.createdAt)
        val s = w.sections.single()
        assertEquals(3, s.rounds)
        assertEquals(Rest.Timed(15), s.restBetweenExercises)
        assertEquals(Rest.Timed(60), s.restBetweenRounds)
        assertNull(s.restAfterSection)
        val i = s.items.single()
        assertEquals("push-up", i.exerciseId)
        assertEquals(ItemMode.TIMED, i.mode)
        assertEquals(30, i.durationSec)
        assertTrue(result.newExercises.isEmpty())
    }

    @Test
    fun `reps items carry sets and reps, and the note passes through`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(mode = "REPS", duration = null, sets = 3, reps = 12, note = "slow down")))))
        val i = ok.workout.sections.single().items.single()
        assertEquals(3, i.sets); assertEquals(12, i.reps); assertEquals("slow down", i.note)
    }

    @Test
    fun `zero or null rests mean no rest`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(), restEx = 0, restRound = null))))
        assertNull(ok.workout.sections.single().restBetweenExercises)
        assertNull(ok.workout.sections.single().restBetweenRounds)
    }

    @Test
    fun `a null exerciseId invents a custom exercise with valid tags only`() {
        val tags = """[{"facet":"EQUIPMENT","value":"Kettlebell"},{"facet":"EQUIPMENT","value":"Sandbag"},
            {"facet":"MUSCLE","value":"Legs"},{"facet":"MUSCLE","value":"Core"}]"""
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(id = null, name = "Kettlebell Halo", muscle = "Legs", tags = tags)))))
        val ex = ok.newExercises.single()
        assertEquals("custom-kettlebell-halo", ex.id)
        assertEquals("Kettlebell Halo", ex.name)
        assertEquals(Muscle.LEGS, ex.primaryMuscle)
        assertTrue(ex.isCustom)
        assertEquals("kettlebell halo proper form", ex.demoQuery)
        // Sandbag is not in the vocabulary; MUSCLE:Legs repeats the primary. Both dropped.
        assertEquals(setOf(Tag(Facet.EQUIPMENT, "Kettlebell"), Tag(Facet.MUSCLE, "Core")), ex.tags)
        assertEquals(ex.id, ok.workout.sections.single().items.single().exerciseId)
    }

    @Test
    fun `an exerciseId not in the library is treated as invented by name`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(id = "made-up-id", name = "Bear Crawl", muscle = "Full body")))))
        assertEquals("custom-bear-crawl", ok.newExercises.single().id)
    }

    @Test
    fun `an invented name matching a library exercise reuses it`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(id = null, name = "  goblet squat ")))))
        assertTrue(ok.newExercises.isEmpty())
        assertEquals("goblet-squat", ok.workout.sections.single().items.single().exerciseId)
    }

    @Test
    fun `the same invented name twice creates one exercise`() {
        val a = item(id = null, name = "Bear Crawl", muscle = "Full body")
        val ok = assertIs<CoachResult.Ok>(map(workout(section(a, a))))
        assertEquals(1, ok.newExercises.size)
    }

    @Test
    fun `invented ids are stable across calls so re-saving does not duplicate`() {
        assertEquals(CoachResponseMapper.customIdFor("Bear Crawl!"), CoachResponseMapper.customIdFor("bear  crawl"))
    }

    @Test
    fun `empty sections are dropped`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(), section(item()))))
        assertEquals(1, ok.workout.sections.size)
    }

    @Test
    fun `validation failures are all reported`() {
        val bad = workout(
            section(
                item(duration = 0),
                item(mode = "REPS", duration = null, sets = 0, reps = null),
                item(name = " "),
                rounds = 0,
            ),
        )
        val invalid = assertIs<CoachResult.Invalid>(map(bad))
        assertTrue(invalid.reasons.any { "rounds" in it })
        assertTrue(invalid.reasons.any { "duration" in it })
        assertTrue(invalid.reasons.any { "sets" in it })
        assertTrue(invalid.reasons.any { "name" in it })
    }

    @Test
    fun `no exercises at all is invalid`() {
        assertIs<CoachResult.Invalid>(map(workout(section())))
        assertIs<CoachResult.Invalid>(map(workout()))
    }

    @Test
    fun `malformed JSON is invalid rather than a crash`() {
        assertIs<CoachResult.Invalid>(map("{not json"))
        assertIs<CoachResult.Invalid>(map("""{"name":"x"}"""))
    }
}
```

- [ ] **Step 4: Run to verify failure** — `./gradlew :app:testDebugUnitTest --tests "*CoachResponseMapperTest"`. Expected: compile FAIL, `Unresolved reference 'CoachResponseMapper'`.

- [ ] **Step 5: Implement `ai/CoachResponseMapper.kt`**

```kotlin
package com.ottotech.workoutbuilder.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.Tag
import com.ottotech.workoutbuilder.domain.TagVocabulary
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutItem
import java.time.Instant

sealed interface CoachResult {
    data class Ok(val workout: Workout, val newExercises: List<Exercise>) : CoachResult
    data class Invalid(val reasons: List<String>) : CoachResult
}

/**
 * Turns the Coach's structured-output JSON into domain objects (spec §9).
 *
 * Structured outputs guarantee the shape, not the numbers, so the numeric rules are checked
 * here and every failure is collected rather than stopping at the first. Exercises resolve in
 * order: a real library id → a library exercise with the same name → an invented custom
 * exercise, whose id is derived from its name so saving twice upserts instead of duplicating.
 */
object CoachResponseMapper {

    private val mapper = ObjectMapper()

    fun customIdFor(name: String): String =
        "custom-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun map(json: String, library: List<Exercise>, newId: () -> String, now: Instant): CoachResult {
        val root = try { mapper.readTree(json) } catch (e: Exception) { null }
            ?: return CoachResult.Invalid(listOf("The Coach's reply wasn't readable"))
        val sectionsNode = root.path("sections")
        val typeLabel = root.path("type").asText()
        val type = TrainingType.entries.firstOrNull { it.label == typeLabel }
        if (!sectionsNode.isArray || type == null || !root.hasNonNull("name")) {
            return CoachResult.Invalid(listOf("The Coach's reply was missing parts"))
        }

        val byId = library.associateBy { it.id }
        val byName = library.associateBy { it.name.normalisedName() }
        val invented = LinkedHashMap<String, Exercise>()
        val reasons = mutableListOf<String>()

        fun resolve(item: JsonNode): String {
            val id = item.path("exerciseId").textOrNull()
            if (id != null && id in byId) return id
            val name = item.path("exerciseName").asText().trim()
            byName[name.normalisedName()]?.let { return it.id }
            val customId = customIdFor(name)
            invented.getOrPut(customId) { invent(customId, name, item) }
            return customId
        }

        val sections = sectionsNode.mapIndexedNotNull { sIndex, s ->
            val label = "Section ${sIndex + 1}"
            val rounds = s.path("rounds").asInt(0)
            if (rounds < 1) reasons += "$label: rounds must be at least 1"
            val items = s.path("items").mapIndexed { iIndex, item ->
                val where = "$label, exercise ${iIndex + 1}"
                val mode = ItemMode.entries.firstOrNull { it.name == item.path("mode").asText() } ?: ItemMode.MANUAL
                val duration = item.path("durationSec").intOrNull()
                val sets = item.path("sets").intOrNull()
                val reps = item.path("reps").intOrNull()
                if (item.path("exerciseName").asText().isBlank()) reasons += "$where: missing a name"
                if (mode == ItemMode.TIMED && (duration == null || duration <= 0)) reasons += "$where: needs a duration"
                if (mode == ItemMode.REPS && (sets == null || sets <= 0 || reps == null || reps <= 0)) {
                    reasons += "$where: needs sets and reps"
                }
                WorkoutItem(
                    id = newId(),
                    exerciseId = if (item.path("exerciseName").asText().isBlank()) "" else resolve(item),
                    mode = mode,
                    durationSec = duration.takeIf { mode == ItemMode.TIMED },
                    sets = sets.takeIf { mode == ItemMode.REPS },
                    reps = reps.takeIf { mode == ItemMode.REPS },
                    note = item.path("note").textOrNull(),
                )
            }
            if (items.isEmpty()) return@mapIndexedNotNull null
            Section(
                id = newId(),
                name = s.path("name").asText().ifBlank { label },
                rounds = rounds.coerceAtLeast(1),
                restBetweenExercises = s.path("restBetweenExercisesSec").restOrNull(),
                restBetweenRounds = s.path("restBetweenRoundsSec").restOrNull(),
                items = items,
            )
        }
        if (sections.isEmpty()) reasons += "The workout has no exercises"
        if (reasons.isNotEmpty()) return CoachResult.Invalid(reasons)

        val workout = Workout(
            id = newId(),
            name = root.path("name").asText().ifBlank { "Coach workout" },
            type = type,
            createdAt = now,
            sections = sections,
        )
        return CoachResult.Ok(workout, invented.values.toList())
    }

    private fun invent(id: String, name: String, item: JsonNode): Exercise {
        val primary = Muscle.fromLabel(item.path("primaryMuscle").asText()) ?: Muscle.FULL_BODY
        val tags = item.path("tags").mapNotNull { t ->
            val facet = Facet.entries.firstOrNull { it.name == t.path("facet").asText() } ?: return@mapNotNull null
            Tag(facet, t.path("value").asText())
        }.filter { TagVocabulary.isValid(it) && !(it.facet == Facet.MUSCLE && it.value == primary.label) }
        return Exercise(
            id = id,
            name = name,
            primaryMuscle = primary,
            tags = tags.toSet(),
            demoQuery = "${name.lowercase()} proper form",
            isCustom = true,
        )
    }

    private fun String.normalisedName() = lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun JsonNode.textOrNull(): String? = if (isNull || isMissingNode) null else asText().ifBlank { null }
    private fun JsonNode.intOrNull(): Int? = if (isNull || isMissingNode) null else asInt()
    private fun JsonNode.restOrNull(): Rest? = intOrNull()?.takeIf { it > 0 }?.let { Rest.Timed(it) }
}
```

- [ ] **Step 6: Run the tests** — `./gradlew :app:testDebugUnitTest --tests "*CoachResponseMapperTest"`. Expected: PASS. Then the whole suite: `./gradlew :app:testDebugUnitTest` — all green (count was 144 before this plan).

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/ottotech/workoutbuilder/ai app/src/test/java/com/ottotech/workoutbuilder/ai/CoachResponseMapperTest.kt
git commit -m "feat: Coach response schema and mapper, with the Anthropic SDK dependency"
```

---

### Task 4: Prompt builder and conversation

**Files:**
- Create: `ai/CoachPrompt.kt`, `ai/CoachConversation.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/ai/CoachPromptTest.kt`, `…/ai/CoachConversationTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object CoachPrompt {
      fun system(library: List<Exercise>): String
      fun request(goal: String, minutes: Int?, equipment: Set<String>): String
  }
  data class Turn(val fromUser: Boolean, val text: String)
  data class CoachConversation(val turns: List<Turn>) {
      companion object { fun start(request: String): CoachConversation }
      fun withReply(json: String): CoachConversation
      fun tweak(instruction: String): CoachConversation   // requires last turn is a reply
      fun forRegenerate(): CoachConversation               // drops a trailing reply
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachPromptTest {
    private val a = Exercise("b-row", "Bent Row", Muscle.BACK, setOf(Tag(Facet.EQUIPMENT, "Barbell")), "row")
    private val b = Exercise("a-squat", "Air Squat", Muscle.LEGS, demoQuery = "squat")

    @Test
    fun `system prompt lists every exercise as id, name, muscle, tags`() {
        val s = CoachPrompt.system(listOf(a, b))
        assertTrue("b-row · Bent Row · Back · EQUIPMENT:Barbell" in s)
        assertTrue("a-squat · Air Squat · Legs" in s)
    }

    @Test
    fun `system prompt is identical regardless of library order, so it caches`() {
        assertEquals(CoachPrompt.system(listOf(a, b)), CoachPrompt.system(listOf(b, a)))
    }

    @Test
    fun `system prompt carries the tag vocabulary`() {
        val s = CoachPrompt.system(listOf(a))
        assertTrue("Pull-up bar" in s)
        assertTrue("Plyometric" in s)
    }

    @Test
    fun `request includes goal and only the chips that were chosen`() {
        val r = CoachPrompt.request("legs and core", minutes = 30, equipment = setOf("Dumbbell"))
        assertTrue("legs and core" in r)
        assertTrue("30 minutes" in r)
        assertTrue("Dumbbell" in r)
        val bare = CoachPrompt.request("anything", minutes = null, equipment = emptySet())
        assertFalse("minutes" in bare)
        assertFalse("Equipment" in bare)
    }
}
```

```kotlin
package com.ottotech.workoutbuilder.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoachConversationTest {
    @Test
    fun `tweak appends the reply and the instruction`() {
        val c = CoachConversation.start("goal").withReply("{json}").tweak("shorter")
        assertEquals(listOf(Turn(true, "goal"), Turn(false, "{json}"), Turn(true, "shorter")), c.turns)
    }

    @Test
    fun `regenerate drops only the trailing reply`() {
        val c = CoachConversation.start("goal").withReply("{a}").tweak("more core").withReply("{b}")
        assertEquals(3, c.forRegenerate().turns.size)
        assertEquals(Turn(true, "more core"), c.forRegenerate().turns.last())
    }

    @Test
    fun `tweak without a reply is a programming error`() {
        assertFailsWith<IllegalStateException> { CoachConversation.start("goal").tweak("x") }
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :app:testDebugUnitTest --tests "*CoachPromptTest" --tests "*CoachConversationTest"`. Expected: compile FAIL.

- [ ] **Step 3: Implement `ai/CoachPrompt.kt`**

```kotlin
package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.TagVocabulary

/**
 * The system prompt is the cached prefix: it must be byte-identical for the same library, so
 * exercises are sorted by id and tags by facet/value, and nothing time-dependent goes in.
 */
object CoachPrompt {

    fun system(library: List<Exercise>): String = buildString {
        appendLine("You are a strength and conditioning coach designing one workout for a single person.")
        appendLine("Build it from the exercise library below. Prefer existing exercises: set exerciseId to the library id.")
        appendLine("Only invent an exercise when nothing in the library fits; then set exerciseId to null, give it a clear")
        appendLine("common name, a primaryMuscle, and tags chosen only from the vocabulary below.")
        appendLine()
        appendLine("Structure: sections run in order; a section repeats its items for `rounds`. Use TIMED items with durationSec")
        appendLine("for intervals, REPS items with sets and reps for strength work, MANUAL for anything the person ends by tapping.")
        appendLine("Rests are whole seconds; use null for no rest. Put a warm-up first and keep the total near any time asked for.")
        appendLine("Use `note` for short form cues only.")
        appendLine()
        appendLine("Tag vocabulary (facet: values):")
        Facet.entries.forEach { appendLine("${it.name}: ${TagVocabulary.valuesFor(it).joinToString(", ")}") }
        appendLine()
        appendLine("Exercise library (id · name · primary muscle · tags):")
        library.sortedBy { it.id }.forEach { ex ->
            val tags = ex.tags.sortedWith(compareBy({ it.facet.ordinal }, { it.value }))
                .joinToString(" ") { "${it.facet.name}:${it.value}" }
            appendLine(listOf(ex.id, ex.name, ex.primaryMuscle.label, tags).filter { it.isNotEmpty() }.joinToString(" · "))
        }
    }

    fun request(goal: String, minutes: Int?, equipment: Set<String>): String = buildString {
        append("Goal: ").append(goal.trim())
        if (minutes != null) append("\nAbout $minutes minutes long.")
        if (equipment.isNotEmpty()) append("\nEquipment available: ").append(equipment.sorted().joinToString(", ")).append('.')
    }
}
```

- [ ] **Step 4: Implement `ai/CoachConversation.kt`**

```kotlin
package com.ottotech.workoutbuilder.ai

data class Turn(val fromUser: Boolean, val text: String)

/**
 * The message history sent to Claude. Tweak continues the conversation — the previous JSON
 * goes back as the assistant's turn, then the refinement — so Claude edits its own draft
 * rather than starting over. Regenerate re-asks the last user turn.
 */
data class CoachConversation(val turns: List<Turn>) {

    fun withReply(json: String) = CoachConversation(turns + Turn(fromUser = false, text = json))

    fun tweak(instruction: String): CoachConversation {
        check(turns.lastOrNull()?.fromUser == false) { "Tweak needs a reply to refine" }
        return CoachConversation(turns + Turn(fromUser = true, text = instruction.trim()))
    }

    fun forRegenerate(): CoachConversation =
        if (turns.lastOrNull()?.fromUser == false) CoachConversation(turns.dropLast(1)) else this

    companion object {
        fun start(request: String) = CoachConversation(listOf(Turn(fromUser = true, text = request)))
    }
}
```

- [ ] **Step 5: Run the tests** — same command. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ai app/src/test/java/com/ottotech/workoutbuilder/ai
git commit -m "feat: Coach prompt builder and conversation history"
```

---

### Task 5: Coach client (the SDK call)

**Files:**
- Create: `ai/CoachClient.kt`

**Interfaces:**
- Consumes: `CoachSchema.JSON`, `Turn`.
- Produces:
  ```kotlin
  sealed interface CoachReply {
      data class Json(val text: String) : CoachReply
      data class Refused(val message: String) : CoachReply
      data class Failed(val kind: FailureKind) : CoachReply
  }
  enum class FailureKind { NO_NETWORK, BAD_KEY, BUSY, OTHER }
  class CoachClient(apiKey: String) {
      fun warmUp()                                                    // blocking; call on IO
      fun generate(system: String, turns: List<Turn>): CoachReply     // blocking; call on IO
  }
  ```

No JVM unit test: this class is a thin adapter over the network. It is verified against the real API in Step 3 (bad key) and Task 7 (real key).

- [ ] **Step 1: Implement `ai/CoachClient.kt`**

```kotlin
package com.ottotech.workoutbuilder.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.fasterxml.jackson.databind.ObjectMapper

sealed interface CoachReply {
    data class Json(val text: String) : CoachReply
    data class Refused(val message: String) : CoachReply
    data class Failed(val kind: FailureKind) : CoachReply
}

enum class FailureKind { NO_NETWORK, BAD_KEY, BUSY, OTHER }

/**
 * One Coach request per call (spec §9): Claude Opus 5, adaptive thinking, medium effort,
 * structured output, cached system prompt, server-side refusal fallback. The SDK retries
 * 429/5xx/connection errors twice with backoff before an exception reaches us.
 *
 * Building the client and the first request pay a ~4 s one-time warm-up on Android (class
 * loading — see STATUS.md), so the screen calls [warmUp] when it opens.
 */
class CoachClient(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    private val schema: Map<String, JsonValue> =
        ObjectMapper().readValue(CoachSchema.JSON, Map::class.java)
            .map { (k, v) -> k as String to JsonValue.from(v) }.toMap()

    /** Touches the classes the real call needs without a network round trip. */
    fun warmUp() {
        buildParams("warm-up", listOf(Turn(true, "warm-up")))
    }

    fun generate(system: String, turns: List<Turn>): CoachReply = try {
        val response = client.messages().create(buildParams(system, turns))
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            val why = response.stopDetails().flatMap { it.explanation() }.orElse(null)
            CoachReply.Refused(why ?: "The Coach declined that request.")
        } else {
            // Not Stream.toList(): that is Java 16 / Android API 34, and minSdk is 26.
            val text = response.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
            if (text.isBlank()) CoachReply.Failed(FailureKind.OTHER) else CoachReply.Json(text)
        }
    } catch (e: UnauthorizedException) {
        CoachReply.Failed(FailureKind.BAD_KEY)
    } catch (e: PermissionDeniedException) {
        CoachReply.Failed(FailureKind.BAD_KEY)
    } catch (e: RateLimitException) {
        CoachReply.Failed(FailureKind.BUSY)
    } catch (e: AnthropicServiceException) {
        CoachReply.Failed(if (e.statusCode() >= 500) FailureKind.BUSY else FailureKind.OTHER)
    } catch (e: AnthropicIoException) {
        CoachReply.Failed(FailureKind.NO_NETWORK)
    }

    private fun buildParams(system: String, turns: List<Turn>): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model("claude-opus-5")
            .maxTokens(16000L)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(JsonOutputFormat.builder().schema(JsonOutputFormat.Schema.builder().putAllAdditionalProperties(schema).build()).build())
                    .build(),
            )
            .systemOfTextBlockParams(
                listOf(TextBlockParam.builder().text(system).cacheControl(CacheControlEphemeral.builder().build()).build()),
            )
            // Server-side refusal fallback (spec §9). Sent as raw header/body because the
            // typed builder may not expose it yet.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        turns.forEach { if (it.fromUser) builder.addUserMessage(it.text) else builder.addAssistantMessage(it.text) }
        return builder.build()
    }
}
```

> **Compiler-guided names.** The builder and exception names above come from the skill's Java reference; if one does not compile, fix it from the compiler error or `jar tf` on the `anthropic-java-core` jar in the Gradle cache (e.g. `find ~/.gradle -name "anthropic-java-core*.jar"`) — do not guess and do not WebFetch. In particular check: `JsonOutputFormat.Schema`, `stopDetails().explanation()` (may be a plain `String`), `AnthropicIoException` (network errors), and whether `putAdditionalBodyProperty` exists on `MessageCreateParams.Builder`.

- [ ] **Step 2: Build** — `./gradlew :app:assembleDebug`. Expected: success.

- [ ] **Step 3: Verify the bad-key path on the emulator.** Temporarily (do not commit) add to `MainActivity.onCreate`:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val r = com.ottotech.workoutbuilder.ai.CoachClient("sk-ant-invalid").generate("s", listOf(com.ottotech.workoutbuilder.ai.Turn(true, "hi")))
    android.util.Log.i("COACH", r.toString())
}
```

Install, launch, `adb logcat -d | grep COACH`. Expected: `Failed(kind=BAD_KEY)`. Then turn on airplane mode on the emulator (`adb shell cmd connectivity airplane-mode enable`), relaunch, expect `Failed(kind=NO_NETWORK)`; disable airplane mode. If the API instead returns 400 naming `fallbacks` or the beta header, remove those two lines, note it in STATUS.md, and continue — refusals are still handled by `StopReason.REFUSAL`. **Remove the temporary code.**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ai/CoachClient.kt
git commit -m "feat: Coach client — Opus 5 structured output with refusal fallback"
```

---

### Task 6: Coach ViewModel and custom-exercise upsert

**Files:**
- Modify: `data/ExerciseRepository.kt`
- Create: `ui/coach/CoachViewModel.kt`

**Interfaces:**
- Consumes: `CoachClient`, `CoachPrompt`, `CoachConversation`, `CoachResponseMapper`, `ApiKeyStore`, `WorkoutRepository.save`, `WorkoutSummarizer.summarize(workout, exercises)`.
- Produces: `ExerciseRepository.upsertCustom(exercises: List<Exercise>)`; `CoachViewModel` with `state: StateFlow<CoachUiState>` and actions below.

- [ ] **Step 1: `ExerciseRepository.upsertCustom`**

```kotlin
    /**
     * Coach-invented exercises. Ids derive from the name (CoachResponseMapper.customIdFor), so
     * saving the same invention twice replaces the row instead of adding a second one.
     */
    suspend fun upsertCustom(exercises: List<Exercise>) {
        if (exercises.isEmpty()) return
        dao.insertAll(exercises = exercises.map { it.toEntity() }, tags = exercises.flatMap { it.toTagEntities() })
    }
```

Check `ExerciseDao.insertAll`: it uses `REPLACE` for exercises and tags. A replaced exercise's old tag rows must not linger — if `exercise_tags` has a foreign key with `onDelete = CASCADE` to `exercises`, REPLACE (delete + insert) clears them; otherwise add `@Query("DELETE FROM exercise_tags WHERE exerciseId IN (:ids)")` and call it first inside a `@Transaction`. Read `data/db/Entities.kt` to decide.

- [ ] **Step 2: `ui/coach/CoachViewModel.kt`**

```kotlin
package com.ottotech.workoutbuilder.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.ai.CoachClient
import com.ottotech.workoutbuilder.ai.CoachConversation
import com.ottotech.workoutbuilder.ai.CoachPrompt
import com.ottotech.workoutbuilder.ai.CoachReply
import com.ottotech.workoutbuilder.ai.CoachResponseMapper
import com.ottotech.workoutbuilder.ai.CoachResult
import com.ottotech.workoutbuilder.ai.FailureKind
import com.ottotech.workoutbuilder.data.ApiKeyStore
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

sealed interface CoachPhase {
    data object Compose : CoachPhase
    data object Loading : CoachPhase
    /** [exerciseNames] covers library and newly invented exercises, keyed by id, for the preview list. */
    data class Preview(
        val result: CoachResult.Ok,
        val summary: WorkoutSummary,
        val exerciseNames: Map<String, String>,
    ) : CoachPhase
    data class Problem(val message: String, val canRetry: Boolean, val keyProblem: Boolean) : CoachPhase
}

data class CoachUiState(
    val hasKey: Boolean,
    val goal: String = "",
    val minutes: Int? = null,
    val equipment: Set<String> = emptySet(),
    val tweak: String = "",
    val phase: CoachPhase = CoachPhase.Compose,
)

/**
 * Nothing is written until [save]. Leaving mid-request just cancels [viewModelScope]; the
 * typed goal survives every error so the user never retypes it.
 */
class CoachViewModel(
    private val keys: ApiKeyStore,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoachUiState(hasKey = keys.hasKey))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    private var client: CoachClient? = null
    private var conversation: CoachConversation? = null

    init { refreshKey() }

    /** Called on resume too — the user may come back from Settings with a key. */
    fun refreshKey() {
        val key = keys.get()
        _state.update { it.copy(hasKey = key != null) }
        if (key != null && client == null) {
            viewModelScope.launch(Dispatchers.IO) { client = CoachClient(key).also { it.warmUp() } }
        }
    }

    fun onGoal(text: String) = _state.update { it.copy(goal = text) }
    fun onMinutes(m: Int?) = _state.update { it.copy(minutes = if (it.minutes == m) null else m) }
    fun onEquipment(value: String) = _state.update {
        it.copy(equipment = if (value in it.equipment) it.equipment - value else it.equipment + value)
    }
    fun onTweakText(text: String) = _state.update { it.copy(tweak = text) }

    fun generate() {
        val s = _state.value
        if (s.goal.isBlank()) return
        run(CoachConversation.start(CoachPrompt.request(s.goal, s.minutes, s.equipment)))
    }

    fun regenerate() { conversation?.let { run(it.forRegenerate()) } }

    fun applyTweak() {
        val c = conversation ?: return
        val text = _state.value.tweak
        if (text.isBlank()) return
        _state.update { it.copy(tweak = "") }
        run(c.tweak(text))
    }

    fun backToCompose() = _state.update { it.copy(phase = CoachPhase.Compose) }

    fun save(onSaved: () -> Unit) {
        val preview = _state.value.phase as? CoachPhase.Preview ?: return
        viewModelScope.launch {
            exercises.upsertCustom(preview.result.newExercises)
            workouts.save(preview.result.workout)
            onSaved()
        }
    }

    private fun run(next: CoachConversation) {
        val key = keys.get() ?: return _state.update { it.copy(hasKey = false) }
        _state.update { it.copy(phase = CoachPhase.Loading) }
        viewModelScope.launch {
            val library = exercises.observeAll().first()
            val reply = withContext(Dispatchers.IO) {
                val c = client ?: CoachClient(key).also { client = it }
                c.generate(CoachPrompt.system(library), next.turns)
            }
            _state.update { it.copy(phase = phaseFor(reply, next, library)) }
        }
    }

    private fun phaseFor(reply: CoachReply, sent: CoachConversation, library: List<Exercise>): CoachPhase =
        when (reply) {
            is CoachReply.Json -> when (val r = CoachResponseMapper.map(reply.text, library, { UUID.randomUUID().toString() }, Instant.now())) {
                is CoachResult.Ok -> {
                    conversation = sent.withReply(reply.text)
                    val all = (library + r.newExercises).associateBy { it.id }
                    CoachPhase.Preview(r, WorkoutSummarizer.summarize(r.workout, all), all.mapValues { it.value.name })
                }
                is CoachResult.Invalid -> {
                    conversation = sent
                    CoachPhase.Problem("Couldn't build that one.", canRetry = true, keyProblem = false)
                }
            }
            is CoachReply.Refused -> { conversation = null; CoachPhase.Problem(reply.message, canRetry = false, keyProblem = false) }
            is CoachReply.Failed -> {
                conversation = sent
                when (reply.kind) {
                    FailureKind.NO_NETWORK -> CoachPhase.Problem("No connection. Check your internet and try again.", true, false)
                    FailureKind.BAD_KEY -> CoachPhase.Problem("Anthropic didn't accept your API key.", false, true)
                    FailureKind.BUSY -> CoachPhase.Problem("The Coach is busy right now. Try again in a moment.", true, false)
                    FailureKind.OTHER -> CoachPhase.Problem("Something went wrong. Try again.", true, false)
                }
            }
        }

    /** Retry re-sends exactly what failed. */
    fun retry() { conversation?.let { run(it.forRegenerate()) } ?: generate() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                CoachViewModel(app.apiKeys, app.exercises, app.workouts)
            }
        }
    }
}
```

Check `WorkoutSummarizer.summarize` is public and its exact name in `domain/WorkoutSummary.kt` (line ~63) before relying on it.

- [ ] **Step 3: Build + full test suite** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/data/ExerciseRepository.kt app/src/main/java/com/ottotech/workoutbuilder/ui/coach/CoachViewModel.kt
git commit -m "feat: Coach view model — generate, tweak, regenerate, save"
```

---

### Task 7: Coach screen, navigation and end-to-end check

**Files:**
- Create: `ui/coach/CoachScreen.kt`
- Modify: `ui/AppNav.kt`

**Interfaces:**
- Consumes: `CoachViewModel`, `Routes.SETTINGS`.
- Produces: `Routes.COACH = "coach"`; Build sheet's "Ask the Coach" navigates there (removes the `TODO(Plan 2)`).

- [ ] **Step 1: `ui/coach/CoachScreen.kt`** — `ScreenHeader("Coach", onBack)`, then by phase (spec §6.6):

- **No key** (`!state.hasKey`): a `HairlineCard` "Add your API key first" + muted "The Coach uses your own Anthropic key. It's stored encrypted on this phone." + `InverseButton("OPEN SETTINGS")` → `onOpenSettings()`. Call `viewModel.refreshKey()` from a `LifecycleResumeEffect` so returning from Settings unlocks the screen.
- **Compose:** `AppTextField(value = goal, placeholder = "What are you training for?", singleLine = false, textStyle = AppType.cardTitle)` at min height 120 dp; `SectionLabel("Length")` + chips `15 / 30 / 45 / 60 min` (`Chip(selected = minutes == m, onClick = { vm.onMinutes(m) })`); `SectionLabel("Equipment")` + a wrapping `FlowRow` of chips over `TagVocabulary.EQUIPMENT`; pinned `InverseButton("BUILD MY WORKOUT", enabled only when goal is not blank — use `colors.surface`/`textMuted` like `SaveButton` when disabled)`.
- **Loading:** centred muted text "Drafting your workout…" and "This takes 15–30 seconds." plus a Material3 `LinearProgressIndicator` (indeterminate) coloured `onBackground` on `surface`. Back is allowed and simply leaves.
- **Preview:** workout name (`AppType.screenTitle`-sized `cardTitle`), `summary.minutesLabel` and `summary.shortMeta`; each section as a `HairlineCard` with name, "× N rounds" when rounds > 1, and its exercise names (from `phase.exerciseNames`; mark ids found in `phase.result.newExercises` with a muted "new" suffix) — collapsed to names only, no editing. Below: a `AppTextField(tweak, placeholder = "Tweak… e.g. swap lunges for step-ups")` with a "Tweak" 48 dp button; a row of `Regenerate` (surface button) and `SAVE` (`InverseButton`). Save → `viewModel.save(onSaved)`.
- **Problem:** the message; `Retry` when `canRetry`; `Open Settings` when `keyProblem`; always a "Back to my goal" link → `backToCompose()` (goal text is intact).

- [ ] **Step 2: Nav.** In `ui/AppNav.kt`: `const val COACH = "coach"`; replace the Build sheet's `onAskCoach` TODO with `showBuildSheet = false; nav.navigate(Routes.COACH)`; add:

```kotlin
        composable(Routes.COACH) {
            val coachVm: CoachViewModel = viewModel(factory = CoachViewModel.Factory)
            CoachScreen(
                viewModel = coachVm,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
```

- [ ] **Step 3: Build, test, install** — `./gradlew :app:testDebugUnitTest :app:installDebug`.

- [ ] **Step 4: Verify on the emulator without a key** — Build → Ask the Coach shows "Add your API key first"; Open Settings, save `sk-ant-invalid-test`, Back → the compose form appears. Type a goal, Build → Problem "Anthropic didn't accept your API key." with Open Settings. Remove the key in Settings.

- [ ] **Step 5: End-to-end with a real key — needs the user.** The user enters their key in Settings on the device. Then: goal "30 minute leg and core session, dumbbells only", chips 30 min + Dumbbell → Preview within ~30 s; name, minutes and sections look sensible; Tweak "add a finisher" → a changed preview; Regenerate → a different draft; Save → Home shows the workout; Edit it via "⋯" → it opens in the Builder; start it → it runs. If the draft invented an exercise, it appears in the Library under its muscle with a working "Watch demo" search. Save the same draft twice (Regenerate then Save → back to Coach → …) and confirm the Library has no duplicate custom exercise. Record cost from the Anthropic console for one generation in STATUS.md.

- [ ] **Step 6: Update `docs/superpowers/STATUS.md`** — Phase 2 row → complete with test count; add environment facts learned (fallback header accepted or not; actual latency and cost of one generation; any SDK name corrections from Task 5).

- [ ] **Step 7: Commit**

```bash
git add -A app/src/main/java/com/ottotech/workoutbuilder docs/superpowers/STATUS.md
git commit -m "feat: Coach screen — describe a goal, preview, tweak, regenerate, save"
```
