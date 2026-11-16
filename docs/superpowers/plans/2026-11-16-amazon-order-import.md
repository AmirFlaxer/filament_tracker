# ייבוא הזמנות מאמזון ומספר משלוח - תוכנית מימוש

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** כפתור "ייבוא מאמזון" קורא את רשימת ההזמנות מחשבון האמזון של אמיר, מציג הזמנות פילמנט שעוד לא נמסרו ושאינן באפליקציה, ופותח מסך אישור ממולא (כרטיס לכל שורה מאמזון). בנוסף נוסף להזמנה שדה "מספר משלוח" שמתמלא אוטומטית מאמזון וגם ידנית.

**Architecture:**
- **פענוח טהור** (`domain/amazon/`): טקסט דף ההזמנות לרשימת הזמנות, שם מוצר לחומר, צבעים וסלילים, טקסט דף המעקב למספרי משלוח, ובדיקות כתובת. הכול פונקציות טהורות עם בדיקות מקובצי טקסט אמיתיים מוסווים.
- **טיוטת ייבוא** (`presentation/amazon/ImportDraft.kt`): לוגיקה טהורה שבונה כרטיסים ושורות מול הקטלוג.
- **WebView** (`data/amazon/AmazonWeb.kt`): WebView נסתר בתצוגת מחשב שקורא דפים, ו-WebView גלוי בתצוגת טלפון להתחברות. העוגיות משותפות דרך `CookieManager`.
- **מסך אחד** (`presentation/amazon/`): מצב רשימה ומצב אישור באותו מסך ובאותו ViewModel, כדי לא להעביר הזמנה מפוענחת בין מסכים.
- **מסד:** עמודה `trackingNumber` בטבלת `orders`, מגרסה 5 ל-6.

