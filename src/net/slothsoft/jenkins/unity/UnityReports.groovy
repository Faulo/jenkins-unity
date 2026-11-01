package net.slothsoft.jenkins.unity

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

class UnityReports {
    static void run(def steps, Map report, Closure body) {
        // Invalidate an earlier invocation's report, even if initialization fails.
        if (steps.fileExists(report.testResults)) {
            steps.writeFile(file: report.testResults, text: '', encoding: 'UTF-8')
        }

        Throwable failure = null
        boolean interrupted = false
        try {
            body()
        } catch (FlowInterruptedException e) {
            interrupted = true
            throw e
        } catch (Throwable e) {
            failure = e
            throw e
        } finally {
            if (!interrupted) {
                if (failure == null) {
                    steps.junit(report)
                } else {
                    try {
                        if (steps.fileExists(report.testResults)) {
                            steps.junit(report)
                        }
                    } catch (FlowInterruptedException e) {
                        throw e
                    } catch (Throwable e) {
                        steps.echo('Could not publish the Unity report; preserving the command failure.')
                    }
                }
            }
        }
    }
}
