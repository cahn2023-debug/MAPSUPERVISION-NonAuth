# Tai lieu tong hop Android MapSupervision

Tai lieu nay la diem vao chinh cho nhom Android cua du an `MapSupervision`. Muc tieu la gom lai cau truc, chuc nang, flow nghiep vu, cach van hanh va nhom loi thuong gap de de onboarding, debug va ban giao.

## 1. Bo tai lieu Android trong `docs/`

- `android_kien_truc_tong_quan.md`: Tong quan app Android, entry point, dependency graph, navigation va cac thanh phan runtime quan trong.
- `android_cau_truc_module_va_du_lieu.md`: Chi tiet tung module, repository, database scope theo du an, storage va event sync.
- `android_flow_nghiep_vu.md`: Luong xu ly chinh tu tao du an, import thiet ke, map, chup anh, cap nhat thi cong, AI, bao cao.
- `android_huong_dan_su_dung_va_xu_ly_loi.md`: Cach su dung theo vai tro nghiep vu, checklist truoc khi chay va bang loi/fix nhanh.
- `tab_nhap_lieu_data_hub.md`: Tai lieu chi tiet rieng cho tab `DATA Hub`.
- `database.md` va `file_database.md`: Tai lieu schema va bang du lieu.

## 2. Tong quan nhanh tinh nang Android

- Quan ly workspace va project, bao gom tao, clone, archive, import/export va chuyen project active.
- Ban do GIS voi `MapLibre`, loc theo nha thau, loai vat tu, tim doi tuong, do khoang cach va xem chi tiet node/route.
- Import ho so thiet ke tu `Excel`, `KML`, `KMZ`, `GeoJSON`, `JSON`, co preview va mapping truoc khi ghi DB.
- Chup anh hien truong co watermark, mini-map, GPS va co che phat hien tinh huong GPS khong dang tin cay.
- Quan ly tien do, khoi luong, nhat ky, note, task, vat tu va ke hoach thi cong ngay trong workspace.
- AI offline/on-device cho mapping, tong hop nhat ky, goi y task, draft bao cao va tro ly chat.
- Xuat bao cao `PDF`, `DOCX`, dong goi du an thanh `ZIP` de chia se va backup.

## 3. Thu tu doc de onboarding nhanh

1. Doc `android_kien_truc_tong_quan.md`.
2. Doc `android_cau_truc_module_va_du_lieu.md`.
3. Doc `android_flow_nghiep_vu.md`.
4. Khi thao tac thuc te, mo them `android_huong_dan_su_dung_va_xu_ly_loi.md`.
5. Neu lam viec voi import, doc them `tab_nhap_lieu_data_hub.md`.

## 4. Ghi chu hien trang du an

- App Android dung `Hilt`, `WorkManager`, `Compose`, `Navigation Compose`, `Room` va `MapLibre`.
- Moi project co kha nang chay voi `project-scoped database`, dong thoi van co cau noi dong bo tu shared DB trong giai doan chuyen doi.
- App dang co mot so file UI/ViewModel dang thay doi trong worktree, vi vay tai lieu nay mo ta theo code hien co o thoi diem cap nhat va tap trung vao luong on dinh.
- Mot so chuoi tieng Viet trong code/manifest dang co dau hieu loi encoding. Tai lieu nay ghi ro trong phan troubleshooting de doi Android xu ly rieng khi can.
