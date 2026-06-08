# MapSupervision - Knowledge Graph Documentation

## Project Overview

**MapSupervision** is a comprehensive Android application for infrastructure construction supervision and management. The application provides map-based visualization, project management, photo documentation, progress tracking, and AI-powered analysis capabilities.

### Project Metadata
- **Name**: MapSupervision
- **Platform**: Android (Kotlin)
- **Architecture**: Clean Architecture with multi-module structure
- **UI Framework**: Jetpack Compose
- **Dependency Injection**: Hilt
- **Database**: Room (SQLite)
- **Map Engine**: MapLibre (optional) with fallback canvas rendering
- **AI Integration**: Google Gemini API with fallback logic

---

## Module Architecture

### Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                         app                                 │
│  (Main Application - UI Layer & Orchestration)              │
└────────────┬────────────────────────────────────────────────┘
             │
    ┌────────┴────────┬──────────────┬──────────────┬────────┐
    │                 │              │              │        │
┌───▼────┐    ┌─────▼─────┐  ┌────▼────┐  ┌────▼────┐  ┌─▼──────┐
│ domain │    │   project │  │  gis    │  │ timeline │  │reporting│
└───┬────┘    └─────┬─────┘  └────┬────┘  └────┬────┘  └─┬──────┘
    │               │             │            │           │
    │        ┌──────▼──────┐     │            │           │
    │        │   storage   │     │            │           │
    │        └──────┬──────┘     │            │           │
    │               │             │            │           │
