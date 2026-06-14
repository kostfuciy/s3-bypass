# Add project specific ProGuard rules here.
# By default, the noise of code shrinker (R8) is sufficient.
# You can add custom rules below if needed.

# Keep Kotlin standard library and coroutines metadata if needed
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep View constructors used by layout inflater
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
