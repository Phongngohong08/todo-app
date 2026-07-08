# Bài 00 — Tổng quan & Môi trường

## 1. Một app Android thực chất là gì?

Với dev backend, hãy hình dung thế này:

- Một app Android là một **process chạy trên máy người dùng**, không phải trên server của bạn.
- Nó do **hệ điều hành Android khởi động và có toàn quyền "giết" bất cứ lúc nào** (hết RAM, người dùng xoay màn hình, chuyển app khác...). Đây là khác biệt tư duy lớn nhất so với server — server của bạn chạy liên tục, còn app Android **sống theo vòng đời do OS điều khiển** (bài 03).
- App **không có `main()` mà bạn tự gọi**. Thay vào đó bạn khai báo các "điểm vào" (Activity, Service, Receiver...) trong một file manifest, và OS gọi chúng khi cần.
- Kết quả cuối cùng là một file `.apk` (hoặc `.aab`) — giống như một binary đã đóng gói kèm toàn bộ tài nguyên (ảnh, chuỗi văn bản, layout).

App này là **client "mỏng"**: phần lớn nghiệp vụ nặng (AI, kế hoạch ngày, thống kê) nằm ở [`backend/`](../backend/) Go của bạn. App chủ yếu: gọi API, hiển thị, cache offline, nhắc nhở. Đó là lý do kiến trúc của nó rất "backend-friendly".

## 2. Ngôn ngữ & công cụ

| Thành phần | Ở đây dùng gì | Tương đương backend |
|---|---|---|
| Ngôn ngữ | **Kotlin** | như Go, nhưng chạy trên JVM |
| Build system | **Gradle** (Kotlin DSL, file `.gradle.kts`) | như `go build` + `go.mod`, nhưng mạnh & phức tạp hơn |
| Khai báo thư viện | [`gradle/libs.versions.toml`](../app/gradle/libs.versions.toml) | như `go.mod` / `package.json` |
| Thư viện UI | **Jetpack Compose** | (không có tương đương — xem bài 04) |
| Kiến trúc khuyến nghị | **MVVM** (Google chính chủ) | như handler → service → repo |

## 3. Cấu trúc thư mục — bản đồ

Đường dẫn hơi "lồng nhau" một chút. **Gốc project Android** là [`app/`](../app/), còn **module ứng dụng** là `app/app/`:

```
app/                          ← GỐC project Gradle (mở cái này trong Android Studio)
├── settings.gradle.kts       ← khai báo có những module nào
├── build.gradle.kts          ← cấu hình build cấp project
├── gradle/
│   └── libs.versions.toml    ← DANH SÁCH THƯ VIỆN & phiên bản (version catalog)
└── app/                      ← MODULE ứng dụng (tên module là "app")
    ├── build.gradle.kts      ← thư viện & cấu hình CỦA RIÊNG module này
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   ← "khai báo" app: quyền, điểm vào (bài 03)
        │   ├── java/com/example/todoapplication/   ← TOÀN BỘ code Kotlin
        │   └── res/                  ← tài nguyên: ảnh, màu, XML cấu hình
        ├── test/             ← unit test (chạy trên JVM máy bạn, nhanh)
        └── androidTest/      ← test cần thiết bị/máy ảo Android (chậm)
```

Bên trong `src/main/java/com/example/todoapplication/` chính là "cây nghiệp vụ" — được chia tầng rất rõ:

```
todoapplication/
├── TodoApplication.kt        ← khởi động app (như bootstrap/main)
├── MainActivity.kt           ← màn hình gốc + router (NavHost)
├── data/                     ← TẦNG DỮ LIỆU
│   ├── api/                  →  Retrofit: định nghĩa & client gọi backend
│   ├── local/                →  Room: database offline (DAO, Entity)
│   ├── model/                →  data class map JSON (DTO)
│   ├── repository/           →  Repository: nguồn dữ liệu duy nhất cho UI
│   └── notifications/        →  WorkManager + Notification (nhắc việc)
├── domain/                   ← LOGIC THUẦN (lọc, sắp xếp task) — dễ test
├── di/                       ← ServiceLocator (dependency injection thủ công)
└── ui/                       ← TẦNG GIAO DIỆN
    ├── screens/              →  các màn hình (Composable)
    ├── viewmodel/            →  ViewModel (state + logic cho từng màn)
    ├── components/           →  mảnh UI tái dùng (nút, thẻ, bottom bar)
    ├── navigation/           →  định nghĩa các route
    ├── state/                →  kiểu UiState chung
    └── theme/                →  màu, font, hình khối (Material Design)
```

