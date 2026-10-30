# Simple server authoring with images

Supersedes the local draft workflow recorded in `../manual-authoring-20261030`.

Real PostgreSQL 18, HTTP and JavaFX run: 30 passed, 3 external-dataset cases skipped, no failures/errors. The owned test database cluster stopped successfully with no owned schemas remaining. `verification.json` lists individual cases.

The lifecycle test saves partial content on the server, keeps 200 text-only questions PENDING when required media is absent, then supplies audio and Part 1 images. The completed document becomes a selectable rich exam, creates a V2 session, and lets its assigned candidate fetch an actual Part 1 image from the preparation manifest. Other checks cover admin image previews, rejected unrelated images, stale revisions and rollback, invalid images, shared audio/passage editing, image previews in JavaFX, no local-draft buttons, and server-version preflight.

`simple-form-with-image.png` shows the actual JavaFX form with a decoded MOCK image. Docker deployment on the user's VPS was not performed: no saved SSH host or deployment workflow was available, and the Docker daemon was not running on this Windows machine.

Final Maven package also ran 108 passing unit/contract tests: ManualExamDraftTest (2), RichExamContractTest (3), ManualExamFilesTest (3), RichExamModelTest (9), RichExamImportTest (91). No failures or skips in that run. The three media-packaging unit tests overlap with the integrated run above.
