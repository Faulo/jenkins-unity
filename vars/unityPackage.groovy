def call(body) {
	assert env.BRANCH_NAME != null

	def args = [
		LOCATION : '',

		TEST_UNITY : '1',
		TEST_MODES : 'EditMode PlayMode',

		TEST_CHANGELOG : '1',
		CHANGELOG_LOCATION : 'CHANGELOG.md',

		TEST_FORMATTING : '0',
		EDITORCONFIG_LOCATION : '.editorconfig',
		FORMATTING_EXCLUDE : '',

		BUILD_DOCUMENTATION : '0',

		DEPLOY_TO_VERDACCIO : '0',
		VERDACCIO_URL : 'http://verdaccio:4873',
		VERDACCIO_STORAGE : '/var/verdaccio',

		DEPLOYMENT_BRANCHES : ["main", "/main"],

		VERSION : '',
		ID : ''
	]

	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = args
	body()

	// backwards compatibility
	if (args.containsKey('PACKAGE_LOCATION')) {
		args.LOCATION = args.PACKAGE_LOCATION
	}

	// we want a path-compatible location
	if (args.LOCATION == '') {
		args.LOCATION = '.'
	}

	def pack = "$WORKSPACE/${args.LOCATION}"
	def project = "$WORKSPACE_TMP/${args.LOCATION}/project"
	def reports = "$WORKSPACE_TMP/${args.LOCATION}/reports"
	def docs = "${project}/.Documentation"

	if (!fileExists(pack)) {
		error "Package folder '${pack}' does not exist!"
	}

	def createSolution = args.TEST_FORMATTING == '1' || args.BUILD_DOCUMENTATION == '1'
	def createProject = createSolution || args.TEST_UNITY == '1'

	if (args.VERSION == '') {
		dir(pack) {
			args.VERSION = callShellStdout "node --eval=\"process.stdout.write(require('./package.json').version)\""
		}
	}
	def localVersion = args.VERSION

	if (args.ID == '') {
		dir(pack) {
			args.ID = callShellStdout "node --eval=\"process.stdout.write(require('./package.json').name)\""
		}
	}
	def id = args.ID

	if (args.TEST_CHANGELOG == '1') {
		dir(pack) {
			stage("Test: ${args.CHANGELOG_LOCATION}") {
				if (!fileExists(args.CHANGELOG_LOCATION)) {
					unstable "Changelog at '${args.CHANGELOG_LOCATION}' is missing."
				}

				def changelogContent = readFile(args.CHANGELOG_LOCATION)
				def expectedChangelogLine = "## \\[$localVersion\\] - \\d{4}-\\d{2}-\\d{2}"
				if (!changelogContent.find(expectedChangelogLine)) {
					unstable "${args.CHANGELOG_LOCATION} does not contain an entry '## [${localVersion}] - YYYY-MM-DD'.\nCurrent changelog is:\n${changelogContent}"
				}
			}
		}
	}

	if (createProject) {
		dir(project) {
			deleteDir()
		}

		dir(reports) {
			deleteDir()

			stage("Build: Empty project with package") {
				callUnity "unity-package-install '${pack}' '${project}' 1>'${reports}/package-install.xml'"
				junit(testResults: 'package-install.xml')
			}

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
						warnError("Code needs formatting!") {
							callShell "dotnet format --verify-no-changes project.sln${exclude}"
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
		}
	}

	if (args.DEPLOYMENT_BRANCHES.contains(env.BRANCH_NAME)) {
		if (args.DEPLOY_TO_VERDACCIO == '1') {
			dir(pack) {
				def publishedVersion = callShellStdout "npm view --registry '${args.VERDACCIO_URL}' . version || echo '0'"

				if (localVersion != publishedVersion) {
					stage('Deploy to: Verdaccio') {
						if (currentBuild.currentResult != "SUCCESS") {
							error "Current result is '${currentBuild.currentResult}', aborting deployment of version ${localVersion}."
						}

						echo "Deploying update: ${publishedVersion} => ${localVersion}"
						try {
							callShell "npm publish . --registry '${args.VERDACCIO_URL}'"
						} catch(e) {
							echo "Deployment via NPM failed, switching to manual mode..."

							dir(pack + "/..") {
								callShell "mv '${id}' package"
								callShell "tar -zcvf package.tgz package"
								callShell "mv package '${id}'"
								callShell "chmod 0777 package.tgz"

								def file = "${id}-${localVersion}.tgz"
								def hash = callShellStdout("sha1sum package.tgz | awk '{ print \$1 }'")
								def timestamp = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

								def packageData = readJSON(file: "${id}/package.json")
								packageData.readmeFilename = "README.md"
								packageData.description = ""
								packageData._id = "${id}@${localVersion}".toString()
								packageData._nodeVersion = "18.16.1"
								packageData._npmVersion = "9.5.1-ulisses.1"
								packageData.dist = [
									shasum: hash,
									tarball: "${args.VERDACCIO_URL}/${id}/-/${file}".toString()
								]

								callShell "mv package.tgz '${args.VERDACCIO_STORAGE}/${id}/${file}'"

								def storageData = readJSON(file: "${args.VERDACCIO_STORAGE}/${id}/package.json")
								storageData.versions[localVersion] = packageData
								storageData.time["modified"] = timestamp
								storageData.time[localVersion] = timestamp
								storageData["dist-tags"]["latest"] = localVersion
								storageData._attachments[file] = [
									shasum: hash,
									version: localVersion
								]

								writeJSON(file: "${args.VERDACCIO_STORAGE}/${id}/package.json", json: storageData, pretty: 2)
							}
						}
					}
				}
			}
		}
	}
}
