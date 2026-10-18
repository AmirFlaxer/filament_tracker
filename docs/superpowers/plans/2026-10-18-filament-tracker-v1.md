# Filament Tracker v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** אפליקציית Android אישית שעוקבת אחרי סלילי-פילמנט: מהזמנה, דרך הגעה, שימוש בחריץ AMS ואחסון פתוח, ועד סיום. מציגה "חסר", "הולך להיות חסר" ותצרוכת.

**Architecture:** מודול Gradle יחיד, ובו שלוש חבילות. `domain` היא Kotlin טהור: מכונת מצבים, תכנון שינויי-סליל, חישובי מלאי וצפי. `data` מכילה Room וריפוזיטוריז קונקרטיים, שמריצים את תוכניות ה-domain בתוך טרנזקציה. `presentation` היא Compose עם ViewModel לכל מסך. בכוונה אין ממשקי repository: אין בדיקות ViewModel שצריכות fake, ולכן ממשק היה רק boilerplate.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01, Material 3), Hilt 2.54, Room 2.6.1, Navigation Compose 2.8.5, JUnit 4, Robolectric 4.14.1.

**Spec:** `docs/superpowers/specs/2026-10-18-filament-tracker-design.md`

## Global Constraints

- Package ו-namespace: `com.amir.filamenttracker`. `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`, JDK 17.
- גרסאות: בדיוק אלה שבקובץ `libs.versions.toml` שבמשימה 1. הן הועתקו מ-`c:\Projects\buy_smart`, שנבנה בהצלחה במכונה הזו ב-2026-10-03. לא לשדרג.
- Gradle wrapper: להעתיק מ-`c:\Projects\buy_smart` (Gradle 9.0.0).
- כל פקודת Gradle מ-Git Bash רצה אחרי `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. `JAVA_HOME` לא מוגדר במכונה.
- ממשק בעברית בלבד, ו-RTL נכפה ב-`MainActivity`. מחרוזות ממשק נכתבות ישירות בקוד, בלי `strings.xml`, חוץ מ-`app_name`.
- בטקסט עברי: מקף רגיל (-) בלבד, בלי מקף ארוך. בלי סימני חץ. בלי תווי כיווניות בלתי-נראים (U+200E, U+200F).
- **אין מחיר** באף ישות או מסך (הוצא מגרסה 1).
- שמות הסטטוסים בדיוק: `ORDERED`, `SEALED`, `IN_USE`, `OPEN_STORED`, `FINISHED`.
- 4 חריצי AMS, ממוספרים 1-4 (`SpoolPlanner.SLOT_COUNT`).
- כל קומיט מסתיים בשורה `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- אמולטור לבדיקה ידנית: AVD בשם `Pixel_6` (API 35).

## Review Focus

1. **שמות כפולים שונים רק ברישיות או ברווחים** ("pla", "PLA ", "שחור  "): חייבים להתמזג לאותה שורה, אחרת ספירת המלאי של חומר+צבע מתפצלת בשקט. נבדק במשימה 7.
2. **הכנסה לחריץ תפוס, וביטול אחרי שסליל הוזז**: לעולם לא קריסת `SQLiteConstraintException` מהאינדקס הייחודי על `amsSlot`. במקום זה החלפה מסודרת, או `SlotOccupiedException` עם הודעה. נבדק במשימות 3 ו-8.
3. **גוון לא תקין** ("red", "#12345"): מוצגת הודעת קלט ולא נוצרת קריסה. `ColorDot` מצייר אפור על כל קלט שבור. נבדק במשימות 7 ו-10.
4. **הזמנה בלי פריטים, כמות 0, או מותג ריק**: נדחים עם הודעה, ולא נוצרת הזמנה ריקה. נבדק במשימות 7 ו-8.
5. **ביטול פעולת היצירה, וביטול פעמיים ברצף**: ביטול היצירה מחזיר הודעה ולא קורס. שני ביטולים ברצף חוזרים שני צעדים אחורה. נבדק במשימה 8.

## File Map

```
filament_tracker/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew(.bat), gradle/wrapper/*
  app/build.gradle.kts
  app/schemas/                                  (Room schema JSON, generated, committed)
  app/src/main/AndroidManifest.xml
  app/src/main/res/values/strings.xml
  app/src/main/java/com/amir/filamenttracker/
    FilamentApp.kt, MainActivity.kt
    domain/
      model/SpoolStatus.kt, model/DomainModels.kt
      SpoolRuleException.kt, SpoolTransitions.kt, SpoolPlanner.kt
      StockCalculator.kt, ConsumptionForecast.kt, ColorHex.kt
    data/
      db/Entities.kt, db/Converters.kt, db/Projections.kt, db/Mappers.kt
      db/CatalogDao.kt, db/SpoolDao.kt, db/OrderDao.kt, db/StockDao.kt, db/AppDatabase.kt
      CatalogRepository.kt, InventoryRepository.kt, StockRepository.kt
      di/DatabaseModule.kt
    presentation/
      theme/Theme.kt
      common/BaseViewModel.kt, common/UiFormat.kt, common/Components.kt, common/ProductDialogs.kt
      navigation/Routes.kt, navigation/AppNav.kt
      catalog/CatalogScreen.kt, catalog/CatalogViewModel.kt
      targets/TargetsScreen.kt, targets/TargetsViewModel.kt
      inventory/InventoryScreen.kt, inventory/InventoryViewModel.kt
      inventory/ColorSpoolsScreen.kt, inventory/ColorSpoolsViewModel.kt
      inventory/AddExistingScreen.kt, inventory/AddExistingViewModel.kt
      ams/AmsScreen.kt, ams/AmsViewModel.kt
      spool/SpoolDetailScreen.kt, spool/SpoolDetailViewModel.kt
      orders/OrdersScreen.kt, orders/OrdersViewModel.kt
      orders/NewOrderScreen.kt, orders/NewOrderViewModel.kt
      orders/OrderDetailScreen.kt, orders/OrderDetailViewModel.kt
      consumption/ConsumptionScreen.kt, consumption/ConsumptionViewModel.kt
  app/src/test/java/com/amir/filamenttracker/
    testutil/FakeClock.kt, testutil/TestDb.kt
    domain/*Test.kt
    data/*Test.kt
    presentation/common/ColorParseTest.kt
```

---

### Task 1: שלד הפרויקט שנבנה ורץ

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `local.properties` (לא בגיט)
- Copy: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` מ-`c:\Projects\buy_smart`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/amir/filamenttracker/FilamentApp.kt`, `MainActivity.kt`, `presentation/theme/Theme.kt`

**Interfaces:**
- Produces: `FilamentTheme { }` composable; `@HiltAndroidApp FilamentApp`; `@AndroidEntryPoint MainActivity`.

- [ ] **Step 1: העתקת ה-wrapper**

```bash
cd /c/Projects/filament_tracker
mkdir -p gradle/wrapper
cp /c/Projects/buy_smart/gradlew /c/Projects/buy_smart/gradlew.bat .
cp /c/Projects/buy_smart/gradle/wrapper/gradle-wrapper.jar /c/Projects/buy_smart/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
```

- [ ] **Step 2: קבצי Gradle בשורש**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FilamentTracker"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigationCompose = "2.8.5"
hilt = "2.54"
hiltNavigation = "1.2.0"
room = "2.6.1"
coroutines = "1.9.0"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
androidxTestExtJunit = "1.2.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigation" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit-ktx", version.ref = "androidxTestExtJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`.gitignore`:
```
.gradle/
build/
local.properties
.idea/
*.iml
.DS_Store
captures/
.kotlin/
```

`local.properties` (לא נכנס לגיט):
```properties
sdk.dir=C\:\\Users\\1\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 3: מודול app**

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.amir.filamenttracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amir.filamenttracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">מלאי פילמנטים</string>
</resources>
```

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- allowBackup: Android Auto Backup copies the Room database files to Google Drive (spec section 7). -->
    <application
        android:name=".FilamentApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Application, Activity, ערכת נושא**

`app/src/main/java/com/amir/filamenttracker/FilamentApp.kt`:
```kotlin
package com.amir.filamenttracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FilamentApp : Application()
```

`app/src/main/java/com/amir/filamenttracker/presentation/theme/Theme.kt`:
```kotlin
package com.amir.filamenttracker.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun FilamentTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
```

`app/src/main/java/com/amir/filamenttracker/MainActivity.kt` (גרסה זמנית. משימה 10 מחליפה את ה-`Text` ב-`AppNav()`):
```kotlin
package com.amir.filamenttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.amir.filamenttracker.presentation.theme.FilamentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilamentTheme {
                // The UI is Hebrew-only, so force RTL even when the device language is not Hebrew.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        Text("מלאי פילמנטים")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: בנייה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`, והקובץ `app/build/outputs/apk/debug/app-debug.apk` קיים.

- [ ] **Step 6: הרצה על האמולטור**

```bash
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Pixel_6 >/dev/null 2>&1 &
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" wait-for-device
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew installDebug
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell am start -n com.amir.filamenttracker/.MainActivity
```
Expected: האפליקציה נפתחת ומציגה "מלאי פילמנטים".

- [ ] **Step 7: Commit**

```bash
cd /c/Projects/filament_tracker
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "build: שלד פרויקט Android - Compose, Hilt, Room

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: מודל ה-domain ומכונת המצבים

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/model/SpoolStatus.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/domain/model/DomainModels.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/domain/SpoolRuleException.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/domain/SpoolTransitions.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/domain/SpoolTransitionsTest.kt`

**Interfaces:**
- Produces:
  - `enum class SpoolStatus { ORDERED, SEALED, IN_USE, OPEN_STORED, FINISHED }`
  - `data class Spool(id: Long, status: SpoolStatus, amsSlot: Int?)`
  - `data class SpoolEvent(id: Long, spoolId: Long, fromStatus: SpoolStatus?, toStatus: SpoolStatus, fromSlot: Int?, toSlot: Int?, at: Instant)`
  - `data class SpoolChange(spoolId: Long, fromStatus: SpoolStatus?, toStatus: SpoolStatus, fromSlot: Int?, toSlot: Int?)`
  - `data class StockKey(materialId: Long, colorId: Long)`, `data class StockTarget(key: StockKey, minSealed: Int)`
  - `open class SpoolRuleException(message)`, `IllegalTransitionException(from, to)`, `SlotOccupiedException(slot)`, `UndoNotPossibleException(message)`
  - `object SpoolTransitions { fun targets(from): Set<SpoolStatus>; fun isAllowed(from, to): Boolean }`

- [ ] **Step 1: הבדיקה הנכשלת**

`app/src/test/java/com/amir/filamenttracker/domain/SpoolTransitionsTest.kt`:
```kotlin
package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoolTransitionsTest {
    private val specTransitions = setOf(
        ORDERED to SEALED,
        SEALED to IN_USE,
        IN_USE to OPEN_STORED,
        OPEN_STORED to IN_USE,
        IN_USE to FINISHED,
        OPEN_STORED to FINISHED,
    )

    @Test
    fun `exactly the six spec transitions are allowed`() {
        for (from in SpoolStatus.entries) {
            for (to in SpoolStatus.entries) {
                assertEquals("$from to $to", (from to to) in specTransitions, SpoolTransitions.isAllowed(from, to))
            }
        }
    }

    @Test
    fun `finished is terminal`() {
        assertTrue(SpoolTransitions.targets(FINISHED).isEmpty())
    }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*SpoolTransitionsTest"
```
Expected: FAIL, compilation error: `Unresolved reference: SpoolStatus`.

- [ ] **Step 3: המימוש**

`domain/model/SpoolStatus.kt`:
```kotlin
package com.amir.filamenttracker.domain.model

enum class SpoolStatus { ORDERED, SEALED, IN_USE, OPEN_STORED, FINISHED }
```

`domain/model/DomainModels.kt`:
```kotlin
package com.amir.filamenttracker.domain.model

import java.time.Instant

/** The slice of a spool that the domain rules need. */
data class Spool(val id: Long, val status: SpoolStatus, val amsSlot: Int?)

data class SpoolEvent(
    val id: Long,
    val spoolId: Long,
    val fromStatus: SpoolStatus?,
    val toStatus: SpoolStatus,
    val fromSlot: Int?,
    val toSlot: Int?,
    val at: Instant,
)

/** One status change for one spool. A list of changes must be applied in order. */
data class SpoolChange(
    val spoolId: Long,
    val fromStatus: SpoolStatus?,
    val toStatus: SpoolStatus,
    val fromSlot: Int?,
    val toSlot: Int?,
)

/** Stock is counted per material+color, regardless of brand. */
data class StockKey(val materialId: Long, val colorId: Long)

data class StockTarget(val key: StockKey, val minSealed: Int)
```

`domain/SpoolRuleException.kt`:
```kotlin
package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus

/** A broken domain rule. The message is Hebrew and is shown to the user as-is. */
open class SpoolRuleException(message: String) : RuntimeException(message)

class IllegalTransitionException(val from: SpoolStatus, val to: SpoolStatus) :
    SpoolRuleException("הפעולה לא אפשרית במצב הנוכחי של הסליל ($from, $to)")

class SlotOccupiedException(val slot: Int) :
    SpoolRuleException("חריץ $slot תפוס. יש לפנות אותו קודם")

class UndoNotPossibleException(message: String) : SpoolRuleException(message)
```

`domain/SpoolTransitions.kt`:
```kotlin
package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED

/** The allowed status transitions, spec section 4. */
object SpoolTransitions {
    private val allowed: Map<SpoolStatus, Set<SpoolStatus>> = mapOf(
        ORDERED to setOf(SEALED),
        SEALED to setOf(IN_USE),
        IN_USE to setOf(OPEN_STORED, FINISHED),
        OPEN_STORED to setOf(IN_USE, FINISHED),
        FINISHED to emptySet(),
    )

    fun targets(from: SpoolStatus): Set<SpoolStatus> = allowed.getValue(from)

    fun isAllowed(from: SpoolStatus, to: SpoolStatus): Boolean = to in targets(from)
}
```

