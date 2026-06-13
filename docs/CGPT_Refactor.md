# MAPSUPERVISION – BÁO CÁO ĐÁNH GIÁ, TỐI ƯU VÀ REFACTOR KIẾN TRÚC


# 1. TỔNG QUAN DỰ ÁN

MAPSUPERVISION là hệ thống Android phục vụ quản lý hạ tầng hiện trường với các chức năng:

* Quản lý dự án
* Quản lý tuyến
* GIS / Bản đồ
* Thu thập dữ liệu hiện trường
* Chụp ảnh GPS
* Timeline tiến độ
* Xuất báo cáo PDF
* AI Assistant
* Import/Export KML, KMZ
* Làm việc Offline

Kiến trúc hiện tại theo hướng:

```text
Presentation
    ↓
Domain
    ↓
Data
    ↓
Storage
```

Kết hợp:

* Jetpack Compose
* Hilt
* Room
* MapLibre
* TensorFlow Lite
* ML Kit
* Gemini AI
* Coroutines
* Flow

---

# 2. ĐÁNH GIÁ TỔNG THỂ

| Hạng mục           | Đánh giá |
| ------------------ | -------- |
| Kiến trúc tổng thể | 8/10     |
| Phân module        | 7.5/10   |
| Khả năng mở rộng   | 8/10     |
| Hiệu năng hiện tại | 6.5/10   |
| AI Integration     | 6/10     |
| Maintainability    | 6/10     |
| Testability        | 5/10     |
| Offline Capability | 8/10     |

---

# 3. CÁC LỖI KIẾN TRÚC ĐANG TỒN TẠI

---

## 3.1 Module Dependency Chưa Sạch

### Hiện trạng

Tài liệu mô tả:

```text
storage
```

Nhưng cấu hình Gradle:

```text
storage-core
storage-import
storage-crypto
```

Tồn tại khả năng:

```text
project(":storage")
```

vẫn còn xuất hiện trong source.

### Hậu quả

* Build fail
* Dependency graph không nhất quán
* Khó maintain

### Khắc phục

Chọn 1 trong 2:

### Option A (Khuyến nghị)

Xóa hoàn toàn:

```text
:storage
```

Giữ:

```text
:storage-core
:storage-import
:storage-crypto
```

### Option B

Tạo module storage mới làm Facade.

---

# 4. WORKSPACEAPPSHELL ĐANG TRỞ THÀNH GOD COMPONENT

---

## Hiện trạng

File:

```text
WorkspaceAppShell.kt
```

Dài hơn 600 dòng.

Đang xử lý:

* Navigation
* State
* Progress
* Chat
* Map
* Report
* Photo
* AI

cùng một nơi.

---

## Hậu quả

### Recomposition lớn

Chỉ cần:

```kotlin
progress++
```

có thể:

```text
Map
Chat
Timeline
Report
```

đều recompose.

---

### Khó test

Unit Test gần như không khả thi.

---

### Khắc phục

Tách thành:

```text
workspace/
│
├── WorkspaceNavHost
├── WorkspaceScreen
├── WorkspaceViewModel
│
├── map/
├── report/
├── progress/
├── chat/
├── timeline/
```

---

# 5. CAMERA OVERLAY ĐANG VI PHẠM CLEAN ARCHITECTURE

---

## Hiện trạng

Composable đang thực hiện:

```text
Lấy GPS
Reverse Geocode
Import ảnh
Watermark
Lưu file
```

trực tiếp.

---

## Hậu quả

### UI Freeze

Khi import:

```text
50 ~ 100 ảnh
```

UI lag rõ rệt.

---

### Memory Leak

Đang sử dụng:

```kotlin
MainScope()
```

trong UI.

---

## Refactor

### Trước

```kotlin
CameraOverlay
   ↓
Location
   ↓
Watermark
   ↓
Save
```

### Sau

```kotlin
CameraOverlay

      ↓

PhotoViewModel

      ↓

CapturePhotoUseCase

      ↓

PhotoPipelineService
```

---

# 6. GEMMA MODEL DOWNLOAD SERVICE

---

## Hiện trạng

Foreground Service:

```text
WakeLock
while(true)
retry
```

---

## Rủi ro

### Hao pin

### Nóng máy

### Service treo

---

## Giải pháp

Sử dụng:

```text
WorkManager
```

thay thế.

---

### Retry Policy

```kotlin
BackoffPolicy.EXPONENTIAL
```

Ví dụ:

```text
1 phút
2 phút
4 phút
8 phút
16 phút
```

Tối đa:

```text
5 lần
```

---

# 7. SYSTEM.GC() ANTI-PATTERN

---

## Hiện trạng

Application:

```kotlin
System.gc()
```

---

## Hậu quả

### Frame Drop

### Jank

### UI Stutter

---

## Khắc phục

Xóa hoàn toàn:

```kotlin
System.gc()
```

Thay bằng:

```text
Coil Cache Policy
Memory Cache
Bitmap Pool
```

---

# 8. RỦI RO API KEY

---

## Hiện trạng

Gemini API Key:

```text
BuildConfig
.env
```

---

## Rủi ro

APK decompile được.

---

## Giải pháp

### Dev

```text
BuildConfig
```

### Production

```text
Backend Token Broker
```

