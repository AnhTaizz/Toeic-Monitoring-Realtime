# TOEIC 200-question dataset acceptance checklist

**Do not invent copyrighted original TOEIC answer keys, transcripts or media.** Dataset owners must check legal permission for redistribution.

| Part | Count | Essential media/layout requirements |
|---|---:|---|
| 1 | 6 | Photos + listening audio; choice letters even without visible spoken transcripts |
| 2 | 25 | Listening audio; typically A/B/C choice labels; no guessed dialogue |
| 3 | 39 | Shared conversation audio by group, 3 related questions/group; optional figures |
| 4 | 30 | Shared talk audio by group, 3 questions/group; optional figures |
| 5 | 30 | Standalone text with four choices |
| 6 | 16 | Passages with related fill-in groups and ordered text |
| 7 | 54 | Single/multiple documents, passages, possible images/charts and related groups |
| **TOTAL** | **200** | **100 Listening + 100 Reading** |

## Manifest requirements for each exam

- [ ] Exam ID and provenance; usage/distribution rights recorded.
- [ ] 200 unique question IDs, 200 unique order positions, Part counts match exact profile.
- [ ] Each question belongs to valid section/part and has exactly one correct option among declared choices.
- [ ] GroupId ownership/order correct; passage and shared audio references resolve to the intended group.
- [ ] All required media URLs have been converted into actual local permitted assets, with MIME/bytes/hash stored.
- [ ] Images render full/correct orientation; audio plays in the packaged client; Unicode/space filenames work.
- [ ] Neither client DTO nor public asset metadata exposes the correct option or server scoring map.
- [ ] Parser fails closed and reports missing answers/options/media rather than filling arbitrary defaults.
- [ ] Duplicate import ID does not overwrite a previously deployed exam without explicit migration/version strategy.
- [ ] One real PostgreSQL import -> catalog -> proctor select -> READY -> START -> candidate exam -> submit -> server score has been witnessed and logged.

## Asset storage concept (not yet an implementation assertion)

```text
exam-assets/
  images/<examId>/<relative-name>.png
  audio/<examId>/<relative-name>.mp3
```

Audio already has its own `TOEIC_AUDIO_ROOT` root; keep it unless an approved change intentionally reconciles roots. Image root is proposed, not current.
