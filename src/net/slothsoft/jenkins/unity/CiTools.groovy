package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS
import java.security.MessageDigest

class CiTools {
    static void inside(def steps, boolean steamState, Closure body) {
        def arguments = ''
        if (steamState) {
            def volume = steamVolume(steps.pwd())
            arguments = "-v ${volume}:${steps.isUnix() ? '/root/Steam' : 'C:/steam'}".toString()
        }
        steps.docker.image('faulo/ci-tools').inside(arguments, body)
    }

    static void steam(def steps, String reportsDirectory, String appId, List<String> depots, String branch) {
        steps.dir(reportsDirectory) {
            inside(steps, true) {
                command(steps, 'steam-login')
                def windows = !steps.isUnix()
                def arguments = [reportsDirectory, reportsDirectory, appId] + depots + [branch]
                def buildfile = command(steps, "steam-buildfile ${arguments.collect { quote(it, windows) }.join(' ')}".toString(), true)
                steps.writeFile(file: 'deploy-steam.vdf', text: buildfile, encoding: 'UTF-8')
                def credentials = steps.isUnix() ? '"$STEAM_CREDENTIALS_USR" "$STEAM_CREDENTIALS_PSW"' : '$env:STEAM_CREDENTIALS_USR $env:STEAM_CREDENTIALS_PSW'
                command(steps, "steamcmd +login ${credentials} +run_app_build 'deploy-steam.vdf' +quit".toString())
            }
        }
    }

    static void itch(def steps, String reportsDirectory, List<Map> uploads) {
        steps.dir(reportsDirectory) {
            inside(steps, false) {
                def windows = !steps.isUnix()
                for (int i = 0; i < uploads.size(); i++) {
                    def upload = uploads[i]
                    command(steps, "butler push --if-changed ${quote(upload.source, windows)} ${quote(upload.target, windows)}".toString())
                }
            }
        }
    }

    static String command(def steps, String script, boolean capture = false) {
        // Native shell steps honor docker.image.inside even within an outer withUnity scope.
        if (steps.isUnix()) {
            return steps.sh(script: "set +x\n${script}".toString(), returnStdout: capture)
        }
        return steps.powershell(script: "& ${script}\nif (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }".toString(), returnStdout: capture, encoding: 'UTF-8')
    }

    @NonCPS
    static String quote(String value, boolean windows) {
        windows ? "'${value.replace("'", "''")}'" : "'${value.replace("'", "'\"'\"'")}'"
    }

    @NonCPS
    static String steamVolume(String directory) {
        'jenkins-unity-steam-' + MessageDigest.getInstance('SHA-256').digest(directory.getBytes('UTF-8')).encodeHex().toString().take(24)
    }
}