**Tech Stack:** Kotlin, Compose Material3, Room 2.6.1, Hilt, Android WebView, JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-16-amazon-order-import-design.md`

## Global Constraints

- **תמיד מסך אישור:** שום הזמנה או מוצר לא נשמרים בלי לחיצה על "שמור הזמנה". מוצר "חדש" נוצר רק בשמירה.
- **רק הזמנות חדשות:** ברשימה רק הזמנות שבשורת המצב שלהן אין `Delivered` ושמספרן לא קיים באפליקציה (כולל ארכיון). החריג היחיד לעדכון הזמנה קיימת: מספר משלוח.
- **לא נוגעים בנתונים היסטוריים:** המעבר רק מוסיף עמודה ריקה. לא מעבירים ערכים בין `orderNumber` ל-`trackingNumber`.
- **רשת:** הרשאת `INTERNET` נוספת ל-manifest הראשי. ניווט ראשי ב-WebView רק ל-`amazon.com` ותתי-הדומיינים שלה.
- **user-agent לקריאה:** `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36`. ה-WebView של ההתחברות נשאר עם user-agent ברירת המחדל.
- **כתובות:** רשימה `https://www.amazon.com/gp/css/order-history`, פרטי הזמנה `https://www.amazon.com/your-orders/order-details?orderID=<מספר>`.
- **הגבלת זמן לקריאת דף:** 15 שניות.
- **ריפו ציבורי:** קובצי בדיקה בלי שם, כתובת, מספרי הזמנה אמיתיים או סכומים. הקבצים ב-`app/src/test/resources/amazon/` כבר מוסווים, ואסור להוסיף להם נתונים אמיתיים.
- **מסד הנתונים:** מגרסה 5 לגרסה 6 עם `MIGRATION_5_6` ובדיקת מעבר. לא משתמשים ב-`fallbackToDestructiveMigration`.
- **גרסת האפליקציה:** `versionName = "0.7.0"`, `versionCode = 11`.
- **טקסט בממשק:** בעברית, בלי חצים ובלי מקף ארוך. מספרים ושמות מאמזון מוצגים בכיוון LTR.
- **בדיקות:** רק על האמולטור (`-s emulator-5554`). האמולטור כבר מחובר לאמזון מהניסוי. לא מתקינים על הטלפון של אמיר בלי בקשה מפורשת.
- **סביבה:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`, ואת adb מריצים בנתיב המלא של ה-SDK.
- **פקודת הבדיקה המלאה:** `./gradlew testDebugUnitTest assembleDebug`. בהתחלה יש 249 בדיקות ירוקות.
- **הענף:** `amazon-import`. ענף הניסוי `spike/amazon-webview` לא ממוזג ולא נוגעים בו.

## Review Focus

1. **שם מוצר עם `+` שאינו רשימת צבעים** ("PLA+ Filament", "+/-0.02mm"): אסור שייקרא כצבעים. נבדק במשימה 3.
2. **הזמנה שכבר יובאה מופיעה שוב** (לחיצה כפולה על שמירה, או רשימה שנטענה לפני השמירה): השמירה השנייה נדחית בהודעה. נבדק במשימה 5.
3. **דף אמזון שנטען בלי הזמנות** (שינוי מבנה, או דף ביניים של אמזון): הודעת שגיאה ולא "אין הזמנות חדשות". נבדק במשימה 2 (`parseAmazonOrders` מחזיר `null` כשאין עוגן `ORDER PLACED`).
4. **צפי שחוצה שנה** (הזמנה בדצמבר עם הגעה בינואר): השנה הבאה. נבדק במשימה 2.
5. **ניווט החוצה מאמזון בזמן התחברות** (קישור עזרה או הפניה): נחסם. נבדק במשימה 4 (`isAmazonUrl`).

---

### Task 1: מספר משלוח במסד, בריפוזיטורי ובמסך ההזמנה

**Files:**
- Modify: `app/src/main/java/com/amir/filamenttracker/data/db/Entities.kt` (`OrderEntity`)
- Modify: `app/src/main/java/com/amir/filamenttracker/data/db/Migrations.kt`, `AppDatabase.kt` (version 6), `data/di/DatabaseModule.kt` (`addMigrations`)
- Modify: `app/src/main/java/com/amir/filamenttracker/data/db/OrderDao.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/data/InventoryRepository.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/presentation/orders/OrderDetailScreen.kt`, `OrderDetailViewModel.kt`
- Create: `app/schemas/com.amir.filamenttracker.data.db.AppDatabase/6.json` (נוצר בבנייה)
- Test: `app/src/test/java/com/amir/filamenttracker/data/MigrationTest.kt`, `data/InventoryRepositoryTest.kt`

**Interfaces:**
- Produces: `OrderEntity.trackingNumber: String? = null` (שדה אחרון); `val MIGRATION_5_6`; `InventoryRepository.updateOrder(orderId: Long, vendor: String, orderNumber: String?, expectedAt: LocalDate?, trackingNumber: String?)`; `suspend fun setTrackingNumber(orderId: Long, trackingNumber: String)`; `suspend fun orderNumbers(): Set<String>`; `suspend fun ordersAwaitingTracking(): List<OrderEntity>`; `createOrder(..., orderedAt: Instant = clock.instant())` (פרמטר אחרון חדש).

- [ ] **Step 1: בדיקות שנכשלות**

ב-`MigrationTest`:
```kotlin
@Test
fun `migration 5 to 6 adds an empty tracking number and keeps every order`() {
    val v5 = helper.createDatabase("migration-5-6.db", 5)
    v5.execSQL("INSERT INTO orders (id, vendor, orderedAt, expectedAt, orderNumber, archived) VALUES (1,'Amazon',0,NULL,'RU0000000000Z',1)")
    v5.close()
    val db = helper.runMigrationsAndValidate("migration-5-6.db", 6, true, MIGRATION_5_6)
    db.query("SELECT orderNumber, trackingNumber FROM orders").use { c ->
        c.moveToFirst()
        assertEquals("RU0000000000Z", c.getString(0)) // historical value stays where it was
        assertTrue(c.isNull(1))
    }
}
```
ב-`InventoryRepositoryTest`:
- `createOrder keeps the given ordered date`: `orderedAt = Instant.parse("2026-11-15T00:00:00Z")` נשמר כמו שהוא.
- `updateOrder stores and clears the tracking number`: `" RU1Z "` נשמר כ-`"RU1Z"`, ו-`""` נשמר כ-`null`.
- `orderNumbers includes archived orders and skips blank ones`: הזמנה בארכיון עם `"111-0000001-0000001"` והזמנה בלי מספר מחזירות `setOf("111-0000001-0000001")`.
- `ordersAwaitingTracking returns only pending Amazon orders without a tracking number`: מתוך ארבע הזמנות (מספר אמזון וסליל ORDERED, אותו דבר עם `trackingNumber`, מספר `"RU0000000000Z"` וסליל ORDERED, מספר אמזון וכל הסלילים הגיעו) חוזרת רק הראשונה.

- [ ] **Step 2: הרצה, נכשל**

Run: `./gradlew testDebugUnitTest --tests "*MigrationTest*" --tests "*InventoryRepositoryTest*"`
Expected: FAIL (`MIGRATION_5_6` ו-`trackingNumber` לא קיימים).

- [ ] **Step 3: מימוש**

- `MIGRATION_5_6`: `ALTER TABLE orders ADD COLUMN trackingNumber TEXT`. ה-KDoc כמו ב-`MIGRATION_4_5`: "הטבלה מקבלת עמודה ריקה, אף שורה קיימת לא משתנה".
- `ordersAwaitingTracking`: `archived = 0`, `trackingNumber IS NULL`, יש לפחות סליל אחד בסטטוס `ORDERED`, ומספר ההזמנה עובר את `isAmazonOrderNumber` (ממשימה 4. עד אז regex מקומי `\d{3}-\d{7}-\d{7}`, ובמשימה 4 מחליפים לקריאה לפונקציה).
- ל-`updateOrder` אין ערך ברירת מחדל ל-`trackingNumber`, כדי שעריכה לא תמחק מספר בטעות. הקריאות הקיימות בבדיקות מקבלות `trackingNumber = null`.
- `setTrackingNumber` זורק `SpoolRuleException("ההזמנה לא נמצאה")` כמו `updateOrder`.
- במסך ההזמנה: מתחת ל"מספר הזמנה", שורה `"מספר משלוח: $it"` (המספר ב-LTR) עם `IconButton` של העתקה (`Icons.Default.ContentCopy`, תיאור "העתק מספר משלוח"), שמעתיק ל-`LocalClipboardManager` ומציג snackbar "הועתק". ב-`EditOrderDialog` שדה חדש "מספר משלוח, לא חובה" אחרי מספר ההזמנה, ו-`onConfirm` מקבל אותו.

- [ ] **Step 4: הרצה, עובר**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, וקובץ `6.json` נוצר.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/schemas
git commit -m "feat(orders): מספר משלוח בהזמנה, מסד 5 ל-6"
```

