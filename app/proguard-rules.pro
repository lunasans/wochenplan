# OkHttp benoetigt diese Regeln, da es optionale Plattform-Klassen referenziert.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Der Anthropic-SDK serialisiert ueber Jackson und laedt Teile per Reflexion.
-keep class com.anthropic.** { *; }
-dontwarn com.anthropic.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# kotlinx.serialization erzeugt Serializer als statische Felder der Klassen.
-keepclassmembers class de.wochenplan.app.** {
    *** Companion;
}
-keepclasseswithmembers class de.wochenplan.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
