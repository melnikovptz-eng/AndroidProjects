# PMCA reusable modules: architecture contract

## Ownership of logic

- **PMCA Core** owns shared API contracts, module registration, compatibility checks, and common logging contracts.
- **Reusable modules** own one capability each: clicking, accessibility scanning, input discovery/text insertion, and diagnostic overlays.
- **Project adapters/coordinators** own application-specific workflows, target coordinates, package names, and safety preconditions.
- **Module passports** describe identity, API compatibility, dependencies, capabilities, input/output, permissions, and limitations.

## Click API

Projects call `ClickApi.click(ClickRequest, diagnostics)` and receive a `ClickResult`. The clicker implementation must validate requests and screen bounds and must be the only component that calls `dispatchGesture`. Projects must confirm the active package and intended target before asking it to tap.

## No fixed-coordinate input focus

The input module must find an editable accessibility node and use supported accessibility actions to clear or insert text. It must not tap an unverified hard-coded coordinate to focus the field.

## Passports

Each module directory contains `module.json` as the machine-readable passport. A passport is descriptive metadata, not proof of implementation or permissions. The registry/validator should compare declared API versions and dependencies with the actual build configuration.

## Reuse status

The current source files are still inside the GPTSenderTest Android application tree. Passports and public API contracts establish the extraction boundary; moving the implementation into independently versioned Gradle Android library modules is a separate build-structure step and must preserve a working APK build.

## Project constants

Project-specific values belong in `app/src/main/java/com/test/accessibility3/config/GptSenderConstants.java`:
- target application package names;
- target IDs;
- user-confirmed button coordinates;
- timing values and debug defaults.

This file is the single source of truth for GPTSenderTest-specific constants. Shared modules must accept project-specific values through constructors or API requests; they must not copy coordinates or package names into their own implementation. Input-field coordinates are deliberately not configured because the input must be found through Accessibility.
