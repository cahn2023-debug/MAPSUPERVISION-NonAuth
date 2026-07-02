# TÀI LIỆU TỔNG HỢP TÍNH NĂNG DỰ ÁN MAPSUPERVISION

MapSupervision là một giải pháp di động toàn diện dành cho kỹ sư giám sát công trình. Ứng dụng hỗ trợ quản lý dự án, lập bản đồ số GIS, giám sát tiến độ thi công, quản lý vật tư, chụp ảnh hiện trường có định vị (chống giả lập GPS), tích hợp trợ lý AI ngoại tuyến (Local LLM) và tự động hóa xuất báo cáo nghiệm thu dạng PDF/Word.

---

## 1. Quản Lý Không Gian Làm Việc & Dự Án (Workspace & Project Management)
- **Tạo và Cấu hình Dự án**: Hỗ trợ tạo mới dự án với mã dự án độc lập, tên dự án, đường dẫn lưu trữ cơ sở dữ liệu riêng biệt.
- **Sao chép & Lưu trữ (Clone & Archive)**: Nhân bản cấu trúc dự án hoặc đóng lưu trữ các dự án đã hoàn thành.
- **Nhập/Xuất Dự án (Import/Export Project)**:
  - Xuất toàn bộ dữ liệu dự án (bao gồm cấu trúc SQLite Room DB và toàn bộ thư mục hình ảnh) thành một file nén để chia sẻ.
  - Nhập dự án từ file chia sẻ, có cơ chế phát hiện trùng lặp mã dự án và đưa ra lựa chọn ghi đè (Overwrite) hoặc tạo bản sao mới (Create Copy).
- **Phân tách Cơ sở Dữ liệu (Project-scoped DB)**: Mỗi dự án hoạt động trên một file cơ sở dữ liệu độc lập giúp bảo vệ an toàn dữ liệu và tối ưu hiệu năng truy vấn.

---

## 2. Bản Đồ Số GIS & Đo Đạc (GIS Mapping & Measurement)
- **Bản đồ tương tác chất lượng cao**: Sử dụng công nghệ MapLibre SDK để kết xuất mượt mà các đối tượng vector và ảnh vệ tinh.
- **Nguồn bản đồ đa dạng (Base maps)**:
  - Bản đồ đường phố (Street Style).
  - Bản đồ ảnh vệ tinh (Satellite Style).
  - Bản đồ ảnh vệ tinh kèm nhãn địa danh (Satellite Labels Style).
  - Bản đồ chế độ tối (Dark Style).
- **Đối tượng GIS**:
  - **Node (Điểm nút)**: Biểu diễn vị trí trụ điện, hố ga, cột viễn thông... hiển thị thông tin chi tiết vật tư, mã thiết kế, tọa độ.
  - **Route (Tuyến đường)**: Biểu diễn tuyến cáp, đường ống, đường giao thông nối giữa các nút... hiển thị thông tin chiều dài thiết kế và tiến độ hoàn thành.
- **Bộ lọc & Tô màu chuyên sâu**:
  - Lọc hiển thị bản đồ theo Nhà thầu (Contractor) hoặc chủng loại vật tư.
  - Tự động tô màu các Node và Route theo màu đặc trưng của từng nhà thầu.
  - Hỗ trợ đổi nhãn hiển thị trên bản đồ (nhãn số, mã thiết kế, hoặc ẩn nhãn).
- **Công cụ đo đạc thực tế**: Hỗ trợ đo khoảng cách giữa các điểm trên bản đồ bằng công thức Haversine với độ chính xác cao.

---

## 3. Nhập Liệu Hồ Sơ Thiết Kế (Design File Imports & Parsing)
- **Đa dạng định dạng hỗ trợ**: Nhập dữ liệu thiết kế từ các file Excel (.xlsx), Word (.docx), KML và KMZ.
- **Trình phân tích KML/KMZ thông minh**:
  - Tự động trích xuất các điểm (`Placemark -> Point`) thành Node và các đường (`Placemark -> LineString`) thành Route.
  - Tự động làm sạch dữ liệu HTML/CDATA từ mô tả KML.
  - Áp dụng thuật toán **gộp tuyến phân đoạn (Segmented Route Merging)**: các phân đoạn tuyến nhỏ dạng `LINE_A_S1`, `LINE_A_S2` sẽ được tự động hợp nhất thành tuyến chính `LINE_A` duy nhất.
- **Hệ thống ánh xạ cột Excel linh hoạt (Mapping Engine)**:
  - Cho phép người dùng chọn Sheet và tự thiết lập ánh xạ các cột dữ liệu (Mã đối tượng, tọa độ X/Y, chủng loại vật tư, nhà thầu).
  - Hỗ trợ cả tọa độ hệ VN-2000 (X/Y) lẫn tọa độ địa lý thông thường (WGS-84 Vĩ độ/Kinh độ).
