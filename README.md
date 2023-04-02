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
unityProject {
	// Relative path to the Unity project inside the repository.
	LOCATION : '',


	// Automatically set the version of the Unity project based on the tags and commits of the VCS.
	AUTOVERSION : '' | 'git' | 'plastic',
	
	
	// Automatically create C# docs using DocFX
	BUILD_DOCUMENTATION = '1'
	
	
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
	
	// The Jenkins credentials to use for Steam deployment. These will be fed to `steamcmd` and should consist of user name and password.
	STEAM_CREDENTIALS : '',
	
	// The Steam App ID to deploy to.
	STEAM_ID : '',
	
	// The Steam Depot ID to deploy the Windows executable to.
	STEAM_DEPOT_WINDOWS : '',
	
	// The Steam Depot ID to deploy the Linux executable to.
	STEAM_DEPOT_LINUX : '',
	
	// The Steam Depot ID to deploy the Mac executable to.
	STEAM_DEPOT_MAC : '',
	
	// The Steam branch to deploy to. Defaults to the current VCS branch with all slashes replaced with dashes ('/main/feature' becomes 'main-feature').
	STEAM_BRANCH : '',


	// If '1', deploy the created executables to itch.io.
	DEPLOY_TO_ITCH : '0',
	
	// The Jenkins credentials to use for itch.io deployment. These will be fed to `butler` and should consist of an authentification token.
	ITCH_CREDENTIALS : '',
	
	// The ID of the itch.io page (usually consists of '${author}/${game}').
	ITCH_ID : '',


	// Only attempt to deploy if the current VCS branch is among the branches listed.
	DEPLOYMENT_BRANCHES : ['main', '/main'],
}
```

### The `unityPackage` command

This command locates a Unity package inside the repository, runs its unit tests, and (if successful) deploys it to a Verdaccio server.

```groovy
unityPackage {
	// Relative path to the Unity package inside the repository.
	LOCATION : '',
	
	
	// Automatically create C# docs using DocFX
	BUILD_DOCUMENTATION = '1'
	
	
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
