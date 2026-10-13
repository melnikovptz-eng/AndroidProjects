# PMCA First Screen — implementation note

## Implemented
- MainActivity: manual command field and compact rows of controls.
- PmcaController: central entry point for UI actions.
- PmcaLogger: in-memory ring buffer capped at 50 lines.
- ClipboardCopyModule: copy/read Android clipboard.
- TermuxBridgeAdapter: writes SHELL command JSON to the shared Taper bridge folder.
- Existing command.json is never deleted or overwritten by the adapter.

## Current limitation
This first screen reports command submission, not completion. It deliberately does not read a potentially stale result.json as a fresh answer. The next step is to bind result polling to the exact request/response contract implemented by the current Termux bridge daemon.

## Routing note
The button “Выполнить в Termux” sends the field content as a SHELL payload. Only run commands you trust. The “В чат” button is not included because the app has no supported direct ChatGPT conversation API.

## Build
Gradle Android application plugin 8.6.1, compileSdk 35, Java 17, minSdk 23.
