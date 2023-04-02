def call(body) {
	assert env.BRANCH_NAME != null

	def args = [
		LOCATION : "",

		TEST_MODES : "",
		TEST_CHANGELOG : '1',
		CHANGELOG_LOCATION : 'CHANGELOG.md',

		BUILD_DOCUMENTATION : "0",

		DEPLOY_TO_VERDACCIO : "0",
		VERDACCIO_URL : "http://verdaccio:4873",

		DEPLOYMENT_BRANCHES : ["main", "/main"],

		VERSION : ''
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

	def testAny = args.TEST_MODES != ''
	def docsAny = args.BUILD_DOCUMENTATION == '1'
	def deployAny = args.DEPLOYMENT_BRANCHES.contains(env.BRANCH_NAME)

	if (args.VERSION == '') {
		dir(pack) {
			args.VERSION = callShellStdout "node --eval=\"process.stdout.write(require('./package.json').version)\""
		}
	}
	def localVersion = args.VERSION

	if (testAny || docsAny) {
		dir(project) {
			deleteDir()
		}

		dir(reports) {
			deleteDir()

			stage("Creating empty project with package") {
				callUnity "unity-package-install '${pack}' '${project}' 1>'${reports}/package-install.xml'"
				junit(testResults: 'package-install.xml')
			}

			if (docsAny) {
				stage("Building: Documentation") {
					catchError(stageResult: 'FAILURE', buildResult: 'UNSTABLE') {
						dir(docs) {
							deleteDir()
						}

						callUnity "unity-documentation '${project}'"
						callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.Solution 1>'${reports}/build-solution.xml'"
						junit(testResults: 'build-solution.xml')

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

			if (args.TEST_CHANGELOG == '1') {
				dir(pack) {
					stage("Testing: ${args.CHANGELOG_LOCATION}") {
						if (!fileExists(args.CHANGELOG_LOCATION)) {
							unstable "${args.CHANGELOG_LOCATION} is missing."
						}

						def changelogContent = readFile(args.CHANGELOG_LOCATION)
						def expectedChangelogLine = "## \\[$localVersion\\] - \\d{4}-\\d{2}-\\d{2}"
						if (!changelogContent.find(expectedChangelogLine)) {
							unstable "${args.CHANGELOG_LOCATION} does not contain an entry '## [${localVersion}] - YYYY-MM-DD'.\nCurrent changelog is:\n${changelogContent}"
						}
					}
				}
			}

			if (testAny) {
				stage("Testing: ${args.TEST_MODES}") {
					callUnity "unity-tests '${project}' ${args.TEST_MODES} 1>'${reports}/tests.xml'"
					junit(testResults: 'tests.xml', allowEmptyResults: true)
				}
			}
		}
	}

	if (deployAny) {
		if (args.DEPLOY_TO_VERDACCIO == '1') {
			dir(pack) {
				def publishedVersion = callShellStdout "npm view --registry '${args.VERDACCIO_URL}' . version || echo '0'"

				if (localVersion != publishedVersion) {
					stage('Deploying to: Verdaccio') {
						if (currentBuild.currentResult != "SUCCESS") {
							error "Current result is '${currentBuild.currentResult}', aborting deployment of version ${localVersion}."
						}

						echo "Deploying update: ${publishedVersion} => ${localVersion}"
						callShell "npm publish . --registry '${args.VERDACCIO_URL}'"
					}
				}
			}
		}
	}
}