---

### Task 2: פענוח דף ההזמנות וחישוב הצפי

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/amazon/AmazonOrders.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/domain/amazon/AmazonOrdersTest.kt`
- Fixture (קיים): `app/src/test/resources/amazon/order-history.txt`

**Interfaces:**
- Produces:
  ```kotlin
  data class AmazonItem(val quantity: Int, val title: String)
  data class AmazonOrder(val orderNumber: String, val orderedOn: LocalDate, val status: String, val items: List<AmazonItem>)
  fun parseAmazonOrders(text: String): List<AmazonOrder>?   // null: no "ORDER PLACED" anchor at all
  val AmazonOrder.isDelivered: Boolean                         // status starts with "Delivered"
  fun expectedDate(status: String, orderedOn: LocalDate, today: LocalDate): LocalDate?
  ```

- [ ] **Step 1: בדיקות שנכשלות**

טעינת הקובץ: `javaClass.getResource("/amazon/order-history.txt")!!.readText()`.
```kotlin
@Test fun `fixture has ten orders in page order`() {
    val orders = parseAmazonOrders(page)!!
    assertEquals(10, orders.size)
    assertEquals("111-0000001-0000001", orders[0].orderNumber)
    assertEquals(LocalDate.of(2026, 11, 15), orders[0].orderedOn)
    assertEquals("Arriving November 24 - December 1", orders[0].status)
    assertEquals(listOf(AmazonItem(2, "Panchroma Matte PLA 3D Printer Filament Bundle 2kg, Earth Brown+Wood Brown | 1.75mm High Speed 3D Printing Filament Fits for Most FDM Printers, ±0.02mm Accuracy, Neaty Wound & Matte Finish, 2kg Pack")), orders[0].items)
}
@Test fun `missing quantity means one`() // orders[1].items == [AmazonItem(1, "iSANMATE Silk PLA ... 4 Spools 1kg Each")]
@Test fun `an order with two products keeps both`() // orders[4].items.map { it.quantity } == listOf(2, 2), titles start with "QFOUR" and "XZN"
@Test fun `footer after the last order is not an item`() // orders[9].items.size == 1, title starts with "UGUESS"
@Test fun `delivered orders are marked`() // orders.count { it.isDelivered } == 8
@Test fun `page without orders is null, not empty`() // parseAmazonOrders("Sign in\nEmail") == null
```
`expectedDate` (היום `2026-11-16`, יום שני):

<div dir="rtl" align="right">

| status | orderedOn | צפוי |
|---|---|---|
| `Arriving November 24 - December 1` | 2026-11-15 | 2026-12-01 |
| `Arriving December 8 - December 21` | 2026-11-08 | 2026-12-21 |
| `Arriving January 3 - January 9` | 2026-12-20 | 2027-01-09 |
| `Arriving November 24` | 2026-11-15 | 2026-11-24 |
| `Arriving Nov 24 - Dec 1` | 2026-11-15 | 2026-12-01 |
| `Arriving tomorrow` | 2026-11-15 | 2026-11-17 |
| `Arriving Saturday` | 2026-11-15 | 2026-11-21 |
| `Shipped` | 2026-11-15 | null |

</div>

- [ ] **Step 2: הרצה, נכשל**

Run: `./gradlew testDebugUnitTest --tests "*AmazonOrdersTest*"`
Expected: FAIL (unresolved reference).

- [ ] **Step 3: מימוש ב-`AmazonOrders.kt`**

הטקסט מחולק לבלוקים לפי שורה שהיא בדיוק `ORDER PLACED`. בכל בלוק: השורה הבאה היא התאריך (`MMMM d, yyyy`, Locale.US), מספר ההזמנה מהשורה `ORDER # <מספר>`, ושורת המצב היא השורה הראשונה אחרי `View order details...` שאינה `Ask Alexa about this order`. אחרי שורת המצב מגיעים הפריטים. שורה שכולה ספרות היא הכמות של הפריט הבא. שורה שאינה ריקה ואינה ברשימת שורות הפעולה היא שם מוצר. רשימת שורות הפעולה: `Buy it again`, `Track package`, `View or edit order`, `Ask Product Question`, `Write a product review`, `Get product support`, `Return or replace items`, `Share gift receipt`, `More options`, וכל שורה שמתחילה ב-`Return or replace items:`. הבלוק האחרון נגמר בשורה `Back to top`. חודשים: שמות מלאים ושלוש אותיות. השנה: של `orderedOn`, וחודש מוקדם מחודש ההזמנה פירושו השנה הבאה. `tomorrow` הוא `today + 1`, ושם יום בשבוע הוא המופע הבא שלו אחרי `today`.

- [ ] **Step 4: הרצה, עובר**

Run: `./gradlew testDebugUnitTest --tests "*AmazonOrdersTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amir/filamenttracker/domain/amazon app/src/test
git commit -m "feat(amazon): פענוח דף ההזמנות וחישוב צפי"
```

---

