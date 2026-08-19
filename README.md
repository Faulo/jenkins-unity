# Jenkins Unity Shared Library

This repository provides the Jenkins Shared Library used by Slothsoft projects to build, test, document, package, and deploy Unity projects.
It is loaded globally and implicitly on [ci.slothsoft.net](https://ci.slothsoft.net/), so every callable script in `vars` is available as a Pipeline command.

Commands must run in the Jenkins context their contracts describe. Workspace commands require an allocated `node`.
Commands that inspect `currentBuild`, `scm`, or Jenkins nodes require those Jenkins globals to be available.

Feature switches in `unityProject` and `unityPackage` are strings, not booleans: use `'1'` to enable a feature and `'0'` to disable it.

## Command index

### Current commands

| Command | Purpose |
|---|---|
| [`unityPipeline`](#unitypipeline) | Check out the current SCM and run `unityProject` on a Unity node. |
| [`unityProject`](#unityproject) | Test, document, build, deploy, and report on a Unity project. |
| [`unityPackage`](#unitypackage) | Test, document, publish, and report on a Unity package. |
| [`withUnity`](#withunity) | Run library shell commands inside a Unity sidecar container. |
| [`callComposer`](#callcomposer) | Invoke the configured `compose-unity` launcher. |
| [`callUnity`](#callunity) | Initialize and invoke a `slothsoft/unity` command. |
| [`callDotnetFormat`](#calldotnetformat) | Check a solution with `dotnet format` and publish a JUnit report. |
| [`callDocFX`](#calldocfx) | Build and publish a DocFX site. |
| [`reportToDiscord`](#reporttodiscord) | Post a build summary to a Discord webhook. |
| [`reportToOffice365`](#reporttooffice365) | Post a build summary through the Office 365 Connector plugin. |
| [`reportToAdaptiveCard`](#reporttoadaptivecard) | Post an Adaptive Card build summary to a webhook. |

### Deprecated and removed commands

| Command | Status |
|---|---|
| `callShell` | Deprecated; use `exec` from Strayfarer Pipeline Steps. |
| `callShellStatus` | Deprecated; use `execStatus` from Strayfarer Pipeline Steps. |
| `callShellStdout` | Deprecated; use `execStdout` from Strayfarer Pipeline Steps. |
| `executeOnAll` | Deprecated; use `everyNode` from Strayfarer Pipeline Steps. |
| `isWindows` | Removed from this library; use `isWindows` from Strayfarer Pipeline Steps. |
| `nodeIfCurrentDoesNotMatch` | Removed from this library; use `nodeIfCurrentDoesNotMatch` from Strayfarer Pipeline Steps. |
| `withEnvFile` | Removed from this library; use `withEnvFile` from Strayfarer Pipeline Steps. |

## Workflow commands

### `unityPipeline`

Configures a simple checkout-and-build Pipeline around `unityProject`.

```groovy
unityPipeline {
    LOCATION = 'Game'
    TEST_UNITY = '1'
    BUILD_FOR_WINDOWS = '1'
}
```

Contract:

- Replaces the job's Pipeline properties with `disableConcurrentBuilds()` and `disableResume()`.
- Allocates a node matching the exact label `unity`.
- Checks out `scm`, then passes its single argument to `unityProject`.
- Accepts the same configuration map or delegated closure as `unityProject`.
- Does not provide a stable return value.

### `unityProject`

Processes a Unity project in the current Jenkins workspace. It can set the project version, check C# formatting, run Unity tests, generate documentation,
build players, deploy them, and send build notifications.

#### Example

```groovy
unityProject(
    LOCATION: 'Game',
    TEST_UNITY: '1',
    BUILD_FOR_WINDOWS: '1',
    BUILD_NAME: 'my-game'
)
```

The equivalent delegated-closure form is:

```groovy
unityProject {
    LOCATION = 'Game'
    TEST_UNITY = '1'
    BUILD_FOR_WINDOWS = '1'
    BUILD_NAME = 'my-game'
}
```

#### Contract

- Must run inside an allocated Jenkins workspace with `WORKSPACE` and `WORKSPACE_TMP` available.
- Requires `BRANCH_NAME`. If it is empty and `PLASTICSCM_BRANCH` is present, the command copies that value to `BRANCH_NAME`.
- Reads the Unity `productName` for the stage name. A failed lookup is ignored and uses `Unknown`.
- Creates reports and build outputs below `WORKSPACE_TMP/<LOCATION>/reports`. Desktop and WebGL build directories are archived as ZIP files,
  Android builds are archived as APK files, and WebGL builds are also published as an HTML report.
- Deletes the reports directory before processing. The finalization block deletes it again after notifications, including after a main-lifecycle failure or interruption;
  a notification failure can prevent that final cleanup.
- Considers deployment only when at least one `BUILD_FOR_*` option is enabled. The current branch must also be an exact member of `DEPLOYMENT_BRANCHES`.
- Rethrows `FlowInterruptedException` after copying its result to `currentBuild.result`.
- Catches other exceptions from the main lifecycle, assigns `'UNKNOWN'` to `currentBuild.result`, and does not rethrow the original exception.
  Exceptions from final notification or cleanup work can still escape.
- Does not provide a stable return value.

#### Project and version options

| Option | Default | Contract |
|---|---:|---|
| `LOCATION` | `''` → `'.'` | Unity project directory relative to `WORKSPACE`. An empty value is normalized to `.`. |
| `AUTOVERSION` | `''` | A non-empty value enables versioning and is passed to `compose-unity autoversion`; expected values are `git` or `plastic`, but this command does not validate them. |
| `AUTOVERSION_REVISION` | `'0'` | When autoversioning is enabled, append `BUILD_NUMBER` if this is exactly `'1'`. |
| `AUTOVERSION_REVISION_PREFIX` | `''` | Text inserted before the appended build number. |
| `AUTOVERSION_REVISION_SUFFIX` | `''` | Text inserted after the appended build number. |

When reporting is enabled without autoversioning, the command reads the existing Unity `bundleVersion`. Version-discovery failures are ignored and the reported version is `?`.

#### Credential options

| Option | Default | Contract |
|---|---:|---|
| `UNITY_CREDENTIALS` | `''` | Optional Jenkins username/password credential, bound as `UNITY_CREDENTIALS_USR` and `UNITY_CREDENTIALS_PSW`. |
| `EMAIL_CREDENTIALS` | `''` | Optional Jenkins username/password credential, bound as `EMAIL_CREDENTIALS_USR` and `EMAIL_CREDENTIALS_PSW`. |
| `STEAM_CREDENTIALS` | `''` | Optional Jenkins username/password credential, bound as `STEAM_CREDENTIALS_USR` and `STEAM_CREDENTIALS_PSW`. |
| `ITCH_CREDENTIALS` | `''` | Optional Jenkins secret-text credential, bound as `BUTLER_API_KEY`. |

Every configured credential is bound around the processing lifecycle, even if the corresponding optional feature is disabled.
When `unityProject` runs inside `withUnity`, container commands receive these values only if their names are included in `JENKINS_UNITY_ENV`.

#### Checks and documentation

| Option | Default | Contract |
|---|---:|---|
| `TEST_FORMATTING` | `'0'` | With `'1'`, generate a C# solution, run `dotnet format --verify-no-changes`, and publish its transformed JUnit report. |
| `EDITORCONFIG_LOCATION` | `'.editorconfig'` | Source relative to `WORKSPACE`; copied to the project root. A missing file marks the build unstable, then its read can fail. |
| `FORMATTING_EXCLUDE` | `'Library'` | Value passed to `dotnet format --exclude`; an empty value omits that argument. |
| `TEST_UNITY` | `'0'` | With `'1'`, run `unity-tests` and publish `tests.xml`, allowing an empty report. |
| `TEST_MODES` | `'EditMode PlayMode'` | Arguments appended to `unity-tests`. An empty value marks the build unstable before invoking the command. |
| `BUILD_DOCUMENTATION` | `'0'` | With `'1'`, generate and publish DocFX documentation. Failures make the stage fail and build unstable, then later work continues. |

#### Player-build options

| Option | Default | Contract |
|---|---:|---|
| `BUILD_FOR_WINDOWS` | `'0'` | With `'1'`, run `BUILD_WINDOWS_CALL`, publish its JUnit report, and archive `<BUILD_NAME>-windows.zip`. |
| `BUILD_FOR_LINUX` | `'0'` | With `'1'`, run `BUILD_LINUX_CALL`, publish its JUnit report, and archive `<BUILD_NAME>-linux.zip`. |
| `BUILD_FOR_MAC` | `'0'` | With `'1'`, run `BUILD_MAC_CALL`, publish its JUnit report, and archive `<BUILD_NAME>-mac.zip`. |
| `BUILD_FOR_WEBGL` | `'0'` | With `'1'`, install WebGL, run the UnityExtensions builder, archive a ZIP, and publish `index.html`. Requires UnityExtensions. |
| `BUILD_FOR_ANDROID` | `'0'` | With `'1'`, install Android, run the UnityExtensions builder, and archive an APK. Requires UnityExtensions. |
| `BUILD_NAME` | `''` → `'build'` | Base name for build directories, archives, and the Android APK. An empty value is normalized to `build`. |
| `BUILD_WINDOWS_CALL` | Built-in closure | Custom Windows build closure described below. |
| `BUILD_LINUX_CALL` | Built-in closure | Custom Linux build closure described below. |
| `BUILD_MAC_CALL` | Built-in closure | Custom macOS build closure described below. |

The three desktop callbacks receive the absolute project directory, output directory, and XML report path.
Each must create the requested build directory and a JUnit-compatible report. The defaults invoke `unity-build` for the corresponding platform.

```groovy
unityProject(
    BUILD_FOR_WINDOWS: '1',
    BUILD_WINDOWS_CALL: { projectDirectory, outputDirectory, reportFile ->
        callUnity "unity-build '${projectDirectory}' '${outputDirectory}' windows", reportFile
    }
)
```

WebGL and Android builds do not have callback options.

#### Deployment options

| Option | Default | Contract |
|---|---:|---|
| `DEPLOY_ON_FAILURE` | `'0'` | With `'1'`, allow a non-`SUCCESS` result if execution reaches deployment. It does not resume after a thrown build error. |
| `DEPLOYMENT_BRANCHES` | `['main', '/main']` | Collection of branch names allowed to deploy. Matching against `BRANCH_NAME` is exact. |

If NPM publication fails, the direct-storage fallback runs only when `VERDACCIO_STORAGE` exists. It writes the package archive and metadata into that storage.
| `DEPLOY_TO_STEAM` | `'0'` | With `'1'`, generate a Steam build file and invoke `steamcmd`. At least one enabled desktop build must also have a depot ID. |
| `STEAM_ID` | `''` | Steam application ID passed to the build-file generator. |
| `STEAM_DEPOT_WINDOWS` | `''` | Depot ID mapped to the Windows build when that build is enabled. |
| `STEAM_DEPOT_LINUX` | `''` | Depot ID mapped to the Linux build when that build is enabled. |
| `STEAM_DEPOT_MAC` | `''` | Depot ID mapped to the macOS build when that build is enabled. |
| `STEAM_BRANCH` | `''` → current branch | Steam branch. When empty, derived from `BRANCH_NAME` by trimming leading/trailing slashes or spaces and replacing each slash or space with `-`. |
| `DEPLOY_TO_ITCH` | `'0'` | With `'1'`, use Butler to push every enabled build to its platform channel. |
| `ITCH_ID` | `''` | itch.io target in `author/game` form. |

Steam deployment supports the Windows, Linux, and macOS builds. itch.io deployment uses the channels `windows-x64`, `linux-x64`, `mac-x64`, `html`, and `android` for the corresponding enabled builds.

#### Notification options

| Option | Default | Contract |
|---|---:|---|
| `REPORT_TO_DISCORD` | `'0'` | With `'1'`, call `reportToDiscord` from the finalization block. |
| `DISCORD_WEBHOOK` | `''` | Webhook URL passed to `reportToDiscord`. |
| `DISCORD_PING_IF` | `''` | Despite its name, this gates the entire Discord report: empty sends for every result; otherwise the report is sent only when the result is at least this severe. |
| `REPORT_TO_OFFICE_365` | `'0'` | With `'1'`, call `reportToOffice365` from the finalization block. |
| `OFFICE_365_WEBHOOK` | `''` | Webhook URL passed to `reportToOffice365`. |
| `OFFICE_365_PING_IF` | `''` | Gates the entire Office 365 report using the same result-threshold rule. |
| `REPORT_TO_ADAPTIVE_CARDS` | `'0'` | With `'1'`, call `reportToAdaptiveCard` from the finalization block. |
| `ADAPTIVE_CARDS_WEBHOOK` | `''` | Webhook URL passed to `reportToAdaptiveCard`. |
| `ADAPTIVE_CARDS_PING_IF` | `''` | Gates the entire Adaptive Card report using the same result-threshold rule. |

#### Compatibility options

These older names remain accepted. If present, they override the corresponding canonical option, even when that option is also supplied.

| Compatibility option | Canonical option |
|---|---|
| `PROJECT_LOCATION` | `LOCATION` |
| `PROJECT_AUTOVERSION` | `AUTOVERSION` |

### `unityPackage`

Processes a Unity package in the current Jenkins workspace. It can validate the changelog, create a temporary Unity project, run checks,
generate documentation, publish the package to Verdaccio, and send build notifications.

#### Example

```groovy
unityPackage(
    LOCATION: 'Packages/net.slothsoft.example',
    TEST_CHANGELOG: '1',
    TEST_UNITY: '1',
    DEPLOY_TO_VERDACCIO: '1'
)
```

The equivalent delegated-closure form is:

```groovy
unityPackage {
    LOCATION = 'Packages/net.slothsoft.example'
    TEST_CHANGELOG = '1'
    TEST_UNITY = '1'
    DEPLOY_TO_VERDACCIO = '1'
}
```

#### Contract

- Must run inside an allocated Jenkins workspace with `WORKSPACE` and `WORKSPACE_TMP` available.
- Requires `BRANCH_NAME`, falling back to `PLASTICSCM_BRANCH` when the former is empty.
- Fails before entering its main lifecycle if the package directory does not exist or package metadata cannot be read.
- Reads a missing `ID` or `VERSION` from `package.json` with Node.js.
- Creates a temporary Unity project only when formatting, documentation, or Unity tests are enabled. Package sources are stashed, and that work runs on a node matching `UNITY_NODE`.
- Deletes the contents of `WORKSPACE_TMP` on the Unity node before creating the temporary package, project, and reports directories.
- Evaluates Verdaccio deployment independently of whether a temporary Unity project was created. The current branch must be an exact member of `DEPLOYMENT_BRANCHES`.
- Rethrows `FlowInterruptedException` after copying its result to `currentBuild.result`.
- Catches other exceptions from the main lifecycle, assigns `'UNKNOWN'` to `currentBuild.result`, and does not rethrow the original exception. Exceptions from final notifications can still escape.
- Does not provide a stable return value.

#### Package and Unity-node options

| Option | Default | Contract |
|---|---:|---|
| `LOCATION` | `''` → `'.'` | Unity package directory relative to `WORKSPACE`. An empty value is normalized to `.`. |
| `UNITY_NODE` | `'unity'` | Jenkins label expression used for temporary Unity-project work. The current node is reused when it already matches. |
| `ID` | `''` | Package ID override. When empty, read from `package.json` field `name`. |
| `VERSION` | `''` | Package version override. When empty, read from `package.json` field `version`. A version containing `-` is treated as a prerelease. |
| `UNITY_CREDENTIALS` | `''` | Optional Jenkins username/password credential, bound on the Unity node as `UNITY_CREDENTIALS_USR` and `UNITY_CREDENTIALS_PSW`. |
| `EMAIL_CREDENTIALS` | `''` | Optional Jenkins username/password credential, bound on the Unity node as `EMAIL_CREDENTIALS_USR` and `EMAIL_CREDENTIALS_PSW`. |
| `UNITY_MANIFEST` | `''` | Non-empty binds file credential `Unity-Manifest` as `UNITY_EMPTY_MANIFEST`; the option value is not used as the credential ID. |

#### Checks and documentation

| Option | Default | Contract |
|---|---:|---|
| `TEST_CHANGELOG` | `'0'` | With `'1'`, require a dated version entry. A prerelease may use its stable-version entry. Missing entries make the build unstable. |
| `CHANGELOG_LOCATION` | `'CHANGELOG.md'` | Changelog path relative to the package directory. |
| `TEST_FORMATTING` | `'0'` | With `'1'`, create a temporary Unity project, generate its solution, run `dotnet format --verify-no-changes`, and publish the transformed JUnit report. |
| `EDITORCONFIG_LOCATION` | `'.editorconfig'` | Source file relative to the original workspace, read before switching nodes and written to the generated project root. |
| `EDITORCONFIG_ADDONS` | `'.editor/**, Directory.Build.props'` | Stash includes for extra formatting files copied to the generated project. Empty disables the stash. |
| `FORMATTING_EXCLUDE` | `''` | Value passed to `dotnet format --exclude`; empty omits that argument. |
| `TEST_UNITY` | `'0'` | With `'1'`, create a temporary Unity project, run `unity-tests`, and publish `tests.xml`, allowing an empty report. |
| `TEST_MODES` | `'EditMode PlayMode'` | Arguments appended to `unity-tests`. An empty value marks the build unstable before invoking the command. |
| `BUILD_DOCUMENTATION` | `'0'` | With `'1'`, generate and publish DocFX documentation. Failures make the stage fail and build unstable, then later work continues. |

#### Verdaccio deployment

| Option | Default | Contract |
|---|---:|---|
| `DEPLOY_ON_FAILURE` | `'0'` | With `'1'`, permit publication when `currentBuild.currentResult` is not `SUCCESS`, provided execution reaches deployment. |
| `DEPLOY_IF_RELEASE` | `'1'` | Exactly `'0'` skips versions without a prerelease suffix; any other value permits them. |
| `DEPLOY_IF_PRERELEASE` | `'1'` | Exactly `'0'` skips versions containing a prerelease suffix; any other value permits them. |
| `DEPLOY_TO_VERDACCIO` | `'0'` | With `'1'`, publish an absent package version to Verdaccio. Already-published versions are skipped. |
| `VERDACCIO_URL` | `'http://verdaccio:4873'` | Registry URL used by `npm view`, `npm show`, and `npm publish`. |
| `VERDACCIO_HOST` | `'verdaccio:4873'` | Host used for the project-level NPM authentication entry. |
| `VERDACCIO_STORAGE` | `'/var/verdaccio'` | Direct-storage fallback after NPM failure. Requires `mv`, `tar`, `chmod`, `openssl`, `sha1sum`, and `awk`. |
| `VERDACCIO_CREDENTIALS` | `''` | Optional Jenkins secret-text credential bound as `NPM_TOKEN` and written to the project-level NPM configuration before publication. |
| `DEPLOYMENT_BRANCHES` | `['main', '/main']` | Collection of branch names allowed to deploy. Matching against `BRANCH_NAME` is exact. |

#### Notification options

`unityPackage` has the same nine notification options and threshold behavior as [`unityProject`](#notification-options):

| Option | Default | Contract |
|---|---:|---|
| `REPORT_TO_DISCORD` | `'0'` | With `'1'`, call `reportToDiscord` from the finalization block. |
| `DISCORD_WEBHOOK` | `''` | Webhook URL passed to `reportToDiscord`. |
| `DISCORD_PING_IF` | `''` | Gates the entire Discord report; empty sends for every result, otherwise the configured result threshold applies. |
| `REPORT_TO_OFFICE_365` | `'0'` | With `'1'`, call `reportToOffice365` from the finalization block. |
| `OFFICE_365_WEBHOOK` | `''` | Webhook URL passed to `reportToOffice365`. |
| `OFFICE_365_PING_IF` | `''` | Gates the entire Office 365 report using the configured result threshold. |
| `REPORT_TO_ADAPTIVE_CARDS` | `'0'` | With `'1'`, call `reportToAdaptiveCard` from the finalization block. |
| `ADAPTIVE_CARDS_WEBHOOK` | `''` | Webhook URL passed to `reportToAdaptiveCard`. |
| `ADAPTIVE_CARDS_PING_IF` | `''` | Gates the entire Adaptive Card report using the configured result threshold. |

The compatibility option `PACKAGE_LOCATION` remains accepted and overrides `LOCATION` whenever it is present.

## Scoped execution commands

### `withUnity`

Runs library shell helpers through a named, already-running Docker container while Jenkins-native steps remain on the Jenkins agent.

```groovy
withUnity('agents_unity') {
    unityProject(unityConfig)
}
```

The closure-only form reads the container name from `JENKINS_UNITY_CONTAINER`:

```groovy
withEnv(['JENKINS_UNITY_CONTAINER=agents_unity']) {
    withUnity {
        callUnity 'unity-help'
    }
}
```

Contract:

- Fails when neither an explicit container name nor `JENKINS_UNITY_CONTAINER` supplies a non-empty name.
- Delegates to the Strayfarer Pipeline Steps plugin's `insideDockerContainer` command.
- Affects the Pipeline Steps plugin's command-execution steps and therefore retained commands built on them.
  Jenkins-native steps such as `dir`, `junit`, `archiveArtifacts`, and `stash` still execute on the agent.
- Uses the current Jenkins `pwd()` as each container command's working directory.
- Applies lexically, supports nesting, and restores the previous execution behavior after success, failure, or interruption.
- Sets compatibility variables `JENKINS_UNITY_CONTAINER`, `JENKINS_UNITY_CONTAINER_ID`, and `JENKINS_UNITY_CONTAINER_OS` inside the body from the container selected by the plugin.
- Passes the colon-separated, de-duplicated variable names in `JENKINS_UNITY_ENV` to `insideDockerContainer`.
  Empty entries are ignored. Names must match `[A-Za-z_][A-Za-z0-9_]*`; WSLENV-style flags are not supported. Values are forwarded by name.
- Returns the body's result only to the extent that `insideDockerContainer` preserves it; callers should not rely on a separate wrapper return contract.

The Jenkins agent must have Docker CLI access to the daemon hosting the sidecar.
`WORKSPACE`, `WORKSPACE_TMP`, and nested workspace paths must be mounted at identical absolute paths in the container.
Linux containers need `/bin/sh`, `setsid`, and `pkill`; Windows containers need PowerShell 7.2 or newer as `pwsh` and `taskkill.exe`.

`unityPackage` is not adapted as a whole for `withUnity`: it changes Jenkins nodes internally and combines Unity work with Node.js, NPM, and direct Verdaccio operations.

## Shell and tool commands

The retained `call*` commands below compose application-specific behavior from the generic commands supplied by
[Strayfarer Pipeline Steps](https://github.com/Strayfarer/com.strayfarer.jenkins.pipeline-steps), version 0.5.0 or newer.

### `callComposer`

Invokes the configured `slothsoft/unity` launcher and returns its captured output.

```groovy
String help = callComposer('exec unity-help')
```

Contract:

- Signature is `String callComposer(String body)`.
- Uses `env.COMPOSE_UNITY` as the launcher. When it is empty, sets it to `compose-unity`.
- Constructs the command as `<COMPOSE_UNITY> <body>` and executes it through Pipeline Steps with captured output.
- The launcher may contain arguments, for example `composer -d /var/unity exec` on Linux or `composer -d C:\Webserver\unity exec` on Windows.
- Inherits Pipeline Steps' streaming, captured-output, exit, and interruption behavior.

### `callUnity`

Initializes and invokes a command supplied by the [slothsoft/unity](https://github.com/Faulo/slothsoft-unity) Composer package.

```groovy
String productName = callUnity(
    "unity-project-setting '${env.WORKSPACE}/Game' 'productName'"
)

callUnity(
    "unity-tests '${env.WORKSPACE}/Game' EditMode",
    "${env.WORKSPACE_TMP}/tests.xml"
)
```

Signature: `String callUnity(String body, String file = '')`.

Contract:

- On the first call for an execution identity, invokes `callComposer('update --no-interaction --no-dev --optimize-autoloader --classmap-authoritative')`.
- Uses `PIPELINE_DOCKER_CONTAINER_ID` as the identity inside a sidecar and otherwise uses `NODE_NAME`. If neither is available, the initializer falls back to the process `NODE_NAME` or `UNKNOWN`.
- Invokes `callComposer("exec ${body}")` and returns its captured standard output.
- When `file` is non-empty, writes that same output to the supplied workspace path as UTF-8 before returning it.
- Container replacement produces a new container identity and therefore triggers initialization for the replacement.
- Inherits `callComposer` failure and interruption behavior.

Initialization state is maintained by `vars/CallUnityInitializer.groovy`. That file is internal support and does not define a Pipeline command.

### `callDotnetFormat`

Checks one solution with `dotnet format` and publishes the result as JUnit data.

```groovy
callDotnetFormat(
    "${env.WORKSPACE}/Game/Game.sln",
    "${env.WORKSPACE_TMP}/format-reports",
    'Library'
)
```

Signature: `callDotnetFormat(String solutionFile, String reportsDirectory, String exclude = '')`.

Contract:

- Runs inside `reportsDirectory`.
- Invokes `dotnet format <solution> --verify-no-changes --verbosity normal --report <reportsDirectory>` and appends `--exclude <exclude>` when `exclude` is non-empty.
- Requests a numeric exit status, allowing `dotnet format` to return non-zero so its report can still be processed.
- Requires `format-report.json`; missing output fails the Pipeline.
- Converts that JSON to `format-report.xml` with `callUnity transform-dotnet-format`; missing converted output also fails the Pipeline.
- Publishes `format-report.xml` with `junit(allowEmptyResults: true)`.
- Does not provide a stable return value.

### `callDocFX`

Builds a DocFX project in the current directory and publishes its generated site.

```groovy
dir('Game/.Documentation') {
    callDocFX('Game API')
}
```

Signature: `callDocFX(String reportName)`.

Contract:

- Uses the current node's operating system to choose its invocation.
- On Windows, checks the global .NET tool list, installs `docfx` globally when absent, and runs `docfx`.
- On non-Windows environments, runs `dotnet tool restore` followed by `dotnet tool run docfx`.
- Publishes `html/index.html` through `publishHTML` with `reportName` as the display name. Missing output fails publication.
- Does not provide a stable return value.

## Notification commands

### `reportToDiscord`

Posts one embedded build summary to a Discord webhook.

```groovy
reportToDiscord(
    env.DISCORD_WEBHOOK,
    currentBuild,
    'Example Game v1.2.3',
    'FAILURE',
    '123456789012345678'
)
```

Supported signatures:

```groovy
reportToDiscord(webhookUrl, currentBuild, name)
reportToDiscord(webhookUrl, currentBuild, name, discordPingIf)
reportToDiscord(webhookUrl, currentBuild, name, discordPingIf, discordPingUser)
```

Contract:

- The three-argument form reads `DISCORD_PING_IF` and `DISCORD_PING_USER` from the environment. The four-argument form reads only `DISCORD_PING_USER` from the environment.
- An empty ping threshold defaults to `FAILURE`; an empty user ID disables the user mention.
- Includes the result and supplied name, build URL, failure cause when available, change messages, and culprit names.
- Adds `Help!` when the build result is at least the configured threshold, plus `<@user>` when a user ID is configured.
- Truncates the title, description, and footer to Discord embed limits and assigns a result-specific color.
- Sends JSON with `httpRequest`, accepting HTTP 200–299 and suppressing response-body logging.
- Rethrows Pipeline interruptions after preserving their result. Other HTTP request failures are caught and logged without failing the Pipeline.
  Failures while constructing the payload can still propagate.
- Does not provide a stable return value.

### `reportToOffice365`

Posts a build summary through the Jenkins Office 365 Connector plugin.

```groovy
reportToOffice365(
    env.OFFICE_365_WEBHOOK,
    currentBuild,
    'Example Game v1.2.3'
)
```

Signature: `reportToOffice365(String webhookUrl, currentBuild, String name)`.

- Sends status `<current result>: <name>`.
- Adds every SCM change message as a Markdown list item; an empty change set produces an empty message.
- Delegates failures and interruptions to `office365ConnectorSend` without handling them.
- Does not provide a stable return value.

### `reportToAdaptiveCard`

Posts a Microsoft Adaptive Card build summary directly to a webhook.

```groovy
reportToAdaptiveCard(
    env.ADAPTIVE_CARDS_WEBHOOK,
    currentBuild,
    'Example Game v1.2.3'
)
```

Signature: `reportToAdaptiveCard(String webhookUrl, currentBuild, String name)`.

Contract:

- Builds links to the job, build, and JUnit test report from `BUILD_URL`, `BUILD_NUMBER`, and `JOB_BASE_NAME`.
- Includes the current result, JUnit totals when present, culprit mentions, the recorded cause of failure, and SCM changes. When there are no changes, says so explicitly.
- Sends an Adaptive Card 1.2 payload with `httpRequest` and echoes the response content.
- Does not catch request, Pipeline, or model-access failures; they propagate to the caller.
- Does not provide a stable return value.

## Runtime requirements

The node or Unity sidecar executing these commands must provide the tools required by the enabled features:

- a working `COMPOSE_UNITY` launcher and the expected Unity installations and reusable license state;
- `dotnet` and DocFX for formatting or documentation;
- `steamcmd` for Steam deployment;
- Butler for itch.io deployment;
- Node.js and NPM for `unityPackage` metadata and Verdaccio publication;
- the Jenkins plugins providing the Pipeline steps used by the selected commands, including Strayfarer Pipeline Steps 0.5.0 or newer.

Local tests exercise configuration and delegation without reproducing every Jenkins CPS, durability, agent, credential, container, or plugin behavior:

```shell
mvn test
```

After Maven dependencies have been downloaded once, the same suite can run offline:

```shell
mvn -o test
```
