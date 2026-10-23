# L?nh v? k?t qu? m?i

Source mainf864034, Java21.0.10 Temurin/Maven3.9.15/Windows11; PG18.3 TEST cluster m?i. Full commands/selectors t?ng l??t c? trong [test-commands.json](test-commands.json); shell PowerShell c?n quote tr?n tham s? -Dtest ch?a d?u #/+.

## Positive targeted runs

| L??t | Scope | PASS | SKIP | FAIL/ERROR |
|---|---|---:|---:|---:|
| audio-client | AudioFiles23, AudioPreparation10, AudioApiClient5, ListeningPlayback8, required lost takeover entry1, selected ExamSession6 |52|1|0|
| backend-monitoring | reducer6, delta16, state8, maintenance4, cleanup3; PG exam8/audio4/listening4 |53|0|0|
| network-fallback | HTTP/WS role/scope/revoke/invalid-client6, candidate FULL/fallback/role4 |10|0|0|
| PR47 new regressions | FullSnapshotDelivery4 + RealtimeClient3, isolated3044e5b source |7|0|0|
| **JUnit positive total** | 123case;16PG ?? n?m trong t?ng |**122**|**1**|**0**|

Symlink SKIP v? privilege Windows. Junction ancestor/root/child/replaced root v?8.3 th?c ch?y PASS. Payload audio/gateway/media/hearing MOCK; filesystem v? PG/HTTP/WS ???c ghi REAL ??ng ph?m vi. Kh?ng c? GUI/hearing m?i.

## Negative/failing checks ph?i gi?

- Main production archive + PR47 test source: `FullSnapshotDeliveryTest#stuckCloseWriteIsBoundedAndCannotWriteAfterNextDeliveryStarts` (2parameter cases) v? `RealtimeClientTest#forgottenCorrelationIgnoresRepeatedAckErrorAndResyncUntilRetried`: **3FAIL**,0ERROR, Mavenexit1. Expected diagnostic failure ch?ng minh main ch?a c? fix47, kh?ng c?ng PASS ho?c gi?u nh? l?i m?i tr??ng.
- [Publication reproducer](REPRODUCERS.md) ch?y2 l?n ?2c?y TEST m?i: **4batch FAIL**,1280counted operations,467errors; m?i batch cu?i5/5validSHA v?0partial. Collector exit0 kh?ng thay outcomeFAIL.
- HTTP lost-response reproducer: **1PASS**; default method retry, commitMOCK/close-after-readSIMULATED, networkREAL.

## PostgreSQL TEST ri?ng

Kh?ng d?ng .env ho?c Docker/shared DB. D?ng binariesPG18 ?? c?i, th? m?c TEST m?i, initdb --no-locale -E UTF8 --auth=scram-sha-256 v?i password ng?u nhi?n qua pwfile; bind127.0.0.1/c?ng tr?ng, timezoneUTC. Kh?ng ??a password v?o CLI/log/Git. Child process Maven nh?n DB_HOST/PORT/NAME/USER/PASSWORD v? TOEIC_SEED_PASSWORD qua environment ch? c?a TEST; TOEIC_PG_INTEGRATION=true ch? trong child n?y.

M?i suite t?o/drop UUIDschema. H?u ki?m qua psql tr?n ??ng cluster TEST: kh?ng c?n schema ngo?i public/information_schema/pg_*. pg_ctl d?ng ??ng data directory s? h?u; status exit3. File password settings ?? x?a; PG data/log c?n ignored ?? ch?n ?o?n. [Cleanup](environment-cleanup.json).

V? d? selector t?i thi?u b?t bu?c (ch? ch?y PG sau khi ?? c?p cluster TEST ri?ng):

```powershell
mvn -pl server -am '-Dtest=ExamRuntimePostgresTest#takeoverReplayAfterLostAckAdvancesEpochButNeverLosesAnswersDeadlineOrFinal' '-Dsurefire.failIfNoSpecifiedTests=false' '-Duser.timezone=UTC' test
mvn -pl client -am '-Dtest=ExamPaperAndEntryTest#lostTakeoverAckIsNeverRetriedAutomaticallyAndReopenUsesAFreshRequestId' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## E1 methodology

```powershell
python -B -m unittest discover -s scripts/benchmarks/e1 -p 'test*.py' -v
```

12PASS m?i; fixture MOCK ki?m CPU weighted/coverage/counters/checksum/binding/run duplicate/negative latency/error inventory. Kh?ng ch?y campaign12run, E2, load, historical raw reanalysis ho?c d?ng raw thay th?.

## Isolation v? raw evidence

T?ng source snapshot/probe/log/XML ? ignored target/stage3-review-runtime. PR47 ch?y archive ri?ng, kh?ng merge/cherry-pick v?o branch review. Baseline negative d?ng main production v? ch?2test source t?47, c? provenance ri?ng; kh?ng s?a test module c?a branch review. Case-by-case JSON kh?ng ch?a password/token/private paths.
Kh?ng ch?y full Maven suite/package ho?c build MSI trong phi?n n?y. Historical924/926/928 v? PR53 166 kh?ng ph?i s? t? ch?y m?i. GitHub CI n?u t? ch?y sau push l? l??t kh?c, kh?ng c?ng v?o k?t qu? local n?y.
