# AutoClicker module

**ID:** \`pmca.module.clicker\`  
**Status:** Implemented inside GPTSenderTest; extraction into a standalone Gradle library is pending.

## Responsibility
Execute a validated click request through Android AccessibilityService and return the actual gesture/click API result. The clicker does not decide whether a target is safe or whether a message was delivered.

## Input and output
- Input: physical display-pixel coordinates, action ID, and expected target package.
- Output: \`ClickResult\` with accepted/rejected state and diagnostic details.

## Safety and failure cases
- Caller must verify the active package and intended target.
- Reject invalid/out-of-bounds coordinates.
- Gesture completion is not proof of target-app processing or message delivery.
- Service unavailable, invalid bounds, or failed gesture must produce a failed result; no unbounded retries.

## Tests
Automated module-focused unit tests are not yet present. Before library extraction, add tests for invalid bounds, missing service, success/failure callback outcomes, duplicate action handling, and no false delivery confirmation. Manual Android-device verification is also required.

## Current implementation
See \`app/src/main/java/com/test/accessibility3/modules/clicker/AutoClicker.java\` and \`app/src/main/java/com/test/accessibility3/api/\`.
