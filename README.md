# Jenkins Pipeline
This repository adds build commands for Unity.

## Usage
All of these commands need to be placed inside a `node` block of the iterative or a `steps` block of the declarative Jenkins pipeline.

All values are optional and default to the first value from among the possible values below.

The `unityProject` command:
```groovy
unityProject {
	// Relative path to the Unity project inside the repository.
	LOCATION : '',

	// Automatically set the version of the Unity project based on the tags and commits of the VCS.
	AUTOVERSION : '' | 'git' | 'plastic',
	
	// If given, run these unit tests
	TEST_MODES : '' | 'EditMode' | 'PlayMode' | 'EditMode PlayMode'

	// If '1', create a Windows executable.
	BUILD_FOR_WINDOWS : '0' | '1',
	
	// If '1', create a Linux executable.
	BUILD_FOR_LINUX : '0' | '1',
	
	// If '1', create a Mac OS executable.
	BUILD_FOR_MAC : '0' | '1',
	
	// If '1', create a WebGL executable. Requires Slothsoft's UnityExtensions to already be installed as a Unity package.
	BUILD_FOR_WEBGL : '0' | '1',
	
	// If '1', create an Android executable. Requires Slothsoft's UnityExtensions to already be installed as a Unity package.
	BUILD_FOR_ANDROID : '0' | '1',

	// If '1', deploy the created executables to Steam.
	DEPLOY_TO_STEAM : '0' | '1',
	
	// The Steam App ID to deploy to.
	STEAM_ID : '',
	
	// The Steam Depot ID to deploy the Windows executable to.
	STEAM_DEPOT_WINDOWS : "",
	
	// The Steam Depot ID to deploy the Linux executable to.
	STEAM_DEPOT_LINUX : "",
	
	// The Steam Depot ID to deploy the Mac executable to.
	STEAM_DEPOT_MAC : "",
	
	// The Steam branch to deploy to. Defaults to the current VCS branch with all slashes replaced with dashes ("/main/feature" becomes "main-feature").
	STEAM_BRANCH : "",

	// If '1', deploy the created executables to itch.io.
	DEPLOY_TO_ITCH : "0",
	
	// The ID of the itch.io page (usually consists of "${author}/${game}").
	ITCH_ID : "",

	// Only attempt to deploy if the current VCS branch is among the branches listed.
	DEPLOYMENT_BRANCHES : ['main', '/main'],
}
```

The `unityPackage` command:
```groovy
unityPackage {
	// Relative path to the Unity package inside the repository.
	LOCATION : '',
	
	// If given, run these unit tests
	TEST_MODES : '' | 'EditMode' | 'PlayMode' | 'EditMode PlayMode'

	// If '1', deploy the package as-is to a Verdaccio.
	DEPLOY_TO_VERDACCIO : '0' | '1',
	
	// The URL of the Verdaccio server to use for deployment.
	VERDACCIO_URL : 'http://verdaccio:4873',

	// Only attempt to deploy if the current VCS branch is among the branches listed.
	DEPLOYMENT_BRANCHES : ['main', '/main'],
}
```