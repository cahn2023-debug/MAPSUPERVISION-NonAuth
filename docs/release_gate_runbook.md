# DATA Hub Release Gate And Runbook

Tai lieu nay dinh nghia diem chan release va quy trinh rollback cho DATA Hub truoc khi day len production.

## Muc tieu

- Bao dam nhung thay doi ve import, sync, migration va UI da duoc kiem tra truoc khi merge.
- Giam rui ro release khi database migration, event outbox hoac import flow thay doi.
- Co buoc rollback ro rang neu san sang production gap su co.

## Release Gate

Chi duoc phep release khi tat ca dieu kien sau deu dat:

- `:app:testDebugUnitTest` xanh.
- `:storage-import:testDebugUnitTest` xanh.
- `:data:testDebugUnitTest` xanh.
- Checklist P7-P11 da duoc tick day du.
- Khong co thay doi schema database chua co migration tuong ung.
- Khong co hardcoded warning ve full module test dang do.
- Neu co thay doi import flow, phai co verify sample file Excel va non-Excel.
- Neu co thay doi sync, phai co verify event outbox va dispatcher.

## CI Gate

- Workflow GitHub Actions: `.github/workflows/android.yml`
- Script gate chay truoc merge: `scripts/release_gate.sh`
- Neu script nay pass, release gate coi nhu da dat trong CI.

## Pre-Release Steps

1. Pull code moi nhat va sync dependency.
2. Chay:
   - `./gradlew.bat :app:testDebugUnitTest`
   - `./gradlew.bat :storage-import:testDebugUnitTest`
   - `./gradlew.bat :data:testDebugUnitTest`
3. Kiem tra checklist trong:
   - `docs/tab_nhap_lieu_data_hub.md`
   - `production-ready-roadmap.md`
4. Neu co thay doi migration, mo lai test migration lien quan va xac nhan schema version.
5. Neu co thay doi import parser, test them file mau tuong ung.

## Release Steps

1. Tao tag hoac release branch theo quy uoc repo.
2. Xac nhan khong co dirty change ngoai scope release.
3. Ghi nhan commit hash va version release.
4. Chay lai smoke test tren build release neu co san.
5. Merge khi tat ca gate da xanh.

## Smoke Test After Release

- Mo tab `Nhap thiet ke`.
- Import 1 file Excel mau.
- Import 1 file non-Excel mau.
- Kiem tra map, danh sach import, note/task va progress co update.
- Kiem tra event outbox khong bi ket pending bat thuong.

## Rollback Runbook

Neu release gay loi:

1. Dung release hien tai.
2. Xac dinh commit / tag gan nhat on dinh.
3. Rollback code ve commit/tag do.
4. Neu da co thay doi database, phai chay migration backwards neu he thong ho tro, hoac khoi phuc tu backup/scoped data cu.
5. Kiem tra lai:
   - import flow
   - outbox
   - migration
   - UI tab nhap lieu

## Go / No-Go Checklist

- [ ] Tat ca test can thiet da xanh.
- [ ] Checklist P7-P11 da dong.
- [ ] Release note da co.
- [ ] Co nguoi xac nhan cuoi cung.
- [ ] Co ke hoach rollback neu gap su co.