### Task 3: פענוח שם מוצר מאמזון

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/amazon/AmazonTitle.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/domain/amazon/AmazonTitleTest.kt`

**Interfaces:**
- Consumes: `readLabel(text, knownBrands, knownFamilies, knownColors)` ו-`MULTI_COLOR_TERMS` הקיימים.
- Produces:
  ```kotlin
  data class AmazonTitleReading(
      val brand: String, val family: String?, val variant: String, val finish: String,
      val weightGrams: Int, val spoolsPerUnit: Int, val colors: List<String>, val combined: Boolean,
  )
  fun readAmazonTitle(title: String, knownBrands: List<String>, knownFamilies: List<String>, knownColors: List<String>): AmazonTitleReading
  fun isFilamentTitle(title: String, knownFamilies: List<String>): Boolean
  ```

- [ ] **Step 1: בדיקות שנכשלות**

בכל הבדיקות: `knownBrands = listOf("SUNLU", "ELEGOO", "iSANMATE")`, `knownFamilies = listOf("PLA", "PETG")`, `knownColors = listOf("Black", "White")`. השמות מתוך `order-history.txt`. `brand / family / colors / spoolsPerUnit`:

<div dir="rtl" align="right">

| שם (התחלה) | brand | family | colors | spoolsPerUnit |
|---|---|---|---|---|
| `Panchroma Matte PLA ... Bundle 2kg, Earth Brown+Wood Brown \| ...` | Panchroma | PLA | Earth Brown, Wood Brown | 2 |
| `SUNLU High Speed PETG Filament 1.75mm, Black+White, 2kg Total, 2-Pack` | SUNLU | PETG | Black, White | 2 |
| `ELEGOO Rapid PETG 3D Printer Filament Bundle 1.75mm Black/Gray 2KG` | ELEGOO | PETG | Black, Gray | 2 |
| `IEMAI High Speed PETG ... 1.75mm, 600mm/s, 1kg, Black` | IEMAI | PETG | Black | 1 |
| `iSANMATE Silk PLA ... Shiny Dual Color ... 4 Spools 1kg Each` | iSANMATE | PLA | (ריק) | 4 |
| `UGUESS 10KG PETG 3D Filament Bundle, 1.75mm 10 Colors` | UGUESS | PETG | (ריק) | 10 |
| `Meuymeu 6KG PLA+ Filament Bundle, 1.75mm 3D Printer Filament` | Meuymeu | PLA | (ריק) | 6 |
| `XZN Silk PLA Filament 1.75mm, Metal-Like Shiny Gold PLA ... 1 Spool ..., Silk Gold` | XZN | PLA | Gold | 1 |

</div>

ועוד:
- `marketing after the pipe is ignored`: ל-Panchroma `finish == "Matte"` ו-`variant == ""` ("High Speed" ו-"Matte Finish" באים אחרי ה-`|`).
- `Meuymeu PLA plus is a variant, not a color`: `variant == "+"`, `colors` ריק.
- `plus-minus is not a color list`: `"ACME PLA Filament, +/-0.02mm, 1kg"` נותן `colors` ריק.
- `dual color with listed colors is one combined spool`: `"ACME Silk PLA Dual Color Red/Gold 1kg"` נותן `combined == true`, `colors == ["Red", "Gold"]`, `spoolsPerUnit == 1`.
- `two colors without a pack hint are two spools`: `"ACME PLA Filament, Black+White"` נותן `spoolsPerUnit == 2`.
- `weight is 1000 by default`: לכל השורות בטבלה `weightGrams == 1000`.
- `non filament`: `isFilamentTitle("Chitu Systems PlateCycler C1M for Bambu Lab A1 Mini 3D Printer", families) == false`. כל השמות בטבלה מחזירים `true`.

- [ ] **Step 2: הרצה, נכשל**

Run: `./gradlew testDebugUnitTest --tests "*AmazonTitleTest*"`
Expected: FAIL

- [ ] **Step 3: מימוש ב-`AmazonTitle.kt`**

- מפענחים רק את `title.substringBefore('|')` ("הראש").
- מותג, חומר, תת-סוג וגימור מ-`readLabel(head, ...)`. כשאין מותג מוכר, המותג הוא המילה הראשונה בראש.
- רשימת צבעים: המופע הראשון בראש של הביטוי הבא, שבו אין רווח לפני ה-`+`/`/` ואין רווח אחריו, וכל חלק הוא מילה או שתיים שמתחילות באות גדולה:
  ```kotlin
  private val COLOR_LIST = Regex("""\p{Lu}\p{L}*(?: \p{Lu}\p{L}*)?(?:[+/]\p{Lu}\p{L}*(?: \p{Lu}\p{L}*)?)+""")
  ```
  חלק שהוא חומר מוכר (`PLA`) לא נחשב צבע. כשאין רשימה, הצבעים הם המילים בראש שה-`colorKey` שלהן נמצא בצבעי `readLabel`, בכתיב שבו הן מופיעות בשם.
- `combined`: יש ברשימה יותר מצבע אחד והראש מכיל מונח מ-`MULTI_COLOR_TERMS` (בלי תלות ברישיות, גם `Tricolor`).
- `spoolsPerUnit`: לפי הסדר: `(\d+)\s*-?\s*(pack|spools?|colors?)\b`; אחרת המשקל הכולל הגדול ביותר `(\d+(?:\.\d+)?)\s*kg\b` כשהוא מעל 1; אחרת מספר הצבעים כשהם לא `combined` ויש יותר מאחד; אחרת 1. כש-`combined`, תמיד 1.
- `weightGrams`: 1000, אלא אם יש בראש משקל `kg` קטן מ-1, ואז הוא.

- [ ] **Step 4: הרצה, עובר**

Run: `./gradlew testDebugUnitTest --tests "*AmazonTitleTest*" --tests "*LabelReadingTest*"`
Expected: PASS (גם הבדיקות הקיימות של `readLabel`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amir/filamenttracker/domain/amazon app/src/test
git commit -m "feat(amazon): פענוח שם מוצר לחומר, צבעים וסלילים"
```

