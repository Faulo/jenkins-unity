def call(body) {
	def args= [
		PACKAGE_LOCATION : "",

		TEST_MODES : "",

		DEPLOY_TO_VERDACCIO : "0",
		VERDACCIO_URL : "http://verdaccio:4873",

		DEPLOYMENT_BRANCHES : [ "main" ],
	]

	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = args
	body()

	if (args.PACKAGE_LOCATION == '') {
		args.PACKAGE_LOCATION = '.'
	}

	def pack = "$WORKSPACE/${args.PACKAGE_LOCATION}"
	def project = "$WORKSPACE_TMP/empty-project"
	def reports = "$WORKSPACE_TMP/reports"

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
		stage('Gathering reports') {
			dir(reports) {
				junit(testResults: '*.xml', allowEmptyResults: true)
				deleteDir()
			}
		}
	}
}
