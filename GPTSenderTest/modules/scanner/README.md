# Accessibility Scanner

**ID:** \`pmca.module.scanner\`  
**Status:** Implemented inside GPTSenderTest; extraction into a standalone Gradle library is pending.

## Responsibility
Find the current active accessibility root and optionally filter by a configured target package. It must not insert text, click controls, or own workflow decisions.

## Inputs and outputs
- Input: current AccessibilityService state and configured target package.
- Output: root node or null when unavailable/not matching.

## Failure cases and limits
The target app may expose incomplete accessibility metadata; the active root may be null or belong to another package. Callers must handle null and validate target identity before side effects.

## Tests
Automated module-focused tests are not yet present. Before library extraction, test null roots, target-package mismatch, configurable package behavior, and activity/window changes. Manual device verification is required.

## Current implementation
See \`app/src/main/java/com/test/accessibility3/modules/scanner/ChatGPTScanner.java\`.
