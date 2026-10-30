# Manual authoring fix — 2026-10-30

`verification.json` records the real PostgreSQL 18 / HTTP / JavaFX targeted run: 26 passed, 3 skipped, no failures or errors. The skipped cases need external crawl datasets. The test cluster stopped successfully and no owned schemas remained.

After adding validation for malformed local draft templates, `ManualExamFilesTest` was rerun: all 3 tests passed. Maven `package` then succeeded for client and server. The other server and GUI code did not change after the integrated run.

`pending-form.png` is a JavaFX snapshot of the manual draft dialog using MOCK content. No deployed-server update, LAN audio playback or installed-client test is claimed.
