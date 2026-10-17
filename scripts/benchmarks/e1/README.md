# E1 polling ProcessHandle

Runner Java tái sử dụng ControlledProcessHarness; analyzer Python stdlib.

- [Hướng dẫn hiện tại, công thức, checklist và lệnh cho B](../../../docs/E1_T4C1_MEASUREMENT.md).
- [Review T4-C1 và kiểm chứng](../../../evidence/t4-c1/2026-10-16-review.md).
- [Lệnh lịch sử trước review](../../../evidence/overnight-20261015/phase-02/E1_RUN_COMMANDS.md).

Không gọi runner từ app production. SMOKE không thay12run; E1 không thay gate delta/E2. Summary mới phải ở output mới, không ghi đè raw/evidence lịch sử. Chạy `python -m unittest discover -s scripts/benchmarks/e1 -p 'test*.py' -v` để kiểm analyzer và fixture tính tay.
