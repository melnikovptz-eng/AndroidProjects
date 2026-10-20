## Summary
<!-- What changed and why? -->

## Mandatory PMCA preflight (complete before implementation)
- [ ] Read current README.md.
- [ ] Read docs/PROJECT_RULES.md.
- [ ] Read docs/MODULE_DEVELOPMENT.md.
- [ ] Read docs/PMCA_SHARED_PLATFORM_RULES.md.
- [ ] Read docs/PMCA_LOGGING_CONTRACT.md.
- [ ] Read docs/ANDROID_APP_REGISTRY.md when Android apps/APKs are affected.
- [ ] Read PMCA/ARCHITECTURE.md and PMCA/PROJECT_CONTRACT.md.
- [ ] Read target module/app passport, README, source, tests, Gradle config, and relevant workflows.
- [ ] Searched for existing implementations, logger call sites, services/daemons, and consumers before adding a component.
- [ ] Confirmed no duplicate implementation/handler/daemon is introduced, or documented the migration reason.
- [ ] Recorded package ID, namespace, versionCode, signing compatibility, and APK workflow impact when relevant.

**Preflight report**
- Rules/source reviewed:
- Existing implementation reused or reason for a new implementation:
- Dependencies and package/signing impact:
- Tests planned and limitations:

## Logging and privacy
- [ ] Successful and failed outcomes are logged, including successful sends/operations.
- [ ] Click/gesture completion is not reported as confirmed message delivery.
- [ ] No raw clipboard content, full user message, credentials, or signing secrets are logged by default.
- [ ] Existing canonical logger is reused; no duplicate operational log was added.
- [ ] Logs are bounded, persisted where required, and do not block the UI thread.

## Validation
- [ ] python3 tools/pmca_module_test.py --all
- [ ] python3 -m unittest tools/test_pmca_module_test.py
- [ ] python3 tools/pmca_android_audit.py
- [ ] Relevant Gradle unit tests and debug build.
- [ ] Reviewed actual GitHub Actions results (not just workflow configuration).
- [ ] Device-only checks and unverified behavior are listed honestly.

## Compatibility and scope
- [ ] No applicationId/signing identity changed without documented authorization.
- [ ] No unrelated working behavior or files were removed.
- [ ] Documentation/passports were updated with implementation changes.
