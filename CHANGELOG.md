# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]


## [4.0.0] - 2026-08-19

### Changed
- Deprecated `callShell`, `callShellStatus`, and `callShellStdout` now delegate to the corresponding Strayfarer Pipeline Steps commands.
- `withUnity` now delegates sidecar execution to Strayfarer Pipeline Steps.
- Require Strayfarer Pipeline Steps 0.5.0 or newer.

### Removed
- Removed `isWindows`, `nodeIfCurrentDoesNotMatch`, and `withEnvFile`; use the corresponding Strayfarer Pipeline Steps commands instead.


## [3.0.0] - 2026-08-12

### Changed
- Windows commands now use `pwsh` and require PowerShell 7.2 or newer on Windows Jenkins agents and Windows Unity containers.

### Fixed
- Removed Windows PowerShell stderr merging and error-record filtering; native stderr no longer causes successful commands to fail or pollutes captured stdout.


## [2.22.0] - 2026-08-02

### Added
- Added `withUnity` for scoped command execution inside a running Unity sidecar container.
- Added `JENKINS_UNITY_CONTAINER` and `JENKINS_UNITY_ENV` configuration for selecting the sidecar and forwarding allowlisted environment variables.

### Fixed
- Stream stdout and stderr in real time from captured shell calls on Windows and inside `withUnity` while preserving stdout-only return values.
- Preserve command status and interruption behavior inside `withUnity` and terminate interrupted sidecar processes.
- Initialize Composer dependencies again when a Unity sidecar container is replaced.


## [2.21.1] - 2026-05-15

### Fixed
- Fixed Jenkins serialization in `executeOnAll`.


## [2.21.0] - 2026-05-09

### Added
- Added `withEnvFile`.
- Added `nodeIfCurrentDoesNotMatch` and reuse the current node in `unityPackage` when it matches `UNITY_NODE`.


## [2.20.13] - 2026-04-25

### Fixed
- Preserve Pipeline aborts and timeouts instead of converting them to ordinary failures.


## [2.20.12] - 2026-04-24

### Added
- Added `isWindows`.
- Added an optional command-echo argument to the `callShell` helpers.

### Fixed
- Fixed Windows detection for shell and DocFX commands.


## [2.20.11] - 2026-04-24

### Fixed
- Fixed parallel `executeOnAll` calls using the wrong node.


## [2.20.10] - 2026-04-22

### Added
- Added optional Discord failure pings and user mentions to `reportToDiscord`.


## [2.20.9] - 2026-04-22

### Changed
- Send Discord reports directly through the webhook instead of the Discord Notifier plugin.

### Fixed
- Limit Discord report fields to the supported lengths and do not fail builds when webhook requests fail.


## [2.20.8] - 2026-04-21

### Fixed
- Discord notification failures and timeouts no longer change the build result.


## [2.20.7] - 2026-04-21

### Added
- Added a 30-second timeout to Discord notifications.


## [2.20.6] - 2025-09-24

### Added
- Added Steam login initialization before deployment.

### Fixed
- Fixed the Steam credential environment variable names.


## [2.20.5] - 2025-09-14

### Added
- Use `PLASTICSCM_BRANCH` as the default for `BRANCH_NAME`.


## [2.20.4] - 2025-09-01

### Fixed
- Initialize Composer dependencies once per Jenkins node instead of once globally.


## [2.20.3] - 2025-08-21

### Fixed
- Do not swallow Pipeline interruptions while handling documentation failures or a missing Unity manifest.


## [2.20.2] - 2025-07-09

### Added
- Added `executeOnAll` and `realpath`.
- Added unityPackage args `VERDACCIO_HOST` and `VERDACCIO_CREDENTIALS`.

### Changed
- Changed unityPackage defaults for `TEST_CHANGELOG` and `TEST_UNITY` to `'0'`.

### Fixed
- Fixed authenticated Verdaccio publication and use the direct-storage fallback only when its storage exists.
- Fixed workspace path handling in Pipeline code.


## [2.20.1] - 2025-06-10

### Fixed
- Fixed stderr handling for captured and status-returning PowerShell commands.


## [2.20.0] - 2025-06-07

### Added
- Added callDotnetFormat.
- Added support for env UNITY_EMPTY_MANIFEST.
- Added unityPackage arg UNITY_MANIFEST (reads Jenkins file credential, stores in UNITY_EMPTY_MANIFEST).