- [ ] **Step 4: להריץ ולוודא הצלחה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*SpoolTransitionsTest"
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(domain): סטטוסי סליל ומכונת מעברים

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: מתכנן שינויי-סליל - חריצים, מעברים וביטול

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/SpoolPlanner.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/domain/SpoolPlannerTest.kt`

**Interfaces:**
- Consumes: `Spool`, `SpoolEvent`, `SpoolChange`, `SpoolTransitions`, the exceptions from Task 2.
- Produces:
  - `SpoolPlanner.SLOT_COUNT = 4`
  - `SpoolPlanner.vacate(slot: Int, inUse: List<Spool>, keepSpoolId: Long? = null): SpoolChange?`
  - `SpoolPlanner.load(target: Spool, slot: Int, inUse: List<Spool>): List<SpoolChange>`. כשהחריץ תפוס, שינוי הדייר נמצא ראשון ברשימה.
  - `SpoolPlanner.move(target: Spool, to: SpoolStatus): SpoolChange`. `to` לא יכול להיות `IN_USE`.
  - `SpoolPlanner.undo(target: Spool, last: SpoolEvent, inUse: List<Spool>): SpoolChange`

- [ ] **Step 1: הבדיקות הנכשלות**

`app/src/test/java/com/amir/filamenttracker/domain/SpoolPlannerTest.kt`:
```kotlin
package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolChange
import com.amir.filamenttracker.domain.model.SpoolEvent
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SpoolPlannerTest {
    private val t = Instant.parse("2026-10-01T10:00:00Z")

    @Test
    fun `load sealed spool into empty slot`() {
        val changes = SpoolPlanner.load(Spool(1, SEALED, null), 2, inUse = emptyList())
        assertEquals(listOf(SpoolChange(1, SEALED, IN_USE, null, 2)), changes)
    }

    @Test
    fun `load into occupied slot moves the occupant to storage first`() {
        val occupant = Spool(9, IN_USE, 2)
        val changes = SpoolPlanner.load(Spool(1, OPEN_STORED, null), 2, inUse = listOf(occupant))
        assertEquals(
            listOf(
                SpoolChange(9, IN_USE, OPEN_STORED, 2, null),
                SpoolChange(1, OPEN_STORED, IN_USE, null, 2),
            ),
            changes,
        )
    }

    @Test
    fun `loading a spool into the slot it is already in does nothing`() {
        val spool = Spool(1, IN_USE, 3)
        assertTrue(SpoolPlanner.load(spool, 3, inUse = listOf(spool)).isEmpty())
    }

    @Test(expected = IllegalTransitionException::class)
    fun `ordered spool cannot be loaded`() {
        SpoolPlanner.load(Spool(1, ORDERED, null), 1, emptyList())
    }

    @Test(expected = IllegalTransitionException::class)
    fun `finished spool cannot be loaded`() {
        SpoolPlanner.load(Spool(1, FINISHED, null), 1, emptyList())
    }

    @Test(expected = IllegalTransitionException::class)
    fun `spool in one slot cannot jump to another slot`() {
        val spool = Spool(1, IN_USE, 1)
        SpoolPlanner.load(spool, 2, listOf(spool))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `slot zero is rejected`() {
        SpoolPlanner.load(Spool(1, SEALED, null), 0, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `slot five is rejected`() {
        SpoolPlanner.load(Spool(1, SEALED, null), 5, emptyList())
    }

    @Test
    fun `vacate returns null for an empty slot`() {
        assertNull(SpoolPlanner.vacate(4, listOf(Spool(1, IN_USE, 1))))
    }

    @Test
    fun `move out of a slot clears the slot`() {
        assertEquals(SpoolChange(1, IN_USE, OPEN_STORED, 3, null), SpoolPlanner.move(Spool(1, IN_USE, 3), OPEN_STORED))
        assertEquals(SpoolChange(1, IN_USE, FINISHED, 3, null), SpoolPlanner.move(Spool(1, IN_USE, 3), FINISHED))
    }

    @Test
    fun `arrival moves ordered to sealed`() {
        assertEquals(SpoolChange(1, ORDERED, SEALED, null, null), SpoolPlanner.move(Spool(1, ORDERED, null), SEALED))
    }

    @Test(expected = IllegalTransitionException::class)
    fun `sealed spool cannot be finished without being opened`() {
        SpoolPlanner.move(Spool(1, SEALED, null), FINISHED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `move cannot target IN_USE`() {
        SpoolPlanner.move(Spool(1, SEALED, null), IN_USE)
    }

    @Test
    fun `undo of unload puts the spool back in its slot`() {
        val spool = Spool(1, OPEN_STORED, null)
        val last = SpoolEvent(5, 1, IN_USE, OPEN_STORED, 2, null, t)
        assertEquals(SpoolChange(1, OPEN_STORED, IN_USE, null, 2), SpoolPlanner.undo(spool, last, emptyList()))
    }

    @Test
    fun `undo of finish restores in-use and slot`() {
        val spool = Spool(1, FINISHED, null)
        val last = SpoolEvent(5, 1, IN_USE, FINISHED, 4, null, t)
        assertEquals(SpoolChange(1, FINISHED, IN_USE, null, 4), SpoolPlanner.undo(spool, last, emptyList()))
    }

    @Test(expected = SlotOccupiedException::class)
    fun `undo is blocked when the old slot is now taken`() {
        val spool = Spool(1, OPEN_STORED, null)
        val last = SpoolEvent(5, 1, IN_USE, OPEN_STORED, 2, null, t)
        SpoolPlanner.undo(spool, last, inUse = listOf(Spool(7, IN_USE, 2)))
    }

    @Test(expected = UndoNotPossibleException::class)
    fun `creation event cannot be undone`() {
        val spool = Spool(1, SEALED, null)
        SpoolPlanner.undo(spool, SpoolEvent(5, 1, null, SEALED, null, null, t), emptyList())
    }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*SpoolPlannerTest"
```
Expected: FAIL, `Unresolved reference: SpoolPlanner`.

- [ ] **Step 3: המימוש**

`domain/SpoolPlanner.kt`:
```kotlin
package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolChange
import com.amir.filamenttracker.domain.model.SpoolEvent
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED

/**
 * Turns user actions into [SpoolChange] lists. Pure: the data layer applies the
 * changes, in list order, inside one transaction.
 */
object SpoolPlanner {
    const val SLOT_COUNT = 4

    /** The change that empties [slot], or null when it is already empty (or holds [keepSpoolId]). */
    fun vacate(slot: Int, inUse: List<Spool>, keepSpoolId: Long? = null): SpoolChange? =
        inUse.firstOrNull { it.amsSlot == slot && it.id != keepSpoolId }
            ?.let { SpoolChange(it.id, IN_USE, OPEN_STORED, slot, null) }

    /** Put [target] into [slot]. A different spool in that slot goes to storage first. */
    fun load(target: Spool, slot: Int, inUse: List<Spool>): List<SpoolChange> {
        require(slot in 1..SLOT_COUNT) { "slot must be 1..$SLOT_COUNT, was $slot" }
        if (target.status == IN_USE && target.amsSlot == slot) return emptyList()
        if (!SpoolTransitions.isAllowed(target.status, IN_USE)) {
            throw IllegalTransitionException(target.status, IN_USE)
        }
        return listOfNotNull(
            vacate(slot, inUse, keepSpoolId = target.id),
            SpoolChange(target.id, target.status, IN_USE, target.amsSlot, slot),
        )
    }

    /** Any transition except into a slot (use [load] for that). Leaving IN_USE clears the slot. */
    fun move(target: Spool, to: SpoolStatus): SpoolChange {
        require(to != IN_USE) { "use load() to put a spool into a slot" }
        if (!SpoolTransitions.isAllowed(target.status, to)) {
            throw IllegalTransitionException(target.status, to)
        }
        return SpoolChange(target.id, target.status, to, target.amsSlot, null)
    }

    /**
     * Reverse [last], the newest event of [target]. Skips the transition table on purpose:
     * undoing a mistaken "finished" must be able to go back to IN_USE.
     */
    fun undo(target: Spool, last: SpoolEvent, inUse: List<Spool>): SpoolChange {
        require(last.spoolId == target.id) { "event ${last.id} belongs to spool ${last.spoolId}" }
        val previous = last.fromStatus
            ?: throw UndoNotPossibleException("זו הפעולה הראשונה של הסליל, ואין מה לבטל")
        val previousSlot = last.fromSlot
        if (previous == IN_USE && previousSlot != null &&
            inUse.any { it.amsSlot == previousSlot && it.id != target.id }
        ) {
            throw SlotOccupiedException(previousSlot)
        }
        return SpoolChange(target.id, target.status, previous, target.amsSlot, previousSlot)
    }
}
```

- [ ] **Step 4: להריץ ולוודא הצלחה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*SpoolPlannerTest"
```
Expected: PASS (17 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(domain): מתכנן שינויי-סליל - חריצים, מעברים וביטול

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: חישוב מלאי והתראות

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/StockCalculator.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/domain/StockCalculatorTest.kt`

**Interfaces:**
- Consumes: `StockKey`, `StockTarget`, `SpoolStatus`.
- Produces:
  - `data class StockCount(available: Int = 0, sealed: Int = 0, onTheWay: Int = 0)`
  - `enum class AlertKind { MISSING, RUNNING_LOW, ON_THE_WAY }`. הסדר הזה הוא גם סדר התצוגה.
  - `data class StockAlert(key: StockKey, kind: AlertKind, count: StockCount, minSealed: Int)`
  - `StockCalculator.count(spools: List<Pair<StockKey, SpoolStatus>>): Map<StockKey, StockCount>`
  - `StockCalculator.alerts(counts: Map<StockKey, StockCount>, targets: List<StockTarget>): List<StockAlert>`, ממוינים לפי `kind`.

- [ ] **Step 1: הבדיקות הנכשלות**

`app/src/test/java/com/amir/filamenttracker/domain/StockCalculatorTest.kt`:
```kotlin
package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import com.amir.filamenttracker.domain.model.StockKey
import com.amir.filamenttracker.domain.model.StockTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockCalculatorTest {
    private val black = StockKey(materialId = 1, colorId = 10)
    private val white = StockKey(materialId = 1, colorId = 11)

    private fun alertsFor(target: StockTarget, vararg statuses: SpoolStatus) =
        StockCalculator.alerts(StockCalculator.count(statuses.map { target.key to it }), listOf(target))

    @Test
    fun `count splits statuses and ignores finished`() {
        val counts = StockCalculator.count(
            listOf(black to SEALED, black to SEALED, black to IN_USE, black to OPEN_STORED, black to ORDERED, black to FINISHED),
        )
        assertEquals(StockCount(available = 4, sealed = 2, onTheWay = 1), counts[black])
    }

    @Test
    fun `untracked combination never alerts`() {
        val counts = StockCalculator.count(listOf(white to FINISHED))
        assertTrue(StockCalculator.alerts(counts, emptyList()).isEmpty())
    }

    @Test
    fun `nothing available and nothing ordered is missing`() {
        assertEquals(AlertKind.MISSING, alertsFor(StockTarget(black, 1), FINISHED).single().kind)
    }

    @Test
    fun `tracked combination with no spools at all is missing`() {
        val alerts = StockCalculator.alerts(emptyMap(), listOf(StockTarget(black, 1)))
        assertEquals(AlertKind.MISSING, alerts.single().kind)
    }

    @Test
    fun `nothing available but ordered is on the way`() {
        assertEquals(AlertKind.ON_THE_WAY, alertsFor(StockTarget(black, 1), ORDERED).single().kind)
    }

    @Test
    fun `open spool without sealed backup is running low`() {
        assertEquals(AlertKind.RUNNING_LOW, alertsFor(StockTarget(black, 1), IN_USE).single().kind)
    }

    @Test
    fun `running low but covered by an order is on the way`() {
        assertEquals(AlertKind.ON_THE_WAY, alertsFor(StockTarget(black, 1), IN_USE, ORDERED).single().kind)
    }

    @Test
    fun `min two with one sealed is running low, with two sealed is fine`() {
        assertEquals(AlertKind.RUNNING_LOW, alertsFor(StockTarget(black, 2), IN_USE, SEALED).single().kind)
        assertTrue(alertsFor(StockTarget(black, 2), SEALED, SEALED).isEmpty())
    }

    @Test
    fun `alerts are sorted missing, running low, on the way`() {
        val counts = StockCalculator.count(listOf(white to IN_USE, black to ORDERED))
        val third = StockKey(2, 12)
        val kinds = StockCalculator.alerts(
            counts,
            listOf(StockTarget(black, 1), StockTarget(white, 1), StockTarget(third, 1)),
        ).map { it.kind }
        assertEquals(listOf(AlertKind.MISSING, AlertKind.RUNNING_LOW, AlertKind.ON_THE_WAY), kinds)
    }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*StockCalculatorTest"
```
Expected: FAIL, `Unresolved reference: StockCalculator`.

- [ ] **Step 3: המימוש**

`domain/StockCalculator.kt`:
```kotlin
package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import com.amir.filamenttracker.domain.model.StockKey
import com.amir.filamenttracker.domain.model.StockTarget

data class StockCount(val available: Int = 0, val sealed: Int = 0, val onTheWay: Int = 0)

/** Declaration order is display order. */
enum class AlertKind { MISSING, RUNNING_LOW, ON_THE_WAY }

data class StockAlert(val key: StockKey, val kind: AlertKind, val count: StockCount, val minSealed: Int)

/** Spec section 5. */
object StockCalculator {
    fun count(spools: List<Pair<StockKey, SpoolStatus>>): Map<StockKey, StockCount> =
        spools.groupBy({ it.first }, { it.second }).mapValues { (_, statuses) ->
            StockCount(
                available = statuses.count { it == SEALED || it == IN_USE || it == OPEN_STORED },
                sealed = statuses.count { it == SEALED },
                onTheWay = statuses.count { it == ORDERED },
            )
        }

    fun alerts(counts: Map<StockKey, StockCount>, targets: List<StockTarget>): List<StockAlert> =
        targets.mapNotNull { target ->
            val c = counts[target.key] ?: StockCount()
            val kind = when {
                c.available == 0 ->
                    if (c.onTheWay > 0) AlertKind.ON_THE_WAY else AlertKind.MISSING
                c.sealed < target.minSealed ->
                    if (c.sealed + c.onTheWay >= target.minSealed) AlertKind.ON_THE_WAY else AlertKind.RUNNING_LOW
                else -> null
            }
            kind?.let { StockAlert(target.key, it, c, target.minSealed) }
        }.sortedBy { it.kind.ordinal }
}
```

- [ ] **Step 4: להריץ ולוודא הצלחה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*StockCalculatorTest"
```
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(domain): חישוב מלאי - חסר, הולך להיות חסר, בדרך

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: קצב תצרוכת וצפי סיום

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/ConsumptionForecast.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/domain/ConsumptionForecastTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface Forecast { NotEnoughHistory; OutOfStock; Soon; data class At(date: Instant) }`
  - `ConsumptionForecast.averageInterval(finishes: List<Instant>): Duration?`. מחזירה null כשיש פחות מ-2 סיומים.
  - `ConsumptionForecast.forecast(finishes: List<Instant>, available: Int, now: Instant): Forecast`

- [ ] **Step 1: הבדיקות הנכשלות**

`app/src/test/java/com/amir/filamenttracker/domain/ConsumptionForecastTest.kt`:
```kotlin
package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ConsumptionForecastTest {
    private val d0 = Instant.parse("2026-01-01T00:00:00Z")
    private fun day(n: Long): Instant = d0.plus(Duration.ofDays(n))

    @Test
    fun `interval needs at least two finishes`() {
        assertNull(ConsumptionForecast.averageInterval(emptyList()))
        assertNull(ConsumptionForecast.averageInterval(listOf(d0)))
    }

    @Test
    fun `interval is the mean gap between consecutive finishes`() {
        assertEquals(Duration.ofDays(15), ConsumptionForecast.averageInterval(listOf(d0, day(10), day(30))))
    }

    @Test
    fun `input order does not matter`() {
        assertEquals(Duration.ofDays(15), ConsumptionForecast.averageInterval(listOf(day(30), d0, day(10))))
    }

    @Test
    fun `nothing available is out of stock even with history`() {
        assertEquals(Forecast.OutOfStock, ConsumptionForecast.forecast(listOf(d0, day(10)), available = 0, now = day(11)))
    }

    @Test
    fun `one finish is not enough history`() {
        assertEquals(Forecast.NotEnoughHistory, ConsumptionForecast.forecast(listOf(d0), available = 2, now = day(1)))
    }

    @Test
    fun `forecast is last finish plus interval times available`() {
        val result = ConsumptionForecast.forecast(listOf(d0, day(10), day(30)), available = 2, now = day(35))
        assertEquals(Forecast.At(day(60)), result)
    }

    @Test
    fun `a forecast date in the past reads as soon`() {
        val result = ConsumptionForecast.forecast(listOf(d0, day(10), day(30)), available = 2, now = day(70))
        assertEquals(Forecast.Soon, result)
    }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*ConsumptionForecastTest"
```
Expected: FAIL, `Unresolved reference: ConsumptionForecast`.

- [ ] **Step 3: המימוש**

`domain/ConsumptionForecast.kt`:
```kotlin
package com.amir.filamenttracker.domain

import java.time.Duration
import java.time.Instant

sealed interface Forecast {
    data object NotEnoughHistory : Forecast
    data object OutOfStock : Forecast
    data object Soon : Forecast
    data class At(val date: Instant) : Forecast
}

/** Spec section 5, "rate and forecast". Informational only, never feeds alerts in v1. */
object ConsumptionForecast {
    fun averageInterval(finishes: List<Instant>): Duration? {
        if (finishes.size < 2) return null
        val sorted = finishes.sorted()
        return Duration.between(sorted.first(), sorted.last()).dividedBy((sorted.size - 1).toLong())
    }

    fun forecast(finishes: List<Instant>, available: Int, now: Instant): Forecast {
        if (available == 0) return Forecast.OutOfStock
        val interval = averageInterval(finishes) ?: return Forecast.NotEnoughHistory
        val at = finishes.max().plus(interval.multipliedBy(available.toLong()))
        return if (at.isAfter(now)) Forecast.At(at) else Forecast.Soon
    }
}
```

- [ ] **Step 4: להריץ ולוודא הצלחה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*ConsumptionForecastTest"
```
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(domain): קצב תצרוכת וצפי סיום

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: מסד הנתונים - ישויות, DAO ו-Hilt

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/data/db/Entities.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/data/db/Converters.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/data/db/Projections.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/data/db/Mappers.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/data/db/CatalogDao.kt`, `SpoolDao.kt`, `OrderDao.kt`, `StockDao.kt`, `AppDatabase.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/data/di/DatabaseModule.kt`
- Create: `app/src/test/java/com/amir/filamenttracker/testutil/TestDb.kt`, `testutil/FakeClock.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/data/DatabaseTest.kt`
- Generated: `app/schemas/com.amir.filamenttracker.data.db.AppDatabase/1.json` (נכנס לגיט)

**Interfaces:**
- Consumes: `SpoolStatus`, `Spool`, `SpoolEvent` (Task 2).
- Produces:
  - Entities: `MaterialEntity`, `ColorEntity`, `ProductEntity`, `OrderEntity`, `SpoolEntity`, `SpoolEventEntity`, `StockTargetEntity` (השדות למטה).
  - Projections: `SpoolRow`, `ProductRow`, `OrderSummary`, `TargetRow`, `FinishRow`, והרחבות `SpoolRow.title`, `ProductRow.title`.
  - `SpoolEntity.toDomain(): Spool`, `SpoolEventEntity.toDomain(): SpoolEvent`
  - `AppDatabase` עם `catalogDao()`, `spoolDao()`, `orderDao()`, `stockDao()`, `AppDatabase.NAME`, `AppDatabase.SEED_CALLBACK`
  - Hilt: `AppDatabase` (singleton) ו-`java.time.Clock` (singleton).
  - Test utils: `inMemoryDb(): AppDatabase`, `class FakeClock(var now: Instant) : Clock`.

- [ ] **Step 1: ישויות וממירים**

`data/db/Entities.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "materials", indices = [Index(value = ["name"], unique = true)])
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
)

@Entity(tableName = "colors", indices = [Index(value = ["name"], unique = true)])
data class ColorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hex: String? = null,
    val archived: Boolean = false,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"]),
        ForeignKey(entity = ColorEntity::class, parentColumns = ["id"], childColumns = ["colorId"]),
    ],
    indices = [Index("materialId"), Index("colorId")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val materialId: Long,
    val colorId: Long,
    val weightGrams: Int = 1000,
    val note: String = "",
    val archived: Boolean = false,
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendor: String = "Amazon",
    val orderedAt: Instant,
    val expectedAt: LocalDate? = null,
    val orderNumber: String? = null,
)

@Entity(
    tableName = "spools",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"]),
        ForeignKey(
            entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // SQLite lets a unique index hold many NULLs, so this enforces one spool per slot
    // only for spools that are actually in a slot.
    indices = [Index("productId"), Index("orderId"), Index(value = ["amsSlot"], unique = true)],
)
data class SpoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val orderId: Long?,
    val status: SpoolStatus,
    val amsSlot: Int?,
)

@Entity(
    tableName = "spool_events",
    foreignKeys = [
        ForeignKey(
            entity = SpoolEntity::class, parentColumns = ["id"], childColumns = ["spoolId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("spoolId")],
)
data class SpoolEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spoolId: Long,
    val fromStatus: SpoolStatus?,
    val toStatus: SpoolStatus,
    val fromSlot: Int?,
    val toSlot: Int?,
    val at: Instant,
)

@Entity(
    tableName = "stock_targets",
    primaryKeys = ["materialId", "colorId"],
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ColorEntity::class, parentColumns = ["id"], childColumns = ["colorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("colorId")],
)
data class StockTargetEntity(val materialId: Long, val colorId: Long, val minSealed: Int = 1)
```

`data/db/Converters.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import androidx.room.TypeConverter
import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    // Stored by name, so SQL can compare against literals like 'IN_USE'.
    @TypeConverter fun statusToString(value: SpoolStatus?): String? = value?.name
    @TypeConverter fun stringToStatus(value: String?): SpoolStatus? = value?.let(SpoolStatus::valueOf)
}
```

- [ ] **Step 2: Projections ו-Mappers**

`data/db/Projections.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Instant
import java.time.LocalDate

/** A spool joined with its product, material and color names. */
data class SpoolRow(
    val spoolId: Long,
    val status: SpoolStatus,
    val amsSlot: Int?,
    val orderId: Long?,
    val productId: Long,
    val brand: String,
    val weightGrams: Int,
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
)

data class ProductRow(
    val productId: Long,
    val brand: String,
    val weightGrams: Int,
    val note: String,
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
)

data class OrderSummary(
    val id: Long,
    val vendor: String,
    val orderedAt: Instant,
    val expectedAt: LocalDate?,
    val orderNumber: String?,
    val spoolCount: Int,
    val pendingCount: Int,
)

/** A material+color pair the user has products for. [minSealed] is null when not tracked. */
data class TargetRow(
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val minSealed: Int?,
)

data class FinishRow(
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val at: Instant,
)

// Extensions, not members: Room would try to map a member property to a column.
val SpoolRow.title: String get() = "$brand $materialName $colorName"
val ProductRow.title: String get() = "$brand $materialName $colorName"

internal const val SPOOL_ROW_SELECT = """
    SELECT s.id AS spoolId, s.status AS status, s.amsSlot AS amsSlot, s.orderId AS orderId,
           p.id AS productId, p.brand AS brand, p.weightGrams AS weightGrams,
           m.id AS materialId, m.name AS materialName,
           c.id AS colorId, c.name AS colorName, c.hex AS colorHex
    FROM spools s
    JOIN products p ON p.id = s.productId
    JOIN materials m ON m.id = p.materialId
    JOIN colors c ON c.id = p.colorId
"""
```

`data/db/Mappers.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolEvent

fun SpoolEntity.toDomain() = Spool(id = id, status = status, amsSlot = amsSlot)

fun SpoolEventEntity.toDomain() = SpoolEvent(
    id = id, spoolId = spoolId, fromStatus = fromStatus, toStatus = toStatus,
    fromSlot = fromSlot, toSlot = toSlot, at = at,
)
```

- [ ] **Step 3: DAOs**

`data/db/CatalogDao.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM materials WHERE archived = 0 ORDER BY name")
    fun activeMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM colors WHERE archived = 0 ORDER BY name")
    fun activeColors(): Flow<List<ColorEntity>>

    @Query(
        """
        SELECT p.id AS productId, p.brand AS brand, p.weightGrams AS weightGrams, p.note AS note,
               m.id AS materialId, m.name AS materialName,
               c.id AS colorId, c.name AS colorName, c.hex AS colorHex
        FROM products p
        JOIN materials m ON m.id = p.materialId
        JOIN colors c ON c.id = p.colorId
        WHERE p.archived = 0 AND m.archived = 0 AND c.archived = 0
        ORDER BY m.name, c.name, p.brand
        """,
    )
    fun activeProducts(): Flow<List<ProductRow>>

    @Query("SELECT * FROM materials WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun materialByName(name: String): MaterialEntity?

    @Query("SELECT * FROM colors WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun colorByName(name: String): ColorEntity?

    @Insert suspend fun insertMaterial(material: MaterialEntity): Long
    @Insert suspend fun insertColor(color: ColorEntity): Long
    @Insert suspend fun insertProduct(product: ProductEntity): Long

    @Query("UPDATE materials SET name = :name WHERE id = :id")
    suspend fun renameMaterial(id: Long, name: String)

    @Query("UPDATE colors SET name = :name, hex = :hex WHERE id = :id")
    suspend fun updateColor(id: Long, name: String, hex: String?)

    @Query("UPDATE products SET brand = :brand, weightGrams = :weightGrams, note = :note WHERE id = :id")
    suspend fun updateProduct(id: Long, brand: String, weightGrams: Int, note: String)

    @Query("UPDATE materials SET archived = :archived WHERE id = :id")
    suspend fun setMaterialArchived(id: Long, archived: Boolean)

    @Query("UPDATE colors SET archived = :archived WHERE id = :id")
    suspend fun setColorArchived(id: Long, archived: Boolean)

    @Query("UPDATE products SET archived = :archived WHERE id = :id")
    suspend fun setProductArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM materials WHERE id = :id") suspend fun deleteMaterial(id: Long)
    @Query("DELETE FROM colors WHERE id = :id") suspend fun deleteColor(id: Long)
    @Query("DELETE FROM products WHERE id = :id") suspend fun deleteProduct(id: Long)

    @Query("SELECT COUNT(*) FROM products WHERE materialId = :id")
    suspend fun productCountForMaterial(id: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE colorId = :id")
    suspend fun productCountForColor(id: Long): Int

    @Query("SELECT COUNT(*) FROM spools WHERE productId = :id")
    suspend fun spoolCountForProduct(id: Long): Int
}
```

`data/db/SpoolDao.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.amir.filamenttracker.domain.model.SpoolStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SpoolDao {
    @Query("$SPOOL_ROW_SELECT ORDER BY m.name, c.name, p.brand, s.id")
    fun spoolRows(): Flow<List<SpoolRow>>

    @Query("$SPOOL_ROW_SELECT WHERE s.id = :id")
    fun spoolRow(id: Long): Flow<SpoolRow?>

    @Query("SELECT * FROM spools WHERE id = :id")
    suspend fun spool(id: Long): SpoolEntity?

    @Query("SELECT * FROM spools WHERE status = 'IN_USE'")
    suspend fun inUse(): List<SpoolEntity>

    @Query("SELECT * FROM spools WHERE orderId = :orderId")
    suspend fun spoolsOfOrder(orderId: Long): List<SpoolEntity>

    @Insert suspend fun insertSpool(spool: SpoolEntity): Long

    @Query("UPDATE spools SET status = :status, amsSlot = :slot WHERE id = :id")
    suspend fun updateState(id: Long, status: SpoolStatus, slot: Int?)

    @Query("DELETE FROM spools WHERE id = :id")
    suspend fun deleteSpool(id: Long)

    @Insert suspend fun insertEvent(event: SpoolEventEntity): Long

    // Events written in one transaction share a timestamp, so id breaks the tie.
    @Query("SELECT * FROM spool_events WHERE spoolId = :spoolId ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun lastEvent(spoolId: Long): SpoolEventEntity?

    @Query("SELECT * FROM spool_events WHERE spoolId = :spoolId ORDER BY at DESC, id DESC")
    fun events(spoolId: Long): Flow<List<SpoolEventEntity>>

    @Query("DELETE FROM spool_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query(
        """
        SELECT m.id AS materialId, m.name AS materialName, c.id AS colorId, c.name AS colorName,
               c.hex AS colorHex, e.at AS at
        FROM spool_events e
        JOIN spools s ON s.id = e.spoolId
        JOIN products p ON p.id = s.productId
        JOIN materials m ON m.id = p.materialId
        JOIN colors c ON c.id = p.colorId
        WHERE e.toStatus = 'FINISHED'
        ORDER BY e.at
        """,
    )
    fun finishEvents(): Flow<List<FinishRow>>
}
```

`data/db/OrderDao.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert suspend fun insertOrder(order: OrderEntity): Long

    @Query(
        """
        SELECT o.id AS id, o.vendor AS vendor, o.orderedAt AS orderedAt, o.expectedAt AS expectedAt,
               o.orderNumber AS orderNumber,
               COUNT(s.id) AS spoolCount,
               COALESCE(SUM(CASE WHEN s.status = 'ORDERED' THEN 1 ELSE 0 END), 0) AS pendingCount
        FROM orders o
        LEFT JOIN spools s ON s.orderId = o.id
        GROUP BY o.id
        ORDER BY o.orderedAt DESC
        """,
    )
    fun orderSummaries(): Flow<List<OrderSummary>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun order(id: Long): Flow<OrderEntity?>

    @Query("$SPOOL_ROW_SELECT WHERE s.orderId = :orderId ORDER BY m.name, c.name, s.id")
    fun orderSpools(orderId: Long): Flow<List<SpoolRow>>

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: Long)
}
```

`data/db/StockDao.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    // Pairs come from products, so a pair appears once the user has a product for it.
    // A tracked pair stays listed even after its products are archived.
    @Query(
        """
        SELECT DISTINCT m.id AS materialId, m.name AS materialName, c.id AS colorId,
               c.name AS colorName, c.hex AS colorHex, t.minSealed AS minSealed
        FROM products p
        JOIN materials m ON m.id = p.materialId
        JOIN colors c ON c.id = p.colorId
        LEFT JOIN stock_targets t ON t.materialId = p.materialId AND t.colorId = p.colorId
        WHERE (p.archived = 0 AND m.archived = 0 AND c.archived = 0) OR t.materialId IS NOT NULL
        ORDER BY m.name, c.name
        """,
    )
    fun targetRows(): Flow<List<TargetRow>>

    @Upsert suspend fun upsert(target: StockTargetEntity)

    @Query("DELETE FROM stock_targets WHERE materialId = :materialId AND colorId = :colorId")
    suspend fun delete(materialId: Long, colorId: Long)

    @Query("DELETE FROM stock_targets WHERE materialId = :materialId")
    suspend fun deleteForMaterial(materialId: Long)

    @Query("DELETE FROM stock_targets WHERE colorId = :colorId")
    suspend fun deleteForColor(colorId: Long)
}
```

- [ ] **Step 4: AppDatabase ו-Hilt**

`data/db/AppDatabase.kt`:
```kotlin
package com.amir.filamenttracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MaterialEntity::class, ColorEntity::class, ProductEntity::class, OrderEntity::class,
        SpoolEntity::class, SpoolEventEntity::class, StockTargetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun spoolDao(): SpoolDao
    abstract fun orderDao(): OrderDao
    abstract fun stockDao(): StockDao

    companion object {
        const val NAME = "filament_tracker.db"

        /** The two materials in use today (spec section 1). More are added from the catalog screen. */
        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO materials (name, archived) VALUES ('PLA', 0), ('PETG', 0)")
            }
        }
    }
}
```

`data/di/DatabaseModule.kt`:
```kotlin
package com.amir.filamenttracker.data.di

import android.content.Context
import androidx.room.Room
import com.amir.filamenttracker.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(AppDatabase.SEED_CALLBACK)
            .build()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
```

- [ ] **Step 5: כלי בדיקה**

`app/src/test/java/com/amir/filamenttracker/testutil/TestDb.kt`:
```kotlin
package com.amir.filamenttracker.testutil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amir.filamenttracker.data.db.AppDatabase

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .addCallback(AppDatabase.SEED_CALLBACK)
        .allowMainThreadQueries()
        .build()
```

`app/src/test/java/com/amir/filamenttracker/testutil/FakeClock.kt`:
```kotlin
package com.amir.filamenttracker.testutil

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeClock(var now: Instant = Instant.parse("2026-10-01T10:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    fun advance(duration: Duration) { now = now.plus(duration) }
}
```

- [ ] **Step 6: בדיקות המסד (נכשלות עד שהקוד מתקמפל)**

`app/src/test/java/com/amir/filamenttracker/data/DatabaseTest.kt`:
```kotlin
package com.amir.filamenttracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.ProductEntity
import com.amir.filamenttracker.data.db.SpoolEntity
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test
    fun `PLA and PETG are seeded`() = runTest {
        assertEquals(listOf("PETG", "PLA"), db.catalogDao().activeMaterials().first().map { it.name })
    }

    @Test
    fun `spool rows join names and keep status`() = runTest {
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().insertColor(ColorEntity(name = "שחור", hex = "#000000"))
        val product = db.catalogDao().insertProduct(ProductEntity(brand = "TEMPlast", materialId = pla, colorId = black))
        db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.IN_USE, amsSlot = 2))

        val row = db.spoolDao().spoolRows().first().single()
        assertEquals("TEMPlast PLA שחור", "${row.brand} ${row.materialName} ${row.colorName}")
        assertEquals(SpoolStatus.IN_USE, row.status)
        assertEquals(2, row.amsSlot)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `two spools can never share a slot`() = runTest {
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().insertColor(ColorEntity(name = "שחור"))
        val product = db.catalogDao().insertProduct(ProductEntity(brand = "X", materialId = pla, colorId = black))
        db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.IN_USE, amsSlot = 1))
        db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.IN_USE, amsSlot = 1))
    }

    @Test
    fun `many spools outside slots are fine`() = runTest {
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().insertColor(ColorEntity(name = "שחור"))
        val product = db.catalogDao().insertProduct(ProductEntity(brand = "X", materialId = pla, colorId = black))
        repeat(3) {
            db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))
        }
        assertEquals(3, db.spoolDao().spoolRows().first().size)
    }
}
```

- [ ] **Step 7: להריץ את כל הבדיקות**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest
```
Expected: PASS. בנוסף 4 בדיקות `DatabaseTest`, והקובץ `app/schemas/com.amir.filamenttracker.data.db.AppDatabase/1.json` נוצר. הריצה הראשונה של Robolectric מורידה את `android-all` של SDK 34, וזה לוקח כמה דקות.

