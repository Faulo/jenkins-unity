def call(body) {
	assert env.BRANCH_NAME != null

	def args = [
		// Define Unity package location relative to repository.
		LOCATION : '',

		// specify Jenkins node to process calls to Unity
		UNITY_NODE : 'unity',

		// If given, use this package information instead of reading from the package's package.json.
		VERSION : '',
		ID : '',

		// Assert that CHANGELOG.md has been updated.
		TEST_CHANGELOG : '1',
		CHANGELOG_LOCATION : 'CHANGELOG.md',

		// Assert that the C# code of the package matches the .editorconfig.
		TEST_FORMATTING : '0',
		EDITORCONFIG_LOCATION : '.editorconfig',
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

							dir('reports') {
								stage("Build: Empty project with package") {
									callUnity "unity-package-install '$WORKSPACE_TMP/package' '$WORKSPACE_TMP/project'", "package-install.xml"
									junit(testResults: 'package-install.xml')
								}

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

											callShell "dotnet tool restore"
											callShell "dotnet tool run docfx"

											publishHTML([
												allowMissing: false,
												alwaysLinkToLastBuild: false,
												keepAll: false,
												reportDir: 'html',
												reportFiles: 'index.html',
												reportName: id,
												reportTitles: '',
												useWrapperFileDirectly: true
											])
										}
									}
								}
							}

							if (args.TEST_FORMATTING == '1') {
								stage("Test: ${args.EDITORCONFIG_LOCATION}") {
									dir('reports') {
										writeFile(file: "$WORKSPACE_TMP/project/.editorconfig", text: editorconfigContent)

										def exclude = args.FORMATTING_EXCLUDE == '' ? '' : " --exclude ${args.FORMATTING_EXCLUDE}"
										def jsonFile = "$WORKSPACE_TMP/reports/format-report.json";
										def xmlFile = "$WORKSPACE_TMP/reports/format-report.xml";

										callShellStatus "dotnet format '$WORKSPACE_TMP/project/project.sln' --verify-no-changes --verbosity normal --report '$WORKSPACE_TMP/reports' ${exclude}"
										if (!fileExists(jsonFile)) {
											error "dotnet format failed to create '${jsonFile}'."
										}

										callUnity "transform-dotnet-format '${jsonFile}'", xmlFile;
										if (!fileExists(xmlFile)) {
											error "transform-dotnet-format failed to create '${xmlFile}'."
										}

										junit(testResults: 'format-report.xml', allowEmptyResults: true)
									}
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
		} catch(org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
			currentBuild.result = e.result
		} catch(e) {
			currentBuild.result = "UNKNOWN"
		} finally {
			if (reportAny) {
				def header = "${currentBuild.currentResult}: ${id} v${localVersion}";
				def footer = ""
				for (changeLogSet in currentBuild.changeSets) {
					for (entry in changeLogSet.getItems()) {
						footer += "- ${entry.msg}\r\n"
					}
				}

				if (args.REPORT_TO_DISCORD == '1') {
					discordSend description: header, footer: footer, link: env.BUILD_URL, result: currentBuild.currentResult, title: JOB_NAME, webhookURL: args.DISCORD_WEBHOOK
				}

				if (args.REPORT_TO_OFFICE_365 == '1') {
					office365ConnectorSend webhookUrl: args.OFFICE_365_WEBHOOK, message: footer, status: header
				}

				if (args.REPORT_TO_ADAPTIVE_CARDS == '1') {
					if (arg.ADAPTIVE_CARDS_PING_IF == '' || currentBuild.resultIsWorseOrEqualTo(arg.ADAPTIVE_CARDS_PING_IF)) {
						sendAdaptiveCard(args.ADAPTIVE_CARDS_WEBHOOK, currentBuild)
					}
				}
			}
		}
	}
}
