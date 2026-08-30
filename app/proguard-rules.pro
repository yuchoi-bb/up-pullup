# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations, AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pullup.tracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.pullup.tracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pullup.tracker.**$$serializer { *; }

# AppAuth / OkHttp
-keep class net.openid.appauth.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
