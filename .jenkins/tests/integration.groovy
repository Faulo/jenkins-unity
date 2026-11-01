import net.slothsoft.jenkins.unity.UnityProjectContext
import net.slothsoft.jenkins.unity.CiTools
import net.slothsoft.jenkins.unity.UnityProjectOptions

import net.slothsoft.jenkins.unity.UnityReports
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def assertValue(actual, expected, description) {
    if (actual != expected) {
        error "${description}: expected '${expected}', got '${actual}'"
    }
}

def verifyUnityOutputReports() {
    def windows = env.PIPELINE_DOCKER_CONTAINER_OS ? env.PIPELINE_DOCKER_CONTAINER_OS == 'windows' : isWindows()
    dir("${pwd(tmp: true)}/unity-output-reports") {
        deleteDir()
        try {
            def xml = "<testsuite name='output-files-${UUID.randomUUID()}' tests='1' failures='0'><testcase name='retained-report'/></testsuite>".toString()
            writeFile(file: 'launcher.sh', text: '''#!/bin/sh
printf '  \n%s\n\n' "REPORT_XML"
printf 'diagnostic\n' >&2
while [ ! -f release ]; do sleep 1; done
case " $* " in *" fail "*) exit 7;; esac
'''.replace('\r\n', '\n').replace('REPORT_XML', xml))
            writeFile(file: 'launcher.ps1', text: '''[Console]::Out.Write("  `n" + "REPORT_XML" + "`n`n")
[Console]::Error.WriteLine('diagnostic')
while (!(Test-Path -LiteralPath 'release')) { Start-Sleep -Seconds 1 }
if ($args -contains 'fail') { exit 7 }
'''.replace('\r\n', '\n').replace('REPORT_XML', xml))
            def launcher = windows ? "pwsh -NoProfile -File '${pwd()}/launcher.ps1'" : "sh '${pwd()}/launcher.sh'"
            // Exercise the launcher through the native callUnity API.
            withEnv(["UNITY_EXECUTABLE=${launcher}"]) {
                timeout(time: 1, unit: 'MINUTES') {
                    parallel(
                        command: {
                            def result = callUnity('tests success', 'nested/live.xml')
                            assertValue(result, xml, 'callUnity must still return trimmed stdout')
                        },
                        observer: {
                            waitUntil {
                                fileExists('nested/live.xml') && readFile('nested/live.xml').replace('\r\n', '\n') == "  \n${xml}\n\n".toString()
                            }
                            assertValue(readFile('nested/live.xml').replace('\r\n', '\n'), "  \n${xml}\n\n".toString(), 'Report must preserve whitespace before command completion')
                            writeFile(file: 'release', text: 'continue')
                        },
                        failFast: true
                    )
                }
                boolean failed = false
                try {
                    UnityReports.run(this, [testResults: 'nested/failed.xml']) {
                        callUnity('tests fail', 'nested/failed.xml')
                    }
                } catch (FlowInterruptedException e) {
                    throw e
                } catch (Throwable expected) {
                    assertValue(expected.message.contains('7'), true, 'Expected the command exit-code failure')
                    failed = true
                }
                assertValue(failed, true, 'Nonzero exit must still fail after report publication')
                assertValue(readFile('nested/failed.xml').replace('\r\n', '\n'), "  \n${xml}\n\n".toString(), 'Nonzero exit must retain stdout without stderr')
            }
        } finally {
            deleteDir()
        }
    }
}


def call(Map config, String platform) {
    def environment = ["UNITY_EXECUTABLE=${config.unityExecutable}", "COMPOSER_EXECUTABLE=${config.composerExecutable}"]
    if (config.unityContainer?.trim()) {
        environment << "JENKINS_UNITY_CONTAINER=${config.unityContainer.trim()}"
    }
    if (config.unityContainerLabel?.trim()) {
        environment << "JENKINS_UNITY_CONTAINER_LABEL=${config.unityContainerLabel.trim()}"
    }
    withEnv(environment) {
        stage('Execution and output reports') {
            assertValue(isWindows(), platform == 'windows', 'Allocated agent OS')
            def workspace = pwd()
            nodeIfCurrentDoesNotMatch(env.NODE_NAME) {
                assertValue(pwd(), workspace, 'Matching-node reuse must retain the workspace')
            }
            dir("${pwd(tmp: true)}/pipeline-environment") {
                try {
                    writeFile(file: 'test.env', text: 'JENKINS_UNITY_TEST_ENV=0.9.0')
                    withEnvFile('test.env') {
                        assertValue(env.JENKINS_UNITY_TEST_ENV, '0.9.0', 'Environment-file application')
                    }
                } finally {
                    deleteDir()
                }
            }
            verifyUnityOutputReports()
            withUnity {
                verifyUnityOutputReports()
                dir("${pwd(tmp: true)}/unity-help-report") {
                    try {
                        def help = callUnity('--help', 'help.txt')
                        assertValue(readFile('help.txt').trim(), help, 'callUnity report forwarding')
                    } finally {
                        deleteDir()
                    }
                }
            }
        }
        stage('CI tools deployment environment') {
            withUnity { verifyCiTools() }
        }
        credentialFiles()
        packagePhases(config)
        projectPhases(config)
    }
}