┌───▼────┐    ┌────▼─────┐  ┌───▼───┐  ┌────▼────┐  ┌─▼──────┐
│  data  │    │   core   │  │ photo │  │gis-maplibre│photo-lite│
└────────┘    └──────────┘  └───────┘  └───────────┘└─────────┘
```

### Module Descriptions

#### 1. **app** (Main Application Module)
- **Purpose**: Entry point, UI composition, and orchestration
- **Key Components**:
  - `MainActivity`: Main activity with bottom navigation (Map, Progress, Photos, Reports)
  - `MapSupervisionApplication`: Application class with Hilt setup and optional map bridge installation
  - `WorkspaceViewModel`: Central view model coordinating workspace operations
  - UI Screens: `MapHubScreen`, `DataHubScreen`, `ProgressHubScreen`, `DashboardHubScreen`
  - Dialogs: `ExcelMappingDialog`, `CombineFilesDialog`
- **Dependencies**: domain, data, core, project, gis, timeline, reporting, storage
- **Build Variants**: 
  - `field`: Lightweight version for field use
  - `full`: Full-featured version

#### 2. **core** (Core Utilities Module)
- **Purpose**: Shared utilities and base classes
- **Key Components**:
  - `AppResult`: Sealed class for operation results (Success/Error)
  - `AppExceptions`: Custom exception hierarchy (ImportException, StorageException, AiException, DatabaseException, ValidationException)
  - `AppLogger`: Logging utilities
  - `DispatcherProvider`: Coroutine dispatcher configuration
- **Dependencies**: None (foundation module)

#### 3. **domain** (Domain Layer Module)
- **Purpose**: Business logic, domain models, and repository interfaces
- **Key Components**:
  - **Models**: `GisNode`, `GisRoute`, `Project`, `ImportedFile`, `NodeProgress`, `MaterialProgress`, `DailyLog`, `SitePhoto`
  - **Repositories**: Interfaces for data access (GisRepository, ProjectRepository, ProgressRepository, etc.)
  - **AI System**: 
    - `AiOrchestrator`: Central AI coordination with fallback logic
    - `AiContracts`: AI capabilities, payloads, and results
    - Capabilities: IMPORT_MAPPING, DISCREPANCY_CHECK, TIMELINE_SUMMARY, PHOTO_QUALITY_CHECK, REPORT_DRAFT, OPS_RECOMMENDATION
  - **Utilities**: `Haversine` (distance calculation), `StringSimilarity`
- **Dependencies**: core

#### 4. **data** (Data Layer Module)
- **Purpose**: Data persistence, external API integration, repository implementations
- **Key Components**:
  - **Database**: `MapSupervisionDatabase` (Room database, version 5)
  - **Entities**: Room entities mapping to domain models
  - **DAOs**: Data access objects for each entity
  - **Repository Implementations**: Concrete implementations of domain repository interfaces
  - **AI Integration**: `GeminiRepositoryImpl` for Google Gemini API
  - **DI Module**: `DataModule` for Hilt dependency injection
- **Dependencies**: core, domain
- **External APIs**: Google Gemini AI API

#### 5. **storage** (Storage Module)
- **Purpose**: File system management, project storage, file import/export
- **Key Components**:
  - `ProjectStorageManager`: Manages project directory structure
  - `ProjectPackageService`: Project export/import packaging
  - `UserFileImportService`: File parsing and import (KML, KMZ, GeoJSON, Excel, DOCX, PDF)
  - `ProjectCryptoManager`: Encryption/decryption for project data
  - `ActiveProjectRepositoryImpl`: Active project persistence
  - **Parsers**: `KmlParser`, `KmzParser`, `DocxParser`
- **Dependencies**: core, domain
- **Supported Formats**: KML, KMZ, GeoJSON, XLSX, DOCX, PDF

#### 6. **gis** (GIS Module)
- **Purpose**: Map rendering and GIS functionality (abstract)
- **Key Components**:
  - `GisScreen`: Composable map screen
  - `GisViewModel`: Map state management
  - `GisMapBridge`: Interface for map engine abstraction
  - `GisMapBridgeRegistry`: Registry for map bridge implementation
  - `GisStyleBuilder`: Map style configuration
  - Enums: `GisLabelField` (CODE, CONTRACTOR, COORDINATE), `MapLayerType` (STREET, SATELLITE, DARK)
- **Dependencies**: domain
- **Fallback**: Canvas-based map rendering when no bridge is installed

#### 7. **gis-maplibre** (MapLibre Integration Module)
- **Purpose**: MapLibre GL Native implementation of GIS bridge
- **Key Components**:
  - `MapBridgeInstaller`: Installs MapLibre bridge into registry
  - `MapLibreGisMapBridge`: Full MapLibre implementation with:
    - Node and route rendering
    - Label customization
    - Contractor-based coloring
    - Measurement tools
    - Layer visibility control
    - Location services integration
- **Dependencies**: gis
- **Map Styles**: Street, Satellite, Dark (asset-based JSON)

#### 8. **photo** (Photo Module)
- **Purpose**: Photo capture, management, and AI quality checking
- **Key Components**:
  - `PhotoScreen`: Photo gallery and capture UI
  - `PhotoViewModel`: Photo state management
  - `PhotoLocationProvider`: GPS location for photos
  - `PhotoPipelineService`: Photo processing (watermark, thumbnail, import)
- **Dependencies**: domain, storage
- **Features**: Camera capture, gallery import, watermarking, thumbnail generation, AI quality assessment

#### 9. **photo-lite** (Photo Lite Module)
- **Purpose**: Lightweight photo functionality for field variant
- **Key Components**: Simplified photo screen
- **Dependencies**: domain

#### 10. **project** (Project Management Module)
- **Purpose**: Project CRUD operations and management
- **Key Components**:
  - `ProjectScreen`: Project list and management UI
  - `ProjectViewModel`: Project state management
  - `ProjectUiState`: Project UI state
- **Dependencies**: domain, storage
- **Features**: Create, switch, clone, archive projects

#### 11. **reporting** (Reporting Module)
- **Purpose**: Report generation and export
- **Key Components**:
  - `ReportingScreen`: Report generation UI
  - `ReportingViewModel`: Report state management
  - `PdfReportGenerator`: PDF report generation
  - `MaterialReportRow`: Material progress reporting structure
- **Dependencies**: domain, storage
- **Features**: PDF export, material progress reports, AI-powered report drafting, project package export

#### 12. **timeline** (Timeline Module)
- **Purpose**: Progress tracking and daily log management
- **Key Components**:
  - `TimelineScreen`: Timeline visualization UI
  - `TimelineViewModel`: Timeline state management
- **Dependencies**: domain
- **Features**: Progress tracking, daily logs, AI timeline summaries

---

## Data Models

### Core Domain Models

#### GisNode
```kotlin
data class GisNode(
    val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val latitude: Double,
    val longitude: Double,
    val mapNumberLabel: String = "",
    val materialSummary: String = ""
)
```
- **Purpose**: Represents a geographic point/node in the infrastructure
- **Usage**: Map visualization, progress tracking, material management

#### GisRoute
```kotlin
data class GisRoute(
    val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val startNodeCode: String,
    val endNodeCode: String
)
```
- **Purpose**: Represents a connection between two nodes
- **Usage**: Route visualization, distance calculation

#### Project
```kotlin
data class Project(
    val id: String,
    val name: String,
    val slug: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long
)
```
- **Purpose**: Project container for all data
- **Usage**: Project management, data isolation

#### NodeProgress
```kotlin
data class NodeProgress(
    val id: String,
    val projectId: String,
    val nodeCode: String,
    val planned: Float,
    val actual: Float,
    val remain: Float,
    val delayed: Boolean
)
```
- **Purpose**: Tracks construction progress per node
- **Usage**: Progress tracking, delay detection

#### MaterialProgress
```kotlin
data class MaterialProgress(
    val id: String,
    val projectId: String,
    val nodeId: String,
    val materialName: String,
    val plannedQty: Float,
    val actualQty: Float
)
```
- **Purpose**: Tracks material quantities per node
- **Usage**: Material management, reporting

#### SitePhoto
```kotlin
data class SitePhoto(
    val id: String,
    val projectId: String,
    val objectCode: String,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double?,
    val longitude: Double?,
    val engineer: String,
    val capturedAtEpochMs: Long
)
```
- **Purpose**: Site photography documentation
- **Usage**: Photo gallery, quality checking, geotagging

#### DailyLog
```kotlin
data class DailyLog(
    val id: String,
    val projectId: String,
    val workItem: String,
    val manpower: Int,
    val note: String,
    val createdAtEpochMs: Long
)
```
- **Purpose**: Daily work logging
- **Usage**: Timeline tracking, reporting

#### ImportedFile
```kotlin
data class ImportedFile(
    val id: String,
    val projectId: String,
    val fileName: String,
    val fileType: String,
    val storedPath: String,
    val summary: String,
    val importedAtEpochMs: Long
)
```
- **Purpose**: Tracks imported design files
- **Usage**: File management, import history

---

## AI System Architecture

### AI Capabilities

#### 1. IMPORT_MAPPING
- **Purpose**: Auto-detect column mappings for Excel imports
- **Input**: Headers and sample rows from Excel file
- **Output**: Column mapping suggestions (node code, lat/lon, contractor, items)
- **Fallback**: Pattern-based column detection

#### 2. DISCREPANCY_CHECK
- **Purpose**: Detect discrepancies between imported and existing data
- **Input**: Project ID and discrepancy rows (code, contractors, distances)
- **Output**: Issues list and recommended actions
- **Fallback**: Rule-based discrepancy detection (distance > 50m, contractor mismatch)

#### 3. TIMELINE_SUMMARY
- **Purpose**: Generate daily progress summaries
- **Input**: Progress data, daily logs, photo count
- **Output**: Summary text, issue highlights, recommended actions
- **Fallback**: Statistical summary generation

#### 4. PHOTO_QUALITY_CHECK
- **Purpose**: Assess photo quality for documentation
- **Input**: Object code, engineer, GPS coordinates
- **Output**: Quality score, issues, recommendation, retake flag
- **Fallback**: Basic validation (GPS presence, object code)

#### 5. REPORT_DRAFT
- **Purpose**: Generate executive report drafts
- **Input**: Project statistics (nodes, delays, progress, photos)
- **Output**: Executive summary, risk section, recommended actions
- **Fallback**: Template-based report generation

#### 6. OPS_RECOMMENDATION
- **Purpose**: Provide operational recommendations
- **Input**: Project metrics (total nodes, delays, completion %, warnings)
- **Output**: Prioritized action list with priority level
- **Fallback**: Rule-based recommendations

### AI Decision Flow

```
User Request
    ↓
