def call(body) {
	properties([
		disableConcurrentBuilds(abortPrevious: true),
		disableResume()
	])

	node('unity') {
		checkout scm

		unityProject(body)
	}
}