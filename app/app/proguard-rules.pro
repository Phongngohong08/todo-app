# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── Retrofit / OkHttp ────────────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ── Gson ─────────────────────────────────────────────────────────────────────
# Gson dùng reflection để (de)serialize model — giữ nguyên tên field, không obfuscate.
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Model API/DB dùng Gson + Room — giữ nguyên cấu trúc field.
-keep class com.example.todoapplication.data.model.** { <fields>; }
-keep class com.example.todoapplication.data.local.** { <fields>; }

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── Tink (dùng bởi androidx.security EncryptedSharedPreferences) ────────────
# error-prone / checker-framework annotations chỉ dùng lúc biên dịch, không có ở runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**