AiOrchestrator.execute()
    ↓
Feature Flag Check
    ↓ (disabled)
Fallback Logic → AiDecision(source=DISABLED)
    ↓ (enabled)
Model Execution
    ↓ (success)
AiDecision(source=MODEL, confidence=80%)
    ↓ (failure)
Fallback Logic → AiDecision(source=FALLBACK, confidence=95%)
```

### AI Integration Points

1. **WorkspaceViewModel**: Ops recommendations during import
2. **PhotoViewModel**: Photo quality checking after capture
3. **ReportingViewModel**: Report drafting for PDF export
4. **TimelineViewModel**: Timeline summaries for progress tracking
5. **Excel Mapping**: Column mapping suggestions during Excel import
6. **Import Process**: Discrepancy checking during data import

---

## Database Schema

### MapSupervisionDatabase (Room, Version 5)

#### Tables

1. **project**
   - Columns: id (PK), name, slug, isArchived, createdAtEpochMs
   - Indexes: slug

2. **gis_node**
   - Columns: id (PK), projectId, code, contractor, latitude, longitude, mapNumberLabel, materialSummary
   - Indexes: projectId, code, contractor

3. **gis_route**
   - Columns: id (PK), projectId, code, contractor, startNodeCode, endNodeCode
   - Indexes: projectId, code

4. **node_progress**
   - Columns: id (PK), projectId, nodeCode, planned, actual, remain, delayed
   - Indexes: projectId, nodeCode

5. **material_progress**
   - Columns: id (PK), projectId, nodeId, materialName, plannedQty, actualQty
   - Indexes: projectId, nodeId, materialName

6. **site_photo**
   - Columns: id (PK), projectId, objectCode, filePath, thumbnailPath, latitude, longitude, engineer, capturedAtEpochMs
   - Indexes: projectId, objectCode

7. **daily_log**
   - Columns: id (PK), projectId, workItem, manpower, note, createdAtEpochMs
   - Indexes: projectId, createdAtEpochMs

8. **imported_file**
   - Columns: id (PK), projectId, fileName, fileType, storedPath, summary, importedAtEpochMs
   - Indexes: projectId

9. **active_project** (via storage module)
   - Single row storing current active project ID

---

## File Import System

### Supported Formats

#### KML/KMZ (Google Earth)
- **Parser**: `KmlParser`, `KmzParser`
- **Extracts**: Placemarks (points), LineStrings (routes)
- **Fields**: Name, coordinates, description
- **Route Calculation**: Automatic distance calculation using Haversine formula

#### GeoJSON
- **Parser**: Native JSON parsing
- **Extracts**: Features (Point, LineString)
- **Fields**: properties.name, properties.id, geometry.coordinates
- **Route Generation**: Sequential point connection

#### Excel (.xlsx)
- **Parser**: Custom ZIP-based XLSX parser
- **Features**: 
  - Header detection
  - Merged cell handling
  - Shared strings support
  - Auto column mapping
  - Item column detection
- **Classification Modes**: FORCE_NODE, FORCE_ROUTE, BY_OBJECT_TYPE_COLUMN, AUTO
- **Coordinate Formats**: Decimal degrees, DMS, coordinate pairs

#### DOCX
- **Parser**: ZIP-based XML parsing
- **Extracts**: Paragraph count
- **Usage**: Metadata extraction only

#### PDF
- **Parser**: Binary pattern matching
- **Extracts**: Page count estimation
- **Usage**: Metadata extraction only

### Import Pipeline

```
File Selection
    ↓
