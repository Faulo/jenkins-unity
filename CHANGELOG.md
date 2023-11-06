# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]


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
