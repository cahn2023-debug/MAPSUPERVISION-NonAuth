Archived on 2026-06-15.

Moved files:
- `storage-import/src/main/java/com/mapsupervision/storage/importer/KmlParser.kt`
- `storage-import/src/main/java/com/mapsupervision/storage/importer/KmzParser.kt`

Reason:
- Both files are deprecated compatibility wrappers.
- Repo-wide search found no live callers outside the archived files themselves.
- Active import flow uses `parseKmlContent()` and `parseKmzContent()` in `UserFileImportService`.

Verification baseline:
- `python .agents/scripts/checklist.py .` passes with all checks.
