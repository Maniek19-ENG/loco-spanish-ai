import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties()
val signingFilePath = providers.environmentVariable("LOCO_SIGNING_PROPERTIES").orNull
val signingFile = signingFilePath?.let { file(it) } ?: rootProject.file("keystore.properties")
if (signingFile.isFile) signingFile.inputStream().use { signingProperties.load(it) }
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
    !signingProperties.getProperty(it).isNullOrBlank()
}


android {
    namespace = "com.locospanish.ai"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.locospanish.ai"
        minSdk = 26
        targetSdk = 37
        versionCode = 56
        versionName = "2.3.25"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "LOCO Spanish AI"
    }
    buildFeatures { compose = true }
    signingConfigs {
        if (hasReleaseSigning) create("locoRelease") {
            storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            // A test build installs beside the signed app instead of colliding with it.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-TEST"
            manifestPlaceholders["appLabel"] = "LOCO TEST"
        }
        // Slim side-by-side copy for downloading straight to a phone; omits the 82 MB rollback installer.
        create("phone") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("locoRelease")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
androidComponents {
    onVariants(selector().withBuildType("phone")) { it.androidResources.ignoreAssetsPatterns.add("loco-*.apk") }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
}


