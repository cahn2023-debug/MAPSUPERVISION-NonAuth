# TÀI LIỆU CHI TIẾT TÍNH NĂNG THEO TỪNG TAB GIAO DIỆN

Giao diện chính của MapSupervision (được cấu hình trong `WorkspaceAppShell`) phân chia ứng dụng thành **5 Tab chính** trên thanh điều hướng điều khiển (NavigationBar cho điện thoại di động / NavigationRail cho máy tính bảng) cùng **1 bong bóng Trợ lý AI nổi (Floating Chat Bubble)** xuất hiện xuyên suốt các màn hình.

---

## 1. Tab Bản Đồ (MAP Hub) - `map`
Đây là màn hình mặc định và là giao diện tương tác cốt lõi của kỹ sư giám sát tại hiện trường.
* **Giao diện bản đồ số**: Hiển thị trực quan các điểm nút (Node) và tuyến cáp/đường ống (Route).
* **Các nút chức năng nhanh trên bản đồ**:
  - **Định vị của tôi (MyLocation)**: Tự động di chuyển tâm bản đồ về tọa độ thực tế của thiết bị.
  - **Thu phóng (Zoom In/Out)**: Điều chỉnh tỉ lệ xem bản đồ.
  - **Thay đổi bản đồ nền (Base Map Selector)**: Chuyển đổi linh hoạt giữa bản đồ Vệ tinh, Đường phố, và Chế độ tối.
  - **Thước đo khoảng cách (Measure tool)**: Kéo chọn các điểm trên bản đồ để đo khoảng cách thực tế (tính toán bằng công thức Haversine).
* **Thanh menu Quản lý dự án**:
  - Chọn dự án làm việc hiện hành.
  - Tạo mới dự án, nhân bản (Clone), hoặc đóng lưu trữ (Archive).
  - Xuất file backup dự án (Export) hoặc nhập dự án từ file nén (Import).
* **Thẻ thông tin chi tiết đối tượng (Node/Route Detail Cards)**: Xuất hiện khi bấm chọn một đối tượng trên bản đồ:
  - Hiển thị nhà thầu phụ trách, nhãn hiển thị và danh sách vật tư chi tiết.
  - Cập nhật tiến độ vật tư nhanh tại chỗ.
  - Xem danh sách hình ảnh hiện trường đã chụp và liên kết với đối tượng này.
  - Giao diện **Ghi chú & Nhiệm vụ (Notes & Tasks)**: Cho phép thêm ghi chú nhanh hoặc tạo danh sách việc cần làm. Tích hợp AI để tự động tóm tắt ghi chú hoặc đề xuất nhiệm vụ tiếp theo.
  - Nút **Chụp ảnh đính kèm (Capture Picture)**: Mở camera đè thông số (Camera Overlay) để ghi hình hiện trường.

---

## 2. Tab Tiến Độ (PROGRESS Hub) - `progress`
Màn hình tổng hợp số liệu và ghi chép nhật ký công trình hàng ngày.
* **Bảng thống kê dự án (Dashboard)**:
  - Tổng số lượng Node và Tuyến đường (Route) trong dự án.
  - Tỉ lệ phần trăm hoàn thành tiến độ chung của toàn dự án.
  - Tổng số lượng các Node hoặc Route đang bị trễ hạn (Delayed count).
  - Tỉ lệ hoàn thành tiến độ cấp phát/lắp đặt vật tư.
* **Lịch nhật ký tương tác (Diary Calendar Widget)**: Hiển thị các ngày trong tháng dưới dạng lịch trực quan. Bấm vào một ngày để xem danh sách nhật ký thi công đã viết trong ngày đó.
* **Bộ biểu mẫu ghi nhận nhật ký (Daily Log Form)**:
  - Nhập nội dung công việc đã làm, số lượng nhân công huy động trong ngày.
  - Tự động lấy tọa độ hiện tại để truy vấn thời tiết và nhiệt độ thực tế thông qua API thời tiết nhằm điền tự động vào nhật ký.
  - Liên kết nhật ký với một điểm nút (Node) hoặc tuyến đường (Route) cụ thể.
  - Liên kết các bức ảnh chụp hiện trường trong ngày vào nhật ký công trình.

---

