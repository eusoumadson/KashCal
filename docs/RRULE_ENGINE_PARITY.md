## Findings at a glance

| Classification | Count | Description |
| --- | --- | --- |
| A | 0 | Clear bug per RFC — one engine is wrong |
| B | 1 | RFC ambiguity or non-authoritative divergence |
| C | 1 | Scope gap — one or both engines errored/timed out |
| D | 103 | Identical output across both engines |

### Category C — error-path divergences (non-blocking, but handle at adapter layer on migration)

- **adversarial: BYMONTH=13 (invalid month)**
  - lib-recur tolerates BYMONTH=13 by treating it as a yearly anniversary at DTSTART's month (returns 3 stamps). IcalDavRRuleEngine catches ical4j's IllegalArgumentException and returns []. Both behaviors are defensible for RFC-invalid input; no user impact since the app doesn't allow authoring RRULEs with invalid month values.

### Recommendation

No Category A bugs. Migration to ical4j is **feasible** but not required — the 103/105 agreement rate (98.1%) is strong, and Category B divergences are either RFC-ambiguous or easily replicated at the adapter layer.


# RRULE Engine Parity Report

## Summary

- Total cases: 105
- Both engines agree: 103 (98.1%)
- Divergences: 2 (1.9%)
- One engine errored: 0
- Both engines errored: 0

### RFC 5545 §3.8.5.3 compliance (Pool A only, 39 cases)
- Both engines match RFC: 38
- Only lib-recur matches RFC: 0
- Only ical4j matches RFC: 0
- Neither matches RFC: 1


## Pool: adversarial (26 cases)

### adversarial: COUNT=5 and UNTIL=20250110 both present (ambiguity)

- RRULE: `FREQ=DAILY;COUNT=5;UNTIL=20250110T000000Z`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 5 occurrences: 20250101T100000Z, 20250102T100000Z, 20250103T100000Z, 20250104T100000Z, 20250105T100000Z
- Classification: D
- Known divergence reason: RFC 5545 §3.3.10 forbids both — behavior undefined; engines may differ


### adversarial: INTERVAL=0 (invalid per RFC)

- RRULE: `FREQ=DAILY;INTERVAL=0;COUNT=3`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 3 occurrences: 20250101T100000Z, 20250102T100000Z, 20250103T100000Z
- Classification: D
- Known divergence reason: INTERVAL=0 undefined per RFC; engines may silently use 1 or reject


### adversarial: INTERVAL=-1 (invalid negative)

- RRULE: `FREQ=DAILY;INTERVAL=-1;COUNT=3`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 3 occurrences: 20250101T100000Z, 20250102T100000Z, 20250103T100000Z
- Classification: D
- Known divergence reason: INTERVAL=-1 is invalid; engines may reject or crash


### adversarial: INTERVAL=2147483647 (Int.MAX_VALUE)

- RRULE: `FREQ=YEARLY;INTERVAL=2147483647;COUNT=3`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20251231T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: huge interval may only yield DTSTART within range; overflow risk


### adversarial: empty RRULE

- RRULE: ``
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: empty RRULE: LibRecurEngine returns [] early; ical4j may expand to just DTSTART


### adversarial: garbage RRULE text

- RRULE: `THIS IS NOT AN RRULE`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: unparseable RRULE — both engines should gracefully yield empty or DTSTART-only


### adversarial: RRULE missing FREQ

