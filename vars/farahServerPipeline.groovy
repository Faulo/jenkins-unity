def call(body) {
    // evaluate the body block, and collect configuration into the object
    def args= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = args
    body()
    
    pipeline {
        agent any
        
        options {
            disableConcurrentBuilds()
            disableResume()
        }
        
        
        environment {
            PHP = "${env.PHP_ROOT}${args.PHP_VERSION}\\php.exe"
            VHOST = "${env.VHOST_ROOT}\\${args.VHOST_PATH}"
        }
        
        stages {
            stage('Install dependencies') { 
                steps {
                    bat "$PHP composer.phar update --no-interaction --no-dev"
                }
            }
            stage('Run PHPUnit') { 
                steps {
                    bat "$PHP vendor/phpunit/phpunit/phpunit --log-junit phpunit.results.xml"
                    junit 'phpunit.results.xml'
                }
            }
            stage('Deploy to vhost') {
                when {
                    branch 'main'
                }
                steps {
                    dir("$VHOST") {
                        checkout scm
                        bat "git checkout --force origin/$BRANCH_NAME"
                        bat "$PHP composer.phar update --no-interaction --no-dev"
                    }
                }
            }
        }
    }
}