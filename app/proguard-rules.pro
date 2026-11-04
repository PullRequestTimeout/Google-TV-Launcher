# dadb uses okio and reflection-free code, but keep its public API intact for safety.
-keep class dadb.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# Kadb: pairing (SPAKE2 + BouncyCastle) relies on JCA providers looked up by name.
-keep class com.flyfishxu.kadb.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class io.github.** { *; }
-dontwarn javax.naming.**
-dontwarn java.lang.management.**
