import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// The Google Services plugin aborts the build outright when `google-services.json`
// is missing, which would make a fresh clone of this repo unbuildable for anyone
// who has not yet been added to the Firebase project. It is applied only when the
// file is actually there.
//
// Drop the real file (Firebase console -> Project settings -> Add app -> Android,
// package name `com.orixto.twt`) into `app/` and it wires itself up on the
// next sync. Without it the app builds and installs but cannot reach Firebase --
// `TwtApplication` says so in one line rather than crashing somewhere obscure.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "com.talkswithtanha.twt"
    // 37, because the current AndroidX stack requires it: hilt-navigation-compose
    // 1.4.0, the 2026.09 Compose BOM and navigation-compose 2.10.1 all refuse to
    // link against anything older. compileSdk only decides which APIs may be
    // called; `targetSdk` below is what opts the app in to new runtime
    // behaviour, and it stays at 36.
    compileSdk = 37

    defaultConfig {
        // Must equal the package registered in the Firebase console, which is
        // what `google-services.json` is generated for. The Kotlin `namespace`
        // above is only where the code and R class live, and can differ.
        applicationId = "com.orixto.twt"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing, read from a file that is not in the repo.
    //
    // keystore.properties and the .jks beside it are gitignored: whoever holds
    // both can publish an update as you, so they belong on your machine and in
    // CI secrets, nowhere else. When the file is absent — which is every clone
    // that is not yours — the release build simply goes unsigned rather than
    // failing, so CI can still compile and check it.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 on. It strips the unused half of Firebase and Compose, which
            // is most of the download size, and it obfuscates — the rules in
            // proguard-rules.pro say what must survive and why.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time on API 24. ActivityTracker keys its usage documents by
        // LocalDate.now().toString(), and that call does not exist before API
        // 26 — on a phone running Android 7 it would throw the first time the
        // app recorded a session. Desugaring is the fix rather than rewriting
        // the call, because the document id has to keep reading exactly
        // "2026-09-28"; the admin panel queries by it.
        isCoreLibraryDesugaringEnabled = true
    }
    // The deployed twt-admin, which holds the Resend key and the Admin SDK.
    //
    // Set `adminApiBase` in gradle.properties or pass -PadminApiBase=... so the
    // URL is not compiled into the source tree, and staging and production
    // builds differ by a property rather than by an edit somebody forgets to
    // revert. Empty is a valid state: the app says email is unavailable rather
    // than crashing.
    defaultConfig {
        buildConfigField(
            "String",
            "ADMIN_API_BASE",
            "\"${project.findProperty("adminApiBase") ?: ""}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Firebase -- the same project iOS talks to.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google sign-in through Credential Manager.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // @AppStorage's counterpart.
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Declared rather than leaned on as a Coil transitive: the Cloudinary
    // uploader posts multipart bodies with it directly.
    implementation(libs.okhttp)
    implementation(libs.androidx.exifinterface)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Home-screen widget for the followed signal.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
