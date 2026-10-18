plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.kthealthsync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.kthealthsync"
        minSdk = 26
        targetSdk = 34
        versionCode = 63
        versionName = "2.31"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    testOptions {
        unitTests.all {
            it.useJUnit {
                excludeCategories("com.example.kthealthsync.LiveNetworkTest")
            }
        }
    }
    lint {
        disable += "CoroutineCreationDuringComposition"
        disable += "StateFlowValueCalledInComposition"
        disable += "FlowOperatorInvokedInComposition"
        disable += "ProduceStateDoesNotAssignValue"
        disable += "RememberReturnType"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Reuse the Android JVM test compilation, but run network tests only on explicit request.
tasks.register<Test>("liveTest") {
    group = "verification"
    description = "Runs live API tests (requires network and Kaloricke credentials)."
    val unitTest = tasks.named<Test>("testDebugUnitTest").get()
    dependsOn(provider { unitTest.taskDependencies.getDependencies(unitTest) })
    testClassesDirs = unitTest.testClassesDirs
    classpath = unitTest.classpath
    useJUnit {
        includeCategories("com.example.kthealthsync.LiveNetworkTest")
    }
    workingDir = rootProject.projectDir
    // External data changes independently of the test sources.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

dependencies {
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    constraints {
        implementation("androidx.fragment:fragment:1.6.2") {
            because("The barcode scanner brings Fragment 1.0.0, incompatible with Activity Result APIs")
        }
    }
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    
    // Retrofit & JSON
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Barcode Scanner (Google Play services code scanner)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    
    // Wearable Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