File Copy to Project Imports
    ↓
Format Detection
    ↓
Parser Selection
    ↓
Content Parsing
    ↓
Geometry Extraction (Nodes/Routes)
    ↓
Deduplication (Code/Name/Coordinate matching)
    ↓
Database Upsert
    ↓
Safety Check (Baseline comparison)
    ↓
State Update
```

### Deduplication Strategy

The system uses multi-signal deduplication to prevent duplicate nodes/routes:

1. **Code Matching**: Exact code match (normalized)
2. **Name Matching**: Fuzzy name matching (Levenshtein distance)
3. **Coordinate Matching**: Spatial proximity (within threshold)
4. **Multi-Signal**: Combination of above signals

**Risk Assessment**:
- **High Risk**: Many weak matches, coordinate-only rejections
- **Medium Risk**: Some weak matches, moderate duplicates
- **Low Risk**: Strong matches, clean import

---

## UI Architecture

### Navigation Structure

```
MainActivity
├── Tab.MAP (Bản đồ)
│   └── MapHubScreen
│       ├── Map View (GIS)
│       ├── Node/Route Selection Cards
│       ├── Project Management Drawer
│       └── Map Controls (Zoom, Layers, Measure)
├── Tab.PROGRESS (Tiến độ)
│   └── ProgressHubScreen
│       └── Progress Entry Form
├── Tab.PHOTOS (Nhập liệu)
│   └── DataHubScreen
│       ├── File Upload
│       ├── Excel Mapping Dialog
│       ├── Node List with Material Progress
│       └── Photo Gallery (filtered by node)
└── Tab.REPORTS (Báo cáo)
    └── ReportingScreen
        ├── Material Progress Report
        ├── PDF Export
        └── Project Package Export
```

### State Management

#### WorkspaceState
```kotlin
data class WorkspaceState(
    val activeProjectId: String?,
    val importedFiles: List<ImportedFile>,
    val designNodes: List<GisNode>,
    val designRoutes: List<GisRoute>,
    val constructionProgress: List<NodeProgress>,
    val dashboard: DashboardData,
    val mapUi: MapUiState,
    val photoFilterNodeCode: String?,
    val importUi: ImportUiState,
    val excelParserUi: ExcelParserUiState,
    val importMappingUi: ImportMappingUiState,
    val aiOpsActions: List<String>,
    val aiOpsPriority: Int,
    val materialProgress: Map<String, String>
)
```

#### MapUiState
```kotlin
data class MapUiState(
    val selectedNode: GisNode?,
    val selectedRoute: GisRoute?,
    val labelField: GisLabelField,
    val showNodes: Boolean,
    val showRoutes: Boolean,
    val measureEnabled: Boolean,
    val filterContractor: String?,
    val searchQuery: String,
    val status: String,
    val expectedCompletion: String,
    val lastInspection: String,
    val message: String
)
```

---

## Key Workflows

### 1. Project Creation Workflow

```
User enters project name
    ↓
ProjectViewModel.createProject(name)
    ↓
ProjectRepository.create(name)
    ↓
ActiveProjectRepository.setActive(projectId)
    ↓
ProjectStorageManager.projectRoot(slug) creates directories
    ↓
UI refreshes with new project
```

### 2. Design Import Workflow

```
User selects files (KML/KMZ/GeoJSON/Excel)
    ↓
FilePicker returns URIs
    ↓