- [ ] **Step 8: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src app/schemas
git commit -m "feat(data): מסד Room - ישויות, DAO, זריעת PLA ו-PETG

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: ריפוזיטורי הקטלוג ונרמול גוון

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/ColorHex.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/data/CatalogRepository.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/domain/ColorHexTest.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/data/CatalogRepositoryTest.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `CatalogDao`, `StockDao`, the entities and `ProductRow` (Task 6).
- Produces:
  - `ColorHex.normalize(input: String): String?`. קלט ריק מחזיר null. קלט תקין מחזיר `"#RRGGBB"` באותיות גדולות. כל קלט אחר זורק `IllegalArgumentException` עם הודעה בעברית.
  - `enum class RemoveResult { DELETED, ARCHIVED }`
  - `class CatalogRepository @Inject constructor(db: AppDatabase)`:
    - `val materials: Flow<List<MaterialEntity>>`, `val colors: Flow<List<ColorEntity>>`, `val products: Flow<List<ProductRow>>`. כולם פריטים פעילים בלבד.
    - `suspend fun addMaterial(name: String): Long`, `suspend fun addColor(name: String, hexInput: String = ""): Long`
    - `suspend fun createProduct(brand: String, materialName: String, colorName: String, colorHex: String, weightGrams: Int, note: String): Long`
    - `suspend fun renameMaterial(id: Long, name: String)`, `suspend fun updateColor(id: Long, name: String, hexInput: String)`, `suspend fun updateProduct(id: Long, brand: String, weightGrams: Int, note: String)`
    - `suspend fun removeMaterial(id: Long): RemoveResult`, `removeColor(id)`, `removeProduct(id)`

- [ ] **Step 1: הבדיקות הנכשלות**

`app/src/test/java/com/amir/filamenttracker/domain/ColorHexTest.kt`:
```kotlin
package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorHexTest {
    @Test fun `blank means no swatch`() { assertNull(ColorHex.normalize("  ")) }
    @Test fun `adds hash and upper-cases`() { assertEquals("#1A2B3C", ColorHex.normalize("1a2b3c")) }
    @Test fun `keeps hash and trims`() { assertEquals("#ABCDEF", ColorHex.normalize(" #abcdef ")) }
    @Test(expected = IllegalArgumentException::class) fun `color word is rejected`() { ColorHex.normalize("red") }
    @Test(expected = IllegalArgumentException::class) fun `five digits are rejected`() { ColorHex.normalize("#12345") }
    @Test(expected = IllegalArgumentException::class) fun `seven digits are rejected`() { ColorHex.normalize("#1234567") }
}
```

