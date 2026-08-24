# Optimization and Shrinking rules for MsgGo

# Apache POI - Allow R8 to strip unused code, but prevent crashes with XML parsing
-keep class org.apache.poi.ss.usermodel.** { *; }
-keep class org.apache.poi.xssf.usermodel.** { *; }
-keep class org.apache.poi.hssf.usermodel.** { *; }

# Keep specific XML beans that POI finds via reflection
-keep class org.apache.xmlbeans.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }

# Silence warnings for things not present on Android (AWT, Swing, etc)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn com.sun.org.apache.xml.internal.resolver.**
-dontwarn org.osgi.framework.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.crypto.**