---

### Task 4: מספרי משלוח וכתובות

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/amazon/AmazonTracking.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/data/InventoryRepository.kt` (`ordersAwaitingTracking` עובר ל-`isAmazonOrderNumber`)
- Test: `app/src/test/java/com/amir/filamenttracker/domain/amazon/AmazonTrackingTest.kt`
- Fixture (קיים): `app/src/test/resources/amazon/track-package.txt`

**Interfaces:**
- Produces: `fun parseTrackingIds(text: String): List<String>`; `fun isAmazonOrderNumber(value: String): Boolean`; `fun isAmazonUrl(url: String): Boolean`; `fun isSignInUrl(url: String): Boolean`.

- [ ] **Step 1: בדיקות שנכשלות**

- `parseTrackingIds(fixture) == listOf("RU0000000000Z")`; `parseTrackingIds("Tracking ID: A1\n...\nTracking ID: B2") == listOf("A1", "B2")`; טקסט בלי `Tracking ID` נותן רשימה ריקה.
- `isAmazonOrderNumber("111-1234567-7654321") == true`; `"RU0000000000Z"`, `"2300000000"` ו-`" 111-1234567-7654321"` נותנים `false`.
- `isAmazonUrl`: `true` ל-`https://www.amazon.com/ap/signin?x=1` ול-`https://amazon.com/`. `false` ל-`https://evil.com/?u=amazon.com`, `https://amazon.com.evil.io/`, `http://www.amazon.com/` (לא https), `https://www.amazon.co.uk/` ו-`javascript:alert(1)`.
- `isSignInUrl`: `true` לנתיבים שמתחילים ב-`/ap/` או `/ax/`. `false` ל-`/gp/css/order-history`.

- [ ] **Step 2: הרצה, נכשל**

Run: `./gradlew testDebugUnitTest --tests "*AmazonTrackingTest*"`
Expected: FAIL

- [ ] **Step 3: מימוש**

`parseTrackingIds`: `Regex("""Tracking ID:\s*([A-Za-z0-9]+)""")`, בלי כפילויות, לפי סדר. `isAmazonUrl`: עם `java.net.URI`, סכמה `https`, ומארח שהוא `amazon.com` או שמסתיים ב-`.amazon.com`. ב-`InventoryRepository.ordersAwaitingTracking` מחליפים את ה-regex המקומי בקריאה ל-`isAmazonOrderNumber`.

- [ ] **Step 4: הרצה, עובר**

Run: `./gradlew testDebugUnitTest --tests "*AmazonTrackingTest*" --tests "*InventoryRepositoryTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat(amazon): מספרי משלוח ובדיקת כתובות"
```

---

### Task 5: טיוטת ייבוא ושמירה

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/amazon/ImportDraft.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/data/InventoryRepository.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/presentation/amazon/ImportDraftTest.kt`, `data/InventoryRepositoryTest.kt`

**Interfaces:**
- Consumes: משימות 2 ו-3; `ProductRow`; `normalizeMaterialParts`; `colorKey`; `composeColorName`.
- Produces:
  ```kotlin
  // presentation/amazon/ImportDraft.kt
  data class ImportRow(val colorName: String, val quantity: Int, val productId: Long?)   // productId null = "חדש"
  data class ImportCard(
      val key: Int, val sourceTitle: String, val itemQuantity: Int,
      val brand: String, val family: String, val variant: String, val finish: String, val weightGrams: Int,
      val rows: List<ImportRow>, val colorsToMark: Int,                                 // >0 while colors are unknown
  )
  data class ImportChoices(val orders: List<AmazonOrder>, val skippedTitles: List<String>)
  data class CatalogLists(val brands: List<String>, val families: List<String>, val colors: List<String>)
  fun importableOrders(orders: List<AmazonOrder>, existingNumbers: Set<String>, knownFamilies: List<String>): ImportChoices
  fun buildCards(order: AmazonOrder, products: List<ProductRow>, lists: CatalogLists): List<ImportCard>
  fun matchProduct(products: List<ProductRow>, card: ImportCard, colorName: String): Long?
  fun ImportCard.rematch(products: List<ProductRow>): ImportCard        // after "עריכה" or a color change
  val List<ImportCard>.spoolCount: Int

  // data/InventoryRepository.kt
  data class ImportLine(
      val productId: Long?, val quantity: Int,
      val brand: String, val family: String, val variant: String, val finish: String,
      val colorName: String, val weightGrams: Int,
  )
  suspend fun importOrder(orderNumber: String, orderedAt: Instant, expectedAt: LocalDate?, lines: List<ImportLine>): Long
  ```

- [ ] **Step 1: בדיקות שנכשלות**

`ImportDraftTest` (מעל `order-history.txt`, `families = listOf("PLA", "PETG")`):
- `only orders not delivered and not already registered`: `importableOrders(orders, setOf("111-0000002-0000002"), families).orders.map { it.orderNumber } == listOf("111-0000001-0000001")`.
- `non filament items of a shown order are listed as skipped`: הזמנה שאינה נמסרה עם Panchroma ו-PlateCycler מחזירה את הזמנה עם פריט Panchroma בלבד, ו-`skippedTitles` עם שם ה-PlateCycler. הזמנה שכולה לא פילמנט לא מופיעה ב-`orders`.
- `panchroma becomes two new rows of two`: ל-`orders[0]` מול קטלוג ריק: כרטיס אחד, `rows == listOf(ImportRow("Earth Brown", 2, null), ImportRow("Wood Brown", 2, null))`, `colorsToMark == 0`.
- `existing product is matched`: קטלוג עם `ProductRow(brand = "Panchroma", materialFamily = "PLA", materialVariant = "", materialFinish = "Matte", colorName = "earth brown", weightGrams = 1000, ...)` נותן `productId` שלו בשורה הראשונה ו-`null` בשנייה. אותו מוצר עם `weightGrams = 750` לא מותאם.
- `bundle without colors asks to mark`: `iSANMATE` נותן `rows` ריק ו-`colorsToMark == 4`. QFOUR עם כמות 2 נותן `colorsToMark == 1`.
- `combined colors make one row`: `"ACME Silk PLA Dual Color Red/Gold 1kg"` בכמות 3 נותן `rows == listOf(ImportRow("Red / Gold", 3, null))`.
- `spool count`: `listOf(panchromaCard).spoolCount == 4`.

`InventoryRepositoryTest`:
- `importOrder creates new products once and the order with Amazon's date`: שתי שורות חדשות עם אותו מפרט (אותו צבע) יוצרות מוצר אחד. שורה עם `productId` קיים לא יוצרת מוצר. ההזמנה נשמרת עם `vendor == "Amazon"`, המספר, `orderedAt` ו-`expectedAt` שניתנו, ומספר הסלילים בסטטוס `ORDERED` שווה לסכום הכמויות.
- `importOrder refuses an order number that already exists`: ייבוא שני של אותו מספר זורק `SpoolRuleException` עם ההודעה `"ההזמנה הזו כבר רשומה"`, ומספר המוצרים לא גדל.
- `importOrder with nothing to order fails before creating products`: כל הכמויות 0 זורק, ואין מוצר חדש.