`app/src/test/java/com/amir/filamenttracker/data/CatalogRepositoryTest.kt`:
```kotlin
package com.amir.filamenttracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.SpoolEntity
import com.amir.filamenttracker.data.db.StockTargetEntity
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CatalogRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CatalogRepository

    @Before fun setUp() { db = inMemoryDb(); repo = CatalogRepository(db) }
    @After fun tearDown() { db.close() }

    @Test
    fun `material names match regardless of case and outer spaces`() = runTest {
        val seeded = db.catalogDao().materialByName("PLA")!!.id
        assertEquals(seeded, repo.addMaterial("  pla "))
        assertEquals(2, repo.materials.first().size)
    }

    @Test
    fun `color names are trimmed and inner spaces collapsed`() = runTest {
        val id = repo.addColor("ירוק ים")
        assertEquals(id, repo.addColor("  ירוק   ים "))
        assertEquals(1, repo.colors.first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank material name is rejected`() = runTest { repo.addMaterial("   ") }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed hex is rejected`() = runTest { repo.addColor("אדום", "red") }

    @Test
    fun `existing color without hex picks up a new hex`() = runTest {
        val id = repo.addColor("לבן")
        repo.addColor("לבן", "#ffffff")
        assertEquals("#FFFFFF", repo.colors.first().single { it.id == id }.hex)
    }

    @Test
    fun `createProduct creates a missing material and color`() = runTest {
        val id = repo.createProduct("Sunlu", "TPU", "כחול", "", 1000, "")
        val product = repo.products.first().single()
        assertEquals(id, product.productId)
        assertEquals("TPU", product.materialName)
        assertEquals("כחול", product.colorName)
        assertEquals(3, repo.materials.first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank brand is rejected`() = runTest { repo.createProduct(" ", "PLA", "שחור", "", 1000, "") }

    @Test(expected = IllegalArgumentException::class)
    fun `zero weight is rejected`() = runTest { repo.createProduct("X", "PLA", "שחור", "", 0, "") }

    @Test
    fun `product with spools is archived, product without spools is deleted`() = runTest {
        val used = repo.createProduct("A", "PLA", "שחור", "", 1000, "")
        val unused = repo.createProduct("B", "PLA", "לבן", "", 1000, "")
        db.spoolDao().insertSpool(SpoolEntity(productId = used, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))

        assertEquals(RemoveResult.ARCHIVED, repo.removeProduct(used))
        assertEquals(RemoveResult.DELETED, repo.removeProduct(unused))
        assertTrue(repo.products.first().isEmpty())
    }

    @Test
    fun `color in use is archived, its target dropped, and re-adding revives it`() = runTest {
        repo.createProduct("A", "PLA", "שחור", "", 1000, "")
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().colorByName("שחור")!!.id
        db.stockDao().upsert(StockTargetEntity(pla, black, 1))

        assertEquals(RemoveResult.ARCHIVED, repo.removeColor(black))
        assertTrue(repo.colors.first().isEmpty())
        assertTrue(db.stockDao().targetRows().first().none { it.minSealed != null })

        assertEquals(black, repo.addColor("שחור"))
        assertEquals(1, repo.colors.first().size)
    }

    @Test
    fun `unused color is deleted`() = runTest {
        val id = repo.addColor("סגול")
        assertEquals(RemoveResult.DELETED, repo.removeColor(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `renaming a material onto an existing name is rejected`() = runTest {
        val petg = db.catalogDao().materialByName("PETG")!!.id
        repo.renameMaterial(petg, "pla")
    }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*ColorHexTest" --tests "*CatalogRepositoryTest"
```
Expected: FAIL, `Unresolved reference: ColorHex` / `CatalogRepository`.

- [ ] **Step 3: המימוש**

`domain/ColorHex.kt`:
```kotlin
package com.amir.filamenttracker.domain

object ColorHex {
    private val pattern = Regex("^#?([0-9A-Fa-f]{6})$")

    /** Blank input means "no swatch" (null). Otherwise "#RRGGBB" in upper case. */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val match = pattern.matchEntire(trimmed)
            ?: throw IllegalArgumentException("גוון לא תקין. יש להזין 6 ספרות הקס, למשל #1A2B3C")
        return "#" + match.groupValues[1].uppercase()
    }
}
```

`data/CatalogRepository.kt`:
```kotlin
package com.amir.filamenttracker.data

import androidx.room.withTransaction
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.domain.ColorHex
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

enum class RemoveResult { DELETED, ARCHIVED }

@Singleton
class CatalogRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.catalogDao()
    private val stockDao = db.stockDao()

    val materials: Flow<List<MaterialEntity>> = dao.activeMaterials()
    val colors: Flow<List<ColorEntity>> = dao.activeColors()
    val products: Flow<List<ProductRow>> = dao.activeProducts()

    /** Id of the material with this name: the existing one (revived if archived) or a new one. */
    suspend fun addMaterial(name: String): Long = db.withTransaction {
        val clean = cleanName(name)
        val existing = dao.materialByName(clean)
        if (existing == null) {
            dao.insertMaterial(MaterialEntity(name = clean))
        } else {
            if (existing.archived) dao.setMaterialArchived(existing.id, false)
            existing.id
        }
    }

    /** Like [addMaterial]. A hex given for an existing color only fills in a missing swatch. */
    suspend fun addColor(name: String, hexInput: String = ""): Long = db.withTransaction {
        val clean = cleanName(name)
        val hex = ColorHex.normalize(hexInput)
        val existing = dao.colorByName(clean)
        if (existing == null) {
            dao.insertColor(ColorEntity(name = clean, hex = hex))
        } else {
            if (existing.archived) dao.setColorArchived(existing.id, false)
            if (existing.hex == null && hex != null) dao.updateColor(existing.id, existing.name, hex)
            existing.id
        }
    }

    suspend fun createProduct(
        brand: String,
        materialName: String,
        colorName: String,
        colorHex: String,
        weightGrams: Int,
        note: String,
    ): Long = db.withTransaction {
        val cleanBrand = cleanName(brand, what = "מותג")
        require(weightGrams > 0) { "משקל חייב להיות גדול מאפס" }
        val materialId = addMaterial(materialName)
        val colorId = addColor(colorName, colorHex)
        dao.insertProduct(
            ProductEntity(
                brand = cleanBrand, materialId = materialId, colorId = colorId,
                weightGrams = weightGrams, note = note.trim(),
            ),
        )
    }

    suspend fun renameMaterial(id: Long, name: String) = db.withTransaction {
        val clean = cleanName(name)
        val clash = dao.materialByName(clean)
        require(clash == null || clash.id == id) { "כבר קיים חומר בשם $clean" }
        dao.renameMaterial(id, clean)
    }

    suspend fun updateColor(id: Long, name: String, hexInput: String) = db.withTransaction {
        val clean = cleanName(name)
        val clash = dao.colorByName(clean)
        require(clash == null || clash.id == id) { "כבר קיים צבע בשם $clean" }
        dao.updateColor(id, clean, ColorHex.normalize(hexInput))
    }

    suspend fun updateProduct(id: Long, brand: String, weightGrams: Int, note: String) {
        require(weightGrams > 0) { "משקל חייב להיות גדול מאפס" }
        dao.updateProduct(id, cleanName(brand, what = "מותג"), weightGrams, note.trim())
    }

    /** Spec section 4: archive when something still uses it, otherwise delete. */
    suspend fun removeMaterial(id: Long): RemoveResult = db.withTransaction {
        if (dao.productCountForMaterial(id) > 0) {
            dao.setMaterialArchived(id, true)
            stockDao.deleteForMaterial(id)
            RemoveResult.ARCHIVED
        } else {
            dao.deleteMaterial(id)
            RemoveResult.DELETED
        }
    }

    suspend fun removeColor(id: Long): RemoveResult = db.withTransaction {
        if (dao.productCountForColor(id) > 0) {
            dao.setColorArchived(id, true)
            stockDao.deleteForColor(id)
            RemoveResult.ARCHIVED
        } else {
            dao.deleteColor(id)
            RemoveResult.DELETED
        }
    }

    suspend fun removeProduct(id: Long): RemoveResult = db.withTransaction {
        if (dao.spoolCountForProduct(id) > 0) {
            dao.setProductArchived(id, true)
            RemoveResult.ARCHIVED
        } else {
            dao.deleteProduct(id)
            RemoveResult.DELETED
        }
    }

    // NOCASE lookups handle Latin case; this handles spaces, so "PLA " and "pla" are one row.
    private fun cleanName(raw: String, what: String = "שם"): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotEmpty()) { "$what לא יכול להיות ריק" }
        return clean
    }
}
```

- [ ] **Step 4: להריץ ולוודא הצלחה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*ColorHexTest" --tests "*CatalogRepositoryTest"
```
Expected: PASS (6 + 12 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(data): ריפוזיטורי קטלוג - איחוד שמות, ארכיון, נרמול גוון

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: ריפוזיטורי המלאי - הזמנות, מלאי קיים, חריצים וביטול

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/data/InventoryRepository.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/data/InventoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `SpoolPlanner`, `SpoolChange`, the exceptions (Tasks 2-3); `AppDatabase`, `SpoolDao`, `OrderDao`, mappers, projections (Task 6); `CatalogRepository.createProduct` (Task 7, tests only); `FakeClock`, `inMemoryDb` (Task 6).
- Produces: `class InventoryRepository @Inject constructor(db: AppDatabase, clock: Clock)`:
  - `val spools: Flow<List<SpoolRow>>`, `val orders: Flow<List<OrderSummary>>`
  - `fun spool(id: Long): Flow<SpoolRow?>`, `fun events(spoolId: Long): Flow<List<SpoolEventEntity>>`, `fun order(id: Long): Flow<OrderEntity?>`, `fun orderSpools(orderId: Long): Flow<List<SpoolRow>>`
  - `suspend fun addExisting(productId: Long, quantity: Int, status: SpoolStatus, slot: Int?): List<Long>`
  - `suspend fun createOrder(vendor: String, orderNumber: String?, expectedAt: LocalDate?, items: Map<Long, Int>): Long`
  - `suspend fun markArrived(spoolIds: Collection<Long>)`, `suspend fun markOrderArrived(orderId: Long)`
  - `suspend fun removeOrderedSpool(spoolId: Long)`, `suspend fun deleteOrder(orderId: Long)`
  - `suspend fun loadIntoSlot(spoolId: Long, slot: Int)`, `suspend fun unload(spoolId: Long)`, `suspend fun finish(spoolId: Long)`, `suspend fun undoLast(spoolId: Long)`
  - כל כשל בכלל עסקי זורק `SpoolRuleException`, וכל קלט שגוי זורק `IllegalArgumentException`. בשני המקרים ההודעה בעברית.

- [ ] **Step 1: הבדיקות הנכשלות**

`app/src/test/java/com/amir/filamenttracker/data/InventoryRepositoryTest.kt`:
```kotlin
package com.amir.filamenttracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.domain.IllegalTransitionException
import com.amir.filamenttracker.domain.SlotOccupiedException
import com.amir.filamenttracker.domain.SpoolRuleException
import com.amir.filamenttracker.domain.UndoNotPossibleException
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import com.amir.filamenttracker.testutil.FakeClock
import com.amir.filamenttracker.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InventoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: InventoryRepository
    private val clock = FakeClock()
    private var black = 0L
    private var white = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        repo = InventoryRepository(db, clock)
        val catalog = CatalogRepository(db)
        black = catalog.createProduct("TEMPlast", "PLA", "שחור", "#000000", 1000, "")
        white = catalog.createProduct("TEMPlast", "PLA", "לבן", "#FFFFFF", 1000, "")
    }

    @After fun tearDown() { db.close() }

    private suspend fun spool(id: Long) = db.spoolDao().spool(id)!!
    private suspend fun events(id: Long) = db.spoolDao().events(id).first()

    @Test
    fun `existing sealed spools are created with one creation event each`() = runTest {
        val ids = repo.addExisting(black, 3, SEALED, slot = null)
        assertEquals(3, ids.size)
        ids.forEach { id ->
            assertEquals(SEALED, spool(id).status)
            val e = events(id).single()
            assertNull(e.fromStatus)
            assertEquals(SEALED, e.toStatus)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `in-use spools are added one at a time`() = runTest { repo.addExisting(black, 2, IN_USE, slot = 1) }

    @Test(expected = IllegalArgumentException::class)
    fun `in-use spool needs a slot`() = runTest { repo.addExisting(black, 1, IN_USE, slot = null) }

    @Test(expected = IllegalArgumentException::class)
    fun `existing stock cannot start as ordered`() = runTest { repo.addExisting(black, 1, ORDERED, slot = null) }

    @Test
    fun `existing in-use spool into an occupied slot moves the occupant to storage`() = runTest {
        val old = repo.addExisting(white, 1, IN_USE, slot = 1).single()
        val new = repo.addExisting(black, 1, IN_USE, slot = 1).single()
        assertEquals(OPEN_STORED, spool(old).status)
        assertNull(spool(old).amsSlot)
        assertEquals(IN_USE, spool(new).status)
        assertEquals(1, spool(new).amsSlot)
    }

    @Test
    fun `an order creates ordered spools per quantity`() = runTest {
        val orderId = repo.createOrder("Amazon", "123-456", null, mapOf(black to 2, white to 1))
        val rows = repo.orderSpools(orderId).first()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.status == ORDERED && it.orderId == orderId })
        val summary = repo.orders.first().single()
        assertEquals(3, summary.spoolCount)
        assertEquals(3, summary.pendingCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an order without items is rejected`() = runTest { repo.createOrder("Amazon", null, null, emptyMap()) }

    @Test(expected = IllegalArgumentException::class)
    fun `an order whose quantities are all zero is rejected`() = runTest {
        repo.createOrder("Amazon", null, null, mapOf(black to 0))
    }

    @Test
    fun `arrival per spool and per order`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2, white to 1))
        val first = repo.orderSpools(orderId).first().first().spoolId
        repo.markArrived(listOf(first))
        assertEquals(2, repo.orders.first().single().pendingCount)

        repo.markOrderArrived(orderId)
        assertEquals(0, repo.orders.first().single().pendingCount)
        assertTrue(repo.orderSpools(orderId).first().all { it.status == SEALED })
    }

    @Test
    fun `only a spool that has not arrived can be removed from an order`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2))
        val (a, b) = repo.orderSpools(orderId).first().map { it.spoolId }
        repo.markArrived(listOf(a))
        try {
            repo.removeOrderedSpool(a)
            throw AssertionError("expected SpoolRuleException")
        } catch (expected: SpoolRuleException) {
        }
        repo.removeOrderedSpool(b)
        assertEquals(listOf(a), repo.orderSpools(orderId).first().map { it.spoolId })
    }

    @Test
    fun `an order can be deleted only while nothing has arrived`() = runTest {
        val arrived = repo.createOrder("Amazon", null, null, mapOf(black to 1))
        repo.markOrderArrived(arrived)
        try {
            repo.deleteOrder(arrived)
            throw AssertionError("expected SpoolRuleException")
        } catch (expected: SpoolRuleException) {
        }

        val pending = repo.createOrder("Amazon", null, null, mapOf(white to 2))
        repo.deleteOrder(pending)
        assertEquals(listOf(arrived), repo.orders.first().map { it.id })
        assertEquals(1, repo.spools.first().size)
    }

    @Test
    fun `loading into an occupied slot swaps without a constraint crash`() = runTest {
        val inSlot = repo.addExisting(white, 1, IN_USE, slot = 2).single()
        val next = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.loadIntoSlot(next, 2)
        assertEquals(OPEN_STORED, spool(inSlot).status)
        assertEquals(IN_USE, spool(next).status)
        assertEquals(2, spool(next).amsSlot)
    }

    @Test(expected = IllegalTransitionException::class)
    fun `a finished spool cannot be loaded`() = runTest {
        val id = repo.addExisting(black, 1, IN_USE, slot = 1).single()
        repo.finish(id)
        repo.loadIntoSlot(id, 1)
    }

    @Test
    fun `finish clears the slot and logs an event`() = runTest {
        val id = repo.addExisting(black, 1, IN_USE, slot = 3).single()
        repo.finish(id)
        assertEquals(FINISHED, spool(id).status)
        assertNull(spool(id).amsSlot)
        assertEquals(FINISHED, events(id).first().toStatus)
    }

    @Test
    fun `undo of finish restores the slot and removes the event`() = runTest {
        val id = repo.addExisting(black, 1, IN_USE, slot = 3).single()
        repo.finish(id)
        repo.undoLast(id)
        assertEquals(IN_USE, spool(id).status)
        assertEquals(3, spool(id).amsSlot)
        assertEquals(1, events(id).size)
    }

    @Test
    fun `two undos walk back two steps`() = runTest {
        val id = repo.addExisting(black, 1, SEALED, slot = null).single()
        clock.advance(Duration.ofMinutes(1))
        repo.loadIntoSlot(id, 2)
        clock.advance(Duration.ofMinutes(1))
        repo.unload(id)

        repo.undoLast(id)
        assertEquals(IN_USE, spool(id).status)
        assertEquals(2, spool(id).amsSlot)

        repo.undoLast(id)
        assertEquals(SEALED, spool(id).status)
        assertNull(spool(id).amsSlot)
    }

    @Test(expected = UndoNotPossibleException::class)
    fun `the creation event cannot be undone`() = runTest {
        val id = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.undoLast(id)
    }

    @Test
    fun `undo is blocked when the old slot is taken, and nothing changes`() = runTest {
        val first = repo.addExisting(white, 1, IN_USE, slot = 1).single()
        val second = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.loadIntoSlot(second, 1)
        try {
            repo.undoLast(first)
            throw AssertionError("expected SlotOccupiedException")
        } catch (expected: SlotOccupiedException) {
        }
        assertEquals(OPEN_STORED, spool(first).status)
        assertEquals(1, spool(second).amsSlot)
    }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*InventoryRepositoryTest"
```
Expected: FAIL, `Unresolved reference: InventoryRepository`.

- [ ] **Step 3: המימוש**

`data/InventoryRepository.kt`:
```kotlin
package com.amir.filamenttracker.data

