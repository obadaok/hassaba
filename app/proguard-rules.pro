# قواعد ProGuard لتطبيق الحسابة البلاتينية

# قواعد الأمان الأساسية
-keepattributes Signature, *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# الحفاظ على كلاس التطبيق الرئيسي
-keep class com.platinum.vip.hasiba.** { *; }

# Gson - الحفاظ على كلاسات البيانات
-keepclassmembers class com.platinum.vip.hasiba.HistoryItem { *; }
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod

# Iconics - مكتبة الأيقونات
-keep class com.mikepenz.** { *; }
-keep class fontawesome.** { *; }

# Material Dialogs
-keep class com.afollestad.materialdialogs.** { *; }

# coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding { *; }

# Material Components
-keep class com.google.android.material.** { *; }
