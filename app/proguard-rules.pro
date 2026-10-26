# R8 rules for the release build.
#
# The app's own code (com.shamela.library, com.shamela.apptheme) is shrunk and optimized: only the
# classes read or written by reflection are kept below. Room, Hilt, WorkManager, Compose, Gson,
# Firebase and the other AndroidX libraries bring their own rules.

# Class and method names stay readable: crash stack traces in logcat need no mapping file.
-dontobfuscate

# --- Gson -----------------------------------------------------------------------------------
# Gson reads and writes these classes' fields by name, and builds them without calling a
# constructor. Generic signatures are needed for List<T> fields (else their items come back as
# maps and fail with a ClassCastException).
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# assets/categories and assets/book-details
-keep class com.shamela.library.data.local.assets.dto.** { <fields>; <init>(...); }
# Settings > backup file
-keep class com.shamela.library.data.local.backup.LibraryBackup$Backup* { <fields>; <init>(...); }
# shamela.ws catalog cache (files/shamela/catalog.json)
-keep class com.shamela.library.data.remote.shamela.ShamelaCatalogData { <fields>; <init>(...); }
-keep class com.shamela.library.data.remote.shamela.ShamelaCategory { <fields>; <init>(...); }
-keep class com.shamela.library.data.remote.shamela.ShamelaBookEntry { <fields>; <init>(...); }
-keep class com.shamela.library.domain.model.BookInfoItem { <fields>; <init>(...); }
# The book-details navigation argument
-keep class com.shamela.library.domain.model.Book { <fields>; <init>(...); }

# --- Reader (folioreader module) ------------------------------------------------------------
# An older library built on reflection: Jackson/Gson models, Retrofit, JavaScript interfaces of its
# WebViews, Parcelables. Kept whole.
-keep class com.folioreader.** { *; }
-keep class org.readium.r2.streamer.** { *; }
# Retrofit (used by the reader): keeps generic return types of its API interfaces.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Joda-Time (via Readium) optionally references Joda-Convert annotations.
-dontwarn org.joda.convert.**

# --- TLS (BookServerConnection, DottedSniConnection) ----------------------------------------
# Conscrypt: native methods and classes looked up by name.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