## 3. Tab Nhập Liệu (DATA Hub) - `data`
Hỗ trợ nạp dữ liệu bản vẽ thiết kế ban đầu và quản lý tài liệu nhập khẩu.
* **Tải lên tài liệu thiết kế (Upload Design)**: Hỗ trợ chọn tệp Excel, Word, KML hoặc KMZ từ bộ nhớ máy.
* **Ánh xạ cột Excel (Excel Mapping Dialog)**:
  - Cho phép chọn Sheet cụ thể trong file Excel.
  - Cấu hình ánh xạ: Định nghĩa cột nào là Vĩ độ/Kinh độ (hoặc hệ tọa độ VN-2000 X/Y), cột nào là Nhà thầu, Mã đối tượng, hay Khối lượng vật tư.
  - Xem trước dữ liệu lưới (Grid Preview) trước khi thực hiện chuyển đổi chính thức vào Database.
* **Hỗ trợ gộp tệp (Combine Files)**: Kết hợp nhiều file thiết kế nhỏ lẻ đã nhập thành một cấu trúc thiết kế hợp nhất.
* **Bộ lọc & Quản lý ảnh hiện trường**: Bộ lọc ảnh theo mã điểm nút hoặc thời gian chụp để dọn dẹp hoặc xuất bản ảnh.

---

## 4. Tab Vật Tư (MATERIALS Hub) - `materials`
Chuyên biệt cho công tác kiểm soát chuỗi cung ứng vật tư thi công tại công trường.
* **Biên bản bàn giao (Material Handover)**: Ghi chép lịch sử bàn giao vật tư cho từng nhà thầu (số lượng giao, người nhận, thời gian bàn giao).
* **Khai báo vật tư (Material Declaration)**: Quản lý thông tin chứng chỉ chất lượng, nguồn gốc xuất xứ của từng lô vật tư đưa vào công trình.
* **Tiến độ sử dụng vật tư**: Xem bảng tổng hợp chênh lệch giữa vật tư thiết kế định mức và khối lượng vật tư thực tế đã thi công lắp đặt tại hiện trường.

---

## 5. Tab Báo Cáo (REPORTS Hub) - `reports`
Hỗ trợ kết xuất hồ sơ báo cáo nghiệm thu tự động để gửi cho chủ đầu tư hoặc ban quản lý.
* **Hộp thoại xem trước báo cáo (Report Preview Dialog)**: Hiển thị giao diện tóm tắt bố cục báo cáo sẽ xuất ra.
* **Bản thảo báo cáo bằng AI (AI Draft Report)**: Trợ lý AI tự động soạn thảo:
  - Phần tóm tắt điều hành (Executive Summary).
  - Đánh giá các rủi ro hiện tại trên công trường (Risk Section).
  - Đề xuất các hành động tiếp theo (Recommended Actions).
* **Cấu hình so khớp ảnh thông minh**:
  - Tùy chỉnh độ lệch thời gian (Photo Match Offset Minutes): tự động quét và đưa các bức ảnh hiện trường chụp lân cận vào đúng vị trí bảng tiến độ của báo cáo.
* **Xuất file**:
  - Xuất ra tệp **PDF** chất lượng cao, định dạng chuyên nghiệp sẵn sàng in ấn.
  - Xuất ra tệp **Microsoft Word (.docx)** để kỹ sư có thể tiếp tục chỉnh sửa nội dung thủ công trên máy tính.

---

## 6. Bong Bóng Chat AI Trợ Lý Gemma (Floating Chat Bubble)
Biểu tượng trợ lý AI dạng bong bóng tròn màu cam nổi trên góc màn hình, cho phép mở nhanh từ bất kỳ Tab nào.
* **Quản lý Mô hình AI cục bộ**: Cho phép tải xuống, cập nhật hoặc xóa các mô hình Gemma LLM trực tiếp trên thiết bị; hiển thị cảnh báo dung lượng và tiến trình tải.
* **Chat hỏi đáp đa văn cảnh (Context-aware Chat)**: AI tự động phân tích không gian thiết kế của dự án hiện tại (tổng số nút, tuyến, tiến độ hoàn thành, thời tiết, ghi chú, nhật ký ngày) để trả lời ngay các câu hỏi tổng hợp dữ liệu của kỹ sư.
* **Điều khiển bằng giọng nói/văn bản tự nhiên**:
  - Kỹ sư có thể nhắn tin ra lệnh, ví dụ: "thêm nhật ký hôm nay thi công hố ga N12, thời tiết nắng, 5 nhân công". AI sẽ tự động phân tích và chuyển đổi thành hành động cập nhật cơ sở dữ liệu thực tế mà người dùng không cần nhập biểu mẫu thủ công.
  - Hỗ trợ làm rõ câu lệnh (Clarification Options): Khi người dùng ra lệnh mơ hồ, AI sẽ đưa ra các nút gợi ý để người dùng chọn nhanh đối tượng cần thao tác.
