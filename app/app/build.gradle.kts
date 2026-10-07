plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.todoapplication"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.todoapplication"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Bản đo hiệu năng (Macrobenchmark): giống release (R8, không debuggable) nhưng ký bằng khóa debug để cài được
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles("benchmark-rules.pro")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Song ngữ (values/ tiếng Anh, values-vi/ tiếng Việt) + đổi ngôn ngữ ngay trong app: App Bundle phải giữ
    // đủ mọi ngôn ngữ trong APK cài về, không tách theo ngôn ngữ của máy (nếu không, chọn ngôn ngữ khác sẽ thiếu chuỗi)
    bundle {
        language { enableSplit = false }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Schema JSON của Room làm asset cho androidTest → MigrationTestHelper dựng lại được DB phiên bản cũ
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Room xuất schema JSON mỗi version -> commit vào git để viết/kiểm tra Migration
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // Màn khởi động (SplashScreen API) đồng nhất từ Android 10 trở lên
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Retrofit & Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // Navigation & Architecture
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.compose.material:material-icons-extended")

    // Khóa ứng dụng bằng vân tay / khuôn mặt / mã PIN máy
    implementation(libs.androidx.biometric)

    // Widget màn hình chính viết bằng Compose (Jetpack Glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Cài đặt trên máy (thay SharedPreferences)
    implementation(libs.androidx.datastore.preferences)

    // Tải dần danh sách dài (Lịch sử, Thùng rác)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)

    // Quét tài liệu + nhận chữ trên máy (ML Kit, chạy offline)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner)

    // Nhắc theo địa điểm (Geofencing) + vị trí hiện tại
    implementation(libs.play.services.location)

    // Giao diện thích ứng (điện thoại / tablet / màn gập)
    implementation(libs.androidx.compose.material3.window.size)

    // Cài Baseline Profile khi cài app → khởi động / cuộn nhanh hơn
    implementation(libs.androidx.profileinstaller)

    // Background reminders / notifications
    implementation(libs.androidx.work.runtime.ktx)

    // Encrypted storage for auth tokens
    implementation(libs.androidx.security.crypto)

    // Drag & drop reordering for LazyColumn
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Room — local DB for offline cache + gamification
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}