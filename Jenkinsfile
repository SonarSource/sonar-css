@Library('SonarSource@2.1.1') _

pipeline {
    agent none
    parameters {
        string(name: 'GIT_SHA1', description: 'Git SHA1 (provided by travisci hook job)')
        string(name: 'CI_BUILD_NAME', defaultValue: 'sonar-css', description: 'Build Name (provided by travisci hook job)')
        string(name: 'CI_BUILD_NUMBER', description: 'Build Number (provided by travisci hook job)')
        string(name: 'GITHUB_BRANCH', defaultValue: 'master', description: 'Git branch (provided by travisci hook job)')
        string(name: 'GITHUB_REPOSITORY_OWNER', defaultValue: 'SonarSource', description: 'Github repository owner(provided by travisci hook job)')
    }
    environment {
        SONARSOURCE_QA = 'true'
        MAVEN_TOOL = 'Maven 3.3.x'
    }
    stages {
        stage('Notify') {
            steps {
                sendAllNotificationQaStarted()
            }
        }
        stage('QA') {
            parallel {
                stage('ITs-lts') {
                    agent {
                        label 'linux'
                    }
                    steps {
                        runITs "LATEST_RELEASE[6.7]"
                    }
                }

                stage('ITs-latest') {
                    agent {
                        label 'linux'
                    }
                    steps {
                        runITs "LATEST_RELEASE"
                    }
                }
                stage('ITs-windows') {
                    agent {
                        label 'windows'
                    }
                    steps {
                        runITs "LATEST_RELEASE"
                    }
                }
                stage('ITs-dev') {
                    agent {
                        label 'linux'
                    }
                    steps {
                        runITs "DEV"
                    }
                }

                stage('CI-windows') {
                    agent {
                        label 'windows'
                    }
                    steps {
                        withMaven(maven: MAVEN_TOOL) {
                            sh 'mvn.cmd clean test'
                        }
                    }
                }
            }
            post {
                always {
                    sendAllNotificationQaResult()
                }
            }
        }
        stage('Promote') {
            steps {
                repoxPromoteBuild()
            }
            post {
                always {
                    sendAllNotificationPromote()
                }
            }
        }
    }
}

def runITs(String sqRuntimeVersion) {
    withQAEnv {
        withMaven(maven: MAVEN_TOOL) {
            mavenSetBuildVersion()
            dir('its') {
                nodejs('NodeJS 10.4.1') {
                    def mvn = isUnix() ? 'mvn' : 'mvn.cmd'
                    sh "${mvn} ${itBuildArguments sqRuntimeVersion}"
                }
            }
        }
    }
}

def withQAEnv(def body) {
    checkout scm
    withCredentials([string(credentialsId: 'ARTIFACTORY_PRIVATE_API_KEY', variable: 'ARTIFACTORY_PRIVATE_API_KEY'),
                     usernamePassword(credentialsId: 'ARTIFACTORY_PRIVATE_USER', passwordVariable: 'ARTIFACTORY_PRIVATE_PASSWORD', usernameVariable: 'ARTIFACTORY_PRIVATE_USERNAME')]) {
        wrap([$class: 'Xvfb']) {
            body.call()
        }
    }
}

String itBuildArguments(String sqRuntimeVersion) {
    "-Pits -Dsonar.runtimeVersion=${sqRuntimeVersion} -Dorchestrator.artifactory.apiKey=${env.ARTIFACTORY_PRIVATE_API_KEY} " +
         "-Dorchestrator.configUrl=https://repox.jfrog.io/repox/orchestrator.properties/orch-h2.properties -Dmaven.test.redirectTestOutputToFile=false clean verify -e -V"
}
