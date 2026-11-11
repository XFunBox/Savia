plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.xfunbox.savia"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.xfunbox.savia"
        minSdk = 31
        targetSdk = 36
        versionCode = 8
        versionName = "3.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    val signingVariables = listOf(
        "SAVIA_KEYSTORE_FILE", "SAVIA_STORE_PASSWORD", "SAVIA_KEY_ALIAS", "SAVIA_KEY_PASSWORD"
    )
    val releaseSigning = signingVariables.map { System.getenv(it) }
    if (releaseSigning.any { !it.isNullOrBlank() }) {
        require(releaseSigning.all { !it.isNullOrBlank() }) {
            "La firma de Savia requiere: ${signingVariables.joinToString()}"
        }
        signingConfigs.create("saviaRelease") {
            storeFile = file(releaseSigning[0]!!)
            storePassword = releaseSigning[1]
            keyAlias = releaseSigning[2]
            keyPassword = releaseSigning[3]
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sin la clave de publicación se genera una APK sin firmar, nunca con otra clave debug.
            signingConfig = signingConfigs.findByName("saviaRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
