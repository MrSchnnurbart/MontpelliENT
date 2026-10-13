# MontpelliENT ProGuard / R8 Rules

# Kotlin Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer();
}

# Modèles et DTOs partagés
-keep class fr.montpellient.core.model.** { *; }
-keep class fr.montpellient.shared.model.** { *; }
-keep class fr.montpellient.core.database.** { *; }

# OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance AppWidgets
-keep class fr.montpellient.app.widget.** { *; }
-keep class androidx.glance.appwidget.** { *; }

# Jakarta Mail (connector:mail)
-keep class jakarta.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn jakarta.mail.**
-dontwarn com.sun.mail.**

# Google Tink & ErrorProne annotations
-dontwarn com.google.errorprone.annotations.**

# AndroidX Room & WorkManager (requis par Glance)
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.work.impl.WorkDatabase
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.InputMerger {
    <init>();
}


