# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-keepclassmembers class * {
  @com.squareup.moshi.FromJson <methods>;
  @com.squareup.moshi.ToJson <methods>;
}
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin Serialization
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class ** {
    *** *(...);
}
-keep class kotlinx.** { *; }
-keep class kotlin.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
  @androidx.room.* <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Generic
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# JavaScript
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Strip non-essential log calls from release builds.
# Log.e (errors) and Log.wtf are kept so crashes/severe issues remain debuggable.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static boolean isLoggable(java.lang.String, int);
}

# Also strip Kotlin stdlib println if accidentally used for debugging.
-assumenosideeffects class java.io.PrintStream {
    public *** println(...);
    public *** print(...);
}
