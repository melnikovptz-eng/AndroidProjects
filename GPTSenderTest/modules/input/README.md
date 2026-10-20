# Input Finder and Text Inserter

**ID:** \`pmca.module.input\`  
**Status:** Implemented inside GPTSenderTest; extraction into a standalone Gradle library is pending.

## Responsibility
Find an enabled editable accessibility node, clear its existing text, and insert supplied text using supported Accessibility APIs. It must not use guessed screen coordinates to focus the input field.

## Inputs and outputs
- Input: active accessibility root and text/action request.
- Output: editable node or null; boolean result for clear/insert operations.

## Failure cases
Root unavailable, editable node not found, action rejected, or target UI replacing the node. Each failure must be reported to the caller and must not trigger an unbounded retry.

## Tests
Automated module-focused tests are not yet present. Before library extraction, test null roots, editable/non-editable nodes, ACTION_SET_TEXT rejection, stale node refresh, empty text, and bounded search behavior. Manual device verification is required.

## Current implementation
See \`app/src/main/java/com/test/accessibility3/modules/input/\`.
