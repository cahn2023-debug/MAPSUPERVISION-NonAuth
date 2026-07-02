# TÀI LIỆU CHI TIẾT CẤU TRÚC MÃ NGUỒN & HỆ THỐNG MODULE

Dự án MapSupervision được xây dựng dưới dạng ứng dụng Android đa module (multi-module) viết bằng ngôn ngữ **Kotlin** và sử dụng công cụ thiết kế giao diện hiện đại **Jetpack Compose**. Cấu trúc mã nguồn tuân thủ chặt chẽ nguyên lý **Kiến trúc sạch (Clean Architecture)** nhằm tách biệt giao diện, logic nghiệp vụ và lưu trữ dữ liệu.

---

## 1. Bản Đồ Tổng Quan Hệ Thống Module (Module Diagram)

Dưới đây là sơ đồ phụ thuộc giữa các module chính trong dự án:

```mermaid
graph TD
    subgraph Giao Diện & Điều Phối (Presentation Layer)
        app[":app (Shell chính, AI Offline, Data Hub)"]
        project[":project (Quản lý dự án)"]
        gis[":gis (Giao diện Bản đồ GIS)"]
        photo[":photo (Máy ảnh thực địa & Đóng dấu tọa độ)"]
        timeline[":timeline (Nhật ký tiến trình thời gian)"]
        reporting[":reporting (Xuất báo cáo PDF/Word)"]
    end

    subgraph Cầu Nối GIS (Contracts)
        gis-maplibre[":gis-maplibre (Hiện thực hóa bản đồ MapLibre)"]
    end

    subgraph Nghiệp Vụ Lõi & Dữ Liệu (Core & Data Layer)
        data[":data (SQLite Room, Repositories)"]
        domain[":domain (Models, UseCases, Repositories Contracts)"]
        storage-import[":storage-import (Excel/KML/Word Parsers)"]
        storage-core[":storage-core (Quản lý lưu trữ & File DB)"]
        storage-crypto[":storage-crypto (Mã hóa dữ liệu)"]
        core[":core (Logger, Tiện ích dùng chung)"]
    end

    app --> project
    app --> gis
    app --> photo
    app --> timeline
    app --> reporting
    app --> gis-maplibre

    project & gis & photo & timeline & reporting --> domain
    gis-maplibre --> gis
    
    data --> domain
    data --> storage-core
    storage-import --> storage-core
    storage-core --> storage-crypto
    storage-crypto --> core
    domain --> core
```

---

## 2. Chi Tiết Vai Trò Của Từng Module

### 2.1. Module `:app` (Application Shell & Orchestrator)
Đóng vai trò là module khởi chạy chính của ứng dụng, liên kết tất cả các module tính năng khác.
- **Tệp tin chính**:
  - [MainActivity.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/MainActivity.kt): Điểm vào chính của ứng dụng, khởi tạo Hilt Dependency Injection và thiết lập màn hình.
  - [WorkspaceAppShell.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/WorkspaceAppShell.kt): Thanh điều hướng chính (NavigationBar / NavigationRail), quản lý trạng thái chuyển đổi tab, và chứa các hộp thoại (Dialog) hệ thống.
  - [AIManager.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/ai/AIManager.kt): Lớp quản lý vòng đời của mô hình Gemma Local LLM chạy trên thiết bị thông qua MediaPipe.
  - [WorkspaceViewModel.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/workspace/WorkspaceViewModel.kt): ViewModel trung tâm xử lý luồng dữ liệu (MVI Pattern) cho không gian làm việc.

### 2.2. Module `:domain` (Pure Business Logic)
Chứa các đối tượng nghiệp vụ thuần túy, không phụ thuộc vào thư viện Android.
- **Tệp tin chính**:
  - Gói `model/`: Định nghĩa các thực thể nghiệp vụ như `Project`, `SitePhoto`, `GisNode`, `GisRoute`, `DailyLog`.
  - Gói `repository/`: Định nghĩa các giao diện (Interface) kết nối dữ liệu như `GisRepository`, `PhotoRepository`, `ProjectRepository`.
  - Gói `usecase/`: Chứa các ca sử dụng nghiệp vụ cụ thể như `ObserveWorkspaceSnapshotUseCase.kt` để thu thập trạng thái hiện tại của khu vực thi công.