- [ ] **Step 2: הרצה, נכשל**

Run: `./gradlew testDebugUnitTest --tests "*ImportDraftTest*" --tests "*InventoryRepositoryTest*"`
Expected: FAIL

- [ ] **Step 3: מימוש**

- `buildCards`: כרטיס לכל פריט שעובר `isFilamentTitle`. השדות מ-`readAmazonTitle`, ו-`family` ריק כשה-`family` מהפענוח `null`. השורות לפי הסעיף "צבעים מהשם" במסמך העיצוב: `combined` נותן שורה אחת `composeColorName(colors)` בכמות `itemQuantity * spoolsPerUnit`. כשמספר הצבעים מחלק את `spoolsPerUnit`, כל צבע מקבל `itemQuantity * spoolsPerUnit / colors.size`. אחרת כל צבע מקבל `itemQuantity`. בלי צבעים: `rows` ריק ו-`colorsToMark = spoolsPerUnit`. לצבע שסומן אחר כך יש כמות `itemQuantity`.
- `matchProduct`: מותג שווה אחרי trim ובלי תלות ברישיות, `materialFamily` שווה בלי תלות ברישיות, `normalizeMaterialParts(variant, finish)` שווה ל-`normalizeMaterialParts(p.materialVariant, p.materialFinish)`, `weightGrams` שווה, ו-`colorKey(colorName) == colorKey(p.colorName)`.
- `importOrder`: בטרנזקציה אחת. בודקים קודם שאין הזמנה עם אותו מספר (אחרי trim), ואחר כך יוצרים מוצרים ל-`productId == null` דרך `CatalogRepository(db).createProduct(brand, family, variant, colorName, colorHex = "", weightGrams, note = "", finish)`, עם מוצר אחד לכל מפרט זהה. בסוף קוראים ל-`createOrder("Amazon", orderNumber, expectedAt, items, orderedAt)`.