### Fixed
- Fixed Unity manifest path resolution on Jenkins agents.
- Install DocFX on Windows only when it is missing.


## [2.19.0] - 2025-05-19

### Added
- Added unityProject arg UNITY_CREDENTIALS.
- Added unityProject arg EMAIL_CREDENTIALS.
- Added callDocFX.

### Fixed
- Fixed docfx call on windows.


## [2.18.0] - 2025-05-06

### Added
- Added unityPackage arg UNITY_CREDENTIALS.
- Added unityPackage arg EMAIL_CREDENTIALS.


## [2.17.2] - 2025-03-09

### Fixed
- Added test report to REPORT_TO_ADAPTIVE_CARDS.


## [2.17.1] - 2025-03-06

### Fixed
- Added "integrity" field to packages >500MB deployed via unityPackage calls.


## [2.17.0] - 2025-03-03

### Added
- Added Map syntax to unityProject and unityPackage calls.
- Added unityPackage arg EDITORCONFIG_ADDONS for copying formatting configuration into its temporary project.


## [2.16.2] - 2025-02-14

### Added
- Added result emojis and summaries to Adaptive Card reports.

### Fixed
- Send Adaptive Card payloads as UTF-8.


## [2.16.1] - 2025-02-14

### Fixed
- Fixed `reportToOffice365` ignoring its webhook argument.


## [2.16.0] - 2025-02-14

### Added
- Added `reportToAdaptiveCard`, `reportToDiscord`, and `reportToOffice365` commands.
- Added unityProject/unityPackage arg REPORT_TO_ADAPTIVE_CARDS, ADAPTIVE_CARDS_WEBHOOK.
- Added unityProject/unityPackage arg DISCORD_PING_IF, OFFICE_365_PING_IF, ADAPTIVE_CARDS_PING_IF.


## [2.15.0] - 2025-01-08

### Added
- Added unityProject arg BUILD_WINDOWS_CALL.
- Added unityProject arg BUILD_LINUX_CALL.
- Added unityProject arg BUILD_MAC_CALL.


## [2.14.6] - 2024-10-31

### Fixed
- Fixed published version detection.


## [2.14.5] - 2024-10-10

### Added
- Added node and workspace logging to unityPackage calls.


## [2.14.4] - 2024-10-06

### Changed
- `callComposer` now defaults `COMPOSE_UNITY` to `compose-unity`.


## [2.14.3] - 2024-10-04

### Fixed
- Fixed unityPackage paths after moving temporary files to `WORKSPACE_TMP`.


## [2.14.2] - 2024-10-04

### Changed
- Moved temporary unityPackage files to `WORKSPACE_TMP`.


## [2.14.1] - 2024-09-25

### Changed
- Optimize the Composer autoloader during Unity command initialization.


## [2.14.0] - 2024-09-18

### Added
- Added UNITY_NODE property to unityPackage call.


## [2.13.0] - 2024-08-15

### Changed
- Changed path for package documentations to match the package id.


## [2.12.0] - 2024-05-31

### Added
- Formatting errors reported by TEST_FORMATTING are now converted to junit test failures.


## [2.11.0] - 2024-05-21

### Changed
- Prerelease changelogs are considered valid if they contain an entry for their stable version.


## [2.10.0] - 2024-05-19

### Added
- Added DEPLOY_IF_RELEASE option (default is '1').
- Added DEPLOY_IF_PRERELEASE option (default is '1').


## [2.9.0] - 2024-04-25

### Added
- Added DEPLOY_ON_FAILURE option (default is '0').


## [2.8.3] - 2024-04-14

### Added
- Added echo to shell calls.

### Fixed
- Fixed callShell spamming NativeCommandError on Windows.


## [2.8.2] - 2024-04-14

### Fixed
- Ignore "Library" folder when asserting formatting.


## [2.8.1] - 2024-04-14

### Changed
- Restored PowerShell for Windows commands and use UTF-8 for shell calls.

### Fixed
- Use writeFile instead of pipes.


## [2.8.0] - 2024-04-13

### Fixed
- Use bat instead of powershell on Windows.


## [2.7.2] - 2024-03-16

### Fixed
- Discard docs and reports directories after run.


