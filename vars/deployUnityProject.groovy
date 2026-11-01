import net.slothsoft.jenkins.unity.CiTools
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

    def credentials = [usernamePassword(credentialsId: options.steamCredentialsId, usernameVariable: 'STEAM_CREDENTIALS_USR', passwordVariable: 'STEAM_CREDENTIALS_PSW')]
    if (options.emailCredentialsId) {
        credentials << usernamePassword(credentialsId: options.emailCredentialsId, usernameVariable: 'EMAIL_CREDENTIALS_USR', passwordVariable: 'EMAIL_CREDENTIALS_PSW')
    }
    withCredentials(credentials) {
        def steamBranch = options.steamBranch ?: project.branch.replace('/', ' ').trim().replace(' ', '-')
        CiTools.steam(this, project.reportsDirectory, options.steamId, depots, steamBranch)
    }
}

private void deployToItch(UnityProjectContext project) {
    def options = project.options
    if (!options.itchCredentialsId || !options.itchId) {
        error 'ITCH_CREDENTIALS and ITCH_ID are required for itch.io deployment.'
    }

    withCredentials([string(credentialsId: options.itchCredentialsId, variable: 'BUTLER_API_KEY')]) {
        def uploads = []
        if (options.buildForWindows) {
            uploads << [source: "${options.buildName}-windows".toString(), target: "${options.itchId}:windows-x64".toString()]
        }
        if (options.buildForLinux) {
            uploads << [source: "${options.buildName}-linux".toString(), target: "${options.itchId}:linux-x64".toString()]
        }
        if (options.buildForMac) {
            uploads << [source: "${options.buildName}-mac".toString(), target: "${options.itchId}:mac-x64".toString()]
        }
        if (options.buildForWebGL) {
            uploads << [source: "${options.buildName}-webgl".toString(), target: "${options.itchId}:html".toString()]
        }
        if (options.buildForAndroid) {
            uploads << [source: "${options.buildName}-android.apk".toString(), target: "${options.itchId}:android".toString()]
        }
        CiTools.itch(this, project.reportsDirectory, uploads)
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
