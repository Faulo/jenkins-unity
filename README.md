# Unity Commands for Jenkins

This repository is the Jenkins Shared Library used by Slothsoft projects to build, test, document, package, and deploy Unity projects. Its production runtime is [ci.slothsoft.net](https://ci.slothsoft.net/).

## Runtime model

The library contributes global Pipeline steps from `vars/*.groovy`. Unity commands follow this call chain:

```text
unityProject / unityPackage
  -> callUnity
  -> callComposer
  -> callShellStdout
     -> powershell or sh on the Jenkins agent (default)
     -> docker exec inside withUnity
```

`callShell`, `callShellStdout`, and `callShellStatus` are also used directly for supporting tools. Jenkins-native steps continue to handle workspaces, credentials, test reports, archives, HTML reports, stashes, and notifications.

## Repository layout

- `vars/unityPipeline.groovy` provides a complete checkout-and-build convenience Pipeline.
- `vars/unityProject.groovy` handles Unity projects, including versioning, tests, player builds, documentation, Steam, itch.io, and result notifications.
- `vars/unityPackage.groovy` handles Unity packages, including generated-project tests and Verdaccio publication.
- `vars/callUnity.groovy` and `vars/callComposer.groovy` initialize and invoke `slothsoft/unity`.
- `vars/withUnity.groovy` scopes external-process execution to a long-running Unity container.
- `vars/callShell*.groovy` define cross-platform external-process behavior.
- `vars/callDocFX.groovy` and `vars/callDotnetFormat.groovy` provide documentation and formatting checks.
- `vars/reportTo*.groovy` send optional build notifications.
- `vars/executeOnAll.groovy` and `vars/nodeIfCurrentDoesNotMatch.groovy` provide node-selection helpers.

There is no standalone test harness that reproduces Jenkins CPS and dynamic global steps. Changes should receive local syntax and call-chain inspection, followed by an authorized replay of a representative Pipeline on `ci.slothsoft.net` when runtime behavior changes.

`callUnity` invokes the [slothsoft/unity](https://github.com/Faulo/slothsoft-unity) Composer package through `COMPOSE_UNITY`. When unset, `COMPOSE_UNITY` defaults to `compose-unity`. A node may instead set it to another working launcher, such as `composer -d /var/unity exec` on Linux or `composer -d C:\Webserver\unity exec` on Windows.

The node executing Unity work must provide:

- a working `COMPOSE_UNITY` command;
- installed Unity versions and reusable license state expected by `slothsoft/unity`;
- `dotnet` when documentation or formatting is enabled;
- DocFX when documentation is enabled;
- `steamcmd` for Steam deployment;
- `butler` for itch.io deployment.

`unityPackage` additionally uses Node.js and NPM for package metadata and Verdaccio deployment. Its Unity-specific work may switch to the configured `UNITY_NODE`; its package and deployment work otherwise remains on the calling node.

## Workspace behavior

Commands must run inside an allocated Jenkins workspace. Jenkins normally provides job-specific paths like:

```text
WORKSPACE=/workspace/root/job
WORKSPACE_TMP=/workspace/root/job@tmp
```

Project sources stay below `WORKSPACE`. Generated Unity logs and intermediate reports use `WORKSPACE_TMP`, then Jenkins publishes the requested reports and artifacts before cleanup. Any external execution environment must see both paths at the same absolute locations.

## The `withUnity` command

`withUnity` routes library shell helpers through a named, running Docker container while leaving Jenkins-native Pipeline steps on the Jenkins agent:

```groovy
environment {
    JENKINS_UNITY_CONTAINER = 'agents_unity'
}

withUnity() {
    unityProject(unityConfig)
}
```

The closure-only form reads the container name from `JENKINS_UNITY_CONTAINER`. Pass a name explicitly, such as `withUnity('agents_unity')`, to override that default.

Inside the scope, `callShell`, `callShellStdout`, and `callShellStatus` execute through `docker exec`. Their existing streamed-output, captured-stdout, and numeric-status contracts remain unchanged. Calls outside the scope continue to execute directly on the Jenkins agent. Custom `BUILD_*_CALL` closures inherit the scope when they use these helpers.

The scope is lexical and nestable. Previous execution behavior is restored after success, failure, or interruption. Each command uses the current Jenkins `pwd()` as its container working directory. Agent environment variables are not forwarded by default; configure persistent sidecar values through the Docker stack environment.

Set `JENKINS_UNITY_ENV` to a colon-separated allowlist when a Pipeline-scoped value must enter the container, such as `UNITY_CREDENTIALS_USR:UNITY_CREDENTIALS_PSW`. Listed variables, including variables added by `withEnv` and `withCredentials`, are forwarded by name so their values do not appear in Docker command-line arguments. Empty list entries are ignored, duplicate names are forwarded once, and variable names must match `[A-Za-z_][A-Za-z0-9_]*`. WSLENV-style flags are not supported.

The Jenkins agent must provide Docker CLI access to the daemon hosting the sidecar. The named container must be running and provide `compose-unity`, `dotnet`, DocFX, `butler`, and `steamcmd`. Linux containers must also provide `/bin/sh`, `setsid`, and `pkill`; Windows containers must provide PowerShell and `taskkill.exe` for interruption cleanup.

`WORKSPACE`, `WORKSPACE_TMP`, and nested workspace directories must be mounted into the container at identical absolute paths. Container replacement is detected through the Docker container ID, causing the replacement container to receive its own one-time `compose-unity update` initialization.

`unityPackage` is not yet adapted for `withUnity`; it changes Jenkins nodes internally and mixes Unity commands with Node.js, NPM, and Verdaccio work.

## Pipeline usage

After this repository is configured as a Jenkins Shared Library, its global commands can be used inside a scripted `node` block or a declarative `steps` block. `unityPipeline` is the convenience entry point that selects a node labeled `unity`, checks out `scm`, and calls `unityProject`.

Configuration can be supplied as a map or as a delegated closure. All values below are optional; shown values are defaults.

## The `unityProject` command
This command locates a Unity project inside the repository, updates the project version, runs its unit tests, builds executables, and (if successful) deploys the executables to either Steam or itch.io.

```groovy
unityProject(
	// Relative path to the Unity project inside the repository.
	LOCATION : '',

	// If given, automatically use these credentials to license a free Unity version.
	UNITY_CREDENTIALS : '',
	EMAIL_CREDENTIALS : '',

	// Automatically set the version of the Unity project based on the tags and commits of the VCS. Can be '' (disabled), 'git' or 'plastic'.
	AUTOVERSION : '',
	// Automatically append the build number to the version of the project.
	AUTOVERSION_REVISION : '0',
	AUTOVERSION_REVISION_PREFIX : '',
	AUTOVERSION_REVISION_SUFFIX : '',

	// Assert that the C# code of the package matches the .editorconfig.
	TEST_FORMATTING : '0',
	EDITORCONFIG_LOCATION : '.editorconfig',
	FORMATTING_EXCLUDE : 'Library',

	// Assert Unity's Test Runner tests.
	TEST_UNITY : '0',
	TEST_MODES : 'EditMode PlayMode',

	// Automatically create C# docs using DocFX
	BUILD_DOCUMENTATION : '0',

	// Which executables to create. Note that WebGL can only be built if the project contains the "Slothsoft's UnityExtensions" package.
	BUILD_FOR_WINDOWS : '0',
	BUILD_FOR_LINUX : '0',
	BUILD_FOR_MAC : '0',
	BUILD_FOR_WEBGL : '0',
	BUILD_FOR_ANDROID : '0',
	BUILD_NAME : '',

	BUILD_WINDOWS_CALL : { project, build, report ->
		echo "Building project '${project}' to directory '${build}' while saving log in '${report}'..."
		callUnity "unity-build '${project}' '${build}' windows", report
	},

	BUILD_LINUX_CALL : { project, build, report ->
		echo "Building project '${project}' to directory '${build}' while saving log in '${report}'..."
		callUnity "unity-build '${project}' '${build}' linux", report
	},

	BUILD_MAC_CALL : {project, build, report ->
		echo "Building project '${project}' to directory '${build}' while saving log in '${report}'..."
		callUnity "unity-build '${project}' '${build}' mac", report
	},

	// Deploy, even if previous steps reported errors or warnings.
	DEPLOY_ON_FAILURE : '0',

	// Deploy the executables to the Steam server.
	DEPLOY_TO_STEAM : '0',
	// The Jenkins credentials to use for Steam deployment. These will be fed to `steamcmd` and should consist of user name and password.
	STEAM_CREDENTIALS : '',
	// The Steam App ID to deploy to.
	STEAM_ID : '',
	// The Steam Depot ID to deploy the Windows executable to.
	STEAM_DEPOT_WINDOWS : '',
	// The Steam Depot ID to deploy the Linux executable to.
	STEAM_DEPOT_LINUX : '',
	// The Steam Depot ID to deploy the MacOS executable to.
	STEAM_DEPOT_MAC : '',
	// The Steam branch to deploy to. Defaults to the current VCS branch with all slashes replaced with dashes ('/main/feature' becomes 'main-feature').
	STEAM_BRANCH : '',

	// Deploy the executables to the itch.io server.
	DEPLOY_TO_ITCH : '0',
	// The Jenkins credentials to use for itch.io deployment. These will be fed to `butler` and should consist of an authentification token.
	ITCH_CREDENTIALS : '',
	// The ID of the itch.io page (usually consists of '${author}/${game}').
	ITCH_ID : '',

	// Only attempt to deploy if the current VCS branch is among the branches listed. Note that Plastic's branches start with a slash.
	DEPLOYMENT_BRANCHES : ["main", "/main"],

	// Report the build status to a Discord Webhook.
	REPORT_TO_DISCORD : '0',
	DISCORD_WEBHOOK : '',
	DISCORD_PING_IF : '',

	// Report the build status to a Microsoft Office 365 Webhook.
	REPORT_TO_OFFICE_365 : '0',
	OFFICE_365_WEBHOOK : '',
	OFFICE_365_PING_IF : '',

	// Report the build status to a Microsoft Office 365 Webhook.
	REPORT_TO_ADAPTIVE_CARDS : '0',
	ADAPTIVE_CARDS_WEBHOOK : '',
	ADAPTIVE_CARDS_PING_IF : '',
)
```

## The `unityPackage` command

This command locates a Unity package inside the repository, runs its unit tests, and (if successful) deploys it to a Verdaccio server.

```groovy
unityPackage(
	// Define Unity package location relative to repository.
	LOCATION : '',

	// specify Jenkins node to process calls to Unity
	UNITY_NODE : 'unity',

	// If given, use this package information instead of reading from the package's package.json.
	VERSION : '',
	ID : '',

	// If given, automatically use these credentials to license a free Unity version.
	UNITY_CREDENTIALS : '',
	EMAIL_CREDENTIALS : '',
	// If enabled, bind the Unity-Manifest file credential for the generated project.
	UNITY_MANIFEST : '',

	// Assert that CHANGELOG.md has been updated.
	TEST_CHANGELOG : '0',
	CHANGELOG_LOCATION : 'CHANGELOG.md',

	// Assert that the C# code of the package matches the .editorconfig.
	TEST_FORMATTING : '0',
	EDITORCONFIG_LOCATION : '.editorconfig',
	EDITORCONFIG_ADDONS : '.editor/**, Directory.Build.props',
	FORMATTING_EXCLUDE : '',

	// Assert Unity's Test Runner tests.
	TEST_UNITY : '0',
	TEST_MODES : 'EditMode PlayMode',

	// Automatically create C# docs using DocFX.
	BUILD_DOCUMENTATION : '0',

	// Deploy, even if previous steps reported errors or warnings.
	DEPLOY_ON_FAILURE : '0',

	// Deploy when the package version is a standard release (according to SemVer)
	DEPLOY_IF_RELEASE : '1',

	// Deploy when the package version is a pre-release (according to SemVer)
	DEPLOY_IF_PRERELEASE : '1',

	// Deploy the package to a Verdaccio server.
	DEPLOY_TO_VERDACCIO : '0',
	VERDACCIO_URL : 'http://verdaccio:4873',
	// Host used when writing the project-level NPM authentication setting.
	VERDACCIO_HOST : 'verdaccio:4873',
	// Optional direct storage fallback when NPM publication fails.
	VERDACCIO_STORAGE : '/var/verdaccio',
	// Optional Jenkins string credential containing the NPM token.
	VERDACCIO_CREDENTIALS : '',

	// Only attempt to deploy if the current VCS branch is among the branches listed. Note that Plastic's branches start with a slash.
	DEPLOYMENT_BRANCHES : ["main", "/main"],

	// Report the build status to a Discord Webhook.
	REPORT_TO_DISCORD : '0',
	DISCORD_WEBHOOK : '',
	DISCORD_PING_IF : '',

	// Report the build status to a Microsoft Office 365 Webhook.
	REPORT_TO_OFFICE_365 : '0',
	OFFICE_365_WEBHOOK : '',
	OFFICE_365_PING_IF : '',

	// Report the build status to a Microsoft Office 365 Webhook.
	REPORT_TO_ADAPTIVE_CARDS : '0',
	ADAPTIVE_CARDS_WEBHOOK : '',
	ADAPTIVE_CARDS_PING_IF : '',
)
```
