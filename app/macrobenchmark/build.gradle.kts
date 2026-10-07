// Module đo hiệu năng (Macrobenchmark) + sinh Baseline Profile cho :app.
// Chạy trên giả lập / máy thật:  ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.example.todoapplication.macrobenchmark"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        // Khớp buildType "benchmark" của :app (release + khóa debug)
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    // Chỉ biến thể benchmark có ý nghĩa với module này
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}