## [2.7.1] - 2024-03-12

### Fixed
- Don't blindly update composer dependencies.


## [2.7.0] - 2024-02-28

### Added
- Added REPORT_TO_OFFICE_365, OFFICE_365_WEBHOOK.

### Changed
- Renamed DEPLOY_TO_DISCORD to REPORT_TO_DISCORD.


## [2.6.3] - 2024-02-27

### Fixed
- Send Discord reports from finalization so failed builds can still be reported.
- Read the project version for reporting instead of for every deployment.


## [2.6.2] - 2024-02-27

### Added
- Added commit messages to Discord reports.


## [2.6.1] - 2024-02-27

### Fixed
- Fixed the project version in Discord reports.


## [2.6.0] - 2024-02-27

### Added
- Added DEPLOY_TO_DISCORD, DISCORD_WEBHOOK.


## [2.5.1] - 2023-11-06

### Fixed
- Added -buildTarget parameters to WebGL and Android build commands.


## [2.5.0] - 2023-11-06

### Added
- Add BUILD_NAME option for specifying the names of the created .zip and .apk files.


## [2.4.2] - 2023-08-15

### Added
- Add AUTOVERSION_REVISION_PREFIX option for adding a string before the revision when auto-versioning.
- Add AUTOVERSION_REVISION_SUFFIX option for adding a string after the revision when auto-versioning.


## [2.4.1] - 2023-08-11

### Changed
- Removed the "r" prefix before the revision when auto-versioning.


## [2.4.0] - 2023-08-11

### Added
- Add AUTOVERSION_REVISION option for adding the build number when auto-versioning.


## [2.3.0] - 2023-07-28

### Added
- Added stage names for projects and packages.


## [2.2.3] - 2023-07-28

### Added
- Added a package ID stage to unityPackage calls.


## [2.2.2] - 2023-07-28

### Fixed
- Delete the temporary Unity project after processing a package.


## [2.2.1] - 2023-07-28

### Fixed
- Do not archive the temporary package created by the direct Verdaccio fallback.


## [2.2.0] - 2023-07-19

### Fixed
- Fixed package deployment failing for packages >500MB.


## [2.1.0] - 2023-04-26

### Added
- Added `TEST_FORMATTING`, `EDITORCONFIG_LOCATION`, and `FORMATTING_EXCLUDE` parameters to `unityProject` command.
- Added `FORMATTING_EXCLUDE` to `unityPackage` command.


## [2.0.0] - 2023-04-25

### Added
- Added `callComposer` command using the `COMPOSE_UNITY` variable.

### Changed
- Changed the expected `COMPOSE_UNITY` envrionment variable to just point to a valid composer installation, excluding the `exec`.


## [1.1.3] - 2023-04-24

### Changed
- Package formatting failures now mark the build unstable instead of stopping it.


## [1.1.2] - 2023-04-24

### Fixed
- Fixed C# solution and DocFX generation order for projects and packages.


## [1.1.1] - 2023-04-24

### Fixed
- Restored the unityProject `TEST_UNITY` default to `'0'`.
- Fixed documentation setup for unityPackage calls.


## [1.1.0] - 2023-04-24

### Changed
- Changed TEST_MODES parameter by adding TEST_UNITY to determine whether or not any Test Runner tests will be run. TEST_UNITY is '1' by default for packages, '0' for projects.
- Changed most stage names.
- Moved the changelog test to be the first stage to be run.

### Added
- Added 'TEST_FORMATTING' and 'EDITORCONFIG_LOCATION' for packages. Requires the custom manifest for [slothsoft/unity](https://github.com/Faulo/slothsoft-unity) to include [Slothsoft's UnityExtensions](https://github.com/Faulo/UnityExtensions).


## [1.0.0] - 2023-04-02

### Added
- `unityPipeline`: check out and run a Unity project Pipeline.
- `callShell`: call either `sh` or `powershell`, depending on the node's operating system.
- `callShellStatus`: `callShell` and return its status code.
- `callShellStdout`: `callShell` and return its output.
- `callUnity`: call one of the subcommands of [slothsoft/unity](https://github.com/Faulo/slothsoft-unity).
- `unityPackage`: run a build pipeline for a custom Unity package.
- `unityProject`: run a build pipeline for a Unity project.
