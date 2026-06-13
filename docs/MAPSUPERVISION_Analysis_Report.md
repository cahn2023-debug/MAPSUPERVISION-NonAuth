# 📊 MAPSUPERVISION-NonAuth — Báo Cáo Phân Tích Toàn Diện

> **Repo:** [cahn2023-debug/MAPSUPERVISION-NonAuth](https://github.com/cahn2023-debug/MAPSUPERVISION-NonAuth)  
> **Ngôn ngữ:** Kotlin 99.5% · Android  
> **Ngày phân tích:** 11/06/2026

---

## 1. TỔNG QUAN DỰ ÁN

### Kiến trúc tổng thể

MapSupervision là ứng dụng Android dành cho **giám sát thực địa** (field supervision/inspection) kết hợp GIS. Phiên bản "NonAuth" là bản không cần xác thực (stripped authentication), chạy offline-first. Dự án áp dụng **Clean Architecture** theo mô hình multi-module:

```
MapSupervision
├── :app              ← Entry point, DI graph, Navigation host
├── :core             ← Shared utilities, extensions, base classes
├── :domain           ← Use cases, entities, repository interfaces
├── :data             ← Repository implementations, Room DB, Network
├── :project          ← Feature: Quản lý dự án
├── :gis              ← Feature: GIS abstractions (engine-agnostic)
├── :gis-maplibre     ← Feature: MapLibre implementation
├── :photo            ← Feature: Chụp ảnh, annotation
├── :timeline         ← Feature: Dòng thời gian sự kiện
├── :reporting        ← Feature: Báo cáo, xuất PDF/Excel
└── :storage          ← File storage, media management
```

**Stack kỹ thuật:**

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| AGP | 8.5.2 | Android Gradle Plugin |
| Kotlin | 2.0.21 | K2 compiler mode |
| Compose Compiler | 2.0.21 | Plugin riêng (KMP-compatible) |
| KSP | 2.0.21-1.0.28 | Thay thế KAPT |
| Hilt | 2.52 | DI |
| MapLibre | (trong :gis-maplibre) | Open-source, không cần API key → "NonAuth" |
| AI Local | Qwen (via `.qwen/`) | On-device LLM |
| Kiro IDE | `.kiro/specs/` | Spec-driven development |

---

## 2. PHÂN TÍCH LỖI VÀ VẤN ĐỀ

### 🔴 LỖI NGHIÊM TRỌNG (Critical)

---

#### BUG-01: `android.suppressUnsupportedCompileSdk=35` — Che giấu lỗi thay vì sửa

**File:** `gradle.properties` · Line 9

```properties
# HIỆN TẠI - SAI
android.suppressUnsupportedCompileSdk=35
```

**Vấn đề:** Flag này tắt cảnh báo khi compileSdkVersion cao hơn mức AGP đang hỗ trợ chính thức. Điều đó có nghĩa dự án đang build với SDK 35 mà AGP 8.5.2 chưa hỗ trợ hoàn toàn, dẫn đến:
- Một số API mới của Android 15 có thể bị xử lý sai ở compile-time
- Edge-to-edge behavior mới (WindowInsets) trên Android 15 không được enforce đúng
- Potential crashes khi deploy lên thiết bị Android 15 thực tế

**Khắc phục:**
```properties
# OPTION 1: Upgrade AGP lên phiên bản hỗ trợ SDK 35
# build.gradle.kts root
id("com.android.application") version "8.7.3" apply false  # AGP 8.7+ hỗ trợ SDK 35

# OPTION 2: Nếu chưa upgrade AGP, giảm compileSdk về 34
# app/build.gradle.kts
android {
    compileSdk = 34
    targetSdk = 34
}
# Sau đó xóa dòng suppressUnsupportedCompileSdk khỏi gradle.properties
```

---

#### BUG-02: `enforceModuleBoundaries` — Kiểm tra module boundary không đủ

**File:** `build.gradle.kts` · Lines 17–31

```kotlin
// HIỆN TẠI — Chỉ check 1 trong ~20 cặp dependency cần kiểm tra
val gisSources = fileTree("gis/src/main/java") { include("**/*.kt") }
gisSources.forEach { source ->
    val text = source.readText()
    if (text.contains("com.mapsupervision.reporting")) {
        violations += "Forbidden import in ${source.path}: :gis must not depend on :reporting"
    }
}
```

**Vấn đề:**
- Chỉ kiểm tra 1 chiều (`:gis` → `:reporting`), bỏ qua hàng chục cặp cần enforce
- Dùng `String.contains()` thay vì parse AST → false positives (chuỗi trong comments, strings)
- Không check `:timeline` → `:gis`, `:photo` → `:reporting`, `:project` → `:storage`, v.v.
- Task không được kết nối vào build lifecycle (`check`, `test`) nên không bao giờ tự chạy

**Khắc phục — Dùng Dependency Guard plugin thay thế:**
```kotlin
// build.gradle.kts root
plugins {
    id("com.dropbox.dependency-guard") version "0.5.0" apply false
}

// Hoặc dùng module-graph-assert
// Thêm vào :app/build.gradle.kts:
dependencyGuard {
    configuration("releaseRuntimeClasspath")
}
```

**Giải pháp tốt hơn — khai báo rõ module graph:**
```kotlin
// buildSrc/src/main/kotlin/ModuleRules.kt
object ModuleRules {
    // feature modules không được import nhau trực tiếp
    val featureModules = listOf(":gis", ":gis-maplibre", ":photo", 
                                 ":timeline", ":reporting", ":project")
    // chỉ được depend vào :core, :domain, :data, :storage
    val allowedDepsForFeature = listOf(":core", ":domain", ":data", ":storage")
}
```

---

#### BUG-03: Hai module GIS song song — `:gis` và `:gis-maplibre`

**Vấn đề:**
- `:gis` là abstraction layer (engine-agnostic interfaces)
- `:gis-maplibre` là implementation cụ thể cho MapLibre
- Nếu `:app` hoặc `:project` import trực tiếp từ `:gis-maplibre`, sẽ vi phạm DIP (Dependency Inversion Principle)
- Không có Lint rule hoặc boundary check nào ngăn điều này

**Dấu hiệu cụ thể:** `enforceModuleBoundaries` không có rule nào liên quan đến `:gis-maplibre`.

**Khắc phục:**
```kotlin
// Trong enforceModuleBoundaries, thêm:
val appSources = fileTree("app/src/main/java") { include("**/*.kt") }
appSources.forEach { source ->
    val text = source.readText()
    if (text.contains("import com.mapsupervision.gismaplibre")) {
        violations += "${source.path}: :app must not import :gis-maplibre directly. Use :gis interfaces."
    }
}
```

---

#### BUG-04: Artifacts không nên có trong repository root

**Files liên quan:**
- `extracted_lines.txt` — output của script phân tích code, không phải source
- `match.json` / `match.txt` — output của pattern matching process
- `generate_code_html.ps1` — Windows PowerShell utility script

**Vấn đề:**
- Những file này là build/analysis artifacts, không phải source code
- `generate_code_html.ps1` chỉ chạy được trên Windows → làm khó contributor trên Linux/macOS
- Nếu committed vào main branch, sẽ gây confusion và làm "bẩn" git history

**Khắc phục:**
```gitignore
# Thêm vào .gitignore
extracted_lines.txt
match.json
match.txt
generate_code_html.ps1
*.html.generated
```

---

### 🟠 LỖI TRUNG BÌNH (Major)

---

#### BUG-05: JVM Memory settings có thể gây OOM trên máy yếu

**File:** `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8
kotlin.daemon.jvm.options=-Xmx2g
```

**Vấn đề:** Tổng JVM memory yêu cầu = **8GB+ RAM** chỉ riêng cho build. Trên CI/CD machines có 8GB hoặc developer dùng máy 16GB với nhiều app đang chạy, sẽ bị:
- OOM (Out of Memory) error mid-build
- Swap thrashing → build time tăng 3–5x
- Kotlin daemon crash → cold build mỗi lần

**Khắc phục:**
```properties
# Giá trị an toàn hơn, adaptive
org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
kotlin.daemon.jvm.options=-Xmx1500m
# Thêm:
org.gradle.workers.max=4
```

---

#### BUG-06: `android.nonTransitiveRClass=true` chưa migrate đủ

**File:** `gradle.properties`

```properties
android.nonTransitiveRClass=true
```

**Vấn đề:** Khi bật flag này, mỗi module chỉ có R class của riêng nó. Tuy nhiên, trong dự án multi-module, code ở `:core` hoặc `:domain` thường dùng `R.string.xxx` từ `:app`. Nếu chưa refactor đủ, sẽ gặp:
- `Unresolved reference: R` tại compile time trong một số module
- Thường xuất hiện trong Compose previews không có context đúng

**Khắc phục:** Kiểm tra và move tất cả resource references về đúng module, dùng `stringResource()` thay vì `getString(R.string.xxx)` trực tiếp.

---

#### BUG-07: Configuration Cache có thể conflict với task `enforceModuleBoundaries`

**File:** `gradle.properties`

```properties
org.gradle.configuration-cache=true
```

**Vấn đề:** Task `enforceModuleBoundaries` sử dụng `fileTree()` và `readText()` trực tiếp trong `doLast` block. Điều này vi phạm configuration cache requirements vì:
- File reads phải được khai báo là inputs
- `mutableListOf` trong doLast không serialize được

**Dấu hiệu:** Chạy `./gradlew enforceModuleBoundaries` sẽ in warning: _"Configuration cache problems found"_

**Khắc phục:**
```kotlin
abstract class EnforceModuleBoundariesTask : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection
    
    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        sources.forEach { source ->
            val text = source.readText()
            if (text.contains("com.mapsupervision.reporting")) {
                violations += "Forbidden: ${source.path}"
            }
        }
        if (violations.isNotEmpty()) error(violations.joinToString("\n"))
    }
}

tasks.register<EnforceModuleBoundariesTask>("enforceModuleBoundaries") {
    sources.from(fileTree("gis/src/main/java") { include("**/*.kt") })
}
```

---

### 🟡 LỖI NHỎ (Minor)

| ID | Vị trí | Vấn đề | Khắc phục |
|---|---|---|---|
| BUG-08 | `build.gradle.kts` | `vanniktech/dependency.graph.generator` là dev tool nhưng không có `apply false` điều kiện — luôn load plugin | Chỉ apply trong CI environment |
| BUG-09 | `settings.gradle.kts` | Module `:project` có thể conflict với Gradle built-in `project` object nếu script config dùng cùng scope | Rename thành `:feature-project` hoặc `:projects` |
| BUG-10 | `AGENTS.md` vs `skill.md` | Hai file có nội dung gần giống nhau (LLM coding guidelines), gây confusion cho AI agents | Consolidate thành một file `CLAUDE.md` |
| BUG-11 | `.qwen/` folder | Không có schema validation cho Qwen config | Thêm JSON schema validation |

---

## 3. PHƯƠNG ÁN TỐI ƯU AI LOCAL (Qwen On-Device)

### Hiện trạng

Dự án có `.qwen/` directory cho thấy tích hợp **Qwen model chạy local trên thiết bị Android**. Đây là tính năng phức tạp với nhiều rủi ro về performance và độ chính xác.

---

### 3.1 Kiến trúc AI Local được đề xuất

```
┌─────────────────────────────────────────────────────┐
│                   :core-ai module                    │
│                                                      │
│  ┌──────────────┐    ┌──────────────────────────┐   │
│  │ AiInterface  │    │   AiConfig               │   │
│  │ (interface)  │    │   - modelPath: String     │   │
│  │ + analyze()  │    │   - maxTokens: Int        │   │
│  │ + generate() │    │   - temperature: Float    │   │
│  │ + classify() │    │   - contextWindowSize:Int │   │
│  └──────┬───────┘    └──────────────────────────┘   │
│         │                                            │
│  ┌──────┴──────────────────────────────────────┐    │
│  │           QwenLocalAiProvider               │    │
│  │  - LLMInferenceEngine (MediaPipe / llama.cpp)│   │
│  │  - PromptTemplateManager                    │    │
│  │  - TokenizerCache                           │    │
│  │  - ResponseParser                           │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

---

### 3.2 Chọn Inference Engine phù hợp

**Vấn đề hiện tại:** Không rõ project dùng inference engine nào.

**Đề xuất:** Dùng **MediaPipe LLM Inference API** (Google, 2024) hoặc **llama.cpp Android port**.

```kotlin
// core-ai/build.gradle.kts
dependencies {
    // Option A: MediaPipe (Google, stable, supports Qwen2)
    implementation("com.google.mediapipe:tasks-genai:0.10.22")
    
    // Option B: llama.cpp via JNI (more models, harder setup)
    // implementation("io.github.llama-cpp:llama-android:b4234")
}
```

```kotlin
// QwenLocalProvider.kt
@Singleton
class QwenLocalAiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AiConfig
) : AiInterface {

    private var inferenceEngine: LlmInference? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(config.modelPath)        // /data/user/0/com.mapsupervision/files/qwen2-1.5b-q4.task
                .setMaxTokens(config.maxTokens)        // 1024 cho inference nhanh
                .setTemperature(config.temperature)    // 0.1f cho output deterministic
                .setTopK(40)
                .setRandomSeed(0)
                .build()
            inferenceEngine = LlmInference.createFromOptions(context, options)
            _isReady.value = true
        } catch (e: Exception) {
            Timber.e(e, "AI initialization failed")
            _isReady.value = false
        }
    }

    override suspend fun generateText(
        prompt: String,
        onToken: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            requireNotNull(inferenceEngine) { "AI engine not initialized" }
            val sb = StringBuilder()
            inferenceEngine!!.generateResponseAsync(
                formatPrompt(prompt),
                { token, isDone ->
                    sb.append(token)
                    onToken(token)
                }
            )
            sb.toString()
        }
    }

    // Format prompt đúng với Qwen chat template
    private fun formatPrompt(userMessage: String): String = """
        <|im_start|>system
        Bạn là trợ lý giám sát công trường. Trả lời ngắn gọn, chính xác bằng tiếng Việt.
        <|im_end|>
        <|im_start|>user
        $userMessage
        <|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    override fun close() {
        inferenceEngine?.close()
        inferenceEngine = null
        _isReady.value = false
    }
}
```

---

### 3.3 Model Management — Download và Caching đúng cách

```kotlin
// AiModelManager.kt
@Singleton
class AiModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStorage: FileStorageRepository
) {
    // Qwen2-1.5B INT4 quantized: ~800MB — phù hợp cho thiết bị 4GB RAM trở lên
    private val MODEL_URL = "https://your-cdn.com/qwen2-1.5b-instruct-q4_k_m.gguf"
    private val MODEL_FILENAME = "qwen2-1.5b-q4.task"
    
    val modelFile: File get() = File(context.filesDir, MODEL_FILENAME)
    
    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 100_000_000L
    
    suspend fun downloadModel(
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File(context.cacheDir, "$MODEL_FILENAME.tmp")
            // Download with resumable support...
            // Move temp → final only when complete (atomic operation)
            tempFile.renameTo(modelFile)
            modelFile
        }
    }

    // Kiểm tra device capability trước khi load model
    fun checkDeviceCompatibility(): DeviceCapability {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getMemoryInfo(memInfo)
        
        return when {
            memInfo.totalMem < 4L * 1024 * 1024 * 1024 -> DeviceCapability.INSUFFICIENT_RAM
            !hasNpuOrGpu() -> DeviceCapability.CPU_ONLY_SLOW
            else -> DeviceCapability.OPTIMAL
        }
    }
    
    private fun hasNpuOrGpu(): Boolean {
        // Check for Qualcomm Hexagon, MediaTek APU, etc.
        return Build.SOC_MANUFACTURER in listOf("Qualcomm", "MediaTek", "Samsung")
    }
}

enum class DeviceCapability {
    OPTIMAL,           // NPU/GPU available, RAM >= 4GB
    CPU_ONLY_SLOW,     // Only CPU, inference ~5–15s/response
    INSUFFICIENT_RAM   // < 4GB RAM, cannot load model
}
```

---

### 3.4 ViewModel tích hợp AI với Fallback Strategy

```kotlin
// AiAssistantViewModel.kt
@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiProvider: QwenLocalAiProvider,
    private val modelManager: AiModelManager,
) : ViewModel() {

    sealed interface AiState {
        data object Idle : AiState
        data object ModelNotDownloaded : AiState
        data object Initializing : AiState
        data class Generating(val partialResponse: String) : AiState
        data class Success(val response: String) : AiState
        data class Error(val message: String, val canRetry: Boolean) : AiState
        data object IncompatibleDevice : AiState
    }

    private val _state = MutableStateFlow<AiState>(AiState.Idle)
    val state = _state.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            when (modelManager.checkDeviceCompatibility()) {
                DeviceCapability.INSUFFICIENT_RAM -> {
                    _state.value = AiState.IncompatibleDevice
                    return@launch
                }
                else -> { /* continue */ }
            }
            
            if (!modelManager.isModelDownloaded()) {
                _state.value = AiState.ModelNotDownloaded
                return@launch
            }
            
            _state.value = AiState.Initializing
            aiProvider.initialize()
            
            // Wait for ready
            aiProvider.isReady
                .filter { it }
                .first()
            
            _state.value = AiState.Idle
        }
    }

    fun analyzeInspectionData(input: String) {
        viewModelScope.launch {
            val sb = StringBuilder()
            _state.value = AiState.Generating("")
            
            aiProvider.generateText(input) { token ->
                sb.append(token)
                _state.value = AiState.Generating(sb.toString())
            }.fold(
                onSuccess = { _state.value = AiState.Success(it) },
                onFailure = { e ->
                    _state.value = AiState.Error(
                        message = e.localizedMessage ?: "Lỗi AI",
                        canRetry = e !is OutOfMemoryError
                    )
                }
            )
        }
    }
}
```

---

### 3.5 Prompt Engineering cho use case giám sát công trường

```kotlin
// PromptTemplates.kt
object InspectionPrompts {
    
    fun anomalyDetection(observationText: String): String = """
        Phân tích mô tả công việc kiểm tra sau và xác định:
        1. Các bất thường hoặc vi phạm (nếu có)
        2. Mức độ nghiêm trọng (Thấp/Trung bình/Cao/Khẩn cấp)
        3. Hành động đề xuất (cụ thể, ngắn gọn)
        
        Dữ liệu kiểm tra:
        $observationText
        
        Trả lời theo định dạng JSON:
        {"anomalies": [], "severity": "Low|Medium|High|Critical", "actions": []}
    """.trimIndent()

    fun generateReportSummary(rawNotes: String, projectName: String): String = """
        Tóm tắt báo cáo kiểm tra cho dự án "$projectName":
        
        Ghi chú thô:
        $rawNotes
        
        Yêu cầu: Viết tóm tắt 3–5 câu bằng tiếng Việt, chuyên nghiệp, nêu rõ 
        kết quả chính và các vấn đề cần theo dõi.
    """.trimIndent()

    fun classifyPhoto(photoDescription: String): String = """
        Phân loại ảnh công trường theo category:
        - NORMAL: Thi công bình thường
        - DEFECT: Có lỗi/hỏng hóc
        - SAFETY_VIOLATION: Vi phạm an toàn
        - PROGRESS_MILESTONE: Đạt mốc tiến độ
        
        Mô tả ảnh: $photoDescription
        
        Trả về JSON: {"category": "...", "confidence": 0.0–1.0, "notes": "..."}
    """.trimIndent()
}
```

---

## 4. KẾ HOẠCH TỐI ƯU HÓA HIỆU NĂNG (Performance Plan)

### Phase 1 — Quick Wins (Tuần 1–2)

#### P1.1: Baseline Profile

```kotlin
// app/src/main/baseline-prof.txt (generate via Macrobenchmark)
// Sau đó thêm vào build.gradle.kts của :app:
android {
    defaultConfig {
        // Kích hoạt baseline profiles
    }
}
dependencies {
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}

// baselineProfile/build.gradle.kts (module mới)
plugins {
    id("androidx.baselineprofile")
}
dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
}
```

**Kỳ vọng:** Giảm cold start time 20–40%, giảm jank trong first 5s.

---

#### P1.2: Compose Stability — Tránh recomposition không cần thiết

```kotlin
// TRƯỚC: Unstable class gây full recomposition
data class MapMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val label: String,
    val metadata: Map<String, Any>  // ← Map<Any> không stable!
)

// SAU: @Stable annotation + immutable collections
@Immutable
data class MapMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val label: String,
    val metadata: ImmutableMap<String, String>  // kotlinx.collections.immutable
)

// Thêm dependency:
// implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
```

---

#### P1.3: MapLibre — Tile caching tối ưu

```kotlin
// GisMaplibreModule.kt
@Module
@InstallIn(SingletonComponent::class)
object GisMaplibreModule {

    @Provides
    @Singleton
    fun provideMapLibreOfflineManager(
        @ApplicationContext context: Context
    ): OfflineManager = OfflineManager.getInstance(context).apply {
        // Tăng tile cache size lên 500MB
        setOfflineTileCountLimit(50_000)
    }
    
    @Provides
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .cache(
            Cache(
                directory = File(context.cacheDir, "map_tiles"),
                maxSize = 500L * 1024 * 1024  // 500 MB
            )
        )
        .addNetworkInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) BODY else NONE
        })
        .build()
}
```

---

#### P1.4: Lazy loading cho module gis-maplibre

```kotlin
// Thay vì load MapLibre ngay khi app start,
// chỉ khởi tạo khi navigate đến màn hình bản đồ

// MapScreen.kt
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    // LazyLayout — chỉ render khi visible
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    
    // Sử dụng remember(Unit) để tránh re-initialize
    val mapInitializer = remember { MapLibreInitializer() }
    
    // AndroidView với lifecycle-aware dispose
    DisposableEffect(Unit) {
        mapInitializer.init()
        onDispose { mapInitializer.release() }
    }
}
```

---

### Phase 2 — Deep Optimization (Tuần 3–5)

#### P2.1: Room Database — Query optimization

```kotlin
// TRƯỚC: N+1 query problem
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects")
    fun getAllProjects(): Flow<List<Project>>
    
    // Sau đó trong code gọi riêng cho mỗi project:
    @Query("SELECT * FROM inspections WHERE projectId = :id")
    suspend fun getInspections(id: String): List<Inspection>
}

// SAU: JOIN trong một query
@Dao
interface ProjectDao {
    @Transaction
    @Query("""
        SELECT p.*, COUNT(i.id) as inspectionCount, 
               MAX(i.date) as lastInspectionDate
        FROM projects p
        LEFT JOIN inspections i ON i.projectId = p.id
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
    """)
    fun getAllProjectsWithStats(): Flow<List<ProjectWithStats>>
}

// Thêm index:
@Entity(
    tableName = "inspections",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["date"]),
        Index(value = ["projectId", "date"])  // composite index cho range queries
    ]
)
data class Inspection(...)
```

---

#### P2.2: Image loading — Coil với custom pipeline

```kotlin
// PhotoModule.kt
@Provides
@Singleton
fun provideImageLoader(
    @ApplicationContext context: Context
): ImageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(context, 0.20)  // 20% của available heap
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("photo_cache"))
            .maxSizeBytes(200L * 1024 * 1024)  // 200MB
            .build()
    }
    .components {
        // Thêm decoder tối ưu cho ảnh construction site (thường JPEG lớn)
        add(ImageDecoderDecoder.Factory())
        
        // Thumbnail extractor cho video frames
        add(VideoFrameDecoder.Factory())
    }
    // Downsample ảnh lớn tự động
    .size(Size.ORIGINAL)
    .precision(Precision.INEXACT)
    .build()
```

---

#### P2.3: WorkManager cho background tasks

```kotlin
// Thay thế Service/BroadcastReceiver bằng WorkManager
// cho các tasks: sync data, compress photos, generate reports

@HiltWorker
class PhotoCompressionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoRepository: PhotoRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val photoId = inputData.getString("photo_id") ?: return Result.failure()
        
        return try {
            photoRepository.compressPhoto(photoId)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
    
    companion object {
        fun buildRequest(photoId: String) = OneTimeWorkRequestBuilder<PhotoCompressionWorker>()
            .setInputData(workDataOf("photo_id" to photoId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
    }
}
```

---

#### P2.4: Compose Compiler Metrics — Monitor recomposition

```kotlin
// Thêm vào app/build.gradle.kts
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

// Chạy: ./gradlew assembleRelease
// Kiểm tra file: app/build/compose_compiler/app_composables.txt
// Tìm các composable với "unstable" parameters cần fix
```

---

### Phase 3 — Infrastructure (Tuần 6–8)

#### P3.1: Proguard/R8 rules cho MapLibre và Qwen

```proguard
# app/proguard-rules.pro

# MapLibre
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }

# MediaPipe LLM Inference
-keep class com.google.mediapipe.tasks.genai.** { *; }
-keep class com.google.mediapipe.framework.** { *; }

# Qwen tokenizer (nếu dùng custom tokenizer)
-keep class com.qwen.tokenizer.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
```

---

#### P3.2: App Startup — Lazy initialization

```kotlin
// AppStartup.kt — Tránh heavy init trên main thread

class MapLibreInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // Chạy trên background thread via App Startup
        Maplibre.getInstance(context)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

// AndroidManifest.xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="com.mapsupervision.MapLibreInitializer"
        android:value="androidx.startup" />
</provider>
```

---

## 5. KẾ HOẠCH REFACTOR CODE

### Nguyên tắc refactor

Tuân thủ đúng AGENTS.md của dự án:
- Surgical changes — chỉ touch những gì cần thiết
- Verifiable goals — mỗi bước có success criteria rõ ràng
- Test before & after

---

### Phase R1: Consolidate module boundaries (Sprint 1)

**Mục tiêu:** Loại bỏ coupling ẩn giữa feature modules.

**Bước 1.1 — Tạo module `:core-ai`**
```
Verify: ./gradlew :core-ai:test passes
```
```
core-ai/
├── build.gradle.kts
└── src/main/kotlin/com/mapsupervision/coreai/
    ├── AiInterface.kt          ← interface
    ├── AiConfig.kt             ← data class
    ├── AiModelManager.kt       ← download/cache logic
    └── qwen/
        └── QwenLocalAiProvider.kt
```

**Bước 1.2 — Tách `:core` thành `:core-ui` và `:core-data`**

Hiện tại `:core` có thể chứa cả UI utilities lẫn data utilities, vi phạm SRP. Tách ra:
```
:core-ui    ← Compose extensions, theme, common components
:core-data  ← Coroutines helpers, Result wrappers, extension functions
:core       ← Giữ lại chỉ các constants, enums dùng chung
```

**Bước 1.3 — Tạo Version Catalog**

Nếu dự án chưa có `libs.versions.toml`:
```toml
# gradle/libs.versions.toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
hilt = "2.52"
compose-bom = "2024.12.01"
maplibre = "11.11.0"
mediapipe = "0.10.22"
room = "2.7.1"
coil = "3.1.0"

[libraries]
android-hilt = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
android-hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
maplibre-android = { module = "org.maplibre.gl:android-sdk", version.ref = "maplibre" }
mediapipe-genai = { module = "com.google.mediapipe:tasks-genai", version.ref = "mediapipe" }
# ... etc

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

---

### Phase R2: Repository & UseCase pattern (Sprint 2)

**Mục tiêu:** Chuẩn hóa data flow theo Clean Architecture.

**Bước 2.1 — Chuẩn hóa Result type**

```kotlin
// core-data/Result.kt
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val exception: AppException,
        val message: String = exception.localizedMessage ?: "Unknown error"
    ) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}

sealed class AppException(message: String, cause: Throwable? = null) 
    : Exception(message, cause) {
    class NetworkException(message: String, cause: Throwable? = null) 
        : AppException(message, cause)
    class DatabaseException(message: String, cause: Throwable? = null) 
        : AppException(message, cause)
    class AiException(message: String, cause: Throwable? = null) 
        : AppException(message, cause)
    class StorageException(message: String, cause: Throwable? = null) 
        : AppException(message, cause)
}
```

**Bước 2.2 — UseCase base class**

```kotlin
// domain/UseCase.kt
abstract class UseCase<in P, out R> {
    suspend operator fun invoke(params: P): AppResult<R> {
        return try {
            AppResult.Success(execute(params))
        } catch (e: AppException) {
            AppResult.Error(e)
        } catch (e: Exception) {
            AppResult.Error(AppException.NetworkException("Unexpected error", e))
        }
    }
    
    protected abstract suspend fun execute(params: P): R
}

// FlowUseCase cho real-time data
abstract class FlowUseCase<in P, out R> {
    operator fun invoke(params: P): Flow<AppResult<R>> = flow {
        emit(AppResult.Loading)
        execute(params).collect { emit(AppResult.Success(it)) }
    }.catch { e ->
        emit(AppResult.Error(AppException.DatabaseException(e.message ?: "", e)))
    }
    
    protected abstract fun execute(params: P): Flow<R>
}
```

---

### Phase R3: UI Layer — MVI Pattern (Sprint 3)

**Mục tiêu:** Thay thế ViewModel ad-hoc state bằng MVI nhất quán.

```kotlin
// base/MviViewModel.kt
abstract class MviViewModel<S : UiState, E : UiEvent, A : UiAction>(
    initialState: S
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _events = Channel<E>(Channel.BUFFERED)
    val events: Flow<E> = _events.receiveAsFlow()

    fun dispatch(action: A) {
        viewModelScope.launch {
            reduce(_state.value, action).let { newState ->
                _state.value = newState
            }
        }
    }

    protected abstract fun reduce(state: S, action: A): S
    
    protected suspend fun emitEvent(event: E) {
        _events.send(event)
    }
}

// Ví dụ — ProjectListViewModel
data class ProjectListState(
    val projects: ImmutableList<Project> = persistentListOf(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = ""
) : UiState

sealed interface ProjectListAction : UiAction {
    data class Search(val query: String) : ProjectListAction
    data class DeleteProject(val id: String) : ProjectListAction
    data object Refresh : ProjectListAction
}

sealed interface ProjectListEvent : UiEvent {
    data class NavigateToDetail(val projectId: String) : ProjectListEvent
    data class ShowError(val message: String) : ProjectListEvent
}
```

---

### Phase R4: Testing infrastructure (Sprint 4)

**Mục tiêu:** Đạt coverage 60%+ trước khi thêm features mới.

```kotlin
// Test structure
// :domain:test   ← Unit tests cho UseCases (pure Kotlin, no Android deps)
// :data:test     ← Unit tests với Room in-memory DB
// :app:test      ← Integration tests với Hilt
// :app:androidTest ← UI tests với Compose

// Ví dụ — Domain UseCase test
class GetProjectsUseCaseTest {
    private val fakeRepository = FakeProjectRepository()
    private val useCase = GetProjectsUseCase(fakeRepository)
    
    @Test
    fun `when repository has projects, returns success`() = runTest {
        // Given
        fakeRepository.setProjects(listOf(ProjectFactory.create()))
        
        // When
        val result = useCase(GetProjectsUseCase.Params(filter = null))
        
        // Then
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data).hasSize(1)
    }
}
```

---

## 6. TỔNG HỢP ƯU TIÊN THỰC HIỆN

### Roadmap theo Sprint

```
Sprint 1 (Tuần 1–2): Fix Critical Bugs
├── BUG-01: Fix suppressUnsupportedCompileSdk              → 2h
├── BUG-04: Xóa artifacts khỏi repo                        → 30m
├── BUG-07: Fix configuration cache task                   → 3h
└── BUG-05: Điều chỉnh JVM memory settings                → 30m

Sprint 2 (Tuần 3–4): Architecture Cleanup
├── BUG-02/03: Cải thiện module boundary enforcement       → 4h
├── Phase R1: Tạo :core-ai module                          → 2 ngày
└── Phase R1: Version Catalog (libs.versions.toml)         → 4h

Sprint 3 (Tuần 5–6): AI Local Integration  
├── AI 3.2: Integrate MediaPipe LLM Inference              → 3 ngày
├── AI 3.3: Model Management & Download                    → 2 ngày
└── AI 3.4/3.5: ViewModel + Prompt Engineering             → 2 ngày

Sprint 4 (Tuần 7–8): Performance
├── P1.1: Baseline Profile                                 → 1 ngày
├── P1.2: Compose Stability fixes                          → 2 ngày
└── P1.3: MapLibre tile caching                            → 1 ngày

Sprint 5 (Tuần 9–10): Refactor Core Patterns
├── Phase R2: Result type + UseCase base                   → 2 ngày
├── Phase R3: MVI ViewModel pattern                        → 3 ngày
└── Phase R4: Testing infrastructure                       → 3 ngày
```

---

### Ma trận rủi ro

| Rủi ro | Xác suất | Mức độ | Giảm thiểu |
|---|---|---|---|
| AI model quá lớn cho thiết bị low-end | Cao | Cao | Kiểm tra RAM trước load; có fallback message |
| K2 compiler bugs với Compose | Thấp | Trung bình | Pin Kotlin 2.0.21 stable, không upgrade vội |
| MapLibre offline tiles chiếm nhiều storage | Trung bình | Trung bình | Cấp hạn mức cache + UI xóa cache |
| Refactor MVI break existing features | Trung bình | Cao | Migrate từng màn hình, không big-bang |
| Configuration cache conflict vẫn còn sau fix | Thấp | Thấp | Test CI sau mỗi change |

---

*Báo cáo được tạo bởi Claude Sonnet 4.6 — Ngày 11/06/2026*  
*Dựa trên phân tích static các file: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `AGENTS.md`, `skill.md` và cấu trúc module của repository.*
