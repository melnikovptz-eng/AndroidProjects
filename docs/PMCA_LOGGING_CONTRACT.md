# PMCA Logging Contract

Status: mandatory project contract for new modules, new applications, and changes to existing logging behavior.

## 1. Purpose and authority

This document defines the minimum observable logging behavior. It complements, and does not replace, `docs/PROJECT_RULES.md`, `docs/PMCA_SHARED_PLATFORM_RULES.md`, `docs/MODULE_DEVELOPMENT.md`, `PMCA/ARCHITECTURE.md`, and `PMCA/PROJECT_CONTRACT.md`.

Before implementing a logger or adding a new logging facility, inspect all existing logger implementations and their call sites. Reuse or extend the canonical logger where practical. Do not create another logger, parallel log file, or duplicate event pipeline without documenting the gap, owner, migration plan, and compatibility impact first.

## 2. Required event fields

Every structured operation event must provide the following fields. Fields that genuinely do not apply may be null; do not invent fake IDs or success states.

- `timestamp`: ISO-8601 date/time with UTC offset.
- `level`: `DEBUG`, `INFO`, `WARN`, or `ERROR`.
- `event`: stable machine-readable event name, such as `SEND_CLICK_COMPLETED`.
- `status`: `STARTED`, `SUCCESS`, `FAILED`, `TIMEOUT`, `CANCELLED`, `REJECTED`, or another documented finite state.
- `stage`: stage/operation name.
- `execution_id`: identifier for the logical execution, when one exists.
- `correlation_id`: identifier connecting related events across components, when one exists.
- `module_id`: canonical module ID, when applicable.
- `duration_ms`: elapsed time for completed operations, otherwise null.
- `error_code`: stable error code for failures, otherwise null.
- `details`: concise, redacted diagnostic metadata; never the raw user payload by default.

The implementation may use a documented typed object or JSON Lines format. If using structured files, write one complete event per line so partial writes and parsing failures are easier to isolate. Existing legacy formats may be preserved during migration, but their owner and migration path must be documented.

Example:
```json
{"timestamp":"2026-10-20T10:15:30.125+02:00","level":"INFO","event":"SEND_CLICK_COMPLETED","status":"SUCCESS","stage":"SEND_MESSAGE","execution_id":"EXEC-000142","correlation_id":"CORR-000142","module_id":"pmca.module.clicker","duration_ms":143,"error_code":null,"details":"gesture_callback=completed"}
```

## 3. Event coverage

Log both successful and unsuccessful outcomes. Log only stages that actually exist in the implementation.

### Common module/runtime events
- module/service start, stop, initialization success/failure;
- request accepted/rejected, validation result;
- duplicate detected/rejected;
- execution start, completion, failure, timeout, cancellation;
- dependency unavailable, retry started/completed/exhausted;
- health-check result and recovery result where implemented.

### PMCA pipeline and bridge
- input received and validation result;
- duplicate guard and processing-gate decision;
- queue admission/rejection and route decision, if those stages exist;
- module invocation and final result;
- Termux request creation/dispatch/rejection;
- Termux result received, parse failure, exit status;
- result delivered to UI or delivery failure.

### UI/accessibility/clicker modules
- target/input search started;
- target found/not found, with non-sensitive selector or node metadata;
- text insertion started/completed/failed;
- send-target validation result;
- click/gesture started and callback outcome;
- message delivery confirmed, or explicitly unconfirmed.

Important: a successful tap, click API return, or gesture callback is not proof that the target application sent the message. Log `SEND_CLICK_COMPLETED` separately from `MESSAGE_DELIVERY_CONFIRMED` and `MESSAGE_DELIVERY_UNCONFIRMED`. Do not claim delivery unless it was independently observed.

## 4. Errors, retries, and duplicate events

- A failure event records the stage, stable error code, exception type where safe, attempt number/max where applicable, and final outcome.
- Retries must be bounded, cancellable, and tied to the same execution/correlation ID.
- Avoid logging the same exception at every layer. The layer that handles/reports the final failure records it; upper layers may log that they received the failure with the same IDs.
- Logger failures must not restart or re-submit the original command and must not enter the PMCA processing pipeline.
- Do not silently swallow logging failures without any safe diagnostic signal. A logger must fail safely and must not crash the main operation solely because a log write failed.

## 5. Storage, performance, and retention

- Primary runtime logs are stored in app-private storage unless a documented platform constraint requires otherwise.
- Diagnostic traces (such as accessibility node dumps or coordinates) are distinct from normal operational logs and are enabled only in explicit diagnostic/debug mode.
- File writes must be concurrency-safe. Avoid blocking the Android main/UI thread; use a bounded asynchronous writer or another justified non-blocking design.
- Logs must be bounded by size and/or rotation and have a documented retention policy. Reading the last N lines must not require loading an unbounded file into memory.
- Logs should persist across process restarts to the extent required for post-failure diagnosis.
- Log-write errors are handled without recursive logging or repeated command execution.
- Document the file path/name, rotation limit, retention, access/export behavior, and whether the log is memory-only or persistent.

## 6. Privacy and redaction

Never log passwords, access tokens, private keys, signing secrets, authentication codes, clipboard contents, full user prompts/messages, or full ChatGPT/Termux result bodies by default.

Use metadata such as payload length, MIME/type, source, target identifier, result code, or a safe redacted excerpt only where justified. Raw payload capture requires an explicit diagnostic mode, clear user awareness, and a deliberate privacy review. Coordinates, accessibility tree data, and screenshots belong to explicit diagnostic mode and must not obstruct target UI controls.

## 7. Required tests

Add automated tests where practical for:
1. success event and failure event fields;
2. timeout, cancellation, duplicate rejection, and retry exhaustion;
3. correlation/execution IDs propagated across components;
4. concurrent writes and valid event boundaries;
5. rotation/retention and reading recent lines;
6. storage unavailable or write failure does not crash or re-run the operation;
7. redaction of sensitive data;
8. click callback success without false message-delivery confirmation;
9. logger does not block the UI thread or create recursive processing.

Document any device-only checks that cannot be automated. A log statement in source code is not proof that the event is actually written or that it captures the real outcome.

## 8. Existing implementation audit and migration

Before changing logging, inventory the current implementations, files, formats, callers, UI readers/exporters, and legacy files. In this repository, known implementations include `PMCA/AndroidBridge/.../PmcaLogger.java`, `GPTSenderTest/.../DebugLogger.java`, and the `GptSenderLog` call sites. Verify their current paths and all usages before editing.

Do not delete or rename existing log files/APIs until their consumers are identified and migrated. Prefer one authoritative operational logger per app/platform boundary, with diagnostic channels clearly separated by purpose. Document any temporary compatibility adapter and its removal condition.

## 9. Definition of done

- [ ] Existing logger implementations and call sites were audited before adding/changing a logger.
- [ ] Event fields and success/failure outcomes meet this contract.
- [ ] Sensitive payloads are redacted by default.
- [ ] File location, persistence, rotation, retention, and failure behavior are documented.
- [ ] Applicable tests cover successful and failed logging paths.
- [ ] The module passport/README states its important diagnostic events and limitations.
- [ ] Relevant automated tests, app build, and CI were run; results are reported accurately.
