# GPTSenderTest configuration

**ID:** \`pmca.project.gptsender.config\`

## Responsibility
Provide the single source of truth for GPTSenderTest target package names, user-confirmed send coordinates, timing values, and diagnostic defaults. Shared modules must receive project-specific values through their API; they must not duplicate these constants.

## Important constraints
- Current send target: \`x=965, y=2125\` in physical display pixels. Re-validate after display/orientation/UI changes.
- The input field is found through Accessibility; no fixed input coordinates are defined.
- Configuration values do not prove that the target is visible or safe to click.

## Tests
Static passport validation is automated. Dedicated unit tests for configuration validation and display-change handling remain pending.
