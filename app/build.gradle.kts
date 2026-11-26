plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "app.dastyar.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.dastyar.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.0"
        vectorDrawables.useSupportLibrary = true
        manifestPlaceholders["appAuthRedirectScheme"] = "app.dastyar.agent"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val store = System.getenv("SIGNING_STORE_FILE")
        val storePassword = System.getenv("SIGNING_STORE_PASSWORD")
        val alias = System.getenv("SIGNING_KEY_ALIAS")
        val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        if (!store.isNullOrBlank() && !storePassword.isNullOrBlank() && !alias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
            create("release") {
                storeFile = file(store)
                this.storePassword = storePassword
                keyAlias = alias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:agent"))
    implementation(project(":feature:providers"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:voice"))
    implementation(project(":feature:connectors"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.navigation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.work)
    implementation(libs.coroutines.android)
    implementation(libs.biometric)
    implementation(libs.browser)
    implementation(libs.androidx.splash)
}
