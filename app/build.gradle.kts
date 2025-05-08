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
        minSdk = 30 // Updated to API 30 (Android 11) for screenshot functionality
        targetSdk = 35
        versionCode = 16
        versionName = "0.16-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Set up manifest placeholders
        manifestPlaceholders["requestLegacyExternalStorage"] = "true"
        
        // Enable multidex to handle larger codebase
        multiDexEnabled = true
        
        // Add BuildConfig field for database name suffix
        buildConfigField("String", "DB_NAME_SUFFIX", "\"\"")
        
        // Increase memory for dexing process
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.incremental" to "true",
                    "room.expandProjection" to "true",
                    "room.schemaLocation" to "$projectDir/schemas"
                )
            }
        }
        
        // Add configurations for large dictionary imports
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
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
            // Debug configuration with a different application ID
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "DB_NAME_SUFFIX", "\"_debug\"")
        }
    }
    
    // Configure the naming of output APK files
    applicationVariants.all {
        val variant = this
        outputs.all {
            // We need to cast to an implementation class to access the outputFileName property
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).apply {
                val appName = "Tekisuto"
                outputFileName = "${appName}-${variant.baseName}-v${variant.versionName}.apk"
            }
        }
    }
    
    // Add memory allocation configuration
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
        useBuildCache = true
    }
    
    // Configure tasks for memory optimizations
    tasks.withType<JavaCompile> {
        options.forkOptions.jvmArgs = listOf("-Xmx2g")
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
    
    // Enable additional memory optimizations
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Exclude conflicting META-INF files from Kuromoji and Jieba
            excludes += "/META-INF/CONTRIBUTORS.md"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val room_version = "2.6.1"
    
    // Add desugaring for better Java 8+ compatibility
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("androidx.activity:activity-ktx:1.8.2") // For edge-to-edge support
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
    implementation("com.github.jens-muenker:uCrop-n-Edit:4.1.0")  // Image cropping library - fork of uCrop with additional features
    implementation("com.github.ankidroid:Anki-Android:api-v1.1.0")
    implementation("com.google.mlkit:translate:17.0.2")  // ML Kit for Translation
    implementation("com.google.mlkit:language-id:17.0.4")  // ML Kit Language Identification
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1")  // For Tasks.await support
    
    // Add multidex support
    implementation("androidx.multidex:multidex:2.0.1")
    
    // Tesseract OCR
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.8.0")
    
    // Japanese text tokenization
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    
    // Chinese text tokenization
    implementation("com.huaban:jieba-analysis:1.0.2")
    
    // Azure AI Speech Service
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.32.1")
    
    // JSON serialization/deserialization
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
    implementation("com.google.code.gson:gson:2.10.1") // Keep for compatibility with existing code
    
    // Emoji support
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    implementation("androidx.emoji2:emoji2-bundled:1.4.0")
    
    kapt("androidx.room:room-compiler:$room_version")  // This is crucial for Room
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}