> 🧭 **Mẹo đọc code:** khi lần theo một tính năng, luôn đi theo hướng
> `ui/screens/*` → `ui/viewmodel/*` → `data/repository/*` → `data/api/*`.
> Đây chính là "call stack" của app.

## 4. Gradle & version catalog trong 60 giây

Mở [`app/app/build.gradle.kts`](../app/app/build.gradle.kts). Vài dòng cần biết:

```kotlin
android {
    namespace = "com.example.todoapplication"
    compileSdk { version = release(36) ... }   // biên dịch với API Android 36
    defaultConfig {
        applicationId = "com.example.todoapplication" // "ID gói" định danh app trên máy
        minSdk = 29        // chạy được từ Android 10 trở lên
        targetSdk = 36     // được test & tối ưu cho API 36
    }
}
dependencies {
    implementation(libs.retrofit)        // ← thêm 1 thư viện
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)     // ← ksp = bộ sinh code lúc build (cho Room)
    ...
}
```

`libs.retrofit` không phải "magic" — nó tra trong [`gradle/libs.versions.toml`](../app/gradle/libs.versions.toml). Ví dụ:

```toml
[versions]
retrofit = "2.11.0"
[libraries]
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
```

Đây gọi là **version catalog** — gom mọi phiên bản về một chỗ (giống việc bạn ghim version trong `go.mod`). Muốn thêm thư viện mới: khai báo ở đây rồi tham chiếu bằng `libs.<tên>` trong `build.gradle.kts`.

### `implementation` vs `ksp` vs `testImplementation`

- `implementation(...)` → thư viện app dùng lúc chạy.
- `ksp(...)` → **Kotlin Symbol Processing**: chương trình chạy *lúc build* để **sinh code tự động**. Room dùng nó để sinh code SQL từ interface DAO của bạn (giống code-gen trong Go với `go generate`). Code sinh ra nằm ở `app/app/build/generated/`.
- `testImplementation(...)` / `androidTestImplementation(...)` → chỉ dùng khi test.

## 5. Build & chạy app

**Cách dễ nhất: Android Studio** (khuyến nghị cho người mới).
1. Cài Android Studio.
2. `File → Open` → chọn thư mục [`app/`](../app/) (gốc Gradle, KHÔNG phải `app/app`).
3. Chờ Gradle "sync" (tải thư viện). Lần đầu khá lâu.
4. Tạo một máy ảo: `Device Manager → Create Device` (chọn ví dụ Pixel 7, API 34+).
5. Bấm nút ▶️ **Run**. App sẽ build và cài lên máy ảo.

**Cách dòng lệnh (khi đã quen):**
```bash
cd app
./gradlew assembleDebug     # build ra file APK debug
./gradlew installDebug      # cài lên máy ảo/thiết bị đang cắm
./gradlew test              # chạy unit test (JVM, nhanh)
```
> Trên Windows dùng `gradlew.bat` thay cho `./gradlew`.

### ⚠️ Kết nối tới backend của bạn
App gọi API tại `BASE_URL` khai báo trong [NetworkClient.kt](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L18). Hiện đang trỏ tới server thật (`https://todo.phongngohong.online/api/v1/`). Nếu muốn chạy backend Go **cục bộ** để thử:
- **Máy ảo Android Studio:** dùng `http://10.0.2.2:8080/api/v1/` (địa chỉ `10.0.2.2` là "cửa hậu" trỏ về máy tính host).
- **Điện thoại thật cùng Wi-Fi:** dùng IP LAN của PC, ví dụ `http://192.168.0.102:8080/api/v1/`.
- **Tuyệt đối không** dùng `localhost` — trên thiết bị Android nó trỏ về chính thiết bị.

Chính comment trong file đó đã ghi sẵn các lưu ý này.

## 6. Tự kiểm tra
1. `app/app/build.gradle.kts` khác `app/build.gradle.kts` ở chỗ nào?
2. Nếu muốn thêm thư viện `coil` để load ảnh, bạn phải sửa mấy file, những file nào?
3. `minSdk = 29` nghĩa là gì với người dùng đang xài Android 9?
4. Vì sao không được dùng `localhost` làm BASE_URL khi test trên máy ảo?

➡️ Tiếp theo: [Bài 01 — Kotlin cho dev backend](./01-kotlin-cho-dev-backend.md)