def verifyCiTools() {
    def windows = !isUnix()
    dir("${pwd(tmp: true)}/ci-tools fixture") {
        deleteDir()
        try {
            writeFile(file: 'payload/check.txt', text: 'shared payload')
            withEnv(['BUTLER_API_KEY=ci-tools-fixture']) {
                CiTools.inside(this, false) {
                    CiTools.command(this, 'butler --version')
                    CiTools.command(this, windows ? 'Get-Command steamcmd -ErrorAction Stop' : 'command -v steamcmd')
                    assertValue(CiTools.command(this, 'steam-login unexpected-argument', true).contains('Sign in to Steam'), true, 'Steam login helper is installed')
                    def check = windows ? '''{ if ($env:BUTLER_API_KEY -ne 'ci-tools-fixture') { throw 'Missing token environment' }; if ((Get-Content -Raw 'payload/check.txt') -ne 'shared payload') { throw 'Missing shared payload' } }''' : '''test "$BUTLER_API_KEY" = ci-tools-fixture && test "$(cat payload/check.txt)" = 'shared payload' '''
                    CiTools.command(this, check)
                    boolean failed = false
                    try {
                        CiTools.command(this, windows ? "pwsh -NoProfile -Command 'exit 7'" : "sh -c 'exit 7'")
                    } catch (FlowInterruptedException e) {
                        throw e
                    } catch (Throwable expected) {
                        assertValue(expected.message.contains('7'), true, 'CI-tools shell preserves exit status')
                        failed = true
                    }
                    assertValue(failed, true, 'A failed deployment command must fail its Pipeline step')
                    def path = CiTools.quote(pwd().replace('\\', '/'), windows)
                    def vdf = CiTools.command(this, "steam-buildfile ${path} ${path} '1234' '11=payload' 'preview'".toString(), true)
                    assertValue(vdf.contains('1234') && vdf.contains('preview'), true, 'Steam buildfile generation')
                    writeFile(file: 'generated.vdf', text: vdf, encoding: 'UTF-8')
                }
            }
            assertValue(readFile('generated.vdf').contains('1234'), true, 'Container output remains visible to Jenkins')
            def marker = "jenkins-unity-fixture-${UUID.randomUUID()}".toString()
            def path = windows ? "C:/steam/${marker}".toString() : "/root/Steam/${marker}".toString()
            def quoted = CiTools.quote(path, windows)
            try {
                CiTools.inside(this, true) {
                    CiTools.command(this, windows ? "{ [IO.File]::WriteAllText(${quoted}, 'retained') }".toString() : "printf retained > ${quoted}".toString())
                }
                CiTools.inside(this, true) {
                    def retained = CiTools.command(this, windows ? "{ [IO.File]::ReadAllText(${quoted}) }".toString() : "cat ${quoted}".toString(), true)
                    assertValue(retained.trim(), 'retained', 'Steam state survives container recreation')
                }
            } finally {
                CiTools.inside(this, true) {
                    CiTools.command(this, windows ? "Remove-Item -LiteralPath ${quoted} -Force -ErrorAction SilentlyContinue".toString() : "rm -f ${quoted}".toString())
                }
            }
        } finally {
            deleteDir()
        }
    }
}

