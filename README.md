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