### 2.3. Module `:data` (Data Source & Storage Implementation)
Triển khai các giao diện dữ liệu của `:domain` bằng SQLite Room Database và các dịch vụ mạng.
- **Tệp tin chính**:
  - [MapSupervisionDatabase.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/data/src/main/java/com/mapsupervision/data/db/MapSupervisionDatabase.kt): Khai báo cấu trúc Room Database (bảng, chỉ mục, khóa ngoại) và lịch sử nâng cấp schema (Migrations).
  - Gói `dao/`: Định nghĩa các phương thức truy vấn SQLite cho các thực thể (`ProjectDao`, `GisNodeDao`, `DailyLogDao`).
  - Gói `repository/` (trong data): Cung cấp mã nguồn thực tế triển khai lưu trữ của các Repository từ `:domain`.

### 2.4. Module `:gis` (GIS Abstract & Presentation)
Mô tả giao diện bản đồ và trạng thái tương tác GIS, độc lập với thư viện vẽ bản đồ cụ thể.
- **Tệp tin chính**:
  - [GisScreen.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/gis/src/main/java/com/mapsupervision/gis/ui/GisScreen.kt): Giao diện hiển thị lớp bản đồ Compose và các nút tiện ích bản đồ.
  - [GisViewModel.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/gis/src/main/java/com/mapsupervision/gis/ui/GisViewModel.kt): Quản lý tọa độ tâm bản đồ, mức thu phóng (Zoom), đo khoảng cách và chọn đối tượng (Node/Route).

### 2.5. Module `:gis-maplibre` (GIS MapLibre Renderer)
Hiện thực hóa việc vẽ bản đồ sử dụng MapLibre SDK.
- **Tệp tin chính**:
  - [MapBridgeInstaller.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/gis-maplibre/src/main/java/com/mapsupervision/gis/maplibre/MapBridgeInstaller.kt): Cài đặt và lắng nghe các tương tác kéo thả bản đồ từ MapLibre truyền về module `:gis`.
  - Thư mục `assets/`: Chứa tệp JSON định nghĩa phong cách bản đồ như `style_street.json`, `style_satellite.json`.

### 2.6. Module `:photo` (Camera & Stamp Watermark)
Xử lý các tác vụ liên quan đến camera hiện trường và đóng dấu thông tin thực địa.
- **Tệp tin chính**:
  - [PhotoPipelineService.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/photo/src/main/java/com/mapsupervision/photo/worker/PhotoPipelineService.kt): Quản lý luồng xử lý và lưu trữ hình ảnh hiện trường.
  - [PhotoStampRenderer.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/photo/src/main/java/com/mapsupervision/photo/worker/PhotoStampRenderer.kt): Vẽ lớp phủ chứa bản đồ mini, GPS, nhà thầu thi công lên bề mặt ảnh chụp.

### 2.7. Module `:storage-import` (Excel/KML/Word Parsers)
Hỗ trợ đọc và chuyển đổi các tệp thiết kế dự án phức tạp sang cấu trúc GIS của MapSupervision.
- **Tệp tin chính**:
  - [ImportParsingModels.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/storage-import/src/main/java/com/mapsupervision/storage/importer/ImportParsingModels.kt): Chứa mã nguồn bộ phân tích KML/KMZ (Hàm `parseKmlContent`, `parseKmlContentStreaming`).
  - [DocxParser.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/storage-import/src/main/java/com/mapsupervision/storage/importer/DocxParser.kt): Trích xuất bảng khối lượng vật tư từ file tài liệu Word.
  - [ExcelParsingHelpers.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/storage-import/src/main/java/com/mapsupervision/storage/importer/ExcelParsingHelpers.kt): Đọc dữ liệu lưới Excel và trích xuất tọa độ VN-2000 hoặc WGS84.

### 2.8. Module `:storage-core` & `:storage-crypto` & `:storage-crypto`
- `:storage-core`: Quản lý vị trí lưu trữ file trên bộ nhớ ngoài, chuyển đổi thư mục dữ liệu dự án khi thay đổi phiên bản.
- `:storage-crypto`: Đảm bảo an toàn thông tin bằng cách mã hóa các payload của dự án trước khi lưu trữ (`ProjectCryptoManager.kt`).

### 2.9. Module `:reporting` (PDF & Word Exporter)
Tự động điền dữ liệu tiến độ và kết xuất báo cáo nghiệm thu chuyên nghiệp.
- **Tệp tin chính**:
  - [PdfReportGenerator.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/reporting/src/main/java/com/mapsupervision/reporting/pdf/PdfReportGenerator.kt): Sử dụng thư viện đồ họa để tạo báo cáo PDF đính kèm ảnh và bảng biểu.
  - [DocxReportGenerator.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/reporting/src/main/java/com/mapsupervision/reporting/docx/DocxReportGenerator.kt): Xuất file mẫu báo cáo Microsoft Word có định dạng sẵn.
