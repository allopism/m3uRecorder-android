import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma de release: datos en keystore.properties (fuera de git). Si no existe (p. ej. alguien que
// clona el repositorio), la versión release se firma con la clave de depuración para poder probarla.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.m3urecorder.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.m3urecorder.app"
        minSdk = 28
        targetSdk = 36
        // Compatible con el servidor m3uRecorder 2.0.x. versionCode sube en cada versión publicada.
        versionCode = 3
        versionName = "2.0.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: quita el código y los recursos que no se usan (sobre todo de la librería de iconos)
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // para mostrar la versión en Ajustes
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Versiones estables (penúltima publicada en nov. 2026)
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.12.4")

    // Ciclo de vida: ViewModel y estado consciente de si la app está visible
    val lifecycleVersion = "2.10.0"
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")

    // Jetpack Compose (Material 3)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Media3 ExoPlayer: MKV, MP4, TS, HLS y varias pistas de audio
    val media3Version = "1.11.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Imágenes: logos de canales (PNG/SVG) y miniaturas de grabaciones
    val coilVersion = "3.6.2"
    implementation("io.coil-kt.coil3:coil-compose:$coilVersion")
    implementation("io.coil-kt.coil3:coil-network-okhttp:$coilVersion")
    implementation("io.coil-kt.coil3:coil-svg:$coilVersion")

    // Corrutinas y comprobación periódica en segundo plano
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Pruebas unitarias (se ejecutan en el PC, sin móvil): ./gradlew test
    testImplementation("junit:junit:4.13.2")
}
