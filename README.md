# Unity Commands for Jenkins
This repository adds build commands for Unity to a Jenkins pipeline context.

### Prerequisites

The Jenkins server needs access to a node tagged with the label `unity`. That node needs the environment variable `COMPOSE_UNITY`, which should point to a working installation of the composer package [slothsoft/unity](https://github.com/Faulo/slothsoft-unity). (On Linux, this would be something like `composer -d /var/unity exec`, whereas on Windows, it could be `composer -d C:\Webserver\unity exec`. Consult that package for information on how to handle Unity licenses.)

## Usage
All of these commands need to be placed inside a `node` block of the iterative or a `steps` block of the declarative Jenkins pipeline.

All values are optional and default to the first value from among the possible values below.


### The `unityProject` command
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

### The `unityPackage` command

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

	// Assert that CHANGELOG.md has been updated.
	TEST_CHANGELOG : '1',
	CHANGELOG_LOCATION : 'CHANGELOG.md',

	// Assert that the C# code of the package matches the .editorconfig.
	TEST_FORMATTING : '0',
	EDITORCONFIG_LOCATION : '.editorconfig',
	EDITORCONFIG_ADDONS : '.editor/**, Directory.Build.props',
	FORMATTING_EXCLUDE : '',

	// Assert Unity's Test Runner tests.
	TEST_UNITY : '1',
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
	VERDACCIO_STORAGE : '/var/verdaccio',

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
