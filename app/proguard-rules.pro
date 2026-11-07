# Reglas de R8 para m3uRecorder.
# La app no usa reflexión propia: las librerías (Compose, WorkManager, Media3, Coil) traen sus
# propias reglas. Solo se protege por si acaso la clase que WorkManager crea por su nombre.
-keep class com.m3urecorder.app.service.ServerSyncWorker { <init>(...); }

# OkHttp (lo usa Coil) avisa de clases opcionales que no están en Android
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
