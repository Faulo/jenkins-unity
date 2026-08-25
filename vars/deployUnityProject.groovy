import com.cloudbees.groovy.cps.NonCPS
import net.slothsoft.jenkins.unity.UnityProjectContext
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(UnityProjectContext project) {
    requireProject(project)
    if (project.options.deployToSteam) {
        call(project, 'steam')
    }
    if (project.options.deployToItch) {
        call(project, 'itch')
    }
}

void call(UnityProjectContext project, String method) {
    requireProject(project)
    try {
        refuseFailedBuild(project)
        switch (method) {
            case 'steam':
                deployToSteam(project)
                break
            case 'itch':
                deployToItch(project)
                break
            default:
                throw new IllegalArgumentException("Unknown Unity project deployment method '${method}'")
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}

private void deployToSteam(UnityProjectContext project) {
    def options = project.options
    if (!options.steamCredentialsId || !options.steamId) {
        error 'STEAM_CREDENTIALS and STEAM_ID are required for Steam deployment.'
    }

    def depots = []
    if (options.buildForWindows && options.steamDepotWindows) {
        depots << "${options.steamDepotWindows}=${options.buildName}-windows"
    }
    if (options.buildForLinux && options.steamDepotLinux) {
        depots << "${options.steamDepotLinux}=${options.buildName}-linux"
    }
    if (options.buildForMac && options.steamDepotMac) {
        depots << "${options.steamDepotMac}=${options.buildName}-mac"
    }
    if (!depots) {
        error 'Steam deployment requires at least one enabled desktop build with a depot ID.'
    }

    withCredentials([usernamePassword(credentialsId: options.steamCredentialsId, usernameVariable: 'STEAM_CREDENTIALS_USR', passwordVariable: 'STEAM_CREDENTIALS_PSW')]) {
        withForwardedEnvironment(['STEAM_CREDENTIALS_USR', 'STEAM_CREDENTIALS_PSW']) {
            withUnityProjectEnvironment(options) {
                dir(project.reportsDirectory) {
                    def steamBranch = options.steamBranch ?: project.branch.replace('/', ' ').trim().replace(' ', '-')
                    callUnity 'steam-login'
                    callUnity "steam-buildfile '${project.reportsDirectory}' '${project.reportsDirectory}' ${options.steamId} ${depots.join(' ')} ${steamBranch}", 'deploy-steam.vdf'
                    callShell "steamcmd +login \"\$STEAM_CREDENTIALS_USR\" \"\$STEAM_CREDENTIALS_PSW\" +run_app_build 'deploy-steam.vdf' +quit"
                }
            }
        }
    }
}

private void deployToItch(UnityProjectContext project) {
    def options = project.options
    if (!options.itchCredentialsId || !options.itchId) {
        error 'ITCH_CREDENTIALS and ITCH_ID are required for itch.io deployment.'
    }

    withCredentials([string(credentialsId: options.itchCredentialsId, variable: 'BUTLER_API_KEY')]) {
        withForwardedEnvironment(['BUTLER_API_KEY']) {
            withUnityProjectEnvironment(options) {
                dir(project.reportsDirectory) {
                    if (options.buildForWindows) {
                        pushToItch("${options.buildName}-windows", options.itchId, 'windows-x64')
                    }
                    if (options.buildForLinux) {
                        pushToItch("${options.buildName}-linux", options.itchId, 'linux-x64')
                    }
                    if (options.buildForMac) {
                        pushToItch("${options.buildName}-mac", options.itchId, 'mac-x64')
                    }
                    if (options.buildForWebGL) {
                        pushToItch("${options.buildName}-webgl", options.itchId, 'html')
                    }
                    if (options.buildForAndroid) {
                        pushToItch("${options.buildName}-android.apk", options.itchId, 'android')
                    }
                }
            }
        }
    }
}

private void pushToItch(String source, String itchId, String channel) {
    callShell "butler push --if-changed ${shellQuote(source)} ${shellQuote("${itchId}:${channel}")}"
}

private void withForwardedEnvironment(List<String> names, Closure body) {
    def existingEnvironment = (env.JENKINS_UNITY_ENV ?: '').tokenize(':')
    withEnv(["JENKINS_UNITY_ENV=${(existingEnvironment + names).findAll { it }.unique().join(':')}"]) {
        body()
    }
}

private void refuseFailedBuild(UnityProjectContext project) {
    if (!project.options.deployOnFailure && currentBuild.currentResult != 'SUCCESS') {
        error "Current result is '${currentBuild.currentResult}', refusing deployment."
    }
}

private void requireProject(UnityProjectContext project) {
    if (!project) {
        throw new IllegalArgumentException('project must not be null')
    }
}

@NonCPS
private String shellQuote(String value) {
    "'${value.replace("'", "'\"'\"'")}'"
}
