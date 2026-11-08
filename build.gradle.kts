plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9 trae Kotlin integrado (2.2); declarar el plugin aquí fija una versión más nueva.
    // El compilador de Compose debe ir siempre en la misma versión que Kotlin.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
