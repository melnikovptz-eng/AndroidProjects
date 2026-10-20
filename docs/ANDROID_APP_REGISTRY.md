# Android application registry and release compatibility

This registry is a review aid, not permission to rename packages. Before changing an app's ID, namespace, signing key, or version, inspect installed-update compatibility and release workflows.

| Project | Gradle path | Current applicationId | Current namespace | Version |
|---|---|---|---|---|
| GPTSenderTest | \`GPTSenderTest/app/build.gradle\` | \`com.test.gptsender\` | \`com.test.accessibility3\` | \`versionCode 2\`, \`1.1-GPTSenderTest\` |
| PMCA AndroidBridge | \`PMCA/AndroidBridge/app/build.gradle\` | Read current Gradle configuration before release | Read current Gradle configuration before release | Read current Gradle configuration before release |

## Rules

- \`applicationId\` identifies the installed Android app. Keep it stable for in-place updates.
- \`namespace\` is the source/generated-code namespace. It may differ from \`applicationId\`; that difference is not automatically a defect.
- Increment \`versionCode\` for a release intended to update an already installed build.
- An update APK must be signed with the compatible existing certificate. Do not create a new keystore or silently change aliases as a repair.
- Never commit keystores, passwords, private keys, access tokens, or signing secrets.
- Pull-request validation builds unsigned/debug artifacts without release secrets. Signed release builds run only in trusted non-PR workflow contexts.
- Confirm the certificate SHA-256 fingerprint and install/update behavior before distributing a release. A successful Gradle build alone does not prove update compatibility.
- The static checker cannot evaluate Gradle expressions or verify the actual certificate. CI/device checks remain necessary.

Run:

\`\`\`sh
python3 tools/pmca_android_audit.py
\`\`\`

The checker fails on duplicate application IDs and missing statically detectable application IDs/version codes. It reports namespace differences and environment-based signing as informational notes.
