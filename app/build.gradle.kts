import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma de la versión publicada: datos en keystore.properties (nunca se sube a git).
// Se busca en la raíz del proyecto y, si no está, en Documentos/claves (compartido con otras apps).
// Sin ese archivo, la versión release sale sin firmar.
val signingProps: Properties? = listOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), "Documents/claves/keystore.properties"),
).firstOrNull { it.isFile }?.let { f -> Properties().apply { f.reader(Charsets.UTF_8).use { load(it) } } }

android {
    namespace = "com.voyapp"
    compileSdk = 36

    signingConfigs {
        if (signingProps != null) create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.voyapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.15.0")
}