- RRULE: `COUNT=5;BYDAY=MO`
- DTSTART: 20250106T100000Z (ms=1736157600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: FREQ is REQUIRED; engines must reject or no-op


### adversarial: FREQ=FORTNIGHTLY (not in enum)

- RRULE: `FREQ=FORTNIGHTLY;COUNT=3`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: FORTNIGHTLY not in RFC enum; both engines should reject


### adversarial: RRULE with SQL-injection-shaped payload

- RRULE: `FREQ=DAILY;COUNT=3;UNTIL=' OR 1=1--`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 3 occurrences: 20250101T100000Z, 20250102T100000Z, 20250103T100000Z
- Classification: D
- Known divergence reason: parser must treat UNTIL value as opaque string, not evaluate it


### adversarial: FREQ=DAILY forever over 1-month range

- RRULE: `FREQ=DAILY`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 31 occurrences: 20250101T100000Z, 20250102T100000Z, 20250103T100000Z … (25 more) … 20250129T100000Z, 20250130T100000Z, 20250131T100000Z
- Classification: D


### adversarial: FREQ=SECONDLY over 1-hour range (MAX_ITERATIONS territory)

- RRULE: `FREQ=SECONDLY;INTERVAL=60`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T100000Z .. 20250101T110000Z
- Result: **AGREE** — both engines returned 60 occurrences: 20250101T100000Z, 20250101T100100Z, 20250101T100200Z … (54 more) … 20250101T105700Z, 20250101T105800Z, 20250101T105900Z
- Classification: D
- Known divergence reason: unbounded SECONDLY — lib-recur's MAX_ITERATIONS caps output


### adversarial: FREQ=MINUTELY unbounded over 1 day

- RRULE: `FREQ=MINUTELY`
- DTSTART: 20250101T000000Z (ms=1735689600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250102T000000Z
- Result: **AGREE** — both engines returned 1440 occurrences: 20250101T000000Z, 20250101T000100Z, 20250101T000200Z … (1434 more) … 20250101T235700Z, 20250101T235800Z, 20250101T235900Z
- Classification: D


### adversarial: UNTIL before DTSTART yields empty expansion

- RRULE: `FREQ=DAILY;UNTIL=20240101T000000Z`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D


### adversarial: UNTIL=19700101 older than Unix epoch boundary

- RRULE: `FREQ=DAILY;UNTIL=19700101T000000Z`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D


### adversarial: FREQ=MONTHLY BYDAY=6MO (6th Monday never exists)

- RRULE: `FREQ=MONTHLY;BYDAY=6MO;COUNT=3`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: no 6th Monday — engines may yield empty, skip months, or error


### adversarial: BYMONTHDAY=30 across Feb (non-leap)

- RRULE: `FREQ=MONTHLY;BYMONTHDAY=30;COUNT=6`
- DTSTART: 20250130T100000Z (ms=1738231200000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20250130T100000Z, 20250330T100000Z, 20250430T100000Z, 20250530T100000Z, 20250630T100000Z, 20250730T100000Z
- Classification: D


### adversarial: FREQ=YEARLY BYMONTH=2 BYMONTHDAY=29 across 3 non-leap years

- RRULE: `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29`
- DTSTART: 20200229T100000Z (ms=1582970400000, TZID=UTC, isAllDay=false)
- Range: 20210101T000000Z .. 20240101T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: Feb 29 in non-leap years — both engines should skip


### adversarial: DAILY at 02:30 landing on DST spring-forward (America/New_York)

- RRULE: `FREQ=DAILY;COUNT=5`
- DTSTART: 20250307T073000Z (ms=1741332600000, TZID=America/New_York, isAllDay=false)
- Range: 20250301T140000Z .. 20250315T130000Z
- Result: **AGREE** — both engines returned 5 occurrences: 20250307T073000Z, 20250308T073000Z, 20250309T073000Z, 20250310T063000Z, 20250311T063000Z
- Classification: D
- Known divergence reason: 02:30 on 3/9 doesn't exist in local time — engines may shift to 03:30 EDT or skip


### adversarial: DAILY at 01:30 landing on DST fall-back (America/New_York)

- RRULE: `FREQ=DAILY;COUNT=5`
- DTSTART: 20251101T053000Z (ms=1761975000000, TZID=America/New_York, isAllDay=false)
- Range: 20251101T130000Z .. 20251110T140000Z
- Result: **DIVERGE**
  - Common: 20251103T063000Z, 20251104T063000Z, 20251105T063000Z
  - lib-recur only: 20251102T063000Z
  - ical4j only: 20251102T053000Z
- Classification: B
- Known divergence reason: 01:30 on 11/2 is ambiguous — engines may choose EDT or EST
- Analyst note: Nov 2 2025 01:30 America/New_York is ambiguous — the local time occurs twice (once EDT=05:30Z, once EST=06:30Z). lib-recur chose EST (06:30Z); ical4j chose EDT (05:30Z). Both are defensible readings of RFC 5545 §3.3.5 (which is silent on fold-back ambiguity). Practically, the difference is a single occurrence one hour apart, on one day per year. Not a migration blocker.


### adversarial: all-day DAILY across DST (should not shift)

- RRULE: `FREQ=DAILY;COUNT=30`
- DTSTART: 20250305T000000Z (ms=1741132800000, TZID=(floating), isAllDay=true)
- Range: 20250301T000000Z .. 20250405T000000Z
- Result: **AGREE** — both engines returned 30 occurrences: 20250305T000000Z, 20250306T000000Z, 20250307T000000Z … (24 more) … 20250401T000000Z, 20250402T000000Z, 20250403T000000Z
- Classification: D


### adversarial: FREQ=DAILY COUNT=10000 (near MAX_ITERATIONS ceiling)

- RRULE: `FREQ=DAILY;COUNT=10000`
- DTSTART: 20200101T100000Z (ms=1577872800000, TZID=UTC, isAllDay=false)
- Range: 20200101T000000Z .. 20480101T000000Z
- Result: **AGREE** — both engines returned 10000 occurrences: 20200101T100000Z, 20200102T100000Z, 20200103T100000Z … (9994 more) … 20470516T100000Z, 20470517T100000Z, 20470518T100000Z
- Classification: D
- Known divergence reason: at/near lib-recur's MAX_ITERATIONS=10000 safety cap


### adversarial: BYSETPOS=-100 exceeds candidates (should yield empty per month)

- RRULE: `FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-100;COUNT=3`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 0 occurrences: (empty)
- Classification: D
- Known divergence reason: BYSETPOS=-100 has no valid candidate in any month; engines may loop


### adversarial: BYMONTH=13 (invalid month)

- RRULE: `FREQ=YEARLY;BYMONTH=13;COUNT=3`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20280101T000000Z
- Result: **DIVERGE**
  - Common: (empty)
  - lib-recur only: 20250101T100000Z, 20260101T100000Z, 20270101T100000Z
  - ical4j only: (empty)
- Classification: C
- Known divergence reason: BYMONTH=13 invalid; engines may reject or silently drop
- Analyst note: lib-recur tolerates BYMONTH=13 by treating it as a yearly anniversary at DTSTART's month (returns 3 stamps). IcalDavRRuleEngine catches ical4j's IllegalArgumentException and returns []. Both behaviors are defensible for RFC-invalid input; no user impact since the app doesn't allow authoring RRULEs with invalid month values.


### adversarial: EXDATE at another time of day than the series removes nothing

- RRULE: `FREQ=DAILY;COUNT=6`
- DTSTART: 20270301T150000Z (ms=1803913200000, TZID=America/New_York, isAllDay=false)
- EXDATE: `1804082400000`
- Range: 20270301T050000Z .. 20270401T040000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20270301T150000Z, 20270302T150000Z, 20270303T150000Z, 20270304T150000Z, 20270305T150000Z, 20270306T150000Z
- Classification: D


### adversarial: RDATE at another time on a day with an occurrence adds one

- RRULE: `FREQ=DAILY;COUNT=6`
- DTSTART: 20270301T150000Z (ms=1803913200000, TZID=America/New_York, isAllDay=false)
- RDATE: `1804190400000`
- Range: 20270301T050000Z .. 20270401T040000Z
- Result: **AGREE** — both engines returned 7 occurrences: 20270301T150000Z, 20270302T150000Z, 20270303T150000Z, 20270304T150000Z, 20270304T200000Z, 20270305T150000Z, 20270306T150000Z
- Classification: D


### adversarial: weekly series in UTC across a DST change keeps its UTC time

- RRULE: `FREQ=WEEKLY;COUNT=4`
- DTSTART: 20270308T150000Z (ms=1804518000000, TZID=UTC, isAllDay=false)
- Range: 20270301T000000Z .. 20270401T000000Z
- Result: **AGREE** — both engines returned 4 occurrences: 20270308T150000Z, 20270315T150000Z, 20270322T150000Z, 20270329T150000Z
- Classification: D


## Pool: critical (6 cases)

### CRITICAL (a): all-day weekly BYDAY=MO stays on Monday regardless of TZID

- RRULE: `FREQ=WEEKLY;BYDAY=MO;COUNT=4`
- DTSTART: 20250106T000000Z (ms=1736121600000, TZID=America/Chicago, isAllDay=true)
- Range: 20250101T000000Z .. 20250215T000000Z
- Result: **AGREE** — both engines returned 4 occurrences: 20250106T000000Z, 20250113T000000Z, 20250120T000000Z, 20250127T000000Z
- Classification: D


### CRITICAL (b): COUNT+UNTIL both present — UNTIL stripped so COUNT wins

- RRULE: `FREQ=DAILY;COUNT=3;UNTIL=20000101T000000Z`
- DTSTART: 20250501T140000Z (ms=1746108000000, TZID=America/New_York, isAllDay=false)
- Range: 20250501T040000Z .. 20250510T040000Z
- Result: **AGREE** — both engines returned 3 occurrences: 20250501T140000Z, 20250502T140000Z, 20250503T140000Z
- Classification: D


### CRITICAL (c+i): all-day YEARLY with DATE-format UNTIL across FastForwarded window

- RRULE: `FREQ=YEARLY;UNTIL=20350927`
- DTSTART: 20200927T000000Z (ms=1601164800000, TZID=(floating), isAllDay=true)
- Range: 20300101T000000Z .. 20360101T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20300927T000000Z, 20310927T000000Z, 20320927T000000Z, 20330927T000000Z, 20340927T000000Z, 20350927T000000Z
- Classification: D


### CRITICAL (d): FastForwarded NOT applied when rangeStart <30d after DTSTART

- RRULE: `FREQ=DAILY;COUNT=10`
- DTSTART: 20250601T130000Z (ms=1748782800000, TZID=America/New_York, isAllDay=false)
- Range: 20250601T040000Z .. 20250630T040000Z
- Result: **AGREE** — both engines returned 10 occurrences: 20250601T130000Z, 20250602T130000Z, 20250603T130000Z … (4 more) … 20250608T130000Z, 20250609T130000Z, 20250610T130000Z
- Classification: D


### CRITICAL (e+h): MINUTELY unbounded over narrow range — second-aligned timestamps

- RRULE: `FREQ=MINUTELY;INTERVAL=15`
- DTSTART: 20250701T140000Z (ms=1751378400000, TZID=America/New_York, isAllDay=false)
- Range: 20250701T140000Z .. 20250701T160000Z
- Result: **AGREE** — both engines returned 8 occurrences: 20250701T140000Z, 20250701T141500Z, 20250701T143000Z, 20250701T144500Z, 20250701T150000Z, 20250701T151500Z, 20250701T153000Z, 20250701T154500Z
- Classification: D


### CRITICAL (g): DATE-format EXDATE on timed DTSTART — time component inherited

- RRULE: `FREQ=DAILY;COUNT=5`
- DTSTART: 20250701T140000Z (ms=1751378400000, TZID=America/New_York, isAllDay=false)
- EXDATE: `20250703`
- Range: 20250701T040000Z .. 20250710T040000Z
- Result: **AGREE** — both engines returned 4 occurrences: 20250701T140000Z, 20250702T140000Z, 20250704T140000Z, 20250705T140000Z
- Classification: D


## Pool: existing (34 cases)

### existing: FREQ=DAILY COUNT=5 UTC

- RRULE: `FREQ=DAILY;COUNT=5`
- DTSTART: 20250115T100000Z (ms=1736935200000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 5 occurrences: 20250115T100000Z, 20250116T100000Z, 20250117T100000Z, 20250118T100000Z, 20250119T100000Z
- Classification: D


### existing: FREQ=DAILY INTERVAL=3 over 30 days

- RRULE: `FREQ=DAILY;INTERVAL=3`
- DTSTART: 20250115T100000Z (ms=1736935200000, TZID=UTC, isAllDay=false)
- Range: 20250115T000000Z .. 20250215T000000Z
- Result: **AGREE** — both engines returned 11 occurrences: 20250115T100000Z, 20250118T100000Z, 20250121T100000Z … (5 more) … 20250208T100000Z, 20250211T100000Z, 20250214T100000Z
- Classification: D


### existing: FREQ=DAILY UNTIL=20250110T235959Z

- RRULE: `FREQ=DAILY;UNTIL=20250110T235959Z`
- DTSTART: 20250105T100000Z (ms=1736071200000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250115T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20250105T100000Z, 20250106T100000Z, 20250107T100000Z, 20250108T100000Z, 20250109T100000Z, 20250110T100000Z
- Classification: D


### existing: FREQ=WEEKLY BYDAY=MO,WE,FR

- RRULE: `FREQ=WEEKLY;BYDAY=MO,WE,FR`
- DTSTART: 20250106T100000Z (ms=1736157600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 12 occurrences: 20250106T100000Z, 20250108T100000Z, 20250110T100000Z … (6 more) … 20250127T100000Z, 20250129T100000Z, 20250131T100000Z
- Classification: D


### existing: FREQ=WEEKLY INTERVAL=2 bi-weekly

- RRULE: `FREQ=WEEKLY;INTERVAL=2`
- DTSTART: 20250106T100000Z (ms=1736157600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250401T000000Z
- Result: **AGREE** — both engines returned 7 occurrences: 20250106T100000Z, 20250120T100000Z, 20250203T100000Z, 20250217T100000Z, 20250303T100000Z, 20250317T100000Z, 20250331T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYDAY=TU BYSETPOS=2 second Tuesday

- RRULE: `FREQ=MONTHLY;BYDAY=TU;BYSETPOS=2`
- DTSTART: 20250114T100000Z (ms=1736848800000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 12 occurrences: 20250114T100000Z, 20250211T100000Z, 20250311T100000Z … (6 more) … 20251014T100000Z, 20251111T100000Z, 20251209T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYDAY=FR BYSETPOS=-1 last Friday

- RRULE: `FREQ=MONTHLY;BYDAY=FR;BYSETPOS=-1`
- DTSTART: 20250131T100000Z (ms=1738317600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 12 occurrences: 20250131T100000Z, 20250228T100000Z, 20250328T100000Z … (6 more) … 20251031T100000Z, 20251128T100000Z, 20251226T100000Z
- Classification: D


### existing: FREQ=YEARLY anniversary

- RRULE: `FREQ=YEARLY`
- DTSTART: 20200315T100000Z (ms=1584266400000, TZID=UTC, isAllDay=false)
- Range: 20200101T000000Z .. 20300101T000000Z
- Result: **AGREE** — both engines returned 10 occurrences: 20200315T100000Z, 20210315T100000Z, 20220315T100000Z … (4 more) … 20270315T100000Z, 20280315T100000Z, 20290315T100000Z
- Classification: D


### existing: FREQ=WEEKLY BYDAY=MO all-day

- RRULE: `FREQ=WEEKLY;BYDAY=MO`
- DTSTART: 20250106T000000Z (ms=1736121600000, TZID=(floating), isAllDay=true)
- Range: 20250101T000000Z .. 20250301T000000Z
- Result: **AGREE** — both engines returned 8 occurrences: 20250106T000000Z, 20250113T000000Z, 20250120T000000Z, 20250127T000000Z, 20250203T000000Z, 20250210T000000Z, 20250217T000000Z, 20250224T000000Z
- Classification: D


### existing: FREQ=MONTHLY BYMONTHDAY=15,-1 15th and last day

- RRULE: `FREQ=MONTHLY;BYMONTHDAY=15,-1;COUNT=12`
- DTSTART: 20250115T100000Z (ms=1736935200000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 12 occurrences: 20250115T100000Z, 20250131T100000Z, 20250215T100000Z … (6 more) … 20250531T100000Z, 20250615T100000Z, 20250630T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYDAY=1MO first Monday

- RRULE: `FREQ=MONTHLY;BYDAY=1MO;COUNT=6`
- DTSTART: 20250106T100000Z (ms=1736157600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20250106T100000Z, 20250203T100000Z, 20250303T100000Z, 20250407T100000Z, 20250505T100000Z, 20250602T100000Z
- Classification: D


### existing: FREQ=YEARLY BYMONTH=2 BYDAY=-1 last day of Feb across leap years

- RRULE: `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1;COUNT=6`
- DTSTART: 20200229T100000Z (ms=1582970400000, TZID=UTC, isAllDay=false)
- Range: 20200101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20200229T100000Z, 20210228T100000Z, 20220228T100000Z, 20230228T100000Z, 20240229T100000Z, 20250228T100000Z
- Classification: D


### existing: FREQ=YEARLY BYMONTH=6,7 COUNT=6

- RRULE: `FREQ=YEARLY;BYMONTH=6,7;COUNT=6`
- DTSTART: 20250610T100000Z (ms=1749549600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20300101T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20250610T100000Z, 20250710T100000Z, 20260610T100000Z, 20260710T100000Z, 20270610T100000Z, 20270710T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYDAY=MO,TU,WE,TH,FR BYSETPOS=-1 last weekday

- RRULE: `FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;COUNT=6`
- DTSTART: 20250131T100000Z (ms=1738317600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20250131T100000Z, 20250228T100000Z, 20250331T100000Z, 20250430T100000Z, 20250530T100000Z, 20250630T100000Z
- Classification: D


### existing: FREQ=DAILY BYHOUR=9,12,15 three times per day

- RRULE: `FREQ=DAILY;BYHOUR=9,12,15;COUNT=6`
- DTSTART: 20250101T090000Z (ms=1735722000000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250110T000000Z
- Result: **AGREE** — both engines returned 6 occurrences: 20250101T090000Z, 20250101T120000Z, 20250101T150000Z, 20250102T090000Z, 20250102T120000Z, 20250102T150000Z
- Classification: D


### existing: FREQ=YEARLY BYWEEKNO=1 BYDAY=MO first ISO week Monday

- RRULE: `FREQ=YEARLY;BYWEEKNO=1;BYDAY=MO;COUNT=5`
- DTSTART: 20240101T100000Z (ms=1704103200000, TZID=UTC, isAllDay=false)
- Range: 20240101T000000Z .. 20300101T000000Z
- Result: **AGREE** — both engines returned 5 occurrences: 20240101T100000Z, 20241230T100000Z, 20251229T100000Z, 20270104T100000Z, 20280103T100000Z
- Classification: D


### existing: FREQ=DAILY COUNT=365 full year

- RRULE: `FREQ=DAILY;COUNT=365`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 365 occurrences: 20250101T100000Z, 20250102T100000Z, 20250103T100000Z … (359 more) … 20251229T100000Z, 20251230T100000Z, 20251231T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYMONTHDAY=31 skips short months

- RRULE: `FREQ=MONTHLY;BYMONTHDAY=31;COUNT=7`
- DTSTART: 20250131T100000Z (ms=1738317600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 7 occurrences: 20250131T100000Z, 20250331T100000Z, 20250531T100000Z, 20250731T100000Z, 20250831T100000Z, 20251031T100000Z, 20251231T100000Z
- Classification: D


### existing: DAILY across DST spring-forward in America/New_York

- RRULE: `FREQ=DAILY;COUNT=7`
- DTSTART: 20250308T140000Z (ms=1741442400000, TZID=America/New_York, isAllDay=false)
- Range: 20250301T140000Z .. 20250320T130000Z
- Result: **AGREE** — both engines returned 7 occurrences: 20250308T140000Z, 20250309T130000Z, 20250310T130000Z, 20250311T130000Z, 20250312T130000Z, 20250313T130000Z, 20250314T130000Z
- Classification: D


### existing: DAILY across DST fall-back in America/New_York

- RRULE: `FREQ=DAILY;COUNT=7`
- DTSTART: 20251101T130000Z (ms=1762002000000, TZID=America/New_York, isAllDay=false)
- Range: 20251101T130000Z .. 20251115T140000Z
- Result: **AGREE** — both engines returned 7 occurrences: 20251101T130000Z, 20251102T140000Z, 20251103T140000Z, 20251104T140000Z, 20251105T140000Z, 20251106T140000Z, 20251107T140000Z
- Classification: D


### existing: WEEKLY across DST in Europe/London

- RRULE: `FREQ=WEEKLY;COUNT=8`
- DTSTART: 20250301T090000Z (ms=1740819600000, TZID=Europe/London, isAllDay=false)
- Range: 20250301T000000Z .. 20250430T230000Z
- Result: **AGREE** — both engines returned 8 occurrences: 20250301T090000Z, 20250308T090000Z, 20250315T090000Z, 20250322T090000Z, 20250329T090000Z, 20250405T080000Z, 20250412T080000Z, 20250419T080000Z
- Classification: D


### existing: FREQ=WEEKLY UNTIL=20260202 all-day

- RRULE: `FREQ=WEEKLY;UNTIL=20260202`
- DTSTART: 20250901T000000Z (ms=1756684800000, TZID=(floating), isAllDay=true)
- Range: 20250901T000000Z .. 20260301T000000Z
- Result: **AGREE** — both engines returned 23 occurrences: 20250901T000000Z, 20250908T000000Z, 20250915T000000Z … (17 more) … 20260119T000000Z, 20260126T000000Z, 20260202T000000Z
- Classification: D


### existing: FREQ=MONTHLY UNTIL=20260615T000000Z timed

- RRULE: `FREQ=MONTHLY;UNTIL=20260615T000000Z`
- DTSTART: 20251015T100000Z (ms=1760522400000, TZID=UTC, isAllDay=false)
- Range: 20251001T000000Z .. 20260701T000000Z
- Result: **AGREE** — both engines returned 8 occurrences: 20251015T100000Z, 20251115T100000Z, 20251215T100000Z, 20260115T100000Z, 20260215T100000Z, 20260315T100000Z, 20260415T100000Z, 20260515T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYDAY=1FR first Friday COUNT=12

- RRULE: `FREQ=MONTHLY;BYDAY=1FR;COUNT=12`
- DTSTART: 20250103T100000Z (ms=1735898400000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 12 occurrences: 20250103T100000Z, 20250207T100000Z, 20250307T100000Z … (6 more) … 20251003T100000Z, 20251107T100000Z, 20251205T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYMONTHDAY=1,15 twice monthly

- RRULE: `FREQ=MONTHLY;BYMONTHDAY=1,15;COUNT=8`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20260101T000000Z
- Result: **AGREE** — both engines returned 8 occurrences: 20250101T100000Z, 20250115T100000Z, 20250201T100000Z, 20250215T100000Z, 20250301T100000Z, 20250315T100000Z, 20250401T100000Z, 20250415T100000Z
- Classification: D


### existing: FREQ=YEARLY BYMONTH=2 BYMONTHDAY=29 leap day anniversary

- RRULE: `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29;COUNT=3`
- DTSTART: 20200229T100000Z (ms=1582970400000, TZID=UTC, isAllDay=false)
- Range: 20200101T000000Z .. 20400101T000000Z
- Result: **AGREE** — both engines returned 3 occurrences: 20200229T100000Z, 20240229T100000Z, 20280229T100000Z
- Classification: D


### existing: RDATE adds out-of-pattern date

- RRULE: `FREQ=WEEKLY;BYDAY=MO;COUNT=3`
- DTSTART: 20250106T100000Z (ms=1736157600000, TZID=UTC, isAllDay=false)
- RDATE: `20250108T100000Z`
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 4 occurrences: 20250106T100000Z, 20250108T100000Z, 20250113T100000Z, 20250120T100000Z
- Classification: D


### existing: EXDATE removes occurrence from pattern

- RRULE: `FREQ=WEEKLY;BYDAY=MO;COUNT=4`
- DTSTART: 20250106T100000Z (ms=1736157600000, TZID=UTC, isAllDay=false)
- EXDATE: `20250113T100000Z`
- Range: 20250101T000000Z .. 20250215T000000Z
- Result: **AGREE** — both engines returned 3 occurrences: 20250106T100000Z, 20250120T100000Z, 20250127T100000Z
- Classification: D


### existing: RDATE and EXDATE combined

- RRULE: `FREQ=WEEKLY;BYDAY=MO;COUNT=3`
- DTSTART: 20250106T100000Z (ms=1736157600000, TZID=UTC, isAllDay=false)
- RDATE: `20250108T100000Z`
- EXDATE: `20250113T100000Z`
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 3 occurrences: 20250106T100000Z, 20250108T100000Z, 20250120T100000Z
- Classification: D


### existing: FREQ=MONTHLY BYMONTHDAY=31 across 2024 (leap)

- RRULE: `FREQ=MONTHLY;BYMONTHDAY=31`
- DTSTART: 20240131T100000Z (ms=1706695200000, TZID=UTC, isAllDay=false)
- Range: 20240101T000000Z .. 20250101T000000Z
- Result: **AGREE** — both engines returned 7 occurrences: 20240131T100000Z, 20240331T100000Z, 20240531T100000Z, 20240731T100000Z, 20240831T100000Z, 20241031T100000Z, 20241231T100000Z
- Classification: D


### existing: FREQ=YEARLY BYYEARDAY=-1 last day of year

- RRULE: `FREQ=YEARLY;BYYEARDAY=-1;COUNT=4`
- DTSTART: 20241231T100000Z (ms=1735639200000, TZID=UTC, isAllDay=false)
- Range: 20240101T000000Z .. 20300101T000000Z
- Result: **AGREE** — both engines returned 4 occurrences: 20241231T100000Z, 20251231T100000Z, 20261231T100000Z, 20271231T100000Z
- Classification: D


### existing: FREQ=HOURLY COUNT=24 over one day

- RRULE: `FREQ=HOURLY;COUNT=24`
- DTSTART: 20250101T000000Z (ms=1735689600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250102T000000Z
- Result: **AGREE** — both engines returned 24 occurrences: 20250101T000000Z, 20250101T010000Z, 20250101T020000Z … (18 more) … 20250101T210000Z, 20250101T220000Z, 20250101T230000Z
- Classification: D


### existing: FREQ=WEEKLY BYDAY=SA,SU weekends COUNT=10

- RRULE: `FREQ=WEEKLY;BYDAY=SA,SU;COUNT=10`
- DTSTART: 20250104T100000Z (ms=1735984800000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250301T000000Z
- Result: **AGREE** — both engines returned 10 occurrences: 20250104T100000Z, 20250105T100000Z, 20250111T100000Z … (4 more) … 20250126T100000Z, 20250201T100000Z, 20250202T100000Z
- Classification: D


### existing: FREQ=DAILY WKST=SU has no effect (daily ignores WKST)

- RRULE: `FREQ=DAILY;COUNT=5;WKST=SU`
- DTSTART: 20250101T100000Z (ms=1735725600000, TZID=UTC, isAllDay=false)
- Range: 20250101T000000Z .. 20250201T000000Z
- Result: **AGREE** — both engines returned 5 occurrences: 20250101T100000Z, 20250102T100000Z, 20250103T100000Z, 20250104T100000Z, 20250105T100000Z
- Classification: D


## Pool: rfc (39 cases)

### RFC 5545 §3.8.5.3 example 1: daily for 10 occurrences

- RRULE: `FREQ=DAILY;COUNT=10`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19970930T130000Z
- RFC expected: 19970902T130000Z, 19970903T130000Z, 19970904T130000Z … (4 more) … 19970909T130000Z, 19970910T130000Z, 19970911T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970902T130000Z, 19970903T130000Z, 19970904T130000Z … (4 more) … 19970909T130000Z, 19970910T130000Z, 19970911T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 2: daily until December 24 1997

- RRULE: `FREQ=DAILY;UNTIL=19971224T000000Z`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971225T140000Z
- RFC expected: 19970902T130000Z, 19970903T130000Z, 19970904T130000Z … (107 more) … 19971221T140000Z, 19971222T140000Z, 19971223T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 113 occurrences: 19970902T130000Z, 19970903T130000Z, 19970904T130000Z … (107 more) … 19971221T140000Z, 19971222T140000Z, 19971223T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 3: every other day forever (bounded to Dec 1 1997)

- RRULE: `FREQ=DAILY;INTERVAL=2`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971202T140000Z
- RFC expected: 19970902T130000Z, 19970904T130000Z, 19970906T130000Z … (40 more) … 19971127T140000Z, 19971129T140000Z, 19971201T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 46 occurrences: 19970902T130000Z, 19970904T130000Z, 19970906T130000Z … (40 more) … 19971127T140000Z, 19971129T140000Z, 19971201T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 4: every 10 days 5 occurrences

- RRULE: `FREQ=DAILY;INTERVAL=10;COUNT=5`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971031T140000Z
- RFC expected: 19970902T130000Z, 19970912T130000Z, 19970922T130000Z, 19971002T130000Z, 19971012T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 5 occurrences: 19970902T130000Z, 19970912T130000Z, 19970922T130000Z, 19971002T130000Z, 19971012T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 5: every day in January for 3 years

- RRULE: `FREQ=YEARLY;UNTIL=20000131T140000Z;BYMONTH=1;BYDAY=SU,MO,TU,WE,TH,FR,SA`
- DTSTART: 19980101T140000Z (ms=883663200000, TZID=America/New_York, isAllDay=false)
- Range: 19971201T140000Z .. 20000201T140000Z
- RFC expected: 19980101T140000Z, 19980102T140000Z, 19980103T140000Z … (87 more) … 20000129T140000Z, 20000130T140000Z, 20000131T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 93 occurrences: 19980101T140000Z, 19980102T140000Z, 19980103T140000Z … (87 more) … 20000129T140000Z, 20000130T140000Z, 20000131T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 6: weekly for 10 occurrences

- RRULE: `FREQ=WEEKLY;COUNT=10`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971130T140000Z
- RFC expected: 19970902T130000Z, 19970909T130000Z, 19970916T130000Z … (4 more) … 19971021T130000Z, 19971028T140000Z, 19971104T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970902T130000Z, 19970909T130000Z, 19970916T130000Z … (4 more) … 19971021T130000Z, 19971028T140000Z, 19971104T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 7: weekly until December 24 1997

- RRULE: `FREQ=WEEKLY;UNTIL=19971224T000000Z`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971225T140000Z
- RFC expected: 19970902T130000Z, 19970909T130000Z, 19970916T130000Z … (11 more) … 19971209T140000Z, 19971216T140000Z, 19971223T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 17 occurrences: 19970902T130000Z, 19970909T130000Z, 19970916T130000Z … (11 more) … 19971209T140000Z, 19971216T140000Z, 19971223T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 8: every other week forever (bounded to listed occurrences)

- RRULE: `FREQ=WEEKLY;INTERVAL=2;WKST=SU`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980218T140000Z
- RFC expected: 19970902T130000Z, 19970916T130000Z, 19970930T130000Z … (7 more) … 19980120T140000Z, 19980203T140000Z, 19980217T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 13 occurrences: 19970902T130000Z, 19970916T130000Z, 19970930T130000Z … (7 more) … 19980120T140000Z, 19980203T140000Z, 19980217T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 9: weekly on Tuesday and Thursday for five weeks (COUNT=10)

- RRULE: `FREQ=WEEKLY;COUNT=10;WKST=SU;BYDAY=TU,TH`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971010T130000Z
- RFC expected: 19970902T130000Z, 19970904T130000Z, 19970909T130000Z … (4 more) … 19970925T130000Z, 19970930T130000Z, 19971002T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970902T130000Z, 19970904T130000Z, 19970909T130000Z … (4 more) … 19970925T130000Z, 19970930T130000Z, 19971002T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 10: every other week MoWeFr until Dec 24 1997

- RRULE: `FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T000000Z;WKST=SU;BYDAY=MO,WE,FR`
- DTSTART: 19970901T130000Z (ms=873118800000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971225T140000Z
- RFC expected: 19970901T130000Z, 19970903T130000Z, 19970905T130000Z … (19 more) … 19971210T140000Z, 19971212T140000Z, 19971222T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 25 occurrences: 19970901T130000Z, 19970903T130000Z, 19970905T130000Z … (19 more) … 19971210T140000Z, 19971212T140000Z, 19971222T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 11: every other week TuTh for 8 occurrences

- RRULE: `FREQ=WEEKLY;INTERVAL=2;COUNT=8;WKST=SU;BYDAY=TU,TH`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971020T130000Z
- RFC expected: 19970902T130000Z, 19970904T130000Z, 19970916T130000Z, 19970918T130000Z, 19970930T130000Z, 19971002T130000Z, 19971014T130000Z, 19971016T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 8 occurrences: 19970902T130000Z, 19970904T130000Z, 19970916T130000Z, 19970918T130000Z, 19970930T130000Z, 19971002T130000Z, 19971014T130000Z, 19971016T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 12: monthly on the first Friday for 10 occurrences

- RRULE: `FREQ=MONTHLY;COUNT=10;BYDAY=1FR`
- DTSTART: 19970905T130000Z (ms=873464400000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980701T130000Z
- RFC expected: 19970905T130000Z, 19971003T130000Z, 19971107T140000Z … (4 more) … 19980403T140000Z, 19980501T130000Z, 19980605T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970905T130000Z, 19971003T130000Z, 19971107T140000Z … (4 more) … 19980403T140000Z, 19980501T130000Z, 19980605T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 13: monthly on the first Friday until Dec 24 1997

- RRULE: `FREQ=MONTHLY;UNTIL=19971224T000000Z;BYDAY=1FR`
- DTSTART: 19970905T130000Z (ms=873464400000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971225T140000Z
- RFC expected: 19970905T130000Z, 19971003T130000Z, 19971107T140000Z, 19971205T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 4 occurrences: 19970905T130000Z, 19971003T130000Z, 19971107T140000Z, 19971205T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 14: every other month 1st and last Sunday for 10 occurrences

- RRULE: `FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYDAY=1SU,-1SU`
- DTSTART: 19970907T130000Z (ms=873637200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980601T130000Z
- RFC expected: 19970907T130000Z, 19970928T130000Z, 19971102T140000Z … (4 more) … 19980329T140000Z, 19980503T130000Z, 19980531T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970907T130000Z, 19970928T130000Z, 19971102T140000Z … (4 more) … 19980329T140000Z, 19980503T130000Z, 19980531T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 15: monthly second-to-last Monday for 6 months

- RRULE: `FREQ=MONTHLY;COUNT=6;BYDAY=-2MO`
- DTSTART: 19970922T130000Z (ms=874933200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980301T140000Z
- RFC expected: 19970922T130000Z, 19971020T130000Z, 19971117T140000Z, 19971222T140000Z, 19980119T140000Z, 19980216T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 6 occurrences: 19970922T130000Z, 19971020T130000Z, 19971117T140000Z, 19971222T140000Z, 19980119T140000Z, 19980216T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 16: monthly third-to-last day forever (bounded to Feb 1998)

- RRULE: `FREQ=MONTHLY;BYMONTHDAY=-3`
- DTSTART: 19970928T130000Z (ms=875451600000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980301T140000Z
- RFC expected: 19970928T130000Z, 19971029T140000Z, 19971128T140000Z, 19971229T140000Z, 19980129T140000Z, 19980226T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 6 occurrences: 19970928T130000Z, 19971029T140000Z, 19971128T140000Z, 19971229T140000Z, 19980129T140000Z, 19980226T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 17: monthly on 2nd and 15th for 10 occurrences

- RRULE: `FREQ=MONTHLY;COUNT=10;BYMONTHDAY=2,15`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980201T140000Z
- RFC expected: 19970902T130000Z, 19970915T130000Z, 19971002T130000Z … (4 more) … 19971215T140000Z, 19980102T140000Z, 19980115T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970902T130000Z, 19970915T130000Z, 19971002T130000Z … (4 more) … 19971215T140000Z, 19980102T140000Z, 19980115T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 18: monthly on 1st and last day for 10 occurrences

- RRULE: `FREQ=MONTHLY;COUNT=10;BYMONTHDAY=1,-1`
- DTSTART: 19970930T130000Z (ms=875624400000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980215T140000Z
- RFC expected: 19970930T130000Z, 19971001T130000Z, 19971031T140000Z … (4 more) … 19980101T140000Z, 19980131T140000Z, 19980201T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970930T130000Z, 19971001T130000Z, 19971031T140000Z … (4 more) … 19980101T140000Z, 19980131T140000Z, 19980201T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 19: every 18 months on 10th thru 15th for 10 occurrences

- RRULE: `FREQ=MONTHLY;INTERVAL=18;COUNT=10;BYMONTHDAY=10,11,12,13,14,15`
- DTSTART: 19970910T130000Z (ms=873896400000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19990401T140000Z
- RFC expected: 19970910T130000Z, 19970911T130000Z, 19970912T130000Z … (4 more) … 19990311T140000Z, 19990312T140000Z, 19990313T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970910T130000Z, 19970911T130000Z, 19970912T130000Z … (4 more) … 19990311T140000Z, 19990312T140000Z, 19990313T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 20: every Tuesday every other month forever (bounded to Mar 1998)

- RRULE: `FREQ=MONTHLY;INTERVAL=2;BYDAY=TU`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980401T140000Z
- RFC expected: 19970902T130000Z, 19970909T130000Z, 19970916T130000Z … (12 more) … 19980317T140000Z, 19980324T140000Z, 19980331T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 18 occurrences: 19970902T130000Z, 19970909T130000Z, 19970916T130000Z … (12 more) … 19980317T140000Z, 19980324T140000Z, 19980331T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 21: yearly in June and July for 10 occurrences

- RRULE: `FREQ=YEARLY;COUNT=10;BYMONTH=6,7`
- DTSTART: 19970610T130000Z (ms=865947600000, TZID=America/New_York, isAllDay=false)
- Range: 19970601T130000Z .. 20010801T130000Z
- RFC expected: 19970610T130000Z, 19970710T130000Z, 19980610T130000Z … (4 more) … 20000710T130000Z, 20010610T130000Z, 20010710T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970610T130000Z, 19970710T130000Z, 19980610T130000Z … (4 more) … 20000710T130000Z, 20010610T130000Z, 20010710T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 22: every other year Jan/Feb/Mar for 10 occurrences

- RRULE: `FREQ=YEARLY;INTERVAL=2;COUNT=10;BYMONTH=1,2,3`
- DTSTART: 19970310T140000Z (ms=858002400000, TZID=America/New_York, isAllDay=false)
- Range: 19970301T140000Z .. 20030401T140000Z
- RFC expected: 19970310T140000Z, 19990110T140000Z, 19990210T140000Z … (4 more) … 20030110T140000Z, 20030210T140000Z, 20030310T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970310T140000Z, 19990110T140000Z, 19990210T140000Z … (4 more) … 20030110T140000Z, 20030210T140000Z, 20030310T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 23: every 3 years on 1st 100th 200th day for 10 occurrences

- RRULE: `FREQ=YEARLY;INTERVAL=3;COUNT=10;BYYEARDAY=1,100,200`
- DTSTART: 19970101T140000Z (ms=852127200000, TZID=America/New_York, isAllDay=false)
- Range: 19961201T140000Z .. 20060201T140000Z
- RFC expected: 19970101T140000Z, 19970410T130000Z, 19970719T130000Z … (4 more) … 20030410T130000Z, 20030719T130000Z, 20060101T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970101T140000Z, 19970410T130000Z, 19970719T130000Z … (4 more) … 20030410T130000Z, 20030719T130000Z, 20060101T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 24: every 20th Monday of the year forever (bounded to 1999)

- RRULE: `FREQ=YEARLY;BYDAY=20MO`
- DTSTART: 19970519T130000Z (ms=864046800000, TZID=America/New_York, isAllDay=false)
- Range: 19970501T130000Z .. 19990601T130000Z
- RFC expected: 19970519T130000Z, 19980518T130000Z, 19990517T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 3 occurrences: 19970519T130000Z, 19980518T130000Z, 19990517T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 25: Monday of ISO week 20 forever (bounded to 1999)

- RRULE: `FREQ=YEARLY;BYWEEKNO=20;BYDAY=MO`
- DTSTART: 19970512T130000Z (ms=863442000000, TZID=America/New_York, isAllDay=false)
- Range: 19970501T130000Z .. 19990601T130000Z
- RFC expected: 19970512T130000Z, 19980511T130000Z, 19990517T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 3 occurrences: 19970512T130000Z, 19980511T130000Z, 19990517T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 26: every Thursday in March forever (bounded to 1999)

- RRULE: `FREQ=YEARLY;BYMONTH=3;BYDAY=TH`
- DTSTART: 19970313T140000Z (ms=858261600000, TZID=America/New_York, isAllDay=false)
- Range: 19970301T140000Z .. 19990401T140000Z
- RFC expected: 19970313T140000Z, 19970320T140000Z, 19970327T140000Z … (5 more) … 19990311T140000Z, 19990318T140000Z, 19990325T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 11 occurrences: 19970313T140000Z, 19970320T140000Z, 19970327T140000Z … (5 more) … 19990311T140000Z, 19990318T140000Z, 19990325T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 27: every Thursday in Jun_Jul_Aug forever (bounded to 1999)

- RRULE: `FREQ=YEARLY;BYDAY=TH;BYMONTH=6,7,8`
- DTSTART: 19970605T130000Z (ms=865515600000, TZID=America/New_York, isAllDay=false)
- Range: 19970601T130000Z .. 19990901T130000Z
- RFC expected: 19970605T130000Z, 19970612T130000Z, 19970619T130000Z … (33 more) … 19990812T130000Z, 19990819T130000Z, 19990826T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 39 occurrences: 19970605T130000Z, 19970612T130000Z, 19970619T130000Z … (33 more) … 19990812T130000Z, 19990819T130000Z, 19990826T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 28: every Friday 13th forever with EXDATE on DTSTART

- RRULE: `FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- EXDATE: `19970902T090000`
- Range: 19970901T130000Z .. 20001014T130000Z
- RFC expected: 19980213T140000Z, 19980313T140000Z, 19981113T140000Z, 19990813T130000Z, 20001013T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 5 occurrences: 19980213T140000Z, 19980313T140000Z, 19981113T140000Z, 19990813T130000Z, 20001013T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 29: first Saturday after first Sunday forever (bounded to Jun 1998)

- RRULE: `FREQ=MONTHLY;BYDAY=SA;BYMONTHDAY=7,8,9,10,11,12,13`
- DTSTART: 19970913T130000Z (ms=874155600000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980701T130000Z
- RFC expected: 19970913T130000Z, 19971011T130000Z, 19971108T140000Z … (4 more) … 19980411T130000Z, 19980509T130000Z, 19980613T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 10 occurrences: 19970913T130000Z, 19971011T130000Z, 19971108T140000Z … (4 more) … 19980411T130000Z, 19980509T130000Z, 19980613T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 30: every 4 years US Election Day forever (bounded to 2004)

- RRULE: `FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8`
- DTSTART: 19961105T140000Z (ms=847202400000, TZID=America/New_York, isAllDay=false)
- Range: 19961101T140000Z .. 20041201T140000Z
- RFC expected: 19961105T140000Z, 20001107T140000Z, 20041102T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 3 occurrences: 19961105T140000Z, 20001107T140000Z, 20041102T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 31: third instance of TuWeTh for next 3 months (BYSETPOS=3)

- RRULE: `FREQ=MONTHLY;COUNT=3;BYDAY=TU,WE,TH;BYSETPOS=3`
- DTSTART: 19970904T130000Z (ms=873378000000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19971201T140000Z
- RFC expected: 19970904T130000Z, 19971007T130000Z, 19971106T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 3 occurrences: 19970904T130000Z, 19971007T130000Z, 19971106T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 32: second-to-last weekday of month forever (bounded to Mar 1998)

- RRULE: `FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-2`
- DTSTART: 19970929T130000Z (ms=875538000000, TZID=America/New_York, isAllDay=false)
- Range: 19970901T130000Z .. 19980401T140000Z
- RFC expected: 19970929T130000Z, 19971030T140000Z, 19971127T140000Z, 19971230T140000Z, 19980129T140000Z, 19980226T140000Z, 19980330T140000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 7 occurrences: 19970929T130000Z, 19971030T140000Z, 19971127T140000Z, 19971230T140000Z, 19980129T140000Z, 19980226T140000Z, 19980330T140000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 33: every 3 hours on Sep 2 1997 until 17Z

- RRULE: `FREQ=HOURLY;INTERVAL=3;UNTIL=19970902T170000Z`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970902T040000Z .. 19970903T040000Z
- RFC expected: 19970902T130000Z, 19970902T160000Z, 19970902T190000Z
- lib-recur matches RFC: false
- ical4j matches RFC: false
- Result: **AGREE** — both engines returned 2 occurrences: 19970902T130000Z, 19970902T160000Z
- Classification: D
- Known divergence reason: RFC prints 09:00,12:00,15:00 but UNTIL=19970902T170000Z (13:00 EDT) excludes 15:00 EDT. The printed output contradicts the example's own UTC UNTIL; both engines honor the UNTIL and yield 2 occurrences.


### RFC 5545 §3.8.5.3 example 34: every 15 minutes for 6 occurrences

- RRULE: `FREQ=MINUTELY;INTERVAL=15;COUNT=6`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970902T040000Z .. 19970903T040000Z
- RFC expected: 19970902T130000Z, 19970902T131500Z, 19970902T133000Z, 19970902T134500Z, 19970902T140000Z, 19970902T141500Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 6 occurrences: 19970902T130000Z, 19970902T131500Z, 19970902T133000Z, 19970902T134500Z, 19970902T140000Z, 19970902T141500Z
- Classification: D


### RFC 5545 §3.8.5.3 example 35: every 90 minutes for 4 occurrences

- RRULE: `FREQ=MINUTELY;INTERVAL=90;COUNT=4`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970902T040000Z .. 19970903T040000Z
- RFC expected: 19970902T130000Z, 19970902T143000Z, 19970902T160000Z, 19970902T173000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 4 occurrences: 19970902T130000Z, 19970902T143000Z, 19970902T160000Z, 19970902T173000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 36: every 20 minutes from 9am to 4_40pm for 3 days

- RRULE: `FREQ=DAILY;BYHOUR=9,10,11,12,13,14,15,16;BYMINUTE=0,20,40`
- DTSTART: 19970902T130000Z (ms=873205200000, TZID=America/New_York, isAllDay=false)
- Range: 19970902T040000Z .. 19970905T040000Z
- RFC expected: 19970902T130000Z, 19970902T132000Z, 19970902T134000Z … (66 more) … 19970904T200000Z, 19970904T202000Z, 19970904T204000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 72 occurrences: 19970902T130000Z, 19970902T132000Z, 19970902T134000Z … (66 more) … 19970904T200000Z, 19970904T202000Z, 19970904T204000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 37a: WKST=MO changes bi-weekly TuSu expansion

- RRULE: `FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=MO`
- DTSTART: 19970805T130000Z (ms=870786000000, TZID=America/New_York, isAllDay=false)
- Range: 19970801T130000Z .. 19970901T130000Z
- RFC expected: 19970805T130000Z, 19970810T130000Z, 19970819T130000Z, 19970824T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 4 occurrences: 19970805T130000Z, 19970810T130000Z, 19970819T130000Z, 19970824T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 37b: WKST=SU changes bi-weekly TuSu expansion

- RRULE: `FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU`
- DTSTART: 19970805T130000Z (ms=870786000000, TZID=America/New_York, isAllDay=false)
- Range: 19970801T130000Z .. 19970901T130000Z
- RFC expected: 19970805T130000Z, 19970817T130000Z, 19970819T130000Z, 19970831T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 4 occurrences: 19970805T130000Z, 19970817T130000Z, 19970819T130000Z, 19970831T130000Z
- Classification: D


### RFC 5545 §3.8.5.3 example 38: invalid date Feb 30 ignored (BYMONTHDAY=15,30)

- RRULE: `FREQ=MONTHLY;BYMONTHDAY=15,30;COUNT=5`
- DTSTART: 20070115T140000Z (ms=1168869600000, TZID=America/New_York, isAllDay=false)
- Range: 20070101T140000Z .. 20070501T130000Z
- RFC expected: 20070115T140000Z, 20070130T140000Z, 20070215T140000Z, 20070315T130000Z, 20070330T130000Z
- lib-recur matches RFC: true
- ical4j matches RFC: true
- Result: **AGREE** — both engines returned 5 occurrences: 20070115T140000Z, 20070130T140000Z, 20070215T140000Z, 20070315T130000Z, 20070330T130000Z
- Classification: D





---

## Methodology

Generated by `RRuleEngineParityReportTest`. Every RRULE case from the four fixture pools (Pool A: RFC 5545 §3.8.5.3 examples, Pool B: CRITICAL-quirk reproducers, Pool C: inputs from existing test suites, Pool D: adversarial inputs) is driven through two engines:

- **lib-recur** — `org.dmfs.rfc5545` via `org.onekash.kashcal.domain.generator.LibRecurEngine` (app-side).
- **ical4j** — via `icaldav-core`'s `RRuleExpander` (library-side).

Each engine call is bounded by a 10-second wall-clock timeout to prevent adversarial cases (unbounded SECONDLY/MINUTELY, open-ended range queries) from hanging the run.

### Classification

- **A** — clear bug per RFC. Pool A case where exactly one engine matches the RFC-spec ground truth. The other engine is wrong.
- **B** — RFC ambiguity or non-Pool-A divergence. No authoritative ground truth available, or both engines diverge from the RFC in the same direction.
- **C** — scope gap. One or both engines timed out or threw an exception.
- **D** — identical. Both engines produced the same set of timestamps.

