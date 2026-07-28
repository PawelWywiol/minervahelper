-keep class org.libtorrent4j.swig.** { *; }
-keep class org.libtorrent4j.** { *; }

# Ktor's debugger detection asks the JVM for its own process management bean, which Android has no
# equivalent of. The call is only reached under a desktop debugger, so the reference is dead here —
# without this line R8 refuses to finish and no release build exists at all.
-dontwarn java.lang.management.**
