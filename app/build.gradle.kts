plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

androidComponents.beforeVariants { variant ->
    variant.enableAndroidTest = false
}

android {
    namespace = "com.abaga129.tekisuto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abaga129.tekisuto"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Set up manifest placeholders
        manifestPlaceholders["requestLegacyExternalStorage"] = "true"
        
        // Enable multidex to handle larger codebase
        multiDexEnabled = true
        
        // Increase memory for dexing process
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.incremental" to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug configuration
        }
    }
    
    // Add memory allocation configuration
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
    
    // Enable additional memory optimizations
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    val room_version = "2.6.1"
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.text.recognition)
    implementation(libs.text.recognition.chinese)
    implementation(libs.text.recognition.devanagari)
    implementation(libs.text.recognition.japanese)
    implementation(libs.text.recognition.korean)
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.github.yalantis:ucrop:2.2.8")  // Image cropping library
    implementation("com.github.ankidroid:Anki-Android:api-v1.1.0")
    implementation("com.google.mlkit:translate:17.0.2")  // ML Kit for Translation
    implementation("com.google.mlkit:language-id:17.0.4")  // ML Kit Language Identification
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1")  // For Tasks.await support
    
    // Add multidex support
    implementation("androidx.multidex:multidex:2.0.1")
    
    kapt("androidx.room:room-compiler:$room_version")  // This is crucial for Room
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}