import androidx.room.withTransaction
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.OrderEntity
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.data.db.SpoolEntity
import com.amir.filamenttracker.data.db.SpoolEventEntity
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.toDomain
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.SpoolRuleException
import com.amir.filamenttracker.domain.UndoNotPossibleException
import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolChange
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val spoolDao = db.spoolDao()
    private val orderDao = db.orderDao()

    val spools: Flow<List<SpoolRow>> = spoolDao.spoolRows()
    val orders: Flow<List<OrderSummary>> = orderDao.orderSummaries()

    fun spool(id: Long): Flow<SpoolRow?> = spoolDao.spoolRow(id)
    fun events(spoolId: Long): Flow<List<SpoolEventEntity>> = spoolDao.events(spoolId)
    fun order(id: Long): Flow<OrderEntity?> = orderDao.order(id)
    fun orderSpools(orderId: Long): Flow<List<SpoolRow>> = orderDao.orderSpools(orderId)

    /** Stock that existed before the app: no order, created directly in [status]. */
    suspend fun addExisting(productId: Long, quantity: Int, status: SpoolStatus, slot: Int?): List<Long> =
        db.withTransaction {
            require(status == SEALED || status == IN_USE || status == OPEN_STORED) {
                "מלאי קיים נקלט כסגור, בשימוש או פתוח באחסון"
            }
            if (status == IN_USE) {
                require(quantity == 1) { "סליל בשימוש נקלט אחד בכל פעם" }
                require(slot != null && slot in 1..SpoolPlanner.SLOT_COUNT) { "יש לבחור חריץ" }
                SpoolPlanner.vacate(slot, inUse())?.let { apply(listOf(it)) }
            } else {
                require(quantity in 1..50) { "כמות חייבת להיות בין 1 ל-50" }
            }
            val storedSlot = if (status == IN_USE) slot else null
            (1..quantity).map { insertWithEvent(productId, orderId = null, status = status, slot = storedSlot) }
        }

    suspend fun createOrder(
        vendor: String,
        orderNumber: String?,
        expectedAt: LocalDate?,
        items: Map<Long, Int>,
    ): Long = db.withTransaction {
        val lines = items.filterValues { it > 0 }
        require(lines.isNotEmpty()) { "הזמנה חייבת לכלול לפחות סליל אחד" }
        val orderId = orderDao.insertOrder(
            OrderEntity(
                vendor = vendor.trim().ifEmpty { "Amazon" },
                orderedAt = clock.instant(),
                expectedAt = expectedAt,
                orderNumber = orderNumber?.trim()?.ifEmpty { null },
            ),
        )
        lines.forEach { (productId, quantity) ->
            repeat(quantity) { insertWithEvent(productId, orderId, ORDERED, slot = null) }
        }
        orderId
    }

    suspend fun markArrived(spoolIds: Collection<Long>) = db.withTransaction {
        apply(spoolIds.map { SpoolPlanner.move(spoolOrThrow(it), SEALED) })
    }

    suspend fun markOrderArrived(orderId: Long) = db.withTransaction {
        val pending = spoolDao.spoolsOfOrder(orderId).filter { it.status == ORDERED }
        apply(pending.map { SpoolPlanner.move(it.toDomain(), SEALED) })
    }

    /** A spool that will not arrive (partial delivery). Spec: no "cancelled" status, just delete. */
    suspend fun removeOrderedSpool(spoolId: Long) = db.withTransaction {
        if (spoolOrThrow(spoolId).status != ORDERED) {
            throw SpoolRuleException("אפשר למחוק רק סליל שעדיין לא הגיע")
        }
        spoolDao.deleteSpool(spoolId)
    }

    suspend fun deleteOrder(orderId: Long) = db.withTransaction {
        if (spoolDao.spoolsOfOrder(orderId).any { it.status != ORDERED }) {
            throw SpoolRuleException("אפשר למחוק רק הזמנה שאף סליל ממנה עוד לא הגיע")
        }
        orderDao.deleteOrder(orderId) // spools and their events cascade
    }

    suspend fun loadIntoSlot(spoolId: Long, slot: Int) = db.withTransaction {
        apply(SpoolPlanner.load(spoolOrThrow(spoolId), slot, inUse()))
    }

    suspend fun unload(spoolId: Long) = db.withTransaction {
        apply(listOf(SpoolPlanner.move(spoolOrThrow(spoolId), OPEN_STORED)))
    }

    suspend fun finish(spoolId: Long) = db.withTransaction {
        apply(listOf(SpoolPlanner.move(spoolOrThrow(spoolId), FINISHED)))
    }

    /** Reverts the newest event of one spool and deletes it, so a second undo goes one step further. */
    suspend fun undoLast(spoolId: Long) = db.withTransaction {
        val last = spoolDao.lastEvent(spoolId)?.toDomain()
            ?: throw UndoNotPossibleException("אין פעולות לבטל")
        val change = SpoolPlanner.undo(spoolOrThrow(spoolId), last, inUse())
        spoolDao.updateState(change.spoolId, change.toStatus, change.toSlot)
        spoolDao.deleteEvent(last.id)
    }

    private suspend fun spoolOrThrow(id: Long): Spool =
        spoolDao.spool(id)?.toDomain() ?: throw SpoolRuleException("הסליל לא נמצא")

    private suspend fun inUse(): List<Spool> = spoolDao.inUse().map { it.toDomain() }

    private suspend fun insertWithEvent(productId: Long, orderId: Long?, status: SpoolStatus, slot: Int?): Long {
        val id = spoolDao.insertSpool(
            SpoolEntity(productId = productId, orderId = orderId, status = status, amsSlot = slot),
        )
        spoolDao.insertEvent(
            SpoolEventEntity(
                spoolId = id, fromStatus = null, toStatus = status,
                fromSlot = null, toSlot = slot, at = clock.instant(),
            ),
        )
        return id
    }

    // Order matters: a vacated slot must be cleared before another spool takes it (unique index).
    private suspend fun apply(changes: List<SpoolChange>) {
        val now = clock.instant()
        changes.forEach { c ->
            spoolDao.updateState(c.spoolId, c.toStatus, c.toSlot)
            spoolDao.insertEvent(
                SpoolEventEntity(
                    spoolId = c.spoolId, fromStatus = c.fromStatus, toStatus = c.toStatus,
                    fromSlot = c.fromSlot, toSlot = c.toSlot, at = now,
                ),
            )
        }
    }
}
```

- [ ] **Step 4: להריץ ולוודא הצלחה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*InventoryRepositoryTest"
```
Expected: PASS (18 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(data): ריפוזיטורי מלאי - הזמנות, מלאי קיים, חריצים וביטול

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: ריפוזיטורי היעדים, ההתראות והתצרוכת

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/data/StockRepository.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/data/StockRepositoryTest.kt`

**Interfaces:**
- Consumes: `StockCalculator`, `AlertKind`, `StockCount` (Task 4); `StockDao`, `SpoolDao`, `TargetRow`, `FinishRow`, `SpoolRow` (Task 6); `InventoryRepository`, `CatalogRepository` (tests only).
- Produces:
  - `data class AlertRow(kind: AlertKind, materialId: Long, materialName: String, colorId: Long, colorName: String, colorHex: String?, count: StockCount, minSealed: Int)`
  - `class StockRepository @Inject constructor(db: AppDatabase)`:
    - `val targetRows: Flow<List<TargetRow>>`, `val alerts: Flow<List<AlertRow>>`, `val finishes: Flow<List<FinishRow>>`
    - `suspend fun setTarget(materialId: Long, colorId: Long, minSealed: Int)` (0..20), `suspend fun removeTarget(materialId: Long, colorId: Long)`

- [ ] **Step 1: הבדיקות הנכשלות**

`app/src/test/java/com/amir/filamenttracker/data/StockRepositoryTest.kt`:
```kotlin
package com.amir.filamenttracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.testutil.FakeClock
import com.amir.filamenttracker.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StockRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var stock: StockRepository
    private lateinit var inventory: InventoryRepository
    private var blackProduct = 0L
    private var pla = 0L
    private var black = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        stock = StockRepository(db)
        inventory = InventoryRepository(db, FakeClock())
        val catalog = CatalogRepository(db)
        blackProduct = catalog.createProduct("TEMPlast", "PLA", "שחור", "#000000", 1000, "")
        catalog.createProduct("TEMPlast", "PLA", "לבן", "#FFFFFF", 1000, "")
        pla = db.catalogDao().materialByName("PLA")!!.id
        black = db.catalogDao().colorByName("שחור")!!.id
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `target rows list product pairs and start untracked`() = runTest {
        val rows = stock.targetRows.first()
        assertEquals(listOf("לבן", "שחור"), rows.map { it.colorName })
        assertTrue(rows.all { it.minSealed == null })

        stock.setTarget(pla, black, 1)
        assertEquals(1, stock.targetRows.first().single { it.colorId == black }.minSealed)
    }

    @Test
    fun `alerts follow the spool lifecycle`() = runTest {
        stock.setTarget(pla, black, 1)
        val missing = stock.alerts.first().single()
        assertEquals(AlertKind.MISSING, missing.kind)
        assertEquals("PLA", missing.materialName)
        assertEquals("שחור", missing.colorName)

        inventory.addExisting(blackProduct, 1, IN_USE, slot = 1)
        assertEquals(AlertKind.RUNNING_LOW, stock.alerts.first().single().kind)

        val orderId = inventory.createOrder("Amazon", null, null, mapOf(blackProduct to 1))
        assertEquals(AlertKind.ON_THE_WAY, stock.alerts.first().single().kind)

        inventory.markOrderArrived(orderId)
        assertTrue(stock.alerts.first().isEmpty())
    }

    @Test
    fun `untracked pairs never alert`() = runTest {
        assertTrue(stock.alerts.first().isEmpty())
    }

    @Test
    fun `removing a target removes its alert`() = runTest {
        stock.setTarget(pla, black, 1)
        stock.removeTarget(pla, black)
        assertTrue(stock.alerts.first().isEmpty())
        assertNull(stock.targetRows.first().single { it.colorId == black }.minSealed)
    }

    @Test
    fun `finishing logs a finish row and undo removes it`() = runTest {
        val id = inventory.addExisting(blackProduct, 1, IN_USE, slot = 1).single()
        inventory.finish(id)
        assertEquals("שחור", stock.finishes.first().single().colorName)

        inventory.undoLast(id)
        assertTrue(stock.finishes.first().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative minimum is rejected`() = runTest { stock.setTarget(pla, black, -1) }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*StockRepositoryTest"
```
Expected: FAIL, `Unresolved reference: StockRepository`.

- [ ] **Step 3: המימוש**

`data/StockRepository.kt`:
```kotlin
package com.amir.filamenttracker.data

import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.FinishRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.StockTargetEntity
import com.amir.filamenttracker.data.db.TargetRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.StockCalculator
import com.amir.filamenttracker.domain.StockCount
import com.amir.filamenttracker.domain.model.StockKey
import com.amir.filamenttracker.domain.model.StockTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class AlertRow(
    val kind: AlertKind,
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val count: StockCount,
    val minSealed: Int,
)

@Singleton
class StockRepository @Inject constructor(db: AppDatabase) {
    private val stockDao = db.stockDao()
    private val spoolDao = db.spoolDao()

    val targetRows: Flow<List<TargetRow>> = stockDao.targetRows()

    val alerts: Flow<List<AlertRow>> =
        combine(spoolDao.spoolRows(), stockDao.targetRows()) { spools, rows -> buildAlerts(spools, rows) }

    val finishes: Flow<List<FinishRow>> = spoolDao.finishEvents()

    suspend fun setTarget(materialId: Long, colorId: Long, minSealed: Int) {
        require(minSealed in 0..20) { "מינימום חייב להיות בין 0 ל-20" }
        stockDao.upsert(StockTargetEntity(materialId, colorId, minSealed))
    }

    suspend fun removeTarget(materialId: Long, colorId: Long) = stockDao.delete(materialId, colorId)

    private fun buildAlerts(spools: List<SpoolRow>, rows: List<TargetRow>): List<AlertRow> {
        val counts = StockCalculator.count(spools.map { StockKey(it.materialId, it.colorId) to it.status })
        val tracked = rows.filter { it.minSealed != null }.associateBy { StockKey(it.materialId, it.colorId) }
        val targets = tracked.map { (key, row) -> StockTarget(key, row.minSealed!!) }
        return StockCalculator.alerts(counts, targets).map { alert ->
            val row = tracked.getValue(alert.key)
            AlertRow(
                kind = alert.kind, materialId = row.materialId, materialName = row.materialName,
                colorId = row.colorId, colorName = row.colorName, colorHex = row.colorHex,
                count = alert.count, minSealed = alert.minSealed,
            )
        }
    }
}
```

- [ ] **Step 4: להריץ את כל הבדיקות**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest
```
Expected: PASS. כל הבדיקות עד כאן, כולל 6 של `StockRepositoryTest`.

- [ ] **Step 5: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(data): יעדי מלאי, התראות ואירועי סיום

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: מעטפת האפליקציה - ניווט, רכיבים משותפים ודיאלוגי מוצר

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/common/BaseViewModel.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/common/UiFormat.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/common/Components.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/common/ProductDialogs.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/navigation/Routes.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/navigation/AppNav.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/MainActivity.kt` (להחליף `Text("מלאי פילמנטים")` ב-`AppNav()`)
- Test: `app/src/test/java/com/amir/filamenttracker/presentation/common/UiFormatTest.kt`

**Interfaces:**
- Consumes: `SpoolStatus`, `AlertKind`, `Forecast`, `ColorHex` (domain); `MaterialEntity`, `ColorEntity`, `ProductRow`, `title` (data).
- Produces:
  - `abstract class BaseViewModel : ViewModel()` עם `val messages: SharedFlow<String>`, `protected fun launchAction(block: suspend () -> String?)`, `protected fun <T> Flow<T>.asState(initial: T): StateFlow<T>`
  - `SpoolStatus.label()`, `SpoolRow.statusText()`, `countLabel(status, n)`, `Instant.formatDate()`, `Instant.formatDateTime()`, `LocalDate.formatDate()`, `Duration.formatInterval()`, `AlertKind.title()`, `Forecast.label(): String?`
  - `NoInsets`, `parseHexOrNull(hex: String?): Color?`, `ColorDot(hex, modifier, size)`, `BackTopBar(title, onBack, actions)`, `SectionHeader(title, actionLabel, onAction)`, `RowActions(onEdit, onRemove)`, `Stepper(value, onChange, min, max)`, `ConfirmDialog(text, confirmLabel, onConfirm, onDismiss)`, `TextFieldsDialog(title, fields, onDismiss, onConfirm)`, `CollectMessages(messages, host)`
  - `data class NewProductInput(...)`, `NewProductDialog(materials, colors, onDismiss, onConfirm)`, `ProductPickerDialog(products, onDismiss, onPick, onNewProduct)`
  - `object Routes` (קבועים ופונקציות `spool(id)`, `order(id)`, `colorSpools(materialId, colorId)`), `AppNav()`. כל המסלולים רשומים, וכל אחד מציג בינתיים `PlaceholderScreen`.

- [ ] **Step 1: הבדיקות הנכשלות**

`app/src/test/java/com/amir/filamenttracker/presentation/common/UiFormatTest.kt`:
```kotlin
package com.amir.filamenttracker.presentation.common

import androidx.compose.ui.graphics.Color
import com.amir.filamenttracker.domain.model.SpoolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class UiFormatTest {
    @Test fun `short interval reads in days`() { assertEquals("סליל כל ~10 ימים", Duration.ofDays(10).formatInterval()) }
    @Test fun `long interval reads in weeks`() { assertEquals("סליל כל ~3 שבועות", Duration.ofDays(21).formatInterval()) }
    @Test fun `tiny interval reads as daily`() { assertEquals("בערך סליל ביום", Duration.ofHours(5).formatInterval()) }

    @Test fun `singular and plural counts`() {
        assertEquals("1 סגור", countLabel(SpoolStatus.SEALED, 1))
        assertEquals("2 סגורים", countLabel(SpoolStatus.SEALED, 2))
        assertEquals("3 בשימוש", countLabel(SpoolStatus.IN_USE, 3))
    }

    @Test fun `valid hex parses`() { assertEquals(Color(0xFF1A2B3C), parseHexOrNull("#1A2B3C")) }
    @Test fun `broken hex never throws`() {
        assertNull(parseHexOrNull(null))
        assertNull(parseHexOrNull("red"))
        assertNull(parseHexOrNull("#12345"))
        assertNull(parseHexOrNull("#-12345"))
    }
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*UiFormatTest"
```
Expected: FAIL, `Unresolved reference: formatInterval`.

- [ ] **Step 3: עיצוב טקסט ו-BaseViewModel**

`presentation/common/UiFormat.kt`:
```kotlin
package com.amir.filamenttracker.presentation.common

import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.Forecast
import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun SpoolStatus.label(): String = when (this) {
    SpoolStatus.ORDERED -> "הוזמן"
    SpoolStatus.SEALED -> "סגור"
    SpoolStatus.IN_USE -> "בשימוש"
    SpoolStatus.OPEN_STORED -> "פתוח באחסון"
    SpoolStatus.FINISHED -> "הסתיים"
}

fun SpoolRow.statusText(): String =
    if (amsSlot != null) "${status.label()} · חריץ $amsSlot" else status.label()

fun countLabel(status: SpoolStatus, n: Int): String = when (status) {
    SpoolStatus.IN_USE -> "$n בשימוש"
    SpoolStatus.OPEN_STORED -> if (n == 1) "1 פתוח" else "$n פתוחים"
    SpoolStatus.SEALED -> if (n == 1) "1 סגור" else "$n סגורים"
    SpoolStatus.ORDERED -> "$n בדרך"
    SpoolStatus.FINISHED -> if (n == 1) "1 הסתיים" else "$n הסתיימו"
}

private val dateFormat = DateTimeFormatter.ofPattern("d/M/yyyy")
private val dateTimeFormat = DateTimeFormatter.ofPattern("d/M/yyyy HH:mm")

fun Instant.formatDate(): String = atZone(ZoneId.systemDefault()).format(dateFormat)
fun Instant.formatDateTime(): String = atZone(ZoneId.systemDefault()).format(dateTimeFormat)
fun LocalDate.formatDate(): String = format(dateFormat)

fun Duration.formatInterval(): String {
    val days = toDays()
    return when {
        days <= 1 -> "בערך סליל ביום"
        days < 14 -> "סליל כל ~$days ימים"
        else -> "סליל כל ~${days / 7} שבועות"
    }
}

fun AlertKind.title(): String = when (this) {
    AlertKind.MISSING -> "חסר"
    AlertKind.RUNNING_LOW -> "הולך להיות חסר"
    AlertKind.ON_THE_WAY -> "בדרך"
}

/** Null when there is nothing worth showing. */
fun Forecast.label(): String? = when (this) {
    Forecast.NotEnoughHistory -> null
    Forecast.OutOfStock -> "אזל מהמלאי"
    Forecast.Soon -> "צפי סיום: בקרוב"
    is Forecast.At -> "צפי סיום: ${date.formatDate()}"
}
```

`presentation/common/BaseViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.domain.SpoolRuleException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Runs [block] and shows the text it returns. A broken rule or bad input shows its
     * Hebrew message instead of crashing.
     */
    protected fun launchAction(block: suspend () -> String?) {
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: SpoolRuleException) {
                e.message
            } catch (e: IllegalArgumentException) {
                e.message ?: "קלט לא תקין"
            }
            message?.let { _messages.emit(it) }
        }
    }

    protected fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
```

- [ ] **Step 4: רכיבים משותפים**

`presentation/common/Components.kt`:
```kotlin
package com.amir.filamenttracker.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

/** Tab screens sit inside the shell Scaffold, whose bars already handle the system insets. */
val NoInsets = WindowInsets(0, 0, 0, 0)

/** Never throws: anything that is not exactly six hex digits gives null. */
fun parseHexOrNull(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6 || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or digits.toLong(16))
}

@Composable
fun ColorDot(hex: String?, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val fill = parseHexOrNull(hex) ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
        },
        actions = actions,
    )
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun RowActions(onEdit: () -> Unit, onRemove: () -> Unit) {
    Row {
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "עריכה") }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "הסרה") }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 50) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Default.Remove, contentDescription = "פחות")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Icon(Icons.Default.Add, contentDescription = "יותר")
        }
    }
}

@Composable
fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** A dialog of labelled single-line fields. [fields] is label to initial value. */
@Composable
fun TextFieldsDialog(
    title: String,
    fields: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val values = remember { fields.map { it.second }.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, (label, _) ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = it },
                        label = { Text(label) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(values.toList()); onDismiss() }) { Text("שמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
fun CollectMessages(messages: Flow<String>, host: SnackbarHostState) {
    LaunchedEffect(messages) { messages.collect { host.showSnackbar(it) } }
}
```

- [ ] **Step 5: דיאלוגי מוצר (משמשים את הקטלוג, קליטת מלאי והזמנה חדשה)**

`presentation/common/ProductDialogs.kt`:
```kotlin
package com.amir.filamenttracker.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.ColorHex

data class NewProductInput(
    val brand: String,
    val materialName: String,
    val colorName: String,
    val colorHex: String,
    val weightGrams: Int,
    val note: String,
)

/**
 * Material and color are typed as names: existing ones can be picked from chips, and a new
 * name creates a new material or color (CatalogRepository merges names that already exist).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProductDialog(
    materials: List<MaterialEntity>,
    colors: List<ColorEntity>,
    onDismiss: () -> Unit,
    onConfirm: (NewProductInput) -> Unit,
) {
    var brand by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf(materials.firstOrNull()?.name ?: "") }
    var color by rememberSaveable { mutableStateOf("") }
    var hex by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("1000") }
    var note by rememberSaveable { mutableStateOf("") }

    val weightValue = weight.toIntOrNull() ?: 0
    val hexValid = runCatching { ColorHex.normalize(hex) }.isSuccess
    val canSave = brand.isNotBlank() && material.isNotBlank() && color.isNotBlank() && weightValue > 0 && hexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מוצר חדש") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(brand, { brand = it }, label = { Text("מותג") }, singleLine = true)
                OutlinedTextField(material, { material = it }, label = { Text("חומר") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    materials.forEach { m ->
                        FilterChip(selected = material == m.name, onClick = { material = m.name }, label = { Text(m.name) })
                    }
                }
                OutlinedTextField(color, { color = it }, label = { Text("צבע") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    colors.forEach { c ->
                        FilterChip(
                            selected = color == c.name,
                            onClick = { color = c.name; if (hex.isBlank()) hex = c.hex ?: "" },
                            label = { Text(c.name) },
                            leadingIcon = { ColorDot(c.hex, size = 14.dp) },
                        )
                    }
                }
                OutlinedTextField(
                    hex, { hex = it },
                    label = { Text("גוון, לא חובה (למשל #1A2B3C)") },
                    singleLine = true,
                    isError = !hexValid,
                    trailingIcon = { ColorDot(hex) },
                )
                OutlinedTextField(
                    weight, { weight = it.filter(Char::isDigit) },
                    label = { Text("משקל בגרמים") },
                    singleLine = true,
                    isError = weightValue <= 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(note, { note = it }, label = { Text("הערה") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onConfirm(NewProductInput(brand, material, color, hex, weightValue, note)) },
            ) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
fun ProductPickerDialog(
    products: List<ProductRow>,
    onDismiss: () -> Unit,
    onPick: (ProductRow) -> Unit,
    onNewProduct: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("בחירת מוצר") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("+ מוצר חדש") },
                        modifier = Modifier.clickable(onClick = onNewProduct),
                    )
                }
                items(products, key = { it.productId }) { p ->
                    ListItem(
                        headlineContent = { Text(p.title) },
                        supportingContent = { Text("${p.weightGrams} ג'") },
                        leadingContent = { ColorDot(p.colorHex) },
                        modifier = Modifier.clickable { onPick(p) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
```

- [ ] **Step 6: ניווט**

`presentation/navigation/Routes.kt`:
```kotlin
package com.amir.filamenttracker.presentation.navigation

object Routes {
    const val INVENTORY = "inventory"
    const val AMS = "ams"
    const val ORDERS = "orders"
    const val CONSUMPTION = "consumption"
    const val CATALOG = "catalog"
    const val TARGETS = "targets"
    const val ADD_EXISTING = "add_existing"
    const val NEW_ORDER = "new_order"

    const val SPOOL = "spool/{spoolId}"
    fun spool(id: Long) = "spool/$id"

    const val ORDER = "order/{orderId}"
    fun order(id: Long) = "order/$id"

    const val COLOR_SPOOLS = "color/{materialId}/{colorId}"
    fun colorSpools(materialId: Long, colorId: Long) = "color/$materialId/$colorId"
}
```

`presentation/navigation/AppNav.kt`:
```kotlin
package com.amir.filamenttracker.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amir.filamenttracker.presentation.common.NoInsets

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.INVENTORY, "מלאי", Icons.Outlined.Inventory2),
    Tab(Routes.AMS, "AMS", Icons.Outlined.ViewModule),
    Tab(Routes.ORDERS, "הזמנות", Icons.Outlined.LocalShipping),
    Tab(Routes.CONSUMPTION, "תצרוכת", Icons.Outlined.BarChart),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentTab = tabs.firstOrNull { it.route == backStack?.destination?.route }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        // Secondary screens bring their own Scaffold and insets. Here only the bars take insets.
        contentWindowInsets = NoInsets,
        topBar = {
            if (currentTab != null) {
                TopAppBar(
                    title = { Text(currentTab.label) },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "תפריט")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("קטלוג") },
                                onClick = { menuOpen = false; nav.navigate(Routes.CATALOG) },
                            )
                            DropdownMenuItem(
                                text = { Text("שומר במלאי") },
                                onClick = { menuOpen = false; nav.navigate(Routes.TARGETS) },
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.INVENTORY, modifier = Modifier.padding(padding)) {
            composable(Routes.INVENTORY) { PlaceholderScreen("מלאי") }
            composable(Routes.AMS) { PlaceholderScreen("AMS") }
            composable(Routes.ORDERS) { PlaceholderScreen("הזמנות") }
            composable(Routes.CONSUMPTION) { PlaceholderScreen("תצרוכת") }
            composable(Routes.CATALOG) { PlaceholderScreen("קטלוג") }
            composable(Routes.TARGETS) { PlaceholderScreen("שומר במלאי") }
            composable(Routes.ADD_EXISTING) { PlaceholderScreen("הוספת סליל קיים") }
            composable(Routes.NEW_ORDER) { PlaceholderScreen("הזמנה חדשה") }
            composable(
                Routes.SPOOL,
                arguments = listOf(navArgument("spoolId") { type = NavType.LongType }),
            ) { PlaceholderScreen("סליל") }
            composable(
                Routes.ORDER,
                arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
            ) { PlaceholderScreen("הזמנה") }
            composable(
                Routes.COLOR_SPOOLS,
                arguments = listOf(
                    navArgument("materialId") { type = NavType.LongType },
                    navArgument("colorId") { type = NavType.LongType },
                ),
            ) { PlaceholderScreen("סלילים") }
        }
    }
}

/** Removed in Task 15, once every route has its real screen. */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}
```

ב-`MainActivity.kt`: להחליף את `Text("מלאי פילמנטים")` ב-`AppNav()`. להחליף את `import androidx.compose.material3.Text` ב-`import com.amir.filamenttracker.presentation.navigation.AppNav`.

- [ ] **Step 7: בדיקות ובנייה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest assembleDebug
```
Expected: `UiFormatTest` עובר (6 tests), כל השאר ירוק, `BUILD SUCCESSFUL`.

- [ ] **Step 8: בדיקה ידנית**

`./gradlew installDebug`, ואז לפתוח את האפליקציה. מה אמור לקרות:
- ארבע לשוניות בסרגל התחתון, ו"מלאי" היא הימנית (RTL).
- מעבר בין הלשוניות עובד.
- בתפריט שלוש-הנקודות יש "קטלוג" ו"שומר במלאי", ושניהם פותחים מסך-מציין-מקום.
- כפתור "חזרה" של המערכת חוזר ללשונית.

- [ ] **Step 9: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(ui): מעטפת - ניווט בלשוניות, רכיבים משותפים, דיאלוגי מוצר

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: מסכי קטלוג ושומר-במלאי

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/catalog/CatalogViewModel.kt`, `CatalogScreen.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/targets/TargetsViewModel.kt`, `TargetsScreen.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/presentation/navigation/AppNav.kt` (שני מסלולים)

**Interfaces:**
- Consumes: `CatalogRepository`, `RemoveResult` (Task 7); `StockRepository`, `TargetRow` (Tasks 6, 9); `BaseViewModel` and all of `presentation/common` (Task 10).
- Produces: `CatalogScreen(onBack: () -> Unit)`, `TargetsScreen(onBack: () -> Unit)`.

- [ ] **Step 1: CatalogViewModel**

`presentation/catalog/CatalogViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.catalog

import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.RemoveResult
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(private val catalog: CatalogRepository) : BaseViewModel() {
    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val products = catalog.products.asState(emptyList())

    fun addMaterial(name: String) = launchAction { catalog.addMaterial(name); "החומר נשמר" }

    fun addColor(name: String, hex: String) = launchAction { catalog.addColor(name, hex); "הצבע נשמר" }

    fun createProduct(input: NewProductInput) = launchAction {
        catalog.createProduct(input.brand, input.materialName, input.colorName, input.colorHex, input.weightGrams, input.note)
        "המוצר נשמר"
    }

    fun renameMaterial(id: Long, name: String) = launchAction { catalog.renameMaterial(id, name); null }

    fun updateColor(id: Long, name: String, hex: String) = launchAction { catalog.updateColor(id, name, hex); null }

    fun updateProduct(id: Long, brand: String, weight: String, note: String) = launchAction {
        val grams = weight.trim().toIntOrNull() ?: throw IllegalArgumentException("משקל לא תקין")
        catalog.updateProduct(id, brand, grams, note)
        null
    }

    fun removeMaterial(id: Long) = launchAction { catalog.removeMaterial(id).message() }
    fun removeColor(id: Long) = launchAction { catalog.removeColor(id).message() }
    fun removeProduct(id: Long) = launchAction { catalog.removeProduct(id).message() }

    private fun RemoveResult.message() = when (this) {
        RemoveResult.DELETED -> "נמחק"
        RemoveResult.ARCHIVED -> "הועבר לארכיון, כי יש בו שימוש"
    }
}
```

- [ ] **Step 2: CatalogScreen**

`presentation/catalog/CatalogScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.catalog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.RowActions
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.TextFieldsDialog

private sealed interface CatalogDialog {
    data object NewProduct : CatalogDialog
    data object NewMaterial : CatalogDialog
    data object NewColor : CatalogDialog
    data class EditMaterial(val material: MaterialEntity) : CatalogDialog
    data class EditColor(val color: ColorEntity) : CatalogDialog
    data class EditProduct(val product: ProductRow) : CatalogDialog
}

@Composable
fun CatalogScreen(onBack: () -> Unit, vm: CatalogViewModel = hiltViewModel()) {
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var dialog by remember { mutableStateOf<CatalogDialog?>(null) }

    Scaffold(
        topBar = { BackTopBar("קטלוג", onBack) },
        snackbarHost = { SnackbarHost(host) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = CatalogDialog.NewProduct },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("מוצר חדש") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item { SectionHeader("מוצרים") }
            if (products.isEmpty()) item { Text("אין עדיין מוצרים", Modifier.padding(horizontal = 16.dp)) }
            items(products, key = { "p${it.productId}" }) { p ->
                ListItem(
                    headlineContent = { Text(p.title) },
                    supportingContent = {
                        Text(listOf("${p.weightGrams} ג'", p.note).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    leadingContent = { ColorDot(p.colorHex) },
                    trailingContent = {
                        RowActions(onEdit = { dialog = CatalogDialog.EditProduct(p) }, onRemove = { vm.removeProduct(p.productId) })
                    },
                )
            }
            item { SectionHeader("חומרים", actionLabel = "הוסף", onAction = { dialog = CatalogDialog.NewMaterial }) }
            items(materials, key = { "m${it.id}" }) { m ->
                ListItem(
                    headlineContent = { Text(m.name) },
                    trailingContent = {
                        RowActions(onEdit = { dialog = CatalogDialog.EditMaterial(m) }, onRemove = { vm.removeMaterial(m.id) })
                    },
                )
            }
            item { SectionHeader("צבעים", actionLabel = "הוסף", onAction = { dialog = CatalogDialog.NewColor }) }
            items(colors, key = { "c${it.id}" }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    leadingContent = { ColorDot(c.hex) },
                    trailingContent = {
                        RowActions(onEdit = { dialog = CatalogDialog.EditColor(c) }, onRemove = { vm.removeColor(c.id) })
                    },
                )
            }
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        CatalogDialog.NewProduct -> NewProductDialog(materials, colors, onDismiss = close) {
            vm.createProduct(it)
            close()
        }
        CatalogDialog.NewMaterial -> TextFieldsDialog("חומר חדש", listOf("שם" to ""), close) { vm.addMaterial(it[0]) }
        CatalogDialog.NewColor -> TextFieldsDialog("צבע חדש", listOf("שם" to "", "גוון (למשל #1A2B3C)" to ""), close) {
            vm.addColor(it[0], it[1])
        }
        is CatalogDialog.EditMaterial -> TextFieldsDialog("עריכת חומר", listOf("שם" to d.material.name), close) {
            vm.renameMaterial(d.material.id, it[0])
        }
        is CatalogDialog.EditColor -> TextFieldsDialog(
            "עריכת צבע",
            listOf("שם" to d.color.name, "גוון (למשל #1A2B3C)" to (d.color.hex ?: "")),
            close,
        ) { vm.updateColor(d.color.id, it[0], it[1]) }
        is CatalogDialog.EditProduct -> TextFieldsDialog(
            "עריכת מוצר",
            listOf("מותג" to d.product.brand, "משקל בגרמים" to "${d.product.weightGrams}", "הערה" to d.product.note),
            close,
        ) { vm.updateProduct(d.product.productId, it[0], it[1], it[2]) }
    }
}
```

- [ ] **Step 3: TargetsViewModel ו-TargetsScreen**

`presentation/targets/TargetsViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.targets

import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.data.db.TargetRow
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TargetsViewModel @Inject constructor(private val stock: StockRepository) : BaseViewModel() {
    val rows = stock.targetRows.asState(emptyList())

    fun setTracked(row: TargetRow, tracked: Boolean) = launchAction {
        if (tracked) stock.setTarget(row.materialId, row.colorId, 1) else stock.removeTarget(row.materialId, row.colorId)
        null
    }

    fun setMin(row: TargetRow, min: Int) = launchAction { stock.setTarget(row.materialId, row.colorId, min); null }
}
```

`presentation/targets/TargetsScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.targets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.Stepper

@Composable
fun TargetsScreen(onBack: () -> Unit, vm: TargetsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)

    Scaffold(topBar = { BackTopBar("שומר במלאי", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "סמן את השילובים שחייבים להיות תמיד במלאי. המספר הוא כמה סלילים סגורים לשמור בגיבוי.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (rows.isEmpty()) {
                item {
                    Text("אין עדיין מוצרים בקטלוג. כל מוצר שיתווסף יופיע כאן כשילוב של חומר וצבע.", Modifier.padding(16.dp))
                }
            }
            items(rows, key = { "${it.materialId}-${it.colorId}" }) { row ->
                val tracked = row.minSealed != null
                ListItem(
                    headlineContent = { Text("${row.materialName} ${row.colorName}") },
                    supportingContent = { if (tracked) Text("לפחות ${row.minSealed} סגורים בגיבוי") },
                    leadingContent = { ColorDot(row.colorHex) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tracked) Stepper(row.minSealed!!, { vm.setMin(row, it) }, min = 0, max = 20)
                            Switch(checked = tracked, onCheckedChange = { vm.setTracked(row, it) })
                        }
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 4: חיבור לניווט**

ב-`AppNav.kt`:
- להחליף `composable(Routes.CATALOG) { PlaceholderScreen("קטלוג") }` ב-`composable(Routes.CATALOG) { CatalogScreen(onBack = { nav.popBackStack() }) }`
- להחליף `composable(Routes.TARGETS) { PlaceholderScreen("שומר במלאי") }` ב-`composable(Routes.TARGETS) { TargetsScreen(onBack = { nav.popBackStack() }) }`
- להוסיף imports: `com.amir.filamenttracker.presentation.catalog.CatalogScreen`, `com.amir.filamenttracker.presentation.targets.TargetsScreen`

- [ ] **Step 5: בנייה ובדיקה ידנית**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest assembleDebug installDebug
```
Expected: `BUILD SUCCESSFUL`. באמולטור, מתפריט שלוש-הנקודות ובמסך "קטלוג":
- PLA ו-PETG מופיעים תחת "חומרים".
- "מוצר חדש" עם מותג TEMPlast, חומר PLA (מהשבב), צבע "שחור" וגוון `#000000` יוצר מוצר, ו"שחור" נוסף לרשימת הצבעים.
- גוון `red` מסמן את השדה באדום, וכפתור "שמור" מושבת.
- מחיקת המוצר מציגה "נמחק", כי עוד אין לו סלילים.
- במסך "שומר במלאי" מופיע השילוב "PLA שחור" כל עוד המוצר קיים. המתג מדליק "לפחות 1 סגורים בגיבוי", והמונה משנה את המספר.

- [ ] **Step 6: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(ui): מסכי קטלוג ושומר-במלאי

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: מסך מלאי, קליטת מלאי קיים, וסלילי צבע

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/inventory/InventoryViewModel.kt`, `InventoryScreen.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/inventory/AddExistingViewModel.kt`, `AddExistingScreen.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/inventory/ColorSpoolsViewModel.kt`, `ColorSpoolsScreen.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/presentation/navigation/AppNav.kt` (שלושה מסלולים)
- Test: `app/src/test/java/com/amir/filamenttracker/presentation/inventory/InventoryGroupingTest.kt`

**Interfaces:**
- Consumes: `InventoryRepository` (Task 8); `StockRepository`, `AlertRow` (Task 9); `CatalogRepository` (Task 7); `presentation/common` (Task 10); `SpoolPlanner.SLOT_COUNT`.
- Produces:
  - `InventoryScreen(onColorClick: (materialId: Long, colorId: Long) -> Unit, onAddExisting: () -> Unit)`
  - `AddExistingScreen(onBack: () -> Unit)`, `ColorSpoolsScreen(onBack: () -> Unit, onSpoolClick: (Long) -> Unit)`
  - `internal fun groupSpools(spools: List<SpoolRow>): List<MaterialSection>`

- [ ] **Step 1: בדיקת הקיבוץ (נכשלת)**

`app/src/test/java/com/amir/filamenttracker/presentation/inventory/InventoryGroupingTest.kt`:
```kotlin
package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryGroupingTest {
    private var nextId = 1L
    private fun row(material: String, materialId: Long, color: String, colorId: Long, status: SpoolStatus) = SpoolRow(
        spoolId = nextId++, status = status, amsSlot = null, orderId = null, productId = 1, brand = "X",
        weightGrams = 1000, materialId = materialId, materialName = material,
        colorId = colorId, colorName = color, colorHex = null,
    )

    @Test
    fun `groups by material then color, counts statuses, and drops finished`() {
        val sections = groupSpools(
            listOf(
                row("PLA", 1, "שחור", 10, IN_USE),
                row("PLA", 1, "שחור", 10, SEALED),
                row("PLA", 1, "שחור", 10, SEALED),
                row("PLA", 1, "לבן", 11, ORDERED),
                row("PETG", 2, "שקוף", 12, FINISHED),
            ),
        )
        assertEquals(listOf("PLA"), sections.map { it.materialName })
        val colors = sections.single().colors
        assertEquals(listOf("לבן", "שחור"), colors.map { it.colorName })
        assertEquals(mapOf(IN_USE to 1, SEALED to 2), colors.single { it.colorName == "שחור" }.counts)
    }
}
```

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*InventoryGroupingTest"
```
Expected: FAIL, `Unresolved reference: groupSpools`.

- [ ] **Step 2: InventoryViewModel**

`presentation/inventory/InventoryViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ColorGroup(
    val materialId: Long,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val counts: Map<SpoolStatus, Int>,
)

data class MaterialSection(val materialName: String, val colors: List<ColorGroup>)

data class InventoryUiState(
    val loaded: Boolean = false,
    val alerts: List<AlertRow> = emptyList(),
    val sections: List<MaterialSection> = emptyList(),
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    inventory: InventoryRepository,
    stock: StockRepository,
) : BaseViewModel() {
    val state: StateFlow<InventoryUiState> =
        combine(inventory.spools, stock.alerts) { spools, alerts ->
            InventoryUiState(loaded = true, alerts = alerts, sections = groupSpools(spools))
        }.asState(InventoryUiState())
}

/** Finished spools are history, not stock. Ordered ones stay, shown as "on the way". */
internal fun groupSpools(spools: List<SpoolRow>): List<MaterialSection> =
    spools.filter { it.status != SpoolStatus.FINISHED }
        .groupBy { it.materialName }
        .toSortedMap()
        .map { (material, rows) ->
            MaterialSection(
                materialName = material,
                colors = rows.groupBy { it.colorId }.values.map { group ->
                    val first = group.first()
                    ColorGroup(
                        materialId = first.materialId,
                        colorId = first.colorId,
                        colorName = first.colorName,
                        colorHex = first.colorHex,
                        counts = group.groupingBy { it.status }.eachCount(),
                    )
                }.sortedBy { it.colorName },
            )
        }
```

- [ ] **Step 3: InventoryScreen**

`presentation/inventory/InventoryScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.countLabel
import com.amir.filamenttracker.presentation.common.title

@Composable
fun InventoryScreen(
    onColorClick: (materialId: Long, colorId: Long) -> Unit,
    onAddExisting: () -> Unit,
    vm: InventoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExisting,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("הוסף סליל קיים") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item { AlertsCard(state.alerts) }
            if (state.loaded && state.sections.isEmpty()) {
                item {
                    Text("אין עדיין סלילים. אפשר להתחיל בהוספת המלאי הקיים, בכפתור שלמטה.", Modifier.padding(16.dp))
                }
            }
            state.sections.forEach { section ->
                item(key = "m-${section.materialName}") { SectionHeader(section.materialName) }
                items(section.colors, key = { "c-${it.materialId}-${it.colorId}" }) { group ->
                    ListItem(
                        headlineContent = { Text(group.colorName) },
                        supportingContent = { Text(group.countsText()) },
                        leadingContent = { ColorDot(group.colorHex, size = 28.dp) },
                        modifier = Modifier.clickable { onColorClick(group.materialId, group.colorId) },
                    )
                }
            }
        }
    }
}

private val displayOrder = listOf(SpoolStatus.IN_USE, SpoolStatus.OPEN_STORED, SpoolStatus.SEALED, SpoolStatus.ORDERED)

private fun ColorGroup.countsText(): String =
    displayOrder.mapNotNull { status -> counts[status]?.let { countLabel(status, it) } }.joinToString(" · ")

@Composable
private fun AlertsCard(alerts: List<AlertRow>) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alerts.isEmpty()) {
                Text("המלאי תקין", style = MaterialTheme.typography.titleMedium)
            } else {
                AlertKind.entries.forEach { kind ->
                    val group = alerts.filter { it.kind == kind }
                    if (group.isNotEmpty()) {
                        Text(kind.title(), style = MaterialTheme.typography.titleSmall, color = kind.color())
                        group.forEach { alert ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorDot(alert.colorHex, size = 14.dp)
                                Text("${alert.materialName} ${alert.colorName}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertKind.color(): Color = when (this) {
    AlertKind.MISSING -> MaterialTheme.colorScheme.error
    AlertKind.RUNNING_LOW -> MaterialTheme.colorScheme.tertiary
    AlertKind.ON_THE_WAY -> MaterialTheme.colorScheme.primary
}
```

- [ ] **Step 4: AddExistingViewModel ו-AddExistingScreen**

`presentation/inventory/AddExistingViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class AddExistingViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val products = catalog.products.asState(emptyList())

    private val selectedId = MutableStateFlow<Long?>(null)
    val selected: StateFlow<ProductRow?> =
        combine(selectedId, catalog.products) { id, list -> list.firstOrNull { it.productId == id } }.asState(null)

    /** Slot number to the title of the spool in it, to warn before displacing it. */
    val occupants: StateFlow<Map<Int, String>> = inventory.spools.map { rows ->
        rows.filter { it.status == SpoolStatus.IN_USE && it.amsSlot != null }.associate { it.amsSlot!! to it.title }
    }.asState(emptyMap())

    fun select(productId: Long) { selectedId.value = productId }

    fun createProduct(input: NewProductInput) = launchAction {
        selectedId.value = catalog.createProduct(
            input.brand, input.materialName, input.colorName, input.colorHex, input.weightGrams, input.note,
        )
        "המוצר נשמר"
    }

    fun add(quantity: Int, status: SpoolStatus, slot: Int?) = launchAction {
        val productId = selectedId.value ?: throw IllegalArgumentException("יש לבחור מוצר")
        val ids = inventory.addExisting(productId, quantity, status, slot)
        if (ids.size == 1) "נוסף סליל אחד" else "נוספו ${ids.size} סלילים"
    }
}
```

`presentation/inventory/AddExistingScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.ProductPickerDialog
import com.amir.filamenttracker.presentation.common.Stepper
import com.amir.filamenttracker.presentation.common.label

private val initialStatuses = listOf(SpoolStatus.SEALED, SpoolStatus.IN_USE, SpoolStatus.OPEN_STORED)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExistingScreen(onBack: () -> Unit, vm: AddExistingViewModel = hiltViewModel()) {
    val products by vm.products.collectAsStateWithLifecycle()
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val occupants by vm.occupants.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)

    var status by rememberSaveable { mutableStateOf(SpoolStatus.SEALED) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var slot by rememberSaveable { mutableStateOf<Int?>(null) }
    var picker by remember { mutableStateOf(false) }
    var newProduct by remember { mutableStateOf(false) }
    val inUse = status == SpoolStatus.IN_USE
    val canAdd = selected != null && (!inUse || slot != null)

    Scaffold(topBar = { BackTopBar("הוספת סליל קיים", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("מוצר", style = MaterialTheme.typography.titleSmall)
            OutlinedCard(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorDot(selected?.colorHex)
                    Text(selected?.title ?: "בחר מוצר")
                }
            }

            Text("מצב", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                initialStatuses.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = status == s,
                        onClick = { status = s },
                        shape = SegmentedButtonDefaults.itemShape(i, initialStatuses.size),
                    ) { Text(s.label()) }
                }
            }

            if (inUse) {
                Text("חריץ", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..SpoolPlanner.SLOT_COUNT).forEach { n ->
                        FilterChip(selected = slot == n, onClick = { slot = n }, label = { Text("$n") })
                    }
                }
                slot?.let { n ->
                    occupants[n]?.let { title ->
                        Text(
                            "בחריץ $n נמצא $title, והוא יעבור לאחסון",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Text("כמות", style = MaterialTheme.typography.titleSmall)
                Stepper(quantity, { quantity = it }, min = 1, max = 50)
            }

            Button(
                onClick = {
                    vm.add(if (inUse) 1 else quantity, status, if (inUse) slot else null)
                    quantity = 1
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("הוסף") }
            Text("אפשר להמשיך ולהוסיף עוד סלילים. כפתור החזרה מסיים.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (picker) {
        ProductPickerDialog(
            products,
            onDismiss = { picker = false },
            onPick = { vm.select(it.productId); picker = false },
            onNewProduct = { picker = false; newProduct = true },
        )
    }
    if (newProduct) {
        NewProductDialog(materials, colors, onDismiss = { newProduct = false }) {
            vm.createProduct(it)
            newProduct = false
        }
    }
}
```

- [ ] **Step 5: ColorSpoolsViewModel ו-ColorSpoolsScreen**

`presentation/inventory/ColorSpoolsViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.inventory

import androidx.lifecycle.SavedStateHandle
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ColorSpoolsViewModel @Inject constructor(
    handle: SavedStateHandle,
    inventory: InventoryRepository,
) : BaseViewModel() {
    private val materialId: Long = checkNotNull(handle.get<Long>("materialId"))
    private val colorId: Long = checkNotNull(handle.get<Long>("colorId"))

    // Finished spools go last: they are history, kept for reference.
    val spools = inventory.spools.map { rows ->
        rows.filter { it.materialId == materialId && it.colorId == colorId }
            .sortedBy { it.status == SpoolStatus.FINISHED }
    }.asState(emptyList())
}
```

`presentation/inventory/ColorSpoolsScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.statusText

@Composable
fun ColorSpoolsScreen(onBack: () -> Unit, onSpoolClick: (Long) -> Unit, vm: ColorSpoolsViewModel = hiltViewModel()) {
    val spools by vm.spools.collectAsStateWithLifecycle()
    val title = spools.firstOrNull()?.let { "${it.materialName} ${it.colorName}" } ?: "סלילים"

    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(spools, key = { it.spoolId }) { spool ->
                ListItem(
                    headlineContent = { Text(spool.brand) },
                    supportingContent = { Text(spool.statusText()) },
                    leadingContent = { ColorDot(spool.colorHex) },
                    modifier = Modifier.clickable { onSpoolClick(spool.spoolId) },
                )
            }
        }
    }
}
```

- [ ] **Step 6: חיבור לניווט**

ב-`AppNav.kt`:
- להחליף `composable(Routes.INVENTORY) { PlaceholderScreen("מלאי") }` ב-:
```kotlin
            composable(Routes.INVENTORY) {
                InventoryScreen(
                    onColorClick = { materialId, colorId -> nav.navigate(Routes.colorSpools(materialId, colorId)) },
                    onAddExisting = { nav.navigate(Routes.ADD_EXISTING) },
                )
            }
```
- להחליף `composable(Routes.ADD_EXISTING) { PlaceholderScreen("הוספת סליל קיים") }` ב-`composable(Routes.ADD_EXISTING) { AddExistingScreen(onBack = { nav.popBackStack() }) }`
- בבלוק של `Routes.COLOR_SPOOLS`: להחליף את `{ PlaceholderScreen("סלילים") }` ב-`{ ColorSpoolsScreen(onBack = { nav.popBackStack() }, onSpoolClick = { nav.navigate(Routes.spool(it)) }) }`
- imports: `com.amir.filamenttracker.presentation.inventory.InventoryScreen`, `...inventory.AddExistingScreen`, `...inventory.ColorSpoolsScreen`

- [ ] **Step 7: בדיקות, בנייה ובדיקה ידנית**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest assembleDebug installDebug
```
Expected: `InventoryGroupingTest` עובר, `BUILD SUCCESSFUL`. באמולטור:
- במסך "מלאי" מופיע "המלאי תקין", וגם ההודעה שאין עדיין סלילים.
- "הוסף סליל קיים" עם מוצר חדש (TEMPlast PLA שחור), מצב "סגור" וכמות 3, ואז "הוסף". מופיע "נוספו 3 סלילים", והמסך נשאר פתוח.
- אותו מוצר, מצב "בשימוש", חריץ 1, ואז "הוסף".
- שוב "בשימוש" בחריץ 1, עם מוצר אחר. מופיעה האזהרה "בחריץ 1 נמצא...". אחרי ההוספה הסליל הקודם עובר ל"פתוח".
- חזרה למלאי: תחת PLA, "שחור" עם "1 פתוח · 3 סגורים" (בהנחה שהסליל השני הוזז).
- אחרי סימון "PLA שחור" ב"שומר במלאי" עם מינימום 4, מופיע בכרטיס "הולך להיות חסר: PLA שחור".
- הקשה על צבע פותחת את רשימת הסלילים שלו.

- [ ] **Step 8: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(ui): מסך מלאי עם התראות, קליטת מלאי קיים, סלילי צבע

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: מסך AMS ומסך פרטי סליל

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/ams/AmsViewModel.kt`, `AmsScreen.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/spool/SpoolDetailViewModel.kt`, `SpoolDetailScreen.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/presentation/navigation/AppNav.kt` (שני מסלולים)

**Interfaces:**
- Consumes: `InventoryRepository` (Task 8); `SpoolPlanner.SLOT_COUNT`; `presentation/common` (Task 10).
- Produces: `AmsScreen(onSpoolClick: (Long) -> Unit)`, `SpoolDetailScreen(onBack: () -> Unit)`.

- [ ] **Step 1: AmsViewModel**

`presentation/ams/AmsViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.ams

import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class AmsUiState(
    val slots: List<SpoolRow?> = List(SpoolPlanner.SLOT_COUNT) { null },
    val candidates: List<SpoolRow> = emptyList(),
)

@HiltViewModel
class AmsViewModel @Inject constructor(private val inventory: InventoryRepository) : BaseViewModel() {
    val state: StateFlow<AmsUiState> = inventory.spools.map { spools ->
        AmsUiState(
            slots = (1..SpoolPlanner.SLOT_COUNT).map { slot ->
                spools.firstOrNull { it.status == SpoolStatus.IN_USE && it.amsSlot == slot }
            },
            // Open spools first: they are the ones that come back to the printer.
            candidates = spools
                .filter { it.status == SpoolStatus.OPEN_STORED || it.status == SpoolStatus.SEALED }
                .sortedWith(
                    compareBy<SpoolRow> { if (it.status == SpoolStatus.OPEN_STORED) 0 else 1 }
                        .thenBy { it.materialName }
                        .thenBy { it.colorName },
                ),
        )
    }.asState(AmsUiState())

    fun load(spoolId: Long, slot: Int) = launchAction { inventory.loadIntoSlot(spoolId, slot); null }
    fun unload(spoolId: Long) = launchAction { inventory.unload(spoolId); "הסליל הוצא לאחסון" }
    fun finish(spoolId: Long) = launchAction { inventory.finish(spoolId); "הסליל סומן שהסתיים" }
}
```

- [ ] **Step 2: AmsScreen**

`presentation/ams/AmsScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.ams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmsScreen(onSpoolClick: (Long) -> Unit, vm: AmsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var openSlot by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(contentWindowInsets = NoInsets, snackbarHost = { SnackbarHost(host) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.slots.forEachIndexed { index, spool ->
                SlotCard(slot = index + 1, spool = spool, onClick = { openSlot = index + 1 })
            }
        }
    }

    openSlot?.let { slot ->
        val spool = state.slots[slot - 1]
        ModalBottomSheet(onDismissRequest = { openSlot = null }) {
            SlotSheet(
                slot = slot,
                spool = spool,
                candidates = state.candidates,
                onLoad = { vm.load(it, slot); openSlot = null },
                onUnload = { spool?.let { vm.unload(it.spoolId) }; openSlot = null },
                onFinish = { spool?.let { vm.finish(it.spoolId) }; openSlot = null },
                onDetails = { spool?.let { onSpoolClick(it.spoolId) }; openSlot = null },
            )
        }
    }
}

@Composable
private fun SlotCard(slot: Int, spool: SpoolRow?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ColorDot(spool?.colorHex, size = 40.dp)
            Column {
                Text("חריץ $slot", style = MaterialTheme.typography.labelMedium)
                Text(spool?.title ?: "ריק", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Swapping is two taps: the slot, then a spool from the list. */
@Composable
private fun SlotSheet(
    slot: Int,
    spool: SpoolRow?,
    candidates: List<SpoolRow>,
    onLoad: (Long) -> Unit,
    onUnload: () -> Unit,
    onFinish: () -> Unit,
    onDetails: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        item {
            Text(
                spool?.let { "חריץ $slot: ${it.title}" } ?: "חריץ $slot ריק",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (spool != null) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUnload) { Text("הוצא לאחסון") }
                    OutlinedButton(onClick = onFinish) { Text("הסתיים") }
                    TextButton(onClick = onDetails) { Text("פרטים") }
                }
            }
        }
        item { SectionHeader(if (spool == null) "טען לחריץ" else "החלף ב:") }
        if (candidates.isEmpty()) item { Text("אין סלילים זמינים", Modifier.padding(16.dp)) }
        items(candidates, key = { it.spoolId }) { c ->
            ListItem(
                headlineContent = { Text(c.title) },
                supportingContent = { Text(c.status.label()) },
                leadingContent = { ColorDot(c.colorHex) },
                modifier = Modifier.clickable { onLoad(c.spoolId) },
            )
        }
    }
}
```

- [ ] **Step 3: SpoolDetailViewModel**

`presentation/spool/SpoolDetailViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.spool

import androidx.lifecycle.SavedStateHandle
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.SpoolEventEntity
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class SpoolDetailState(
    val loaded: Boolean = false,
    val spool: SpoolRow? = null,
    val events: List<SpoolEventEntity> = emptyList(),
    /** Other spools in slots, to warn before displacing one. */
    val occupants: Map<Int, String> = emptyMap(),
) {
    // The creation event (no fromStatus) cannot be undone.
    val canUndo: Boolean get() = events.firstOrNull()?.fromStatus != null
}

@HiltViewModel
class SpoolDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    private val spoolId: Long = checkNotNull(handle.get<Long>("spoolId"))

    val state: StateFlow<SpoolDetailState> =
        combine(inventory.spool(spoolId), inventory.events(spoolId), inventory.spools) { spool, events, all ->
            SpoolDetailState(
                loaded = true,
                spool = spool,
                events = events,
                occupants = all
                    .filter { it.status == SpoolStatus.IN_USE && it.amsSlot != null && it.spoolId != spoolId }
                    .associate { it.amsSlot!! to it.title },
            )
        }.asState(SpoolDetailState())

    fun arrive() = launchAction { inventory.markArrived(listOf(spoolId)); "סומן שהגיע" }
    fun load(slot: Int) = launchAction { inventory.loadIntoSlot(spoolId, slot); "נטען לחריץ $slot" }
    fun unload() = launchAction { inventory.unload(spoolId); "הוצא לאחסון" }
    fun finish() = launchAction { inventory.finish(spoolId); "סומן שהסתיים" }
    fun undo() = launchAction { inventory.undoLast(spoolId); "הפעולה האחרונה בוטלה" }

    fun removeOrdered(onDone: () -> Unit) = launchAction {
        inventory.removeOrderedSpool(spoolId)
        onDone()
        null
    }
}
```

- [ ] **Step 4: SpoolDetailScreen**

`presentation/spool/SpoolDetailScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.spool

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.SpoolEventEntity
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.ConfirmDialog
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.formatDateTime
import com.amir.filamenttracker.presentation.common.label
import com.amir.filamenttracker.presentation.common.statusText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpoolDetailScreen(onBack: () -> Unit, vm: SpoolDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var slotPicker by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val spool = state.spool

    Scaffold(topBar = { BackTopBar(spool?.title ?: "סליל", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        if (spool == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loaded) Text("הסליל לא נמצא")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ColorDot(spool.colorHex, size = 48.dp)
                    Column {
                        Text(spool.statusText(), style = MaterialTheme.typography.titleMedium)
                        Text("${spool.weightGrams} ג'", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (spool.status) {
                        SpoolStatus.ORDERED -> {
                            Button(onClick = vm::arrive) { Text("הגיע") }
                            OutlinedButton(onClick = { confirmRemove = true }) { Text("מחק, לא יגיע") }
                        }
                        SpoolStatus.SEALED -> Button(onClick = { slotPicker = true }) { Text("טען לחריץ") }
                        SpoolStatus.OPEN_STORED -> {
                            Button(onClick = { slotPicker = true }) { Text("טען לחריץ") }
                            OutlinedButton(onClick = vm::finish) { Text("הסתיים") }
                        }
                        SpoolStatus.IN_USE -> {
                            Button(onClick = vm::unload) { Text("הוצא לאחסון") }
                            OutlinedButton(onClick = vm::finish) { Text("הסתיים") }
                        }
                        SpoolStatus.FINISHED -> Unit
                    }
                }
            }
            item { OutlinedButton(onClick = vm::undo, enabled = state.canUndo) { Text("בטל פעולה אחרונה") } }
            item { SectionHeader("היסטוריה") }
            items(state.events, key = { it.id }) { e ->
                Text("${e.at.formatDateTime()} · ${e.describe()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (slotPicker) {
        SlotPickerDialog(state.occupants, onDismiss = { slotPicker = false }) {
            vm.load(it)
            slotPicker = false
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            text = "למחוק את הסליל מההזמנה? אי אפשר לבטל את זה.",
            confirmLabel = "מחק",
            onConfirm = { vm.removeOrdered(onBack) },
            onDismiss = { confirmRemove = false },
        )
    }
}

// Words, not arrows: arrows flip in RTL text.
private fun SpoolEventEntity.describe(): String {
    val slotText = toSlot?.let { " (חריץ $it)" } ?: ""
    val from = fromStatus ?: return "נקלט במצב ${toStatus.label()}$slotText"
    return "מ-${from.label()} ל-${toStatus.label()}$slotText"
}

@Composable
private fun SlotPickerDialog(occupants: Map<Int, String>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("לאיזה חריץ?") },
        text = {
            Column {
                (1..SpoolPlanner.SLOT_COUNT).forEach { n ->
                    ListItem(
                        headlineContent = { Text("חריץ $n") },
                        supportingContent = { Text(occupants[n]?.let { "$it, יעבור לאחסון" } ?: "ריק") },
                        modifier = Modifier.clickable { onPick(n) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
```

- [ ] **Step 5: חיבור לניווט**

ב-`AppNav.kt`:
- להחליף `composable(Routes.AMS) { PlaceholderScreen("AMS") }` ב-`composable(Routes.AMS) { AmsScreen(onSpoolClick = { nav.navigate(Routes.spool(it)) }) }`
- בבלוק של `Routes.SPOOL`: להחליף את `{ PlaceholderScreen("סליל") }` ב-`{ SpoolDetailScreen(onBack = { nav.popBackStack() }) }`
- imports: `com.amir.filamenttracker.presentation.ams.AmsScreen`, `com.amir.filamenttracker.presentation.spool.SpoolDetailScreen`

- [ ] **Step 6: בנייה ובדיקה ידנית**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest assembleDebug installDebug
```
Expected: `BUILD SUCCESSFUL`. באמולטור, עם הנתונים ממשימה 12:
- מסך "AMS" מציג ארבעה חריצים, ובחריץ 1 את הסליל שנקלט.
- הקשה על חריץ ריק, ואז על סליל פתוח: הסליל נטען. זה שתי הקשות.
- הקשה על חריץ מלא, ואז על סליל אחר: הסליל הקודם עובר לאחסון, והחדש נכנס.
- "הוצא לאחסון" מרוקן את החריץ, ו"הסתיים" מרוקן אותו ומעביר את הסליל ל"הסתיים".
- "פרטים" פותח את פרטי הסליל. ההיסטוריה מציגה שורות כמו "מ-בשימוש ל-פתוח באחסון", בלי חצים.
- "בטל פעולה אחרונה" מחזיר צעד אחד אחורה. לחיצה שנייה מחזירה צעד נוסף. על אירוע היצירה הכפתור מושבת.
- ביטול שחוסם (החריץ הקודם תפוס) מציג "חריץ N תפוס. יש לפנות אותו קודם", והאפליקציה לא קורסת.

- [ ] **Step 7: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(ui): מסך AMS ופרטי סליל עם היסטוריה וביטול

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 14: מסכי הזמנות - רשימה, הזמנה חדשה ופרטי הזמנה

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/orders/OrdersViewModel.kt`, `OrdersScreen.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/orders/NewOrderViewModel.kt`, `NewOrderScreen.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/orders/OrderDetailViewModel.kt`, `OrderDetailScreen.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/presentation/navigation/AppNav.kt` (שלושה מסלולים)

**Interfaces:**
- Consumes: `InventoryRepository`, `OrderSummary`, `OrderEntity` (Tasks 6, 8); `CatalogRepository` (Task 7); `presentation/common` (Task 10).
- Produces: `OrdersScreen(onOrderClick: (Long) -> Unit, onNewOrder: () -> Unit)`, `NewOrderScreen(onBack: () -> Unit)`, `OrderDetailScreen(onBack: () -> Unit, onSpoolClick: (Long) -> Unit)`.

- [ ] **Step 1: רשימת הזמנות**

`presentation/orders/OrdersViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(inventory: InventoryRepository) : BaseViewModel() {
    /** Null until the first load, so an empty list really means "no orders". */
    val orders: StateFlow<List<OrderSummary>?> = inventory.orders.asState<List<OrderSummary>?>(null)
}
```

`presentation/orders/OrdersScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.formatDate

@Composable
fun OrdersScreen(onOrderClick: (Long) -> Unit, onNewOrder: () -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    val orders by vm.orders.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewOrder,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("הזמנה חדשה") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (orders?.isEmpty() == true) item { Text("אין עדיין הזמנות.", Modifier.padding(16.dp)) }
            items(orders.orEmpty(), key = { it.id }) { order ->
                ListItem(
                    headlineContent = { Text("${order.vendor} · ${order.orderedAt.formatDate()}") },
                    supportingContent = { Text(order.statusText()) },
                    modifier = Modifier.clickable { onOrderClick(order.id) },
                )
            }
        }
    }
}

private fun OrderSummary.statusText(): String {
    val arrived = spoolCount - pendingCount
    val state = when {
        spoolCount == 0 -> "ריקה"
        pendingCount == 0 -> "הגיעה"
        arrived == 0 -> "ממתינה"
        else -> "ממתינה, הגיעו $arrived מתוך $spoolCount"
    }
    val expected = expectedAt?.takeIf { pendingCount > 0 }?.let { " · צפי ${it.formatDate()}" } ?: ""
    return "$spoolCount סלילים · $state$expected"
}
```

- [ ] **Step 2: הזמנה חדשה**

`presentation/orders/NewOrderViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class NewOrderViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val products = catalog.products.asState(emptyList())

    // Product id to quantity. Map + keeps insertion order, so lines stay in the order added.
    private val quantities = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val lines: StateFlow<List<Pair<ProductRow, Int>>> = combine(quantities, catalog.products) { q, list ->
        q.mapNotNull { (id, n) -> list.firstOrNull { it.productId == id }?.let { it to n } }
    }.asState(emptyList())

    fun add(productId: Long) = quantities.update { it + (productId to ((it[productId] ?: 0) + 1)) }

    fun setQuantity(productId: Long, n: Int) =
        quantities.update { if (n <= 0) it - productId else it + (productId to n) }

    fun createProductAndAdd(input: NewProductInput) = launchAction {
        add(catalog.createProduct(input.brand, input.materialName, input.colorName, input.colorHex, input.weightGrams, input.note))
        "המוצר נשמר"
    }

    fun save(vendor: String, orderNumber: String, expectedAt: LocalDate?, onDone: () -> Unit) = launchAction {
        inventory.createOrder(vendor, orderNumber, expectedAt, quantities.value)
        onDone()
        null
    }
}
```

`presentation/orders/NewOrderScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.ProductPickerDialog
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.Stepper
import com.amir.filamenttracker.presentation.common.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(onBack: () -> Unit, vm: NewOrderViewModel = hiltViewModel()) {
    val products by vm.products.collectAsStateWithLifecycle()
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)

    var vendor by rememberSaveable { mutableStateOf("Amazon") }
    var orderNumber by rememberSaveable { mutableStateOf("") }
    var expectedAt by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var picker by remember { mutableStateOf(false) }
    var newProduct by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("הזמנה חדשה", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(vendor, { vendor = it }, label = { Text("ספק") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(
                    orderNumber, { orderNumber = it },
                    label = { Text("מספר הזמנה, לא חובה") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { datePicker = true }) {
                        Text(expectedAt?.let { "צפי הגעה: ${it.formatDate()}" } ?: "צפי הגעה, לא חובה")
                    }
                    if (expectedAt != null) TextButton(onClick = { expectedAt = null }) { Text("נקה") }
                }
            }
            item { SectionHeader("פריטים", actionLabel = "הוסף מוצר", onAction = { picker = true }) }
            if (lines.isEmpty()) item { Text("עוד לא נבחרו מוצרים") }
            items(lines, key = { it.first.productId }) { (product, quantity) ->
                ListItem(
                    headlineContent = { Text(product.title) },
                    leadingContent = { ColorDot(product.colorHex) },
                    trailingContent = { Stepper(quantity, { vm.setQuantity(product.productId, it) }, min = 0, max = 50) },
                )
            }
            item {
                Button(
                    onClick = { vm.save(vendor, orderNumber, expectedAt, onBack) },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("שמור הזמנה") }
            }
        }
    }

    if (datePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports UTC midnight of the chosen day.
                    dateState.selectedDateMillis?.let {
                        expectedAt = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    datePicker = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("ביטול") } },
        ) { DatePicker(state = dateState) }
    }
    if (picker) {
        ProductPickerDialog(
            products,
            onDismiss = { picker = false },
            onPick = { vm.add(it.productId); picker = false },
            onNewProduct = { picker = false; newProduct = true },
        )
    }
    if (newProduct) {
        NewProductDialog(materials, colors, onDismiss = { newProduct = false }) {
            vm.createProductAndAdd(it)
            newProduct = false
        }
    }
}
```

- [ ] **Step 3: פרטי הזמנה**

`presentation/orders/OrderDetailViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.orders

import androidx.lifecycle.SavedStateHandle
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    private val orderId: Long = checkNotNull(handle.get<Long>("orderId"))

    val order = inventory.order(orderId).asState(null)
    val spools = inventory.orderSpools(orderId).asState(emptyList())

    fun arriveAll() = launchAction { inventory.markOrderArrived(orderId); "כל ההזמנה סומנה שהגיעה" }
    fun arrive(spoolId: Long) = launchAction { inventory.markArrived(listOf(spoolId)); null }
    fun removeSpool(spoolId: Long) = launchAction { inventory.removeOrderedSpool(spoolId); "הסליל הוסר מההזמנה" }

    fun deleteOrder(onDone: () -> Unit) = launchAction {
        inventory.deleteOrder(orderId)
        onDone()
        null
    }
}
```

`presentation/orders/OrderDetailScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.ConfirmDialog
import com.amir.filamenttracker.presentation.common.formatDate
import com.amir.filamenttracker.presentation.common.statusText

@Composable
fun OrderDetailScreen(onBack: () -> Unit, onSpoolClick: (Long) -> Unit, vm: OrderDetailViewModel = hiltViewModel()) {
    val order by vm.order.collectAsStateWithLifecycle()
    val spools by vm.spools.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var confirmDeleteOrder by remember { mutableStateOf(false) }
    var removeSpoolId by remember { mutableStateOf<Long?>(null) }
    val nothingArrived = spools.all { it.status == SpoolStatus.ORDERED }

    Scaffold(
        topBar = {
            BackTopBar("הזמנה", onBack, actions = {
                if (order != null && nothingArrived) {
                    IconButton(onClick = { confirmDeleteOrder = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "מחק הזמנה")
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            order?.let { o ->
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("${o.vendor} · ${o.orderedAt.formatDate()}", style = MaterialTheme.typography.titleMedium)
                        o.orderNumber?.let { Text("מספר הזמנה: $it") }
                        o.expectedAt?.let { Text("צפי הגעה: ${it.formatDate()}") }
                    }
                }
            }
            if (spools.any { it.status == SpoolStatus.ORDERED }) {
                item {
                    Button(onClick = vm::arriveAll, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Text("הכל הגיע")
                    }
                }
            }
            items(spools, key = { it.spoolId }) { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(s.statusText()) },
                    leadingContent = { ColorDot(s.colorHex) },
                    trailingContent = {
                        if (s.status == SpoolStatus.ORDERED) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { vm.arrive(s.spoolId) }) { Text("הגיע") }
                                IconButton(onClick = { removeSpoolId = s.spoolId }) {
                                    Icon(Icons.Default.Delete, contentDescription = "הסר מההזמנה")
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { onSpoolClick(s.spoolId) },
                )
            }
        }
    }

    if (confirmDeleteOrder) {
        ConfirmDialog(
            text = "למחוק את ההזמנה ואת כל הסלילים שבה?",
            confirmLabel = "מחק",
            onConfirm = { vm.deleteOrder(onBack) },
            onDismiss = { confirmDeleteOrder = false },
        )
    }
    removeSpoolId?.let { id ->
        ConfirmDialog(
            text = "להסיר את הסליל מההזמנה? כלומר, הוא לא יגיע.",
            confirmLabel = "הסר",
            onConfirm = { vm.removeSpool(id) },
            onDismiss = { removeSpoolId = null },
        )
    }
}
```

- [ ] **Step 4: חיבור לניווט**

ב-`AppNav.kt`:
- להחליף `composable(Routes.ORDERS) { PlaceholderScreen("הזמנות") }` ב-:
```kotlin
            composable(Routes.ORDERS) {
                OrdersScreen(
                    onOrderClick = { nav.navigate(Routes.order(it)) },
                    onNewOrder = { nav.navigate(Routes.NEW_ORDER) },
                )
            }
```
- להחליף `composable(Routes.NEW_ORDER) { PlaceholderScreen("הזמנה חדשה") }` ב-`composable(Routes.NEW_ORDER) { NewOrderScreen(onBack = { nav.popBackStack() }) }`
- בבלוק של `Routes.ORDER`: להחליף את `{ PlaceholderScreen("הזמנה") }` ב-`{ OrderDetailScreen(onBack = { nav.popBackStack() }, onSpoolClick = { nav.navigate(Routes.spool(it)) }) }`
- imports: `com.amir.filamenttracker.presentation.orders.OrdersScreen`, `...orders.NewOrderScreen`, `...orders.OrderDetailScreen`

- [ ] **Step 5: בנייה ובדיקה ידנית**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest assembleDebug installDebug
```
Expected: `BUILD SUCCESSFUL`. באמולטור:
- "הזמנה חדשה": ספק Amazon, צפי הגעה דרך לוח-שנה, "הוסף מוצר" עם PLA שחור כפול 2, ומוצר חדש PETG שקוף כפול 1. "שמור הזמנה" חוזר לרשימה, ושם מופיעה "3 סלילים · ממתינה · צפי ...".
- בפרטי ההזמנה, "הגיע" על סליל אחד משנה את הרשימה ל"ממתינה, הגיעו 1 מתוך 3".
- כפתור הפח העליון נעלם ברגע שסליל אחד הגיע.
- הסרת סליל ממתין, אחרי אישור, משאירה 2 סלילים.
- "הכל הגיע": ההזמנה עוברת ל"הגיעה", ובמסך המלאי הסלילים מופיעים כסגורים.
- הזמנה חדשה עם סליל אחד, ומחיקתה דרך הפח: ההזמנה נעלמת, וגם הסליל.
- במסך המלאי, ספירת "בדרך" מופיעה לפני ההגעה, וכרטיס ההתראות מציג "בדרך" לשילוב חסר שהוזמן.

- [ ] **Step 6: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(ui): מסכי הזמנות - רשימה, הזמנה חדשה, פרטים והגעה

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 15: מסך תצרוכת וסגירת הניווט

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/consumption/ConsumptionViewModel.kt`, `ConsumptionScreen.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/presentation/navigation/AppNav.kt` (המסלול האחרון, ומחיקת `PlaceholderScreen`)
- Test: `app/src/test/java/com/amir/filamenttracker/presentation/consumption/ConsumptionRowsTest.kt`

**Interfaces:**
- Consumes: `StockRepository.finishes` (Task 9); `InventoryRepository.spools` (Task 8); `ConsumptionForecast`, `StockCalculator` (Tasks 4-5); `formatInterval`, `Forecast.label` (Task 10); `java.time.Clock` (Hilt, Task 6).
- Produces: `ConsumptionScreen()`, `enum class ConsumptionRange`, `internal fun buildRows(finishes, spools, range, now): List<ConsumptionRowUi>`.

- [ ] **Step 1: בדיקת בניית השורות (נכשלת)**

`app/src/test/java/com/amir/filamenttracker/presentation/consumption/ConsumptionRowsTest.kt`:
```kotlin
package com.amir.filamenttracker.presentation.consumption

import com.amir.filamenttracker.data.db.FinishRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ConsumptionRowsTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private fun daysAgo(n: Long) = now.minus(Duration.ofDays(n))
    private fun finish(colorId: Long, color: String, at: Instant) = FinishRow(1, "PLA", colorId, color, null, at)
    private fun spool(colorId: Long, status: SpoolStatus) = SpoolRow(
        spoolId = colorId * 100, status = status, amsSlot = null, orderId = null, productId = 1, brand = "X",
        weightGrams = 1000, materialId = 1, materialName = "PLA", colorId = colorId, colorName = "c", colorHex = null,
    )

    @Test
    fun `counts finishes in range, uses full history for the rate, sorts by count`() {
        val rows = buildRows(
            finishes = listOf(
                finish(10, "שחור", daysAgo(400)),
                finish(10, "שחור", daysAgo(30)),
                finish(10, "שחור", daysAgo(10)),
                finish(11, "לבן", daysAgo(5)),
            ),
            spools = listOf(spool(10, SpoolStatus.SEALED)),
            range = ConsumptionRange.SIX_MONTHS,
            now = now,
        )
        assertEquals(listOf("PLA שחור", "PLA לבן"), rows.map { it.title })
        assertEquals(2, rows[0].finishedInRange)
        assertEquals("סליל כל ~27 שבועות", rows[0].rateText)
        assertEquals("אזל מהמלאי", rows[1].forecastText)
    }
}
```

החישוב לבדיקה: פער ממוצע = 390 / 2 = 195 ימים, כלומר 27 שבועות בחלוקה שלמה (195 / 7 = 27). ללבן אין סליל זמין, ולכן "אזל מהמלאי".

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest --tests "*ConsumptionRowsTest"
```
Expected: FAIL, `Unresolved reference: buildRows`.

- [ ] **Step 2: ConsumptionViewModel**

`presentation/consumption/ConsumptionViewModel.kt`:
```kotlin
package com.amir.filamenttracker.presentation.consumption

import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.data.db.FinishRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.ConsumptionForecast
import com.amir.filamenttracker.domain.StockCalculator
import com.amir.filamenttracker.domain.model.StockKey
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.formatInterval
import com.amir.filamenttracker.presentation.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class ConsumptionRange(val label: String, val months: Long?) {
    THREE_MONTHS("3 חודשים", 3),
    SIX_MONTHS("6 חודשים", 6),
    YEAR("שנה", 12),
    ALL("הכל", null),
}

data class ConsumptionRowUi(
    val key: StockKey,
    val title: String,
    val colorHex: String?,
    val finishedInRange: Int,
    val rateText: String,
    val forecastText: String?,
)

@HiltViewModel
class ConsumptionViewModel @Inject constructor(
    stock: StockRepository,
    inventory: InventoryRepository,
    private val clock: Clock,
) : BaseViewModel() {
    private val range = MutableStateFlow(ConsumptionRange.SIX_MONTHS)
    val selectedRange: StateFlow<ConsumptionRange> = range.asStateFlow()

    /** Null until the first load. */
    val rows: StateFlow<List<ConsumptionRowUi>?> =
        combine(stock.finishes, inventory.spools, range) { finishes, spools, r ->
            buildRows(finishes, spools, r, clock.instant())
        }.asState<List<ConsumptionRowUi>?>(null)

    fun selectRange(r: ConsumptionRange) { range.value = r }
}

internal fun buildRows(
    finishes: List<FinishRow>,
    spools: List<SpoolRow>,
    range: ConsumptionRange,
    now: Instant,
): List<ConsumptionRowUi> {
    val from = range.months?.let { now.atZone(ZoneId.systemDefault()).minusMonths(it).toInstant() }
    val available = StockCalculator.count(spools.map { StockKey(it.materialId, it.colorId) to it.status })
    return finishes.groupBy { StockKey(it.materialId, it.colorId) }.map { (key, rows) ->
        val times = rows.map { it.at }
        val first = rows.first()
        ConsumptionRowUi(
            key = key,
            title = "${first.materialName} ${first.colorName}",
            colorHex = first.colorHex,
            finishedInRange = times.count { from == null || !it.isBefore(from) },
            // The rate uses the whole history: a short window would make it jumpy.
            rateText = ConsumptionForecast.averageInterval(times)?.formatInterval() ?: "אין מספיק היסטוריה לקצב",
            forecastText = ConsumptionForecast.forecast(times, available[key]?.available ?: 0, now).label(),
        )
    }.sortedWith(compareByDescending<ConsumptionRowUi> { it.finishedInRange }.thenBy { it.title })
}
```

- [ ] **Step 3: ConsumptionScreen**

`presentation/consumption/ConsumptionScreen.kt`:
```kotlin
package com.amir.filamenttracker.presentation.consumption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NoInsets

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsumptionScreen(vm: ConsumptionViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val range by vm.selectedRange.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = NoInsets) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsumptionRange.entries.forEach { r ->
                        FilterChip(selected = r == range, onClick = { vm.selectRange(r) }, label = { Text(r.label) })
                    }
                }
            }
            val list = rows
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        "עוד לא הסתיים אף סליל. התצרוכת תופיע כאן אחרי שתסמן סלילים שהסתיימו.",
                        Modifier.padding(16.dp),
                    )
                }
            }
            if (!list.isNullOrEmpty()) {
                item {
                    Text(
                        "הסתיימו בטווח: ${list.sumOf { it.finishedInRange }} סלילים",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { "${it.key.materialId}-${it.key.colorId}" }) { row ->
                ListItem(
                    headlineContent = { Text(row.title) },
                    leadingContent = { ColorDot(row.colorHex, size = 28.dp) },
                    supportingContent = { Text(listOfNotNull(row.rateText, row.forecastText).joinToString("\n")) },
                    trailingContent = { Text("${row.finishedInRange}", style = MaterialTheme.typography.titleLarge) },
                )
            }
        }
    }
}
```

- [ ] **Step 4: סגירת הניווט**

ב-`AppNav.kt`:
- להחליף `composable(Routes.CONSUMPTION) { PlaceholderScreen("תצרוכת") }` ב-`composable(Routes.CONSUMPTION) { ConsumptionScreen() }`
- למחוק את הפונקציה `PlaceholderScreen` ואת ההערה שמעליה.
- למחוק imports שכבר לא בשימוש: `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.Alignment`.
- להוסיף import: `com.amir.filamenttracker.presentation.consumption.ConsumptionScreen`
- לוודא שהמחרוזת `PlaceholderScreen` לא מופיעה עוד בקוד: `grep -rn PlaceholderScreen app/src` לא מחזיר כלום.

- [ ] **Step 5: בדיקות, בנייה ובדיקה ידנית**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew testDebugUnitTest assembleDebug installDebug
grep -rn PlaceholderScreen app/src || echo "no placeholders left"
```
Expected: `ConsumptionRowsTest` עובר, `BUILD SUCCESSFUL`, ומודפס `no placeholders left`. באמולטור, אחרי שסומנו "הסתיים" שני סלילים של אותו צבע:
- בלשונית "תצרוכת" מופיעה שורה לצבע, עם מספר הסיומים בטווח.
- שורת קצב מופיעה. "בערך סליל ביום" צפוי כשהסיומים קרובים בזמן.
- צפי סיום, או "אזל מהמלאי".
- החלפת טווח משנה את הספירה.