def credentialFiles() {
    stage('Runtime credential files') {
        dir("${env.WORKSPACE_TMP}/jenkins-unity-credential-files") {
            deleteDir()
            try {
                writeFile(file: 'unity-user.txt', text: 'integration-user')
                writeFile(file: 'unity-password.txt', text: 'integration-password')
                writeFile(file: 'empty.txt', text: '')

                def credentialRoot = pwd()
                def unsetDirectCredentials = isWindows()
                    ? 'Remove-Item Env:UNITY_CREDENTIALS_USR, Env:UNITY_CREDENTIALS_PSW -ErrorAction SilentlyContinue; '
                    : 'unset UNITY_CREDENTIALS_USR UNITY_CREDENTIALS_PSW; '
                def unsetPasswordFile = isWindows()
                    ? 'Remove-Item Env:UNITY_CREDENTIALS_PSW_FILE -ErrorAction SilentlyContinue; '
                    : 'unset UNITY_CREDENTIALS_PSW_FILE; '

                withEnv([
                    'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                    "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/unity-user.txt",
                    "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
                ]) {
                    withUnity {
                        def status = callShellStatus "${unsetDirectCredentials}${env.UNITY_EXECUTABLE} --help"
                        assertValue(status == 0, true, 'The Unity launcher is expected to accept a complete credential file pair')
                    }
                }

                withEnv([
                    'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR:UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                    'UNITY_CREDENTIALS_USR=integration-user',
                    "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/unity-user.txt",
                    "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
                ]) {
                    withUnity {
                        def status = callShellStatus "${env.UNITY_EXECUTABLE} --help"
                        assertValue(status == 0, false, 'The Unity launcher is expected to reject direct and file-backed forms together')
                    }
                }

                withEnv([
                    'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                    "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/missing.txt",
                    "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
                ]) {
                    withUnity {
                        def status = callShellStatus "${unsetDirectCredentials}${env.UNITY_EXECUTABLE} --help"
                        assertValue(status == 0, false, 'The Unity launcher is expected to reject a missing credential file')
                    }
                }

                withEnv([
                    'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                    "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/empty.txt",
                    "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
                ]) {
                    withUnity {
                        def status = callShellStatus "${unsetDirectCredentials}${env.UNITY_EXECUTABLE} --help"
                        assertValue(status == 0, false, 'The Unity launcher is expected to reject an empty credential file')
                    }
                }

                withEnv([
                    'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE',
                    "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/unity-user.txt"
                ]) {
                    withUnity {
                        def status = callShellStatus "${unsetDirectCredentials}${unsetPasswordFile}${env.UNITY_EXECUTABLE} --help"
                        assertValue(status == 0, true, 'The native launcher accepts independently supplied credential values')
                    }
                }

            } finally {
                deleteDir()
            }
        }
    }
}

def packagePhases(Map config) {
    def prepared
    def installed
    stage('Prepare package fixture') {
        prepared = prepareUnityPackage([
            PACKAGE_LOCATION: config.packageFixture,
            PACKAGE_BRANCH: 'main',
            UNITY_MANIFEST_LOCATION: '.jenkins/manifest.json',
            UNITY_CREDENTIALS: config.unityCredentials,
            EMAIL_CREDENTIALS: config.emailCredentials,
            TEST_CHANGELOG: true,
            TEST_FORMATTING: true,
            FORMATTING_LOCATION: "${config.packageFixture}/.editorconfig".toString(),
            TEST_UNITY: true,
            UNITY_TEST_MODES: ['EditMode'],
            BUILD_DOCUMENTATION: true,
        ])
    }
    try {
        stage('Install package fixture') {
            installed = installUnityPackage(prepared)
        }
        stage('Package changelog') { testUnityPackage(installed) }
        stage('Package solution') { buildUnityProject(installed, 'solution') }
        stage('Package formatting') { testUnityProject(installed, 'formatting') }
        stage('Package documentation') { buildUnityProject(installed, 'documentation') }
        stage('Package Unity tests') {
            testUnityProject(installed, 'unity')
            assertFixtureTest(installed.reportsDirectory)
        }
    } finally {
        if (installed != null) {
            deleteUnityProject(installed)
        }
    }
}

def projectPhases(Map config) {
    def workspace = pwd().replace('\\', '/')
    def options = UnityProjectOptions.fromMap([
        PROJECT_LOCATION: config.projectFixture,
        PROJECT_BRANCH: 'main',
        UNITY_CREDENTIALS: config.unityCredentials,
        EMAIL_CREDENTIALS: config.emailCredentials,
        TEST_FORMATTING: true,
        FORMATTING_LOCATION: "${config.projectFixture}/.editorconfig".toString(),
        TEST_UNITY: true,
        UNITY_TEST_MODES: ['EditMode'],
        BUILD_DOCUMENTATION: true,
    ])
    def project = new UnityProjectContext(options, 'Jenkins Unity Project Integration', '1.0.0', 'main',
        workspace, "${workspace}/${config.projectFixture}".toString(), "${pwd(tmp: true)}/project-reports".replace('\\', '/'))
    try {
        stage('Project solution') { buildUnityProject(project, 'solution') }
        stage('Project formatting') { testUnityProject(project, 'formatting') }
        stage('Project documentation') { buildUnityProject(project, 'documentation') }
        stage('Project Unity tests') {
            testUnityProject(project, 'unity')
            assertFixtureTest(project.reportsDirectory)
        }
    } finally {
        dir(project.reportsDirectory) { deleteDir() }
    }
}

def assertFixtureTest(String reportsDirectory) {
    dir(reportsDirectory) {
        if (!readFile('tests.xml').contains('name="ReturnsExpectedValue"')) {
            error 'Unity did not execute the expected integration fixture test.'
        }
    }
}

return this
