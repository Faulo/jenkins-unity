def call(body) {
	assert env.BRANCH_NAME != null

	def args = [
		LOCATION : '',

		AUTOVERSION : '',
		AUTOVERSION_REVISION : '0',

		BUILD_DOCUMENTATION : '0',

		TEST_UNITY : '0',
		TEST_MODES : 'EditMode PlayMode',

		TEST_FORMATTING : '0',
		EDITORCONFIG_LOCATION : '.editorconfig',
		FORMATTING_EXCLUDE : '',

		BUILD_FOR_WINDOWS : '0',
		BUILD_FOR_LINUX : '0',
		BUILD_FOR_MAC : '0',
		BUILD_FOR_WEBGL : '0',
		BUILD_FOR_ANDROID : '0',

		DEPLOY_TO_STEAM : '0',
		STEAM_CREDENTIALS : '',
		STEAM_ID : '',
		STEAM_DEPOT_WINDOWS : '',
		STEAM_DEPOT_LINUX : '',
		STEAM_DEPOT_MAC : '',
		STEAM_BRANCH : '',

		DEPLOY_TO_ITCH : '0',
		ITCH_CREDENTIALS : '',
		ITCH_ID : '',

		DEPLOYMENT_BRANCHES : ["main", "/main"],
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

	// steam branches can't contain slashes or spaces
	if (args.STEAM_BRANCH == '') {
		args.STEAM_BRANCH = env.BRANCH_NAME.replace("/", " ").trim().replace(" ", "-")
	}

	def project = "$WORKSPACE/${args.LOCATION}"
	def reports = "$WORKSPACE_TMP/${args.LOCATION}/reports"
	def docs = "${project}/.Documentation"

	def setVersion = args.AUTOVERSION != ''

	def createSolution = args.TEST_FORMATTING == '1' || args.BUILD_DOCUMENTATION == '1'
	def createBuild = [
		args.BUILD_FOR_WINDOWS,
		args.BUILD_FOR_LINUX,
		args.BUILD_FOR_MAC,
		args.BUILD_FOR_WEBGL,
		args.BUILD_FOR_ANDROID
	].contains('1');

	def deployAny = args.DEPLOYMENT_BRANCHES.contains(env.BRANCH_NAME)

	def id = 'Unknown'

	try {
		id = callUnity "unity-project-setting '${project}' 'productName'"
	} catch(e) {
	}

	stage("Project: ${id}") {

		if (setVersion) {
			stage("Set: Project version") {
				def version = callUnity "autoversion '${args.AUTOVERSION}' '$WORKSPACE'"
				if (args.AUTOVERSION_REVISION == '1') {
					version += "+${env.BUILD_NUMBER}"
				}
				callUnity "unity-project-version '${project}' set '${version}'"
			}
		}


		dir(reports) {
			deleteDir()

			if (createSolution) {
				stage("Build: C# solution") {
					callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.Solution 1>'${reports}/build-solution.xml'"
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
						writeFile(file: "${editorconfigTarget}", text: editorconfigContent)
					}
					dir(project) {
						def exclude = args.FORMATTING_EXCLUDE == '' ? '' : " --exclude ${args.FORMATTING_EXCLUDE}"
						def files = findFiles(glob: '*.sln')
						for (file in files) {
							warnError("Code needs formatting!") {
								callShell "dotnet format --verify-no-changes ${file.name}${exclude}"
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
					callUnity "unity-tests '${project}' ${args.TEST_MODES} 1>'${reports}/tests.xml'"
					junit(testResults: 'tests.xml', allowEmptyResults: true)
				}
			}

			if (createBuild) {
				if (args.BUILD_FOR_WINDOWS == '1') {
					stage('Build: Windows') {
						callUnity "unity-build '${project}' '${reports}/build-windows' windows 1>'${reports}/build-windows.xml'"
						junit(testResults: 'build-windows.xml')
						zip(zipFile: 'build-windows.zip', dir: 'build-windows', archive: true)
					}
				}

				if (args.BUILD_FOR_LINUX == '1') {
					stage('Build: Linux') {
						callUnity "unity-build '${project}' '${reports}/build-linux' linux 1>'${reports}/build-linux.xml'"
						junit(testResults: 'build-linux.xml')
						zip(zipFile: 'build-linux.zip', dir: 'build-linux', archive: true)
					}
				}

				if (args.BUILD_FOR_MAC == '1') {
					stage('Build: MacOS') {
						callUnity "unity-build '${project}' '${reports}/build-mac' mac 1>'${reports}/build-mac.xml'"
						junit(testResults: 'build-mac.xml')
						zip(zipFile: 'build-mac.zip', dir: 'build-mac', archive: true)
					}
				}

				if (args.BUILD_FOR_WEBGL == '1') {
					stage('Build: WebGL') {
						callUnity "unity-module-install '${project}' webgl 1>'${reports}/install-webgl.xml'"
						junit(testResults: 'install-webgl.xml')
						callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.WebGL '${reports}/build-webgl' 1>'${reports}/build-webgl.xml'"
						junit(testResults: 'build-webgl.xml')
						zip(zipFile: 'build-webgl.zip', dir: 'build-webgl', archive: true)
						publishHTML([
							allowMissing: false,
							alwaysLinkToLastBuild: false,
							keepAll: false,
							reportDir: 'build-webgl',
							reportFiles: 'index.html',
							reportName: 'WebGL Build',
							reportTitles: '',
							useWrapperFileDirectly: true
						])
					}
				}

				if (args.BUILD_FOR_ANDROID == '1') {
					stage('Build: Android') {
						callUnity "unity-module-install '${project}' android 1>'${reports}/install-android.xml'"
						junit(testResults: 'install-android.xml')
						callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.Android '${reports}/build-android.apk' 1>'${reports}/build-android.xml'"
						junit(testResults: 'build-android.xml')
						archiveArtifacts(artifacts: 'build-android.apk')
					}
				}

				if (deployAny) {
					if (args.DEPLOY_TO_STEAM == '1') {
						stage('Deploy to: Steam') {
							if (currentBuild.currentResult != "SUCCESS") {
								error "Current result is '${currentBuild.currentResult}', skipping deployment."
							}

							def depots = '';
							if (args.BUILD_FOR_WINDOWS == '1' && args.STEAM_DEPOT_WINDOWS != '') {
								depots += "${args.STEAM_DEPOT_WINDOWS}=build-windows "
							}
							if (args.BUILD_FOR_LINUX == '1' && args.STEAM_DEPOT_LINUX != '') {
								depots += "${args.STEAM_DEPOT_LINUX}=build-linux "
							}
							if (args.BUILD_FOR_MAC == '1' && args.STEAM_DEPOT_MAC != '') {
								depots += "${args.STEAM_DEPOT_MAC}=build-mac "
							}

							if (depots == '') {
								error "Missing Steam depots! Please specify any of the parameters STEAM_DEPOT_WINDOWS, STEAM_DEPOT_LINUX, or STEAM_DEPOT_MAC."
							}

							callUnity "steam-buildfile '${reports}' '${reports}' ${args.STEAM_ID} ${depots} ${args.STEAM_BRANCH} 1>'${reports}/deploy-steam.vdf'"
							withCredentials([
								usernamePassword(credentialsId: args.STEAM_CREDENTIALS, usernameVariable: 'STEAM_CREDS_USR', passwordVariable: 'STEAM_CREDS_PSW')
							]) {
								callShell "steamcmd +login $STEAM_CREDS_USR $STEAM_CREDS_PSW +run_app_build '${reports}/deploy-steam.vdf' +quit"
							}
						}
					}

					if (args.DEPLOY_TO_ITCH == '1') {
						stage('Deploy to: itch.io') {
							if (currentBuild.currentResult != "SUCCESS") {
								error "Current result is '${currentBuild.currentResult}', skipping deployment."
							}

							withCredentials([
								string(credentialsId: args.ITCH_CREDENTIALS, variable: 'BUTLER_API_KEY')
							]) {
								if (args.BUILD_FOR_WINDOWS == '1') {
									callShell "butler push --if-changed build-windows ${args.ITCH_ID}:windows-x64"
								}
								if (args.BUILD_FOR_LINUX == '1') {
									callShell "butler push --if-changed build-linux ${args.ITCH_ID}:linux-x64"
								}
								if (args.BUILD_FOR_MAC == '1') {
									callShell "butler push --if-changed build-mac ${args.ITCH_ID}:mac-x64"
								}
								if (args.BUILD_FOR_WEBGL == '1') {
									callShell "butler push --if-changed build-webgl ${args.ITCH_ID}:html"
								}
								if (args.BUILD_FOR_ANDROID == '1') {
									callShell "butler push --if-changed build-android.apk ${args.ITCH_ID}:android"
								}
							}
						}
					}
				}
			}
		}
	}
}