WorkspaceViewModel.importDesignFiles(uris)
    ↓
For each file:
    - Copy to project imports directory
    - Parse based on file type
    - Extract nodes and routes
    - Deduplicate against existing data
    - Calculate quality metrics
    - Batch upsert to database
    - Safety check against baseline
    ↓
Update UI with import results
    ↓
AI Ops recommendations generated
```

### 3. Excel Import Workflow

```
User selects Excel file
    ↓
DataHubScreen detects Excel
    ↓
UserFileImportService.inspectExcel(uri)
    ↓
ExcelPreview generated (headers, sample rows)
    ↓
AI suggests column mapping
    ↓
User confirms mapping in ExcelMappingDialog
    ↓
WorkspaceViewModel.parseExcelToDesign()
    ↓
UserFileImportService.importExcelWithMapping()
    ↓
Parse Excel with mapping
    ↓
Classify rows (node/route)
    ↓
Extract coordinates and materials
    ↓
Generate nodes and routes
    ↓
Deduplicate and upsert
```

### 4. Photo Capture Workflow

```
User selects node on map
    ↓
Clicks "Chụp ảnh" button
    ↓
PhotoViewModel.createCaptureFile(objectCode)
    ↓
Camera intent launched
    ↓
Photo captured
    ↓
PhotoViewModel.registerCapturedPhoto(file, objectCode, engineer)
    ↓
PhotoLocationProvider gets GPS
    ↓
PhotoPipelineService applies watermark
    ↓
Thumbnail generated
    ↓
PhotoQualityPayload created
    ↓
AiOrchestrator.execute(PHOTO_QUALITY_CHECK)
    ↓
SitePhoto saved to database
    ↓
UI refreshes with quality feedback
```

### 5. Progress Tracking Workflow

```
User opens Progress tab
    ↓
TimelineViewModel.refresh()
    ↓
Load NodeProgress and DailyLog
    ↓
AiOrchestrator.execute(TIMELINE_SUMMARY)
    ↓
Display progress list with AI summary
    ↓
User adds progress entry
    ↓
TimelineViewModel.addProgress(nodeCode, planned, actual)
    ↓
Calculate remain = planned - actual
    ↓
Set delayed = actual < planned
    ↓
Upsert to database
    ↓
Refresh timeline
```

### 6. Report Generation Workflow

```
User opens Reports tab
    ↓
ReportingViewModel.refreshReportData()
    ↓
Load MaterialProgress data
    ↓
Group by material name
    ↓
Calculate totals and completion %
    ↓
User clicks "Export PDF"
    ↓
ReportingViewModel.exportPdf()
    ↓
Load photos, progress, material data
    ↓
AiOrchestrator.execute(REPORT_DRAFT)
    ↓
PdfReportGenerator.exportProjectSummary()
    ↓
Generate PDF with AI summary
    ↓
Save to reports directory
    ↓
