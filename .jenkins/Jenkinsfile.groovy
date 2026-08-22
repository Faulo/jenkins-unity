unityPackagePipeline {
    PACKAGE_LOCATION = '.jenkins/fixtures/unity-package'
    PACKAGE_BRANCH = 'main'
    VALIDATE_CHANGELOG = true
    CHECK_FORMATTING = true
    EDITORCONFIG_FILE = '.jenkins/fixtures/unity-package/.editorconfig'
    FORMATTING_FILES = []
    FORMATTING_EXCLUDE = []
    RUN_UNITY_TESTS = true
    UNITY_TEST_MODES = ['EditMode']
    BUILD_DOCUMENTATION = false
    PUBLISH_TO_VERDACCIO = false

    PREPARE_AGENT = 'linux && compose-unity'
    PREPARE_DOCKER_IMAGE = 'node:22-bookworm-slim'
    PUBLISH_AGENT = 'linux && compose-unity'
    PUBLISH_DOCKER_IMAGE = 'node:22-bookworm-slim'
    UNITY_AGENTS = [
        linux: 'linux && compose-unity',
        windows: 'windows && compose-unity',
    ]
}
