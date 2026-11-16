# Filament Tracker

אפליקציית Android אישית לניהול מלאי סלילי-פילמנט למדפסת Bambu Lab A1 mini עם AMS lite.

- מחזור-חיים של סליל: הוזמן, סגור, בשימוש בחריץ, פתוח באחסון, הסתיים
- "חסר" ו"הולך להיות חסר" לפי רשימת שילובים שתמיד צריכים להיות במלאי
- תצרוכת לפי סלילים שהסתיימו, עם קצב וצפי סיום
- ייבוא הזמנות מאמזון (מ-0.7.0): האפליקציה קוראת את רשימת ההזמנות מהחשבון שלך ב-amazon.com דרך WebView, ומציגה מסך אישור לפני כל שמירה. לשם כך נדרשת הרשאת אינטרנט (`INTERNET`), והיא משמשת רק לגישה ל-amazon.com. הסיסמה לא נשמרת באפליקציה, רק עוגיות ההתחברות של ה-WebView.

Kotlin, Jetpack Compose, Hilt, Room. כל הנתונים מקומיים במכשיר.

עיצוב: `docs/superpowers/specs/2026-10-18-filament-tracker-design.md`

## בנייה

    export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
    ./gradlew testDebugUnitTest assembleDebug