Display file path
```

---

## Technology Stack

### Core Technologies
- **Language**: Kotlin 2.0.21
- **Build System**: Gradle 8.5.2 with Kotlin DSL
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Java Version**: 17

### UI Framework
- **Jetpack Compose**: Modern declarative UI
- **Material3**: Material Design 3 components
- **Compose Navigation**: Navigation component
- **Coil**: Image loading (implied)

### Architecture
- **Hilt**: Dependency injection (v2.52)
- **Coroutines**: Asynchronous programming
- **Flow**: Reactive data streams
- **ViewModel**: UI state management

### Data Persistence
- **Room**: Local database (v2.6.1)
- **DataStore**: (implied for preferences)
- **File System**: Project file storage

### Mapping
- **MapLibre GL Native**: Vector map rendering
- **GeoJSON**: Geographic data format
- **Haversine**: Distance calculations

### AI/ML
- **Google Gemini API**: Generative AI (v0.9.0)
- **Fallback Logic**: Rule-based AI alternatives

### File Processing
- **Apache POI**: (implied for Excel)
- **Custom ZIP parsers**: For XLSX, DOCX, KMZ
- **XML Pull Parser**: KML parsing
- **JSON**: GeoJSON parsing

### Utilities
- **Timber**: Logging (v5.0.1)
- **AndroidX Security**: Encryption (v1.1.0-alpha06)

---

## Security & Privacy

### Data Encryption
- **ProjectCryptoManager**: Encrypts sensitive project data
- **AndroidX Security**: Uses hardware-backed keystore when available

### File Permissions
- **Scoped Storage**: Android 10+ file access model
- **Persistable URI Permissions**: For file access across app restarts

### API Keys
- **Environment Variables**: API keys stored in .env file
- **BuildConfig**: Keys compiled into BuildConfig at build time
- **No hardcoded keys**: Security best practice

---

## Performance Optimizations

### Database
- **Batch Operations**: Bulk upsert for nodes/routes
- **Indexes**: Strategic indexing on frequently queried columns
- **Coroutines**: All database operations on IO dispatcher

### File Processing
- **Streaming**: Large file processing with streams
- **Caching**: Coordinate parsing caches, flexible number parsing
- **Lazy Loading**: Pagination for large datasets

### UI
- **LazyColumn/LazyVerticalGrid**: Efficient list rendering
- **State Hoisting**: Proper state management
- **Disposable Effects**: Clean up resources
- **Signature-based Updates**: Avoid unnecessary map redraws

### Map Rendering
- **GeoJSON Sources**: Efficient vector rendering
- **Layer Visibility**: Toggle layers instead of removing
- **Signature Caching**: Prevent redundant updates
- **Fit Bounds**: Automatic viewport optimization

---

## Testing Strategy

### Unit Tests
- **Domain Layer**: Business logic testing
- **Data Layer**: Repository implementation testing
- **AI System**: Fallback logic testing
- **Utilities**: Haversine, StringSimilarity testing

### Integration Tests
- **Database**: Room DAO testing
- **File Import**: Parser testing (KML/KMZ/Excel)
- **Repository**: End-to-end data flow testing

### UI Tests
- **Compose UI**: Component testing
- **Navigation**: Flow testing
- **ViewModel**: State management testing

### Test Files
- `SmokeTest.kt`: Basic smoke tests
- `DedupAiSummaryFormatterTest.kt`: AI summary formatting
- `DedupBatchDecisionAdvisorTest.kt`: Batch decision logic
- `DedupCoordMatchPolicyTest.kt`: Coordinate matching
- `DedupQualityAdvisorTest.kt`: Quality assessment
- `DedupQualityScorerTest.kt`: Quality scoring
- `DedupRiskSummaryFormatterTest.kt`: Risk summarization
- `DedupSignalPolicyTest.kt`: Signal policy logic
- `ImportSafetyGuardsTest.kt`: Import safety validation
- `WorkspaceDedupUtilsTest.kt`: Deduplication utilities
- `AiOrchestratorTest.kt`: AI orchestration
- `KmlKmzParserTest.kt`: KML/KMZ parsing

---

## Build Configuration

### Product Flavors

#### field (Field Edition)
- **Application ID**: com.mapsupervision.field
- **Features**: Lightweight, essential features only
- **Modules**: photo-lite, gis-maplibre
- **Use Case**: Field workers with limited device resources

#### full (Full Edition)
- **Application ID**: com.mapsupervision.full
- **Features**: Complete feature set
- **Modules**: photo, gis-maplibre
- **Use Case**: Full supervision with advanced features

### Build Types

#### debug
- **Minification**: Disabled
- **Debugging**: Full debug symbols
- **Build Config**: Debug configuration

#### release
- **Minification**: Enabled (R8/ProGuard)
- **Resource Shrinking**: Enabled
- **Optimization**: Full optimization
- **ProGuard Rules**: Custom rules for reflection and serialization

### Dependencies by Module

#### app
- AndroidX Core, Lifecycle, Activity Compose
- Jetpack Compose BOM, UI, Material3
- Hilt Android, Navigation Compose
- Timber (logging)
- All feature modules

#### domain
- Core module
- javax.inject (DI annotations)
- JUnit (testing)

#### data
- Core, Domain modules
- Room Runtime, KTX, Compiler
- Hilt Android, Compiler
- Google Generative AI

#### storage
- Core, Domain modules
- AndroidX Security (crypto)
- Hilt Android, Compiler
- JUnit (testing)

#### gis
- Domain module
- Jetpack Compose
- MapLibre (optional)

#### gis-maplibre
- GIS module
- MapLibre Android SDK
- GeoJSON library

#### photo
- Domain, Storage modules
- AndroidX libraries
- Hilt

#### project
- Domain, Storage modules
- AndroidX libraries
- Hilt

#### reporting
- Domain, Storage modules
- AndroidX libraries
- Hilt
- PDF generation library

#### timeline
- Domain module
- AndroidX libraries
- Hilt

---

## Error Handling

### Exception Hierarchy

```
Exception
└── AppException
    ├── ImportException
    ├── StorageException
    ├── AiException
    ├── DatabaseException
    └── ValidationException
