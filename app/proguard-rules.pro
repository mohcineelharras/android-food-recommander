-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

-keep class com.foodrecommender.core.** { *; }
-keepclassmembers class com.foodrecommender.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.foodrecommender.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn okhttp3.internal.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
