# Tong hop du an MapSupervision - Version 1.1

## 1. Thong tin phat hanh

- Ten ung dung: `MapSupervision`
- Application id: `com.mapsupervision`
- Version name: `1.1`
- Version code: `2`
- Nen tang: Android
- Min SDK: `24`
- Target SDK: `35`
- Compile SDK: `36`

## 2. Muc tieu san pham

`MapSupervision` la ung dung Android phuc vu giam sat cong trinh theo huong local-first. He thong tap trung vao cac nhu cau chinh:

- Quan ly du an va workspace theo project active.
- Nhap du lieu thiet ke tu Excel, KML, KMZ, GeoJSON, JSON.
- Quan ly node, route, tien do, nhat ky, ghi chu va task.
- Chup anh hien truong, gan vi tri, dong dau thong tin va luu theo du an.
- Ho tro AI on-device/offline cho tom tat, goi y va mapping.
- Xuat bao cao `PDF`, `DOCX` va dong goi du an `ZIP`.

## 3. Pham vi chuc nang chinh

### 3.1 Quan ly du an

- Tao, chon, clone, archive va import/export du an.
- Theo doi `activeProjectId` de dong bo toan bo workspace.
- Ho tro luu du lieu theo `project-scoped database`.

### 3.2 DATA Hub va import

- Import file Excel co preview va mapping cot.
- Import file KML/KMZ/GeoJSON/JSON co mapping field.
- Quan ly file qua cac trang thai `pending`, `processed`, `failed`.
- Cho phep remap va thay the geometry khi can.

### 3.3 Ban do va giam sat

- Hien thi node/route tren map.
- Loc theo nha thau, vat tu, doi tuong.
- Theo doi tien do, khoi luong, tien do tre va dashboard du an.

### 3.4 Anh hien truong

- Chup anh/quay video trong app.
- Nhan media tu gallery hoac share intent ben ngoai.
- Gan GPS, object code, watermark/stamp va luu vao storage cua du an.

### 3.5 Bao cao va dong goi

- Xuat bao cao `PDF`.
- Xuat bao cao `DOCX`.
- Dong goi du an thanh `ZIP` de backup/chia se.

### 3.6 AI ho tro nghiep vu

- Tom tat timeline va ghi chu.
- Goi y task tiep theo.
- Ho tro mapping import.
- Tao draft noi dung bao cao.

## 4. Cau truc module

Theo `settings.gradle.kts`, workspace gom cac module sau:

- `app`: shell Android, navigation, ViewModel tong, widget, WorkManager.
- `core`: utility chung, logging, error/result.
- `domain`: model, repository contract, use case.
- `data`: Room database, DAO, repository implementation, migration.
- `project`: quan ly vong doi du an.
- `gis`: model va logic GIS.
- `gis-maplibre`: bridge render map voi MapLibre.
- `photo`: xu ly capture, gallery, stamp va review anh.
- `timeline`: tong hop tien do, nhat ky va AI summary.
- `reporting`: snapshot, draft va export bao cao.
- `storage-core`: storage va package du an.
- `storage-crypto`: xu ly storage can bao mat.
- `storage-import`: import pipeline va parser.
- `ai-core`, `ai-agent`, `ai-model`, `ai-rag`, `ai-prompt`: he thong AI on-device.

## 5. Kien truc du lieu va runtime

He thong theo huong tach lop ro rang:

1. `app` dieu huong UI va orchestration.
2. `domain` dinh nghia nghiep vu va contract.
3. `data` va `storage-*` xu ly Room DB, file, migration, import/export.
4. `ProjectSyncRepository` phat su kien de cac feature refresh dong bo.

Du lieu du an uu tien luu theo `project-scoped database` de:

- Tach biet du lieu tung du an.
- Ho tro backup/export rieng.
- Giam rui ro query cheo du an.

## 6. Flow nghiep vu tong quat

### 6.1 Khoi dong

- `MapSupervisionApplication` khoi tao logger, map bridge, image loader va WorkManager.
- `MainActivity` parse share intent neu app duoc mo bang file media.
- `StartupPermissionWrapper` kiem tra `Location` va `Camera`.

### 6.2 Lam viec theo project

- Chon hoac tao du an.
- Set `activeProjectId`.
- `WorkspaceViewModel` nap snapshot va dong bo state cho cac tab.

### 6.3 Nhap lieu

- Chon file thiet ke.
- Preview va mapping neu can.
- Parse vao DB va storage.
- Phat sync event de map, report, timeline va photo refresh.

### 6.4 Ghi nhan hien truong

- Chup anh/video hoac nhan media tu ngoai app.
- Dong dau thong tin.
- Gan node/route va luu vao du an.

### 6.5 Bao cao

- Tong hop snapshot.
- Tao draft AI neu can.
- Xuat `PDF`, `DOCX` hoac `ZIP`.

## 7. Huong dan build va release

### 7.1 Lenh build release

```powershell
.\gradlew.bat :app:assembleRelease
```

### 7.2 Release gate hien co

Script gate:

```text
scripts/release_gate.sh
```

Gate hien tai bao gom:

- `:app:testDebugUnitTest`
- `:storage-import:testDebugUnitTest`
- `:data:testDebugUnitTest`
- `lint`
- `assembleDebug`
- `enforceModuleBoundaries`

Tai lieu runbook lien quan:

- `docs/release_gate_runbook.md`
- `docs/tab_nhap_lieu_data_hub.md`
- `production-ready-roadmap.md`

## 8. Artifact phat hanh version 1.1

Sau khi build release, artifact can duoc doi chieu tai:

- `app/build/outputs/apk/release/`

Build gan nhat cho ban `1.1` duoc tao luc:

- `2026-07-04 22:57:52 +07:00`

Ban `1.1` da sinh cac APK theo ABI:

- `app-arm64-v8a-release.apk` - `101244739` bytes
- `app-armeabi-v7a-release.apk` - `66097612` bytes

Metadata build xac nhan:

- `versionCode = 2`
- `versionName = 1.1`

Goi tong hop release duoc tao trong thu muc `docs`:

- `MapSupervision_v1.1_release.zip`

## 9. Rui ro ky thuat can ghi nho

- Chuyen doi giua shared DB va project-scoped DB.
- Import remap co the anh huong geometry da ton tai.
- Thay doi du lieu project co tac dong lien tab qua `ProjectSyncRepository`.
- Export bao cao/package de nhay cam voi state project vua thay doi.

## 10. Tai lieu tham chieu noi bo

- `docs/android_kien_truc_tong_quan.md`
- `docs/android_cau_truc_module_va_du_lieu.md`
- `docs/android_flow_nghiep_vu.md`
- `docs/android_huong_dan_su_dung_va_xu_ly_loi.md`
- `docs/release_gate_runbook.md`
