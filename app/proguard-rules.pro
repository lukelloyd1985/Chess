# Keep readable stack traces (shown on the crash screen).
-keepattributes SourceFile,LineNumberTable

# App code.
-keep class com.github.lukelloyd1985.chess.** { *; }

# Credential Manager + Google Identity Services (Sign in with Google): these talk to Play Services
# over reflection/Parcelable IPC, a known R8 failure class that only shows up in minified release
# builds. Keep rules can only prevent stripping, never break a working build.
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# Appwrite SDK: Realtime's error handler deserialises through Gson reflection onto SDK classes, so
# R8 renaming breaks it in release builds only (a real crash found in the MyTaskList app).
-keep class io.appwrite.** { *; }
-dontwarn io.appwrite.**
