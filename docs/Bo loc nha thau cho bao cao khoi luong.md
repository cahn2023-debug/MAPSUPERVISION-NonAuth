# Bộ Lọc Nhà Thầu Cho Báo Cáo Khối Lượng

## Summary
- Thêm bộ lọc nhà thầu tại header của “Bảng tổng hợp khối lượng thi công”.
- Khi chọn nhà thầu, bảng trên màn hình, preview xuất báo cáo, PDF và Word sẽ dùng khối lượng của đúng nhà thầu đó.
- Sửa cách tính `%` để tính trên dữ liệu đã lọc: `Tổng thi công / Tổng thiết kế * 100`.

## Key Changes
- Trong `ReportingScreen`, thêm state `selectedContractor` và dropdown chọn:
  - `Tất cả nhà thầu`
  - danh sách nhà thầu lấy từ `reportSnapshot.nodes + reportSnapshot.routes`, bỏ rỗng, distinct, sort theo tên.
- Đặt control lọc ở vùng bên phải tiêu đề bảng tổng hợp như ảnh đánh dấu.
- Truyền `workspaceState.materialProgress` từ `WorkspaceAppShell` vào `ReportingScreen` để màn báo cáo refresh khi khối lượng vừa cập nhật.
- Mở rộng hàm `buildMaterialReportRows(...)` trong `ReportExportBuilder.kt` thành nguồn tính chung cho cả UI và export:
  - nhận thêm `filterContractor: String? = null`
  - lọc node/route theo contractor trước khi cộng planned/actual
  - không cộng trùng planned nếu cùng vật tư đã có trong `materialSummary` và `MaterialProgress.plannedQty`
  - dùng `plannedQty` từ `MaterialProgress` làm fallback khi `materialSummary` thiếu hoặc không parse được
  - tính `%` từ tổng actual/planned sau lọc, không dựa trên bảng toàn dự án.
- Gỡ hoặc thay phần duplicate `buildMaterialReportRows` private trong `ReportingViewModel` bằng hàm chung để UI/export không lệch nhau.
- Khi xuất PDF/Word, truyền `selectedContractor` vào `exportPdf/exportWord`, preview cũng nhận `filteredMaterialRows`.

## Test Plan
- Cập nhật `ReportExportBuilderTest`:
  - không filter vẫn giữ tổng toàn dự án.
  - filter `Contractor A` chỉ cộng planned/actual của node/route thuộc A.
  - filter `Contractor B` tạo bảng và dòng tổng riêng của B.
  - `%` bằng `actual / planned * 100` trên dữ liệu đã lọc.
  - fallback dùng `MaterialProgress.plannedQty` khi node không có planned parse được.
- Chạy:
  - `.\gradlew.bat :reporting:testDebugUnitTest`
  - nếu cần kiểm tra compile UI: `.\gradlew.bat :reporting:compileDebugKotlin`

## Assumptions
- Bộ lọc chỉ áp dụng cho bảng khối lượng và bảng trong file xuất; danh sách ảnh vẫn giữ toàn dự án.
- So khớp nhà thầu bằng tên đã trim, không phân biệt hoa thường.
- `%` hiển thị có thể lớn hơn `100%` nếu actual vượt planned, để phản ánh dữ liệu thực thay vì che mất vượt khối lượng.
