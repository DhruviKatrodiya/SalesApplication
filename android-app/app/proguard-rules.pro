# Keep the jTDS JDBC driver (loaded reflectively via Class.forName / DriverManager).
-keep class net.sourceforge.jtds.** { *; }
-dontwarn net.sourceforge.jtds.**
-dontwarn java.sql.**
-dontwarn javax.**