- [ ] **Step 4: הרצה, עובר**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat(amazon): טיוטת ייבוא מול הקטלוג ושמירה בטרנזקציה"
```

---

### Task 6: WebView לקריאה ולהתחברות

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/data/amazon/AmazonWeb.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/amazon/AmazonSignInView.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`<uses-permission android:name="android.permission.INTERNET" />`)

**Interfaces:**
- Consumes: `isAmazonUrl`, `isSignInUrl` (משימה 4).
- Produces:
  ```kotlin
  sealed interface PageRead {
      data class Text(val text: String) : PageRead
      data object SignInNeeded : PageRead
      data class Failed(val reason: String) : PageRead   // Hebrew, shown to the user
  }
  @Singleton class AmazonWeb @Inject constructor(@ApplicationContext context: Context) {
      suspend fun readPage(url: String, ready: (String) -> Boolean): PageRead        // main thread inside
      suspend fun trackPackageLinks(orderNumber: String): List<String>              // hrefs of "Track package" on the order-details page
      fun signOut()                                                                  // CookieManager.removeAllCookies + flush
  }
  @Composable fun AmazonSignInView(onSignedIn: () -> Unit, modifier: Modifier = Modifier)
  const val ORDER_HISTORY_URL = "https://www.amazon.com/gp/css/order-history"
  fun orderDetailsUrl(orderNumber: String): String
  ```

- [ ] **Step 1: מימוש `AmazonWeb`**

WebView נסתר אחד, לא מחובר לחלון (כך עבד בניסוי), שנוצר בעצלות על ה-main thread עם `javaScriptEnabled`, `domStorageEnabled` וה-user-agent מ-Global Constraints. `readPage` טוען את הכתובת. ב-`onPageFinished`: כתובת שעוברת `isSignInUrl` מחזירה `SignInNeeded`. אחרת בודקים את `document.body.innerText` (מפוענח עם `JSONArray("[$raw]").getString(0)` כמו בניסוי) כל 500ms, עד ש-`ready(text)` או עד 15 שניות. בסיום הזמן מחזירים `Failed("לא הצלחתי לקרוא את הדף מאמזון")`. שגיאת רשת (`onReceivedError` של המסגרת הראשית) מחזירה `Failed("אין חיבור לאינטרנט")`. `shouldOverrideUrlLoading` חוסם כל כתובת שלא עוברת `isAmazonUrl`. קריאה אחת בכל רגע, עם `Mutex`. `trackPackageLinks` טוען את `orderDetailsUrl` ומריץ JS שמחזיר את ה-`href` המלאים של קישורי `a` שהטקסט שלהם הוא `Track package`.

- [ ] **Step 2: מימוש `AmazonSignInView`**

`AndroidView` עם WebView גלוי, בלי שינוי user-agent, עם JS ו-DOM storage. אותה חסימה של `shouldOverrideUrlLoading`. טוען את `ORDER_HISTORY_URL`. כש-`onPageFinished` מגיע לכתובת שמכילה `order-history` או `your-orders/orders`, הוא קורא ל-`CookieManager.getInstance().flush()` ואחר כך ל-`onSignedIn()`.

- [ ] **Step 3: בנייה**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. אין בדיקות יחידה ל-WebView. הוא נבדק ידנית במשימה 8.

- [ ] **Step 4: Commit**

```bash
git add app/src/main
git commit -m "feat(amazon): WebView לקריאת דפים ולהתחברות"
```

---

### Task 7: מסך הייבוא: רשימה, אישור ומספרי משלוח

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/amazon/AmazonImportViewModel.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/amazon/AmazonImportScreen.kt`
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/amazon/ImportCardView.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/presentation/common/ProductDialogs.kt` (`NewProductDialog`)
- Modify: `presentation/navigation/Routes.kt` (`AMAZON_IMPORT = "amazon_import"`), `presentation/navigation/AppNav.kt`, `presentation/orders/OrdersScreen.kt` (פרמטר `onAmazonImport`)

**Interfaces:**
- Consumes: כל המשימות הקודמות.
- Produces: `NewProductDialog(..., initial: NewProductInput? = null, showColor: Boolean = true)`. כש-`showColor == false`, שדה הצבע מוסתר ולא נבדק, ו-`onConfirm` מקבל `colorName = ""`. הקוראים הקיימים לא משתנים.

- [ ] **Step 1: ViewModel**

מצב המסך:
```kotlin
sealed interface ImportScreenState {
    data object Loading : ImportScreenState
    data object SignIn : ImportScreenState
    data class Error(val message: String, val canShowPage: Boolean) : ImportScreenState
    data class Choices(val choices: ImportChoices, val trackingAdded: Int) : ImportScreenState
    data class Confirm(val order: AmazonOrder, val expectedAt: LocalDate?, val cards: List<ImportCard>) : ImportScreenState
}
```
- טעינה: `readPage(ORDER_HISTORY_URL) { "ORDER PLACED" in it }`. `SignInNeeded` נותן `SignIn`. `Failed` נותן `Error(reason, canShowPage = false)`. כש-`parseAmazonOrders` מחזיר `null`, זה `Error("לא הצלחתי לקרוא את רשימת ההזמנות מאמזון", canShowPage = true)`. אחרת `importableOrders(orders, inventory.orderNumbers(), families)`, ואחר כך עדכון מספרי משלוח (למטה).
- מספרי משלוח: לכל `inventory.ordersAwaitingTracking()`: `trackPackageLinks`, ולכל קישור `readPage(link) { "Tracking ID" in it }` ו-`parseTrackingIds`. אם נמצאו מספרים: `setTrackingNumber(id, ids.joinToString(", "))`. `trackingAdded` סופר הזמנות שעודכנו. כישלון כאן לא מפיל את המסך.
- בחירת הזמנה: `Confirm(order, expectedDate(order.status, order.orderedOn, LocalDate.now()), buildCards(...))`. "חזרה" חוזרת למצב `Choices`.
- פעולות כרטיס: שינוי כמות בשורה, שינוי שם צבע בשורה, הסרת שורה, הסרת כרטיס, סימון צבעים (כל צבע מוסיף שורה בכמות `itemQuantity` ומוריד 1 מ-`colorsToMark`), ועריכת כרטיס מ-`NewProductInput`. אחרי כל שינוי `rematch(products)`.
- שמירה: `importOrder(order.orderNumber, order.orderedOn` בתחילת היום באזור הזמן של המכשיר`, expectedAt, lines)`. נעילה נגד לחיצה כפולה כמו `NewOrderViewModel.save`. אחרי הצלחה מעדכנים מספר משלוח להזמנה החדשה, כמו בטעינה, ויוצאים.
- `signOut()`: `AmazonWeb.signOut()` ואחר כך מצב `SignIn`.

- [ ] **Step 2: המסך**

- `TopAppBar` עם "ייבוא מאמזון", חזרה, ותפריט עם "התנתק מאמזון".
- `Loading`: מחוון ו"קורא את ההזמנות מאמזון...". `SignIn`: `AmazonSignInView` על כל המסך, ו-`onSignedIn` טוען מחדש. `Error`: ההודעה, "נסה שוב", ואם `canShowPage`, גם "הצג את הדף" שמציג את `AmazonSignInView`.
- `Choices`: לפי ההדמיה שאושרה (מסך 1 במסמך העיצוב). שורת סיכום `"${n} הזמנות חדשות"`, או "אין הזמנות חדשות באמזון" כשאין. כשיש `trackingAdded > 0`: `"נוסף מספר משלוח ל-${n} הזמנות"`. כרטיס לכל הזמנה: תאריך (`d/M/yyyy`) ומספר ב-LTR, שורה `"${q} x ${title.substringBefore('|').trim()}"` לכל פריט, ו-`"צפי: ..."`. בתחתית: `"לא פילמנט, לא יובא: ..."` כשיש `skippedTitles`.
- `Confirm`: לפי אפשרות ב' בהדמיה. למעלה מספר ההזמנה (LTR) ו-`ExpectedDateField`. אחריהם `ImportCardView` לכל כרטיס: השם המקורי באפור (LTR, עד 2 שורות, ולחיצה פותחת את כולו), צ'יפים של מותג, `composeMaterialName(family, variant, finish)` ומשקל, וקישורים "עריכה" ו"הסר". אחריהם שורה לכל צבע: `ColorDot(resolveColorHex(name, null))`, השם, תגית "קיים" או "חדש", ו-`Stepper` (0 עד 50, ו-0 מסיר). כש-`colorsToMark > 0`: "הצבעים לא מופיעים בשם" וקישור `"סמן ${n} צבעים"`, שפותח בחירת צבעים מהקטלוג עם שדה לצבע חדש. בתחתית: `"שמור הזמנה (${spoolCount} סלילים)"`. הכפתור כבוי כל עוד יש כרטיס עם `colorsToMark > 0` או עם `family` ריק, או כשאין סלילים. מעליו `"חסר: צבעים או חומר ב-${n} כרטיסים"`.
- "עריכה": `NewProductDialog(initial = <ערכי הכרטיס>, showColor = false)`.

- [ ] **Step 3: חיבור**

`OrdersScreen` מקבל `onAmazonImport: () -> Unit` ומציג `OutlinedButton("ייבוא מאמזון")` באותה שורה עם "סרוק משלוח" (`Row` עם `Arrangement.spacedBy(8.dp)`). ב-`AppNav` נוסף `composable(Routes.AMAZON_IMPORT)`.

- [ ] **Step 4: בנייה**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, וכל הבדיקות ירוקות (כולל בדיקות `NewProductDialog` הקיימות, אם יש).

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat(amazon): מסך ייבוא מאמזון עם רשימה, אישור ומספרי משלוח"
```

