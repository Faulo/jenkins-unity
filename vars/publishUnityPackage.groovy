import com.cloudbees.groovy.cps.NonCPS
import net.slothsoft.jenkins.unity.PreparedUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(PreparedUnityPackage preparedPackage) {
    if (!preparedPackage) {
        throw new IllegalArgumentException('preparedPackage must not be null')
    }

    def invocationId = UUID.randomUUID().toString()
    def temporaryRoot = pwd(tmp: true)
    def workDirectory = "${temporaryRoot}/unity-package-publish-${preparedPackage.executionId}-${invocationId}"
    def options = preparedPackage.options
    def context = preparedPackage.context

    try {
        dir(workDirectory) {
            deleteDir()
            dir('package') {
                unstash preparedPackage.packageStash
            }

            if (!options.publishToVerdaccio) {
                echo 'Verdaccio publication is disabled.'
                return
            }
            if (!options.publishBranches.contains(context.branch)) {
                echo "Branch '${context.branch}' is not configured for publication."
                return
            }
            if (context.release && !options.publishReleases) {
                echo "Publication of release ${context.version} is disabled."
                return
            }
            if (!context.release && !options.publishPrereleases) {
                echo "Publication of prerelease ${context.version} is disabled."
                return
            }
            if (!options.publishOnFailure && currentBuild.currentResult != 'SUCCESS') {
                error "Current result is '${currentBuild.currentResult}', refusing to publish ${context.packageId}@${context.version}."
            }

            dir('package') {
                def packageSpec = shellQuote("${context.packageId}@${context.version}")
                def registry = shellQuote(options.verdaccioUrl)
                if (execStatus("npm view ${packageSpec} version --registry ${registry}") == 0) {
                    echo "${context.packageId}@${context.version} is already published."
                    return
                }

                int publishStatus
                if (options.verdaccioCredentialsId) {
                    withCredentials([string(credentialsId: options.verdaccioCredentialsId, variable: 'NPM_TOKEN')]) {
                        exec "npm config set --location project ${shellQuote("//${options.verdaccioHost}/:_authToken")} \"\$NPM_TOKEN\""
                        publishStatus = execStatus("npm publish . --registry ${registry}")
                    }
                } else {
                    publishStatus = execStatus("npm publish . --registry ${registry}")
                }

                if (publishStatus != 0) {
                    publishDirectly(preparedPackage, workDirectory)
                }
            }
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    } finally {
        dir(workDirectory) {
            deleteDir()
        }
    }
}

private void publishDirectly(PreparedUnityPackage preparedPackage, String workDirectory) {
    def options = preparedPackage.options
    def context = preparedPackage.context
    if (!options.verdaccioStorage || !fileExists("${options.verdaccioStorage}/${context.packageId}/package.json")) {
        error "NPM publication failed and direct Verdaccio storage is not configured for ${context.packageId}."
    }

    echo 'Deployment via NPM failed, switching to direct Verdaccio storage.'
    def packOutput = execStdout("npm pack --json --pack-destination ${shellQuote(workDirectory)}")
    def packData = readJSON(text: packOutput)
    if (!(packData instanceof List) || packData.size() != 1 || !packData[0].filename) {
        error 'npm pack did not return one package archive.'
    }

    def archive = packData[0]
    def archiveName = archive.filename.toString()
    def packageData = readJSON(file: 'package.json')
    packageData.readmeFilename = 'README.md'
    packageData._id = "${context.packageId}@${context.version}".toString()
    packageData._nodeVersion = execStdout('node --version').trim().replaceFirst('^v', '')
    packageData._npmVersion = execStdout('npm --version').trim()
    packageData.dist = [
        integrity: archive.integrity.toString(),
        shasum: archive.shasum.toString(),
        tarball: "${options.verdaccioUrl}/${context.packageId}/-/${archiveName}".toString(),
    ]

    def storageDirectory = "${options.verdaccioStorage}/${context.packageId}"
    def storageFile = "${storageDirectory}/package.json"
    def storageData = readJSON(file: storageFile)
    storageData.versions[context.version] = packageData
    def timestamp = java.time.Instant.now().toString()
    storageData.time.modified = timestamp
    storageData.time[context.version] = timestamp
    storageData['dist-tags'].latest = context.version
    storageData._attachments[archiveName] = [
        shasum: archive.shasum.toString(),
        version: context.version,
    ]

    exec "mv ${shellQuote("${workDirectory}/${archiveName}")} ${shellQuote("${storageDirectory}/${archiveName}")}"
    writeJSON(file: storageFile, json: storageData, pretty: 2)
}

@NonCPS
private String shellQuote(String value) {
    "'${value.replace("'", "'\"'\"'")}'"
}