- [ ] **Step 6: Commit**

```bash
cd /c/Projects/filament_tracker
git add app/src
git commit -m "feat(ui): מסך תצרוכת עם קצב וצפי; כל המסלולים מחוברים

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 16: אימות מקצה לקצה ותיעוד

**Files:**
- Create: `README.md`, `SPEC.md`
- Modify: `current_focus.md` (נוצר בסשן התכנון. להוסיף בלוק-סשן חדש בראש, מעל הקיים)
- No code changes, unless the walkthrough finds a bug. A bug goes back to the task that owns the code: fix it with a test there, then repeat this task.

**Interfaces:**
- Consumes: the whole app.
- Produces: an install that has been verified, and the project's truth documents.

- [ ] **Step 1: ריצה מלאה נקייה**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew clean testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL`, וכל הבדיקות ירוקות. לרשום את מספר הבדיקות מתוך `app/build/reports/tests/testDebugUnitTest/index.html`.

- [ ] **Step 2: התקנה נקייה על האמולטור**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" uninstall com.amir.filamenttracker || true
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Projects/filament_tracker && ./gradlew installDebug
```

- [ ] **Step 3: שלושת המסלולים של סעיף 8 במסמך העיצוב**

לבצע ולסמן כל שורה. אחרי כל מסלול, לצלם מסך עם `adb exec-out screencap -p > C:/DL/ft_<n>.png`.

**מסלול 1: קליטת מלאי קיים.** 5 סלילים מייצגים מתוך הרשימה ב-`c:\Projects\bambu_printer\SPEC.md`, סעיף "מלאי-חומרים":
  - [ ] TEMPlast PLA Pro שחור, בשימוש בחריץ 1
  - [ ] PLA לבן, פתוח באחסון
  - [ ] PETG TEMPlast אדום, סגור, כמות 1
  - [ ] PETG שקוף, סגור
  - [ ] PETG Velvet סגול, בשימוש בחריץ 4
  - [ ] במסך המלאי: PETG ו-PLA כקבוצות, ספירות נכונות, "המלאי תקין" (אין עדיין יעדים)

**מסלול 2: הזמנה עד הגעה.**
  - [ ] ב"שומר במלאי": PLA שחור עם מינימום 1. בכרטיס מופיע "הולך להיות חסר: PLA שחור".
  - [ ] הזמנה חדשה: PLA שחור כפול 1, PLA כתום כפול 1 (מוצר חדש). הכרטיס עובר ל"בדרך".
  - [ ] "הגיע" לסליל השחור בלבד. ההזמנה "ממתינה, הגיעו 1 מתוך 2", והכרטיס "המלאי תקין".
  - [ ] "הכל הגיע". ההזמנה "הגיעה".

**מסלול 3: AMS, אחסון וסיום.**
  - [ ] חריץ 1: החלפה לכתום. השחור עובר ל"פתוח".
  - [ ] חריץ 1: "הסתיים". החריץ ריק.
  - [ ] בפרטי הסליל הכתום: "בטל פעולה אחרונה" מחזיר אותו לחריץ 1.
  - [ ] "הסתיים" שוב. בלשונית "תצרוכת" מופיעה שורת "PLA כתום" עם 1.
  - [ ] אחרי סיום השחור, מתחלפת שורת ההתראה ל"חסר" או "הולך להיות חסר" לפי מה שנשאר.

**קצוות (Review Focus):**
  - [ ] מוצר חדש עם חומר "pla" (אותיות קטנות) לא יוצר חומר שלישי.
  - [ ] גוון `red` נחסם בדיאלוג.
  - [ ] ביטול על סליל שזה עתה נקלט: הכפתור מושבת.
  - [ ] סיבוב מסך במסך "הוספת סליל קיים" שומר את המצב שנבחר.
  - [ ] מצב כהה במערכת (`adb shell cmd uimode night yes`): הכל קריא. אחר כך `night no`.

- [ ] **Step 4: קובצי תיעוד**

`README.md`:
```markdown
# Filament Tracker