---

### Task 8: גרסה, מסמכים ובדיקה על האמולטור

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 11`, `versionName = "0.7.0"`)
- Modify: `SPEC.md`, `README.md`

- [ ] **Step 1: גרסה ובנייה**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. מספר הבדיקות גדל מ-249.

- [ ] **Step 2: בדיקה ידנית על `emulator-5554`**

התקנה עם `install -r` (האמולטור מחובר לאמזון מהניסוי) ועוברים על:
1. מסך ההזמנות, "ייבוא מאמזון": מופיעה הזמנת Panchroma מ-15/11 עם "צפי: 24/11 עד 1/12". הזמנות אוקטובר לא מופיעות.
2. בחירה בה: כרטיס אחד עם Earth Brown ו-Wood Brown, 2 מכל אחד, תגית "חדש", והכפתור "שמור הזמנה (4 סלילים)".
3. "עריכה", שינוי משקל ל-750 ושמירה: התגיות נשארות "חדש" והמשקל בצ'יפ מתעדכן.
4. שמירה: ההזמנה ב"ממתינות" עם המספר וצפי 1/12/2026. פתיחה מחדש של הייבוא: ההזמנה כבר לא ברשימה.
5. במסך ההזמנה: עיפרון, מספר משלוח `TEST123`, שמירה, ואז כפתור העתקה ו-"הועתק".
6. "התנתק מאמזון": מוצג דף ההתחברות בגודל טלפון. (בלי להתחבר שוב, אם אמיר לא ליד המחשב.)
7. מצב אופקי ומצב כהה במסך האישור: אין חיתוך.

- [ ] **Step 3: מסמכים**

ב-`SPEC.md`: סעיף "ייבוא מאמזון (0.7.0)" עם ההחלטות, מסד 6, ופריט 1 ב"הצעד הבא" מסומן כמומש. ב"מה פתוח": מה לא נבדק (הזמנה עם כמה משלוחים, ניסוחי צפי יחסיים מול אמזון אמיתי, התחברות בטלפון האמיתי). ב-`README.md`: שורה על הייבוא ועל הרשאת האינטרנט.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts SPEC.md README.md
git commit -m "chore: גרסה 0.7.0 ועדכון מסמכים"
```
