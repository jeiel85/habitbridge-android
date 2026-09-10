# Room entities/DAOs are kept via the Room annotation processor's generated code.
# Keep enum names: they are persisted as strings by Room's built-in converters.
-keepclassmembers enum com.habitbridge.policy.** { *; }
-keepclassmembers enum com.habitbridge.data.** { *; }
