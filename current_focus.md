# Current Focus - filament_tracker

<!-- מקור-האמת למצב הסשנים של filament_tracker. אין לכתוב את מצבו ב-~/.claude/current_focus.md
     הגלובלי: הקובץ הגלובלי נכתב read-modify-write על-ידי כל סשן בכל פרויקט, ושני
     סשנים מקבילים כבר דרסו זה את כתיבתו של זה (LESSONS.md, 26/7/2026). -->

## 🆕 סשן 2026-10-18: עיצוב ותוכנית (הושלם, נדחף) - עוד אין קוד

**מה יש:** מסמך-עיצוב מאושר ותוכנית-מימוש מלאה, עוד בלי שורת קוד.
- עיצוב: `docs/superpowers/specs/2026-10-18-filament-tracker-design.md`
- תוכנית: `docs/superpowers/plans/2026-10-18-filament-tracker-v1.md` - 16 משימות עם קוד מלא
  (1 שלד · 2-5 domain ב-TDD · 6-9 Room וריפוזיטוריז · 10-15 מסכים · 16 אימות מקצה-לקצה ותיעוד).

העיצוב נעשה ב-`c:\Projects\new_idea` (שם רק שורת-הפניה).

**לסשן הבא - להתחיל מכאן:**
1. אמיר עוד לא בחר שיטת-הרצה. ההמלצה הייתה **subagent-driven**: Haiku כמממש, כי התוכנית
   כוללת קוד מלא והמימוש הוא כמעט העתקה, ו-Sonnet כסוקר. הלולאה הראשית יכולה להיות Sonnet.
2. סקיל: `superpowers:subagent-driven-development` (או `superpowers:executing-plans` ל-native).
3. **לפני כל פקודת Gradle:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
   JAVA_HOME לא מוגדר במכונה.
4. הגרסאות ב-Task 1 הועתקו בכוונה מ-`c:\Projects\buy_smart`, שנבנה בהצלחה במכונה הזו ב-3/10/2026. לא לשדרג.
5. אמולטור: AVD `Pixel_6`.

**אין SPEC.md עדיין** - נוצר במשימה 16 של התוכנית (יחד עם README). עד אז הקובץ הזה ומסמך-העיצוב
הם מקור-האמת.
