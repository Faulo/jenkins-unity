def call(body) {
	assert env.BRANCH_NAME != null

	def args = [
		// Relative path to the Unity project inside the repository.
		LOCATION : '',

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
	]

	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = args
	body()

	// backwards compatibility
	if (args.containsKey('PROJECT_LOCATION')) {
		args.LOCATION = args.PROJECT_LOCATION
	}
	if (args.containsKey('PROJECT_AUTOVERSION')) {
		args.AUTOVERSION = args.PROJECT_AUTOVERSION
	}

	// we want a path-compatible location
	if (args.LOCATION == '') {
		args.LOCATION = '.'
	}

	if (args.BUILD_NAME == '') {
		args.BUILD_NAME = 'build'
	}

	// steam branches can't contain slashes or spaces
	if (args.STEAM_BRANCH == '') {
		args.STEAM_BRANCH = env.BRANCH_NAME.replace("/", " ").trim().replace(" ", "-")
	}

	def project = "$WORKSPACE/${args.LOCATION}"
	def reports = "$WORKSPACE_TMP/${args.LOCATION}/reports"
	def docs = "${project}/.Documentation"

	def createSolution = args.TEST_FORMATTING == '1' || args.BUILD_DOCUMENTATION == '1'
	def createBuild = [
		args.BUILD_FOR_WINDOWS,
		args.BUILD_FOR_LINUX,
		args.BUILD_FOR_MAC,
		args.BUILD_FOR_WEBGL,
		args.BUILD_FOR_ANDROID
	].contains('1')

	def deployAny = args.DEPLOYMENT_BRANCHES.contains(env.BRANCH_NAME)

	def reportAny = [
		args.REPORT_TO_DISCORD,
		args.REPORT_TO_OFFICE_365,
		args.REPORT_TO_ADAPTIVE_CARDS,
	].contains('1')

	def setVersion = args.AUTOVERSION != ''
	def getVersion = setVersion || reportAny

	def id = 'Unknown'

	try {
		id = callUnity "unity-project-setting '${project}' 'productName'"
	} catch(e) {
	}

	def localVersion = '?'

	if (getVersion) {
		try {
			if (setVersion) {
				localVersion = callUnity "autoversion '${args.AUTOVERSION}' '$WORKSPACE'"
				if (args.AUTOVERSION_REVISION == '1') {
					localVersion += "+${args.AUTOVERSION_REVISION_PREFIX}${env.BUILD_NUMBER}${args.AUTOVERSION_REVISION_SUFFIX}"
				}
			} else {
				localVersion = callUnity "unity-project-setting '${project}' 'bundleVersion'"
			}
		} catch(e) {
		}
	}

	stage("Project: ${id}") {
		try {
			if (setVersion) {
				stage("Set: Project version") {
					callUnity "unity-project-version '${project}' set '${localVersion}'"
				}
			}

			dir(reports) {
				deleteDir()

				if (createSolution) {
					stage("Build: C# solution") {
						callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.Solution", "${reports}/build-solution.xml"
						junit(testResults: 'build-solution.xml')
					}
				}

				if (args.BUILD_DOCUMENTATION == '1') {
					stage("Build: DocFX documentation") {
						catchError(stageResult: 'FAILURE', buildResult: 'UNSTABLE') {
							dir(docs) {
								deleteDir()
							}

							callUnity "unity-documentation '${project}'"

							dir(docs) {
								callShell "dotnet tool restore"
								callShell "dotnet tool run docfx"

								publishHTML([
									allowMissing: false,
									alwaysLinkToLastBuild: false,
									keepAll: false,
									reportDir: 'html',
									reportFiles: 'index.html',
									reportName: 'Documentation',
									reportTitles: '',
									useWrapperFileDirectly: true
								])

								deleteDir()
							}
						}
					}
				}

				if (args.TEST_FORMATTING == '1') {
					stage("Test: ${args.EDITORCONFIG_LOCATION}") {
						def editorconfigTarget = "${project}/.editorconfig"
						dir(env.WORKSPACE) {
							def editorconfigSource = args.EDITORCONFIG_LOCATION
							if (!fileExists(editorconfigSource)) {
								unstable "Editor Config at '${editorconfigSource}' is missing."
							}
							def editorconfigContent = readFile(file: editorconfigSource)
							writeFile(file: editorconfigTarget, text: editorconfigContent)
						}
						dir(project) {
							def exclude = args.FORMATTING_EXCLUDE == '' ? '' : " --exclude ${args.FORMATTING_EXCLUDE}"
							def jsonFile = "${reports}/format-report.json";
							def xmlFile = "${reports}/format-report.xml";

							def files = findFiles(glob: '*.sln')
							for (file in files) {
								callShellStatus "dotnet format ${file.name} --verify-no-changes --verbosity normal --report ${reports}${exclude}"
								if (!fileExists(jsonFile)) {
									error "dotnet format failed to create '${jsonFile}'."
								}

								callUnity "transform-dotnet-format '${jsonFile}'", xmlFile;
								if (!fileExists(xmlFile)) {
									error "transform-dotnet-format failed to create '${xmlFile}'."
								}

								dir(reports) {
									junit(testResults: 'format-report.xml', allowEmptyResults: true)
								}
							}
						}
					}
				}

				if (args.TEST_UNITY == '1') {
					stage("Test: ${args.TEST_MODES}") {
						if (args.TEST_MODES == '') {
							unstable "Parameter TEST_MODES is missing."
						}
						callUnity "unity-tests '${project}' ${args.TEST_MODES}", "${reports}/tests.xml"
						junit(testResults: 'tests.xml', allowEmptyResults: true)
					}
				}

				if (createBuild) {
					if (args.BUILD_FOR_WINDOWS == '1') {
						stage('Build: Windows') {
							args.BUILD_WINDOWS_CALL(project, "${reports}/${args.BUILD_NAME}-windows", "${reports}/${args.BUILD_NAME}-windows.xml")
							junit(testResults: "${args.BUILD_NAME}-windows.xml")
							zip(zipFile: "${args.BUILD_NAME}-windows.zip", dir: "${args.BUILD_NAME}-windows", archive: true)
						}
					}

					if (args.BUILD_FOR_LINUX == '1') {
						stage('Build: Linux') {
							args.BUILD_LINUX_CALL(project, "${reports}/${args.BUILD_NAME}-linux", "${reports}/${args.BUILD_NAME}-linux.xml")
							junit(testResults: "${args.BUILD_NAME}-linux.xml")
							zip(zipFile: "${args.BUILD_NAME}-linux.zip", dir: "${args.BUILD_NAME}-linux", archive: true)
						}
					}

					if (args.BUILD_FOR_MAC == '1') {
						stage('Build: MacOS') {
							args.BUILD_MAC_CALL(project, "${reports}/${args.BUILD_NAME}-mac", "${reports}/${args.BUILD_NAME}-mac.xml")
							junit(testResults: "${args.BUILD_NAME}-mac.xml")
							zip(zipFile: "${args.BUILD_NAME}-mac.zip", dir: "${args.BUILD_NAME}-mac", archive: true)
						}
					}

					if (args.BUILD_FOR_WEBGL == '1') {
						stage('Build: WebGL') {
							callUnity "unity-module-install '${project}' webgl", "${reports}/install-webgl.xml"
							junit(testResults: 'install-webgl.xml')
							callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.WebGL -- -buildTarget WebGL '${reports}/${args.BUILD_NAME}-webgl'", "${reports}/${args.BUILD_NAME}-webgl.xml"
							junit(testResults: "${args.BUILD_NAME}-webgl.xml")
							zip(zipFile: "${args.BUILD_NAME}-webgl.zip", dir: "${args.BUILD_NAME}-webgl", archive: true)
							publishHTML([
								allowMissing: false,
								alwaysLinkToLastBuild: false,
								keepAll: false,
								reportDir: "${args.BUILD_NAME}-webgl",
								reportFiles: 'index.html',
								reportName: 'WebGL Build',
								reportTitles: '',
								useWrapperFileDirectly: true
							])
						}
					}

					if (args.BUILD_FOR_ANDROID == '1') {
						stage('Build: Android') {
							callUnity "unity-module-install '${project}' android", "${reports}/install-android.xml"
							junit(testResults: 'install-android.xml')
							callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.Android -- -buildTarget Android '${reports}/${args.BUILD_NAME}-android.apk'", "${reports}/${args.BUILD_NAME}-android.xml"
							junit(testResults: "${args.BUILD_NAME}-android.xml")
							archiveArtifacts(artifacts: "${args.BUILD_NAME}-android.apk")
						}
					}

					if (deployAny) {
						if (args.DEPLOY_TO_STEAM == '1') {
							stage('Deploy to: Steam') {
								if (args.DEPLOY_ON_FAILURE != '1' && currentBuild.currentResult != "SUCCESS") {
									error "Current result is '${currentBuild.currentResult}', skipping deployment."
								}

								def depots = ''
								if (args.BUILD_FOR_WINDOWS == '1' && args.STEAM_DEPOT_WINDOWS != '') {
									depots += "${args.STEAM_DEPOT_WINDOWS}=${args.BUILD_NAME}-windows "
								}
								if (args.BUILD_FOR_LINUX == '1' && args.STEAM_DEPOT_LINUX != '') {
									depots += "${args.STEAM_DEPOT_LINUX}=${args.BUILD_NAME}-linux "
								}
								if (args.BUILD_FOR_MAC == '1' && args.STEAM_DEPOT_MAC != '') {
									depots += "${args.STEAM_DEPOT_MAC}=${args.BUILD_NAME}-mac "
								}

								if (depots == '') {
									error "Missing Steam depots! Please specify any of the parameters STEAM_DEPOT_WINDOWS, STEAM_DEPOT_LINUX, or STEAM_DEPOT_MAC."
								}

								callUnity "steam-buildfile '${reports}' '${reports}' ${args.STEAM_ID} ${depots} ${args.STEAM_BRANCH}", "${reports}/deploy-steam.vdf"
								withCredentials([
									usernamePassword(credentialsId: args.STEAM_CREDENTIALS, usernameVariable: 'STEAM_CREDS_USR', passwordVariable: 'STEAM_CREDS_PSW')
								]) {
									callShell "steamcmd +login $STEAM_CREDS_USR $STEAM_CREDS_PSW +run_app_build '${reports}/deploy-steam.vdf' +quit"
								}
							}
						}

						if (args.DEPLOY_TO_ITCH == '1') {
							stage('Deploy to: itch.io') {
								if (args.DEPLOY_ON_FAILURE != '1' && currentBuild.currentResult != "SUCCESS") {
									error "Current result is '${currentBuild.currentResult}', skipping deployment."
								}

								withCredentials([
									string(credentialsId: args.ITCH_CREDENTIALS, variable: 'BUTLER_API_KEY')
								]) {
									if (args.BUILD_FOR_WINDOWS == '1') {
										callShell "butler push --if-changed ${args.BUILD_NAME}-windows ${args.ITCH_ID}:windows-x64"
									}
									if (args.BUILD_FOR_LINUX == '1') {
										callShell "butler push --if-changed ${args.BUILD_NAME}-linux ${args.ITCH_ID}:linux-x64"
									}
									if (args.BUILD_FOR_MAC == '1') {
										callShell "butler push --if-changed ${args.BUILD_NAME}-mac ${args.ITCH_ID}:mac-x64"
									}
									if (args.BUILD_FOR_WEBGL == '1') {
										callShell "butler push --if-changed ${args.BUILD_NAME}-webgl ${args.ITCH_ID}:html"
									}
									if (args.BUILD_FOR_ANDROID == '1') {
										callShell "butler push --if-changed ${args.BUILD_NAME}-android.apk ${args.ITCH_ID}:android"
									}
								}
							}
						}
					}
				}
			}
		} catch(org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
			currentBuild.result = e.result
		} catch(e) {
			currentBuild.result = "UNKNOWN"
		} finally {
			if (reportAny) {
				def name = "${id} v${localVersion}";

				if (args.REPORT_TO_DISCORD == '1') {
					if (args.DISCORD_PING_IF == '' || currentBuild.resultIsWorseOrEqualTo(args.DISCORD_PING_IF)) {
						reportToDiscord(args.DISCORD_WEBHOOK, currentBuild, name)
					}
				}

				if (args.REPORT_TO_OFFICE_365 == '1') {
					if (args.OFFICE_365_PING_IF == '' || currentBuild.resultIsWorseOrEqualTo(args.OFFICE_365_PING_IF)) {
						reportToOffice365(args.OFFICE_365_WEBHOOK, currentBuild, name)
					}
				}

				if (args.REPORT_TO_ADAPTIVE_CARDS == '1') {
					if (args.ADAPTIVE_CARDS_PING_IF == '' || currentBuild.resultIsWorseOrEqualTo(args.ADAPTIVE_CARDS_PING_IF)) {
						reportToAdaptiveCard(args.ADAPTIVE_CARDS_WEBHOOK, currentBuild, name)
					}
				}
			}

			dir(reports) {
				deleteDir()
			}
		}
	}
}