- **Kiểm soát trùng lặp & Lỗi**: Có hệ thống cảnh báo trùng lặp mã đối tượng, lỗi định dạng số hoặc thiếu tọa độ trước khi ghi vào Database.

---

## 4. Chụp Ảnh Hiện Trường Định Vị (Geotagged Camera Overlay)
- **Chụp ảnh đè thông số (Camera Overlay)**: Khi chụp ảnh tại thực địa cho một điểm nút, hệ thống hiển thị lớp phủ thông tin trực quan trên màn hình chụp.
- **Đóng dấu thông tin (Photo Stamp Renderer)**: Tự động kết xuất đè lên ảnh các thông số:
  - Bản đồ mini hiển thị vị trí hiện tại của kỹ sư và các điểm nút xung quanh.
  - Tọa độ GPS (Vĩ độ, Kinh độ) cùng sai số định vị (Accuracy).
  - Tên dự án, mã đối tượng, nhà thầu thi công, tên kỹ sư giám sát và thời gian chụp.
- **Bảo mật & Chống gian lận (Anti-spoofing)**:
  - Kiểm tra trạng thái giả lập GPS (Mock Location detection). Nếu phát hiện GPS bị giả lập, hệ thống sẽ đánh dấu cờ cảnh báo trên bức ảnh và cơ sở dữ liệu.
  - Lưu giữ lịch sử sai số định vị để đảm bảo tính khách quan trong nghiệm thu.

---

## 5. Giám Sát Tiến Độ & Quản Lý Nhật Ký (Progress & Daily Logs)
- **Cập nhật tiến độ vật tư (Work Volume Progress)**: Kỹ sư có thể nhập khối lượng thực tế đã hoàn thành đối với từng loại vật tư tại từng điểm nút hoặc tuyến đường.
- **Nhật ký thi công hàng ngày (Daily Logs)**:
  - Ghi nhận thông tin thời tiết, nhiệt độ hiện tại (hỗ trợ tự động lấy dữ liệu thời tiết dựa trên tọa độ GPS hiện tại thông qua Weather API).
  - Lưu trữ thông số nhân công (manpower), công việc cụ thể đã triển khai và các ghi chú khác.
  - Liên kết trực tiếp nhật ký với các điểm nút, tuyến đường và các bức ảnh chụp hiện trường trong khoảng thời gian tương ứng.
- **Lập kế hoạch công việc (Work Plan & Categories)**: Định nghĩa các danh mục công việc thi công và lập kế hoạch mục tiêu cụ thể theo từng giai đoạn.

---

## 6. Trợ Lý AI Ngoại Tuyến (Offline Local AI Assistant)
- **Tích hợp mô hình ngôn ngữ lớn cục bộ (Local LLM)**: Sử dụng mô hình Gemma chạy trực tiếp trên thiết bị thông qua thư viện MediaPipe LLM Inference / LiteRT. Không cần kết nối Internet, bảo mật tuyệt đối dữ liệu dự án.
- **Các tác vụ AI hỗ trợ**:
  - **Tóm tắt nhật ký và ghi chú**: Tóm tắt hàng loạt các ghi chú công việc thành một bản tổng hợp ngắn gọn về tình hình công trường.
  - **Đề xuất nhiệm vụ (Task Suggestions)**: Tự động phân tích trạng thái thi công của điểm nút/tuyến đường để đề xuất danh sách đầu việc cần thực hiện tiếp theo.
  - **So khớp câu lệnh tự nhiên (Chat Dictionary & Normalization)**: Người dùng có thể ra lệnh bằng giọng nói hoặc văn bản tiếng Việt tự nhiên (ví dụ: "cập nhật tiến độ hố ga 15 lên 100%"). AI sẽ tự động phân tích cú pháp để ánh xạ chuẩn xác vào mã đối tượng, danh mục và cập nhật thẳng vào Database.

---

## 7. Xuất Báo Cáo Tự Động (Automated Reporting)
- **Đa định dạng báo cáo**: Hỗ trợ xuất báo cáo nghiệm thu, báo cáo ngày dưới dạng tài liệu Word (.docx) hoặc tài liệu PDF (.pdf).
- **Tự động điền dữ liệu (Smart Templating)**: Báo cáo tự động tổng hợp thông tin dự án, tiến độ hoàn thành, thống kê khối lượng vật tư đã lắp đặt, và bảng nhật ký thi công.
- **Đính kèm hình ảnh trực quan**: Tự động chèn các ảnh chụp hiện trường tương ứng với từng điểm nút. Có cơ chế cấu hình lệch thời gian chụp (Offset minutes) để tự động ghép các ảnh chụp lân cận vào đúng đầu mục công việc tương ứng.
