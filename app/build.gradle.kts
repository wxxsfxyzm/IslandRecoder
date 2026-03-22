plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "com.island.recorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.island.recorder"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Read from environment variables (GitHub Secrets)
            val envKeystoreFile = System.getenv("SIGNING_KEY")
            val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val envAlias = System.getenv("ALIAS")
            val envKeyPassword = System.getenv("KEY_PASSWORD")

            if (envKeystoreFile != null) {
                storeFile = file(envKeystoreFile)
                storePassword = envKeystorePassword
                keyAlias = envAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Only apply signing if environment variables are present
            if (System.getenv("SIGNING_KEY") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Generate native debug symbols for crash analysis
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,io.netty.versions.properties}"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Miuix UI
    implementation(libs.miuix)
    implementation(libs.miuix.icons)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // Navigation3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.miuix.navigation3.ui)
    
    // CameraX for Facecam
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
    // ExoPlayer for Video Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    
    // Accompanist for Permissions
    implementation(libs.accompanist.permissions)
    
    // Security: Protobuf with buffer overflow fix
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.protobuf:protobuf-java-util:3.25.5")
    
    // Coil for Image/Video Thumbnails
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    
    // Security: Netty dependency constraints for CVE mitigation
    implementation("io.netty:netty-codec-http:4.1.125.Final")
    implementation("io.netty:netty-codec-http2:4.1.125.Final")
    implementation("io.netty:netty-handler:4.1.118.Final")
    implementation("io.netty:netty-common:4.1.118.Final")
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    
    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}