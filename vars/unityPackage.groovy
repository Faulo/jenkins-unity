def call(Closure body) {
	def args = [:]

	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = args
	body()

	unityPackage(args)
}

def call(Map args) {
	assert env.BRANCH_NAME != null

	def defaultArgs = [
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
		VERDACCIO_HOST : 'verdaccio',
		VERDACCIO_STORAGE : '/var/verdaccio',
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
	]

	args = defaultArgs + args

	// backwards compatibility
	if (args.containsKey('PACKAGE_LOCATION')) {
		args.LOCATION = args.PACKAGE_LOCATION
	}

	// we want a path-compatible location
	if (args.LOCATION == '') {
		args.LOCATION = '.'
	}

	def pack = "$WORKSPACE/${args.LOCATION}"

	if (!fileExists(pack)) {
		error "Package folder '${pack}' does not exist!"
	}

	if (args.ID == '') {
		dir(pack) {
			args.ID = callShellStdout "node --eval=\"process.stdout.write(require('./package.json').name)\""
		}
	}
	def id = args.ID


	stage("Package: ${id}") {

		def createSolution = args.TEST_FORMATTING == '1' || args.BUILD_DOCUMENTATION == '1'
		def createProject = createSolution || args.TEST_UNITY == '1'

		def reportAny = [
			args.REPORT_TO_DISCORD,
			args.REPORT_TO_OFFICE_365,
			args.REPORT_TO_ADAPTIVE_CARDS,
		].contains('1')

		if (args.VERSION == '') {
			dir(pack) {
				args.VERSION = callShellStdout "node --eval=\"process.stdout.write(require('./package.json').version)\""
			}
		}
		def localVersion = args.VERSION
		def isRelease = !localVersion.contains("-")
		def stableVersion = isRelease
				? localVersion
				: localVersion.substring(0, localVersion.indexOf("-"))

		def editorconfigContent = args.TEST_FORMATTING == '1'
				? readFile(file: args.EDITORCONFIG_LOCATION)
				: ""

		def editorStashed = false
		if (args.TEST_FORMATTING == '1' && args.EDITORCONFIG_ADDONS != '') {
			editorStashed = true
			stash name: 'editorconfig', allowEmpty: true, includes: args.EDITORCONFIG_ADDONS
		}

		try {

			if (args.TEST_CHANGELOG == '1') {
				dir(pack) {
					stage("Test: ${args.CHANGELOG_LOCATION}") {
						if (!fileExists(args.CHANGELOG_LOCATION)) {
							unstable "Changelog at '${args.CHANGELOG_LOCATION}' is missing."
						}

						def changelogContent = readFile(args.CHANGELOG_LOCATION)
						def expectedChangelogLine = "## \\[$localVersion\\] - \\d{4}-\\d{2}-\\d{2}"
						if (isRelease) {
							if (!changelogContent.find(expectedChangelogLine)) {
								unstable "${args.CHANGELOG_LOCATION} does not contain an entry '## [${localVersion}] - YYYY-MM-DD'.\nCurrent changelog is:\n${changelogContent}"
							}
						} else {
							def expectedStableChangelogLine = "## \\[$stableVersion\\] - \\d{4}-\\d{2}-\\d{2}"
							if (!(changelogContent.find(expectedChangelogLine) || changelogContent.find(expectedStableChangelogLine))) {
								unstable "${args.CHANGELOG_LOCATION} does not contain an entry '## [${stableVersion}] - YYYY-MM-DD' or '## [${localVersion}] - YYYY-MM-DD'.\nCurrent changelog is:\n${changelogContent}"
							}
						}
					}
				}
			}

			if (createProject) {
				stage("Switch to Unity node") {
					dir(pack) {
						stash name: 'package', includes: "**"
					}

					node(args.UNITY_NODE) {
						dir("$WORKSPACE_TMP") {
							echo "Running on '$NODE_NAME' in ${pwd()}"

							deleteDir()

							dir('package') {
								unstash 'package'
							}

							def credentials = []

							if (args.UNITY_CREDENTIALS != '') {
								credentials << usernamePassword(credentialsId: args.UNITY_CREDENTIALS, usernameVariable: 'UNITY_CREDENTIALS_USR', passwordVariable: 'UNITY_CREDENTIALS_PSW')
							}

							if (args.EMAIL_CREDENTIALS != '') {
								credentials << usernamePassword(credentialsId: args.EMAIL_CREDENTIALS, usernameVariable: 'EMAIL_CREDENTIALS_USR', passwordVariable: 'EMAIL_CREDENTIALS_PSW')
							}

							if (args.UNITY_MANIFEST != '') {
								credentials << file(credentialsId: 'Unity-Manifest', variable: 'UNITY_EMPTY_MANIFEST')
							}

							withCredentials(credentials) {
								def envOverrides = []

								if (env.UNITY_EMPTY_MANIFEST) {
									// try absolute path
									def file = new File(env.UNITY_EMPTY_MANIFEST)
									if (!file.exists()) {
										// try relative path
										file = new File(pwd() + "/package", env.UNITY_EMPTY_MANIFEST)
									}
									if (file.exists()) {
										def resolved = file.canonicalPath
										envOverrides << "UNITY_EMPTY_MANIFEST=${resolved}"
										echo "Setting 'UNITY_EMPTY_MANIFEST' to '${resolved}'"
									} else {
										echo "WARNING: Failed to find 'UNITY_EMPTY_MANIFEST' file '${file}'"
									}
								}

								withEnv(envOverrides) {
									dir('reports') {
										stage("Build: Empty project with package") {
											callUnity "unity-package-install '$WORKSPACE_TMP/package' '$WORKSPACE_TMP/project'", "package-install.xml"
											junit(testResults: 'package-install.xml')
										}
									}

									if (editorStashed) {
										dir('project') {
											unstash 'editorconfig'
										}
									}

									dir('reports') {
										if (createSolution) {
											stage("Build: C# solution") {
												callUnity "unity-method '$WORKSPACE_TMP/project' Slothsoft.UnityExtensions.Editor.Build.Solution", "build-solution.xml"
												junit(testResults: 'build-solution.xml')
											}
										}
									}

									if (args.BUILD_DOCUMENTATION == '1') {
										stage("Build: DocFX documentation") {
											catchError(stageResult: 'FAILURE', buildResult: 'UNSTABLE') {
												dir('project/.Documentation') {
													deleteDir()

													callUnity "unity-documentation '$WORKSPACE_TMP/project'"

													callDocFX(id)
												}
											}
										}
									}

									if (args.TEST_FORMATTING == '1') {
										stage("Test: ${args.EDITORCONFIG_LOCATION}") {
											writeFile(file: "$WORKSPACE_TMP/project/.editorconfig", text: editorconfigContent)

											callDotnetFormat("$WORKSPACE_TMP/project/project.sln", "$WORKSPACE_TMP/reports", args.FORMATTING_EXCLUDE)
										}
									}

									if (args.TEST_UNITY == '1') {
										stage("Test: ${args.TEST_MODES}") {
											dir('reports') {
												if (args.TEST_MODES == '') {
													unstable "Parameter TEST_MODES is missing."
												}
												callUnity "unity-tests '$WORKSPACE_TMP/project' ${args.TEST_MODES}", "tests.xml"

												junit(testResults: 'tests.xml', allowEmptyResults: true)
											}
										}
									}
								}
							}
						}
					}
				}
			}

			if (args.DEPLOYMENT_BRANCHES.contains(env.BRANCH_NAME)) {
				if (args.DEPLOY_TO_VERDACCIO == '1') {
					dir(pack) {
						def publishedVersion = callShellStdout "npm view --registry '${args.VERDACCIO_URL}' . version || echo '0'"
						def isPublished = publishedVersion == '0'
								? false
								: callShellStdout("npm show ${id}@${localVersion} --registry '${args.VERDACCIO_URL}' version || echo '-'") != '-'

						stage('Deploy to: Verdaccio') {
							if (!isPublished) {
								def doDeploy = true

								if (isRelease && args.DEPLOY_IF_RELEASE == '0') {
									echo "Deployment of standard releases is disabled via DEPLOY_IF_RELEASE, skipping deployment of version ${localVersion}."
									doDeploy = false
								}

								if (!isRelease && args.DEPLOY_IF_PRERELEASE == '0') {
									echo "Deployment of pre-releases is disabled via DEPLOY_IF_PRERELEASE, skipping deployment of version ${localVersion}."
									doDeploy = false
								}

								if (doDeploy) {
									if (args.DEPLOY_ON_FAILURE != '1' && currentBuild.currentResult != "SUCCESS") {
										error "Current result is '${currentBuild.currentResult}', aborting deployment of version ${localVersion}."
									}

									echo "Deploying update: ${publishedVersion} => ${localVersion}"
									try {
										if (args.VERDACCIO_CREDENTIALS != '') {
											def credentials = []
											credentials << string(credentialsId: args.VERDACCIO_CREDENTIALS, variable: 'NPM_TOKEN')
											withCredentials(credentials) {
												callShell "npm publish . --registry '${args.VERDACCIO_URL}'"
											}
										} else {
											callShell "npm publish . --registry '${args.VERDACCIO_URL}'"
										}
									} catch(e) {
										if (!fileExists(args.VERDACCIO_STORAGE)) {
											throw e
										}

										echo "Deployment via NPM failed, switching to manual mode..."

										dir(pack + "/..") {
											callShell "mv '${id}' package"
											callShell "tar -zcvf package.tgz package"
											callShell "mv package '${id}'"
											callShell "chmod 0777 package.tgz"

											def file = "${id}-${localVersion}.tgz"
											def integrity = callShellStdout("openssl dgst -sha512 -binary package.tgz | openssl base64 | tr -d '\n' | awk '{print \"sha512-\" \$0}'")
											def shasum = callShellStdout("sha1sum package.tgz | awk '{ print \$1 }'")
											def timestamp = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

											def packageData = readJSON(file: "${id}/package.json")
											packageData.readmeFilename = "README.md"
											packageData.description = ""
											packageData._id = "${id}@${localVersion}".toString()
											packageData._nodeVersion = callShellStdout("node -v").trim().replaceAll("^v", "")
											packageData._npmVersion = callShellStdout("npm -v").trim()
											packageData.dist = [
												integrity: integrity,
												shasum: shasum,
												tarball: "${args.VERDACCIO_URL}/${id}/-/${file}".toString()
											]

											callShell "mv package.tgz '${args.VERDACCIO_STORAGE}/${id}/${file}'"

											def storageData = readJSON(file: "${args.VERDACCIO_STORAGE}/${id}/package.json")
											storageData.versions[localVersion] = packageData
											storageData.time["modified"] = timestamp
											storageData.time[localVersion] = timestamp
											storageData["dist-tags"]["latest"] = localVersion
											storageData._attachments[file] = [
												shasum: shasum,
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
		}
	}
}