```

### Error Handling Strategy

1. **Result Wrapper**: All operations return `AppResult<T>` (Success/Error)
2. **Graceful Degradation**: AI features fall back to rule-based logic
3. **User-Friendly Messages**: Vietnamese error messages for users
4. **Logging**: Comprehensive logging with Timber
5. **Safety Checks**: Import safety checks prevent data loss

### Common Error Scenarios

- **Import Errors**: File format errors, parsing errors, coordinate errors
- **Database Errors**: Constraint violations, connection errors
- **AI Errors**: API failures, timeout, rate limiting
- **Storage Errors**: File system errors, permission errors
- **Validation Errors**: Missing required fields, invalid data

---

## Localization

### Supported Language
- **Vietnamese**: Primary language for UI and user messages
- **English**: Technical terms, code comments, API keys

### Key Vietnamese Terms
- Bản đồ (Map)
- Tiến độ (Progress)
- Nhập liệu (Data Entry/Photos)
- Báo cáo (Reports)
- Nhà thầu (Contractor)
- Thi công (Construction)
- Thiết kế (Design)

---

## Future Enhancements

### Potential Features
1. **Offline Mode**: Full offline capability with sync
2. **Multi-user Support**: Team collaboration features
3. **Advanced Analytics**: More sophisticated progress analysis
4. **Integration APIs**: External system integration
5. **Cloud Backup**: Cloud storage integration
6. **Voice Notes**: Audio recording for daily logs
7. **AR Visualization**: Augmented reality for site visualization
8. **Real-time Sync**: WebSocket-based real-time updates

### Technical Improvements
1. **Kotlin Multiplatform**: Share code with iOS/Web
2. **Compose Multiplatform**: Cross-platform UI
3. **KSP**: Move from KAPT to KSP for annotation processing
4. **Coroutines Flow**: Enhanced reactive programming
5. **Paging 3**: Efficient pagination for large datasets
6. **WorkManager**: Background task scheduling

---

## Maintenance & Deployment

### Version Control
- **Git**: Version control system
- **Branch Strategy**: Feature branches, main branch for releases
- **CI/CD**: (implied) Automated build and testing

### Release Process
1. Version bump in build.gradle.kts
2. Update changelog
3. Build release APK/AAB
4. Test on multiple devices
5. Deploy to Play Store (internal testing first)
6. Monitor crash reports
7. Gather user feedback

### Monitoring
- **Crashlytics**: (implied) Crash reporting
- **Analytics**: (implied) Usage analytics
- **Logging**: Timber logs for debugging

---

## Conclusion

MapSupervision is a sophisticated Android application for infrastructure construction supervision, featuring:

- **Clean Architecture**: Well-structured, maintainable codebase
- **Modern UI**: Jetpack Compose with Material3 design
- **Comprehensive Features**: Map visualization, project management, photo documentation, progress tracking, reporting
- **AI Integration**: Smart features with fallback logic
- **Robust Data Handling**: Multi-format import, deduplication, safety checks
- **Performance Optimized**: Efficient database operations, lazy loading, signature-based updates
- **Extensible**: Modular design allows easy feature additions
- **User-Friendly**: Vietnamese localization, intuitive UI, helpful error messages

The application demonstrates best practices in Android development, including dependency injection, reactive programming, and separation of concerns. The AI integration with fallback logic ensures reliability even when external services are unavailable.

---

## Appendix: File Structure Reference

### Complete File Tree

```
MAPSUPERVISION/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/mapsupervision/app/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── MapSupervisionApplication.kt
│   │   │   │   ├── ui/theme/
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   ├── Shape.kt
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   └── workspace/
│   │   │   │       ├── CombineFilesDialog.kt
│   │   │   │       ├── DashboardHubScreen.kt
│   │   │   │       ├── DataHubScreen.kt
│   │   │   │       ├── ExcelMappingDialog.kt
│   │   │   │       ├── ImportSafetyGuards.kt
│   │   │   │       ├── MapHubScreen.kt
│   │   │   │       ├── ProgressHubScreen.kt
│   │   │   │       └── WorkspaceViewModel.kt
│   │   │   └── test/
│   │   │       └── java/com/mapsupervision/app/
│   │   │           ├── SmokeTest.kt
│   │   │           └── workspace/
│   │   │               ├── DedupAiSummaryFormatterTest.kt
│   │   │               ├── DedupBatchDecisionAdvisorTest.kt
│   │   │               ├── DedupCoordMatchPolicyTest.kt
│   │   │               ├── DedupQualityAdvisorTest.kt
│   │   │               ├── DedupQualityScorerTest.kt
│   │   │               ├── DedupRiskSummaryFormatterTest.kt
│   │   │               ├── DedupSignalPolicyTest.kt
│   │   │               ├── ImportSafetyGuardsTest.kt
│   │   │               └── WorkspaceDedupUtilsTest.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── core/
│   └── src/main/java/com/mapsupervision/core/
│       ├── coroutines/DispatcherProvider.kt
│       ├── error/AppExceptions.kt
│       ├── logging/AppLogger.kt
│       └── result/AppResult.kt
├── domain/
│   └── src/
│       ├── main/java/com/mapsupervision/domain/
│       │   ├── ai/
│       │   │   ├── AiContracts.kt
│       │   │   └── AiOrchestrator.kt
│       │   ├── model/
│       │   │   ├── DailyLog.kt
│       │   │   ├── GisNode.kt
│       │   │   ├── GisRoute.kt
│       │   │   ├── ImportedFile.kt
│       │   │   ├── MaterialProgress.kt
│       │   │   ├── NodeProgress.kt
│       │   │   ├── Project.kt
│       │   │   └── SitePhoto.kt
│       │   ├── repository/
│       │   │   ├── ActiveProjectRepository.kt
│       │   │   ├── AiRepository.kt
│       │   │   ├── DailyLogRepository.kt
│       │   │   ├── GisRepository.kt
│       │   │   ├── ImportedFileRepository.kt
│       │   │   ├── MaterialProgressRepository.kt
│       │   │   ├── PhotoRepository.kt
│       │   │   ├── ProgressRepository.kt
│       │   │   └── ProjectRepository.kt
│       │   ├── usecase/CreateProjectUseCase.kt
│       │   └── util/
│       │       ├── Haversine.kt
│       │       └── StringSimilarity.kt
│       └── test/
│           └── java/com/mapsupervision/domain/ai/
│               └── AiOrchestratorTest.kt
├── data/
│   └── src/main/java/com/mapsupervision/data/
│       ├── DataModule.kt
│       ├── db/
│       │   ├── MapSupervisionDatabase.kt
│       │   ├── dao/
│       │   │   ├── DailyLogDao.kt
│       │   │   ├── GisNodeDao.kt
│       │   │   ├── GisRouteDao.kt
│       │   │   ├── ImportedFileDao.kt
│       │   │   ├── MaterialProgressDao.kt
│       │   │   ├── NodeProgressDao.kt
│       │   │   ├── ProjectDao.kt
│       │   │   └── SitePhotoDao.kt
│       │   └── entity/
│       │       ├── DailyLogEntity.kt
│       │       ├── GisNodeEntity.kt
│       │       ├── GisRouteEntity.kt
│       │       ├── ImportedFileEntity.kt
│       │       ├── MaterialProgressEntity.kt
│       │       ├── NodeProgressEntity.kt
│       │       ├── ProjectEntity.kt
│       │       └── SitePhotoEntity.kt
│       └── repository/
│           ├── FieldRepositories.kt
│           ├── GeminiRepositoryImpl.kt
│           ├── GisRepositoryImpl.kt
│           ├── ImportedFileRepositoryImpl.kt
│           └── ProjectRepositoryImpl.kt
├── storage/
│   └── src/main/java/com/mapsupervision/storage/
│       ├── ActiveProjectRepositoryImpl.kt
│       ├── ProjectPackageService.kt
│       ├── ProjectStorageManager.kt
│       ├── StorageBindModule.kt
│       ├── StorageModule.kt
│       ├── crypto/ProjectCryptoManager.kt
│       ├── importer/
│       │   ├── DocxParser.kt
│       │   ├── KmlParser.kt
│       │   ├── KmzParser.kt
│       │   └── UserFileImportService.kt
│       └── test/
│           └── java/com/mapsupervision/storage/importer/
│               └── KmlKmzParserTest.kt
├── gis/
│   └── src/main/java/com/mapsupervision/gis/
│       ├── style/GisStyleBuilder.kt
│       └── ui/
│           ├── GisScreen.kt
│           └── GisViewModel.kt
├── gis-maplibre/
│   └── src/main/java/com/mapsupervision/gis/maplibre/
│       └── MapBridgeInstaller.kt
├── photo/
│   └── src/main/java/com/mapsupervision/photo/
│       ├── location/PhotoLocationProvider.kt
│       ├── ui/
│       │   ├── PhotoScreen.kt
│       │   └── PhotoViewModel.kt
│       └── worker/PhotoPipelineService.kt
├── photo-lite/
│   └── src/main/java/com/mapsupervision/photo/ui/
│       └── PhotoScreen.kt
├── project/
│   └── src/main/java/com/mapsupervision/project/ui/
│       ├── ProjectScreen.kt
│       └── ProjectViewModel.kt
├── reporting/
│   └── src/main/java/com/mapsupervision/reporting/
│       ├── pdf/PdfReportGenerator.kt
│       └── ui/
│           ├── ReportingScreen.kt
│           └── ReportingViewModel.kt
├── timeline/
│   └── src/main/java/com/mapsupervision/timeline/ui/
│       ├── TimelineScreen.kt
│       └── TimelineViewModel.kt
├── docs/
│   └── knowledge-graph.md (this file)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── .gitignore
├── AGENTS.md
├── PROJECT_DOCUMENTATION.md
├── skill.md
└── [Python utility scripts - gitignored]
```

---

**Document Version**: 1.0  
**Last Updated**: 2026-05-31  
**Generated By**: Cascade AI Assistant  
**Project**: MapSupervision - Infrastructure Construction Supervision System
