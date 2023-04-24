# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2023-04-24

### Changed
- Changed TEST_MODES parameter by adding TEST_UNITY to determine whether or not any Test Runner tests will be run. TEST_UNITY is '1' by default.
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
