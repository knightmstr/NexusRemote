# Preserve Room Database entities and DAOs
-keep class com.nexusremote.domain.model.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Preserve Protocol Controllers and Security components
-keep class com.nexusremote.security.** { *; }
-keep class com.nexusremote.protocols.** { *; }
