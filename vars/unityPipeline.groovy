def call(body) {
	properties([
		disableConcurrentBuilds(abortPrevious: true),
		disableResume()
	])

	node {
		checkout scm

		unityProject(body)
	}
}