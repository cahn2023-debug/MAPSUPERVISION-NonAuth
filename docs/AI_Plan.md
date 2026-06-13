Giai đoạn 1: Nâng cấp AiOrchestrator thành "Smart Router"
AiOrchestrator trong module domain sẽ không chỉ gọi một Repository nữa, mà sẽ đóng vai trò như một bộ định tuyến, quyết định tác vụ nào dùng engine nào.

Luồng xử lý mới:

Plaintext
AiOrchestrator.execute(Contract)
    ├── Tác vụ Văn bản/Logic phức tạp -> Giao cho MediaPipe (LLM)
    ├── Tác vụ Phân loại ảnh chuyên sâu -> Giao cho TensorFlow Lite
    ├── Tác vụ Tiện ích (Quét mã, đọc chữ) -> Giao cho ML Kit
    └── Nếu thiết bị quá yếu/lỗi -> Giao cho Fallback Logic (Code thuần)
Giai đoạn 2: Tích hợp ML Kit (Giải pháp "Ăn liền" cho Hình ảnh & Tiện ích)
ML Kit cung cấp các API đã được tối ưu hóa sẵn, không cần tải model lớn, rất phù hợp cho các tác vụ đầu vào tại hiện trường (Module photo và data).

Tính năng áp dụng:

Trích xuất văn bản từ ảnh chụp (OCR): Hỗ trợ kỹ sư chụp lại nhãn mác vật tư, biên bản giấy để tự động điền vào form MaterialProgress hoặc DailyLog.

Quét QR/Barcode: Thêm tính năng quét mã QR trên các thiết bị, trụ điện, hoặc tủ cáp để tự động định vị GisNode trên bản đồ thay vì phải tìm kiếm thủ công.

Triển khai: Nằm trong PhotoPipelineService hoặc tạo thêm MlKitScannerService trong lớp data.

Giai đoạn 3: Tích hợp TensorFlow Lite (TFLite cho Custom Vision)
TFLite hoàn hảo cho các mô hình do chính bạn huấn luyện (Custom Models) có kích thước siêu nhỏ gọn (dưới 50MB), chuyên biệt cho nghiệp vụ giám sát thi công hạ tầng.

Tính năng áp dụng:

PHOTO_QUALITY_CHECK (Chấm điểm chất lượng ảnh):

Thay vì dùng LLM (quá nặng) hay quy tắc cơ bản (quá ngu ngơ), bạn có thể train một model TFLite nhỏ bằng Teachable Machine hoặc Vertex AI.

Model này nhận đầu vào là ảnh từ module photo và trả về các nhãn: Blurry (Mờ), No_Subject (Không thấy đối tượng thi công), Good_Quality (Chất lượng tốt).

DISCREPANCY_CHECK (Phát hiện vật tư bất thường): Một model phân loại dạng bảng (Tabular model) nhỏ gọn dùng thuật toán Random Forest/XGBoost chuyển sang định dạng TFLite để nhận diện các số liệu nhập vào bất thường.

Triển khai: Đặt model .tflite trong thư mục assets và gọi thông qua TfLiteRepositoryImpl trong module data.

Giai đoạn 4: Tích hợp MediaPipe Offline (LLM On-Device cho Suy luận Text)
Đây là "bộ não" thực sự thay thế cho Google Gemini Cloud. MediaPipe cung cấp LLM Inference API cho phép chạy các mô hình ngôn ngữ lượng tử hóa (như Gemma 2B, Phi-3 Mini) trực tiếp trên Android.

Tính năng áp dụng (Module timeline, reporting, domain):

TIMELINE_SUMMARY & REPORT_DRAFT: Đưa dữ liệu thô (tiến độ, log hàng ngày) vào prompt và yêu cầu Gemma 2B viết tóm tắt lưu loát.

IMPORT_MAPPING: Gợi ý khớp cột Excel thông minh.

OPS_RECOMMENDATION: Đưa ra cảnh báo ưu tiên dựa trên dữ liệu dự án.

Chiến lược triển khai:

Tải Model: Model .task của Gemma 2B nặng khoảng 1.5GB - 2GB. Bạn sử dụng module storage + WorkManager để tải model này ngầm khi người dùng kết nối Wi-Fi, lưu vào ProjectStorageManager.

Khởi tạo: LocalLiteRtRepositoryImpl sẽ chọn model và load vào bộ nhớ. Cần check RAM thiết bị trước khi chạy (chỉ kích hoạt nếu thiết bị có > 4GB RAM trống).

Giai đoạn 5: Tối ưu hiệu năng và Pin (Quan trọng cho Field App)
Bản full chạy nhiều AI sẽ rất hao tài nguyên, vì vậy hệ thống cần cơ chế bảo vệ:

Thermal/Battery Listener: Nếu nhiệt độ máy quá cao hoặc pin < 20%, AiOrchestrator tự động bypass các engine AI và fallback về rule-based logic tĩnh để cứu pin.

Background Processing: Các tác vụ dùng MediaPipe (như viết ReportDraft) nên được đưa vào Kotlin Coroutines Dispatchers.Default (xử lý CPU chuyên sâu) và hiển thị thanh tiến trình UI rõ ràng.
