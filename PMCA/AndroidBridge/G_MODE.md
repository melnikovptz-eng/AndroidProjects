# PMCA G mode

## What was added

- A **G** button on the PMCA main screen toggles G mode.
- G mode uses a separate `GModePanelService`; the existing compact panel is not replaced.
- A narrow, light panel is anchored to the bottom edge and contains three icon-only buttons:
  1. `↔` moves the panel to the opposite edge.
  2. Black `T` with a white letter opens the existing `ClipboardPasteActivity`, which reads the clipboard and sends the text through the existing PMCA controller to Termux.
  3. `⧉` copies the stored Termux result through `PmcaController.copy`.
- The selected edge is persisted in `pmca_g_mode/side_right` and restored on the next entry.
- Button taps animate with a brief press effect. The copy icon changes to a check mark on success or an X if no result is available/copying fails.
- The panel listens for the existing PMCA clipboard-state broadcast to give the T button a visual status.
- The panel makes a best-effort attempt to move above the on-screen keyboard using visible-frame measurements; Android may not expose reliable IME geometry to a non-focusable overlay on every device.
- G mode launches the ChatGPT Android app if it is installed and requests adjacent launch when Android split-screen is already active.
- The overlay permission is checked before enabling the mode.

## Android split-screen limitation

A regular third-party Android app cannot guarantee that another app will be placed into a dedicated half-screen area programmatically on every device. `FLAG_ACTIVITY_LAUNCH_ADJACENT` is only a request and is most useful when split-screen is already active. Without system support or a user-initiated split-screen action, G mode opens ChatGPT and keeps the PMCA panel floating at the selected edge; it does not resize ChatGPT's own window.

## Manual acceptance checklist

1. Open PMCA and tap **G**. Confirm the edge panel appears and ChatGPT is launched when installed.
2. Tap `↔`; confirm the panel moves from left to right and back.
3. Close/reopen G mode; confirm the last selected edge is restored.
4. Put text in the Android clipboard, tap `T`, and confirm the existing clipboard activity passes it to Termux.
5. After a Termux result is stored, tap `⧉`; confirm the result is copied and the icon briefly shows a check mark.
6. Tap copy before a result exists; confirm the icon briefly indicates failure and PMCA remains responsive.
7. Revoke overlay permission and attempt to enable G mode; confirm the app requests permission rather than crashing.
8. Test while the keyboard is open and in landscape/split-screen; check that the overlay does not obscure important ChatGPT controls.

## Validation status

Source changes have been committed to the feature branch. They have not yet been built or exercised on a physical Android device, so the checklist above remains to be run.
