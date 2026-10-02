# kotlinx.serialization, OkHttp et les bibliothèques AndroidX fournissent leurs propres règles R8.

# Le scanner de QR code (play-services-code-scanner 16.1.0) n'en fournit pas : en mode complet de R8
# (AGP 9), GmsBarcodeScanning.getClient() lève une NullPointerException et l'application plante.
# https://github.com/googlesamples/mlkit/issues/1018
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
