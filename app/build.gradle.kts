plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uy.autovideo"
    compileSdk = 35

    defaultConfig {
        applicationId = "uy.autovideo"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
    }

    signingConfigs {
        // Clave fija guardada en el repositorio: todas las compilaciones quedan
        // firmadas igual, así una versión nueva se instala encima de la anterior.
        getByName("debug") {
            storeFile = file("autovideo-debug.keystore")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    // Inyectar los ajustes de diseño al inicio de cada página de YouTube
    implementation("androidx.webkit:webkit:1.12.1")
}
