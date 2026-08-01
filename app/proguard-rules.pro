# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard configuration.

# Keep Koin
-keep class org.koin.** { *; }

# Keep data classes used in SharedPreferences
-keep class com.example.salarynaftan.RegularAlarm { *; }
-keep class com.example.salarynaftan.SalaryHistoryRecord { *; }