אפליקציית Android אישית לניהול מלאי סלילי-פילמנט למדפסת Bambu Lab A1 mini עם AMS lite.

- מחזור-חיים של סליל: הוזמן, סגור, בשימוש בחריץ, פתוח באחסון, הסתיים
- "חסר" ו"הולך להיות חסר" לפי רשימת שילובים שתמיד צריכים להיות במלאי
- תצרוכת לפי סלילים שהסתיימו, עם קצב וצפי סיום

Kotlin, Jetpack Compose, Hilt, Room. כל הנתונים מקומיים במכשיר.

עיצוב: `docs/superpowers/specs/2026-10-18-filament-tracker-design.md`

## בנייה

    export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
    ./gradlew testDebugUnitTest assembleDebug
```

`SPEC.md`: מצב נוכחי (גרסה 0.1.0, מה עובד), הפניה למסמך העיצוב במקום לשכפל אותו, ו"הצעד הבא". הצעד הבא הוא רשימת סעיף 9 במסמך העיצוב, וחילוץ הזמנה מטקסט ראשון בתור. כל טבלה בעברית עטופה ב-`<div dir="rtl" align="right">`.

`current_focus.md`: בלוק סשן חדש בראש הקובץ, מתחת לכותרת-האזהרה הקיימת. הבלוק מתאר מה נבנה, מספר הבדיקות, תוצאת המעבר הידני, ומה פתוח.

- [ ] **Step 5: Commit ו-push**

```bash
cd /c/Projects/filament_tracker
git add README.md SPEC.md current_focus.md
git commit -m "docs: README, SPEC ו-current_focus אחרי אימות מקצה לקצה

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
git status --short && git rev-list --left-right --count origin/main...main
```
Expected: `status` ריק, ו-`0	0`.
