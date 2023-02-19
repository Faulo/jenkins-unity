def call(body) {
	assert env.BRANCH_NAME != null

	def args= [
		LOCATION : "",

		TEST_MODES : "",

		DEPLOY_TO_VERDACCIO : "0",
		VERDACCIO_URL : "http://verdaccio:4873",

		DEPLOYMENT_BRANCHES : ["main", "/main"],
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

	def testAny = args.TEST_MODES != ''
	def deployAny = args.DEPLOYMENT_BRANCHES.contains(env.BRANCH_NAME)

	try {
		sh "mkdir -p '${reports}'"

		if (testAny) {
			dir(project) {
				deleteDir()
			}

			stage("Creating empty project with package") {
				callUnity "unity-package-install '${pack}' '${project}' 1>'${reports}/package-install.xml'"
			}

			stage("Testing: ${args.TEST_MODES}") {
				callUnity "unity-tests '${project}' ${args.TEST_MODES} 1>'${reports}/tests.xml'"
			}
		}

		if (deployAny) {
			if (args.DEPLOY_TO_VERDACCIO == '1') {
				stage('Deploying to: Verdaccio') {
					echo "Deploying package '${pack}' to Verdaccio at ${args.VERDACCIO_URL}"
				}
			}
		}
	} catch (err) {
		currentBuild.result = "FAILURE"
		throw err
	} finally {
		dir(reports) {
			junit(testResults: '*.xml', allowEmptyResults: true)
			deleteDir()
		}
	}
}
