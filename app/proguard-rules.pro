# R8 rules for the release build.
#
# Everything here exists because something in this app is reached by
# reflection or by name rather than by a call R8 can see. A rule without a
# reason is a rule nobody dares delete later, so each block says what breaks
# without it.

# ── Kotlin coroutines ────────────────────────────────────────────────────
# The debug agent and the internal ServiceLoader entries are looked up by
# name. Without this, coroutine dispatch fails at runtime in release only,
# which is the worst place to discover it.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Firebase / Firestore ─────────────────────────────────────────────────
# This app parses documents by hand (snapshot.get("field")) rather than with
# toObject(), so the reflective POJO mapper is mostly unused — but Firestore
# still reflects over its own model classes, and Auth reflects over provider
# responses.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore carries generated protobuf classes that R8 cannot prove are used.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.protobuf.**

# ── Models we serialise to and from Firestore ────────────────────────────
# Field *names* are the wire format here. Obfuscating them would not break
# compilation, it would silently write documents with names like `a` and `b`
# that the iOS app and the admin panel cannot read.
-keepclassmembers class com.talkswithtanha.twt.core.model.** {
    <fields>;
    <init>(...);
}

# ── OkHttp ───────────────────────────────────────────────────────────────
# Used directly by the Cloudinary uploader and the auth API client.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Hilt / Dagger ────────────────────────────────────────────────────────
# Generated components are resolved by name at startup.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# ── Compose ──────────────────────────────────────────────────────────────
# Compose ships its own consumer rules; this only silences a warning about
# the tooling classes that are not in a release build.
-dontwarn androidx.compose.ui.tooling.**

# ── Coil ─────────────────────────────────────────────────────────────────
-dontwarn coil3.**

# ── Room, and therefore WorkManager ──────────────────────────────────────
# Room generates <Database>_Impl and instantiates it with Class.forName on the
# database class's own name plus "_Impl". R8 renames the generated class, the
# lookup misses, and the app dies at startup — not on the screen that uses it,
# but in InitializationProvider before any UI exists.
#
# This app never touches Room directly. WorkManager does, and WorkManager
# arrives transitively through Glance for the home-screen widget, which is why
# a crash in a database nobody wrote takes down launch. Verified: without these
# lines the release build dies with "Failed to create an instance of
# androidx.work.impl.WorkDatabase".
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-dontwarn androidx.work.**

# ── Keep our own crash-report line numbers readable ──────────────────────
# Without this a stack trace from the field is unreadable, which defeats the
# point of collecting one.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
