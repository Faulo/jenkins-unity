def call(body) {
	properties([
		disableConcurrentBuilds(),
		disableResume()
	])

	node('unity') {
		checkout scm

		unityProject(body)
	}
}