---

# 9. ĐÁNH GIÁ AI HIỆN TẠI

---

## Các AI đang tồn tại

### Gemini

```text
Cloud LLM
```

### TensorFlow Lite

```text
Local Vision
```

### ML Kit

```text
OCR
Barcode
```

### LiteRTLM

```text
Local LLM
```

---

## Vấn đề

Mỗi AI đang hoạt động riêng lẻ.

Không có:

```text
AI Orchestrator
```

---

# 10. KIẾN TRÚC AI LOCAL MỚI

---

## Mục tiêu

AI Local phải:

```text
Nhanh
Offline
Ổn định
Đúng dữ liệu
```

---

## Kiến trúc

```text
AI Router
│
├── OCR Engine
├── Vision Engine
├── LLM Engine
└── Cloud Fallback
```

---

# 11. AI ORCHESTRATOR

---

## Layer

```text
domain/ai
```

```text
AiOrchestrator
│
├── TaskClassifier
├── ConfidenceScorer
├── Validator
├── FallbackManager
└── PromptBuilder
```

---

## Luồng xử lý

```text
Request
   ↓
Classifier
   ↓
OCR ?
   ↓
ML Kit

Vision ?
   ↓
TFLite

Text ?
   ↓
Gemma

Confidence Check
   ↓
Pass
   ↓
Return

Fail
   ↓
Fallback
```

---

# 12. CHÍNH SÁCH AI LOCAL-FIRST

---

## OCR

Luôn Local

```text
ML Kit
```

---

## QR

Luôn Local

```text
ML Kit
```

---

## Chất lượng ảnh

Luôn Local

```text
TFLite
```

---

## Chat AI

Ưu tiên:

```text
Gemma Local
```

Fallback:

```text
Gemini
```

---

# 13. CƠ CHẾ KIỂM SOÁT ĐỘ CHÍNH XÁC AI

---

## Golden Dataset

Xây dựng:

```text
1000 ảnh thực tế
```

bao gồm:

* Camera
* Tủ kỹ thuật
* Cột
* Tuyến cáp
* Tem nhãn

---

## Regression Test

Mỗi lần update model:

```text
Accuracy >= 95%
```

---

# 14. KẾ HOẠCH TỐI ƯU HIỆU NĂNG

---

# GIAI ĐOẠN 1

## Stabilization

### Mục tiêu

Build sạch.

### Công việc

* Fix module dependency
* Fix Gradle
* Fix BuildConfig

### Thời gian

1 tuần

---

# GIAI ĐOẠN 2

## UI Optimization

### Công việc

Tách:

```text
WorkspaceAppShell
```

thành:

```text
Map
Timeline
Chat
Report
```

riêng biệt.

---

### Kết quả

Giảm:

```text
40%
```

Recomposition.

---

# GIAI ĐOẠN 3

## Background Processing

### Chuyển toàn bộ

```text
Import ảnh
Export PDF
Download model
Sync dữ liệu
```

sang:

```text
WorkManager
```

---

### Kết quả

UI luôn responsive.

---

# GIAI ĐOẠN 4

## Database Optimization

---

### Room

Thêm:

```sql
INDEX(project_id)

INDEX(route_id)

INDEX(created_at)
```

---

### Paging

Áp dụng:

```text
Paging3
```

cho:

* Timeline
* Photos
* Reports

---

# GIAI ĐOẠN 5

## Map Optimization

---

### Cluster Marker

Thay:

```text
10.000 Marker
```

bằng:

```text
Cluster
```

---

### Tile Cache

Offline Cache:

```text
500MB
```

---

### Geometry Simplification

Douglas-Peucker.

---

# GIAI ĐOẠN 6

## AI Optimization

---

### Quantization

INT8

---

### GPU Delegate

Thiết bị hỗ trợ:

```text
GPU
```

---

### CPU Fallback

Thiết bị yếu:

```text
4 thread
```

---

# 15. KẾ HOẠCH REFACTOR TOÀN BỘ

---

# PHASE R1

## Architecture Cleanup

```text
app
core
domain
data
storage
```

chuẩn hóa dependency.

---

# PHASE R2

## Feature Modularization

```text
feature-map
feature-photo
feature-report
feature-chat
feature-timeline
```

---

# PHASE R3

## Domain Cleanup

```text
UseCase
Entity
Repository
```

thuần Kotlin.

---

# PHASE R4

## AI Refactor

```text
ai-core
ai-local
ai-cloud
ai-router
```

---

# PHASE R5

## Performance Refactor

Tối ưu:

* Coroutine
* Flow
* Room
* Compose
* MapLibre

---

# PHASE R6

## Testing

---

### Unit Test

```text
>= 80%
```

Coverage.

---

### Integration Test

* GIS
* Camera
* Timeline
* Report

---

### Benchmark

* Startup Time
* Scrolling
* Camera Capture
* Map Render

---

# 16. KIẾN TRÚC MỤC TIÊU SAU REFACTOR

```text
app
│
├── feature-map
├── feature-photo
├── feature-report
├── feature-chat
├── feature-timeline
│
├── ai-core
├── ai-router
├── ai-local
├── ai-cloud
│
├── domain
├── data
├── core
│
├── storage-core
├── storage-import
└── storage-crypto
```

---
