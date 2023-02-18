def call(body) {
	node {
		checkout scm
		
		unityProject(body)
	}
}