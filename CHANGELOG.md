# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]


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


## [2.16.0] - 2025-02-13

### Added
- Added unityProject/unityPackage arg REPORT_TO_ADAPTIVE_CARDS, ADAPTIVE_CARDS_WEBHOOK.
- Added unityProject/unityPackage arg DISCORD_PING_IF, OFFICE_365_PING_IF, ADAPTIVE_CARDS_PING_IF.


## [2.15.0] - 2025-01-08

### Added
- Added unityProject arg BUILD_WINDOWS_CALL.
- Added unityProject arg BUILD_LINUX_CALL.
- Added unityProject arg BUILD_MAC_CALL.


## [2.14.1] - 2024-10-31

### Fixed
- Fixed published version detection.


## [2.14.0] - 2024-09-17

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


## [2.6.0] - 2024-02-27

### Added
- Added DEPLOY_TO_DISCORD, DISCORD_WEBHOOK.


## [2.5.0] - 2023-11-06

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


## [1.1.0] - 2023-04-24

### Changed
- Changed TEST_MODES parameter by adding TEST_UNITY to determine whether or not any Test Runner tests will be run. TEST_UNITY is '1' by default for packages, '0' for projects.
- Changed most stage names.
- Moved the changelog test to be the first stage to be run.

### Added
- Added 'TEST_FORMATTING' and 'EDITORCONFIG_LOCATION' for packages. Requires the custom manifest for [slothsoft/unity](https://github.com/Faulo/slothsoft-unity) to include [Slothsoft's UnityExtensions](https://github.com/Faulo/UnityExtensions).


## [1.0.0] - 2023-04-02

### Added
- `callShell`: call either `sh` or `powershell`, depending on the node's operating system.
- `callShellStatus`: `callShell` and return its status code.
- `callShellStdout`: `callShell` and return its output.
- `callUnity`: call one of the subcommands of [slothsoft/unity](https://github.com/Faulo/slothsoft-unity).
- `unityPackage`: run a build pipeline for a custom Unity package.
- `unityProject`: run a build pipeline for a Unity project.
