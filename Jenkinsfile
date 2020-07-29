pipeline {
	options {
		timeout(time: 40, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr:'10'))
	}
	agent {
		label "centos-latest"
	}
	tools {
		maven 'apache-maven-latest'
		jdk 'openjdk-jdk11-latest'
	}
	stages {
		stage('initialize Gerrit review') {
			steps {
				gerritReview labels: [Verified: 0], message: "Build started $BUILD_URL"
			}
		}
		stage('Build') {
			steps {
				wrap([$class: 'Xvnc', useXauthority: true]) {
					sh 'mvn clean install -B -PpackAndSign -Dmaven.repo.local=$WORKSPACE/.m2/repository'
				}
			}
			post {
				always {
					archiveArtifacts artifacts: 'repository/target/repository/**/*,repository/target/*.zip,*/target/work/data/.metadata/.log'
					junit '*/target/surefire-reports/TEST-*.xml'
				}
				success {
					gerritReview labels: [Verified: 1], message: "Build Succcess $BUILD_URL"
					gerritCheck checks: ['lsp4e-bot@eclipse.org': 'SUCCESSFUL']
				}
				unstable {
					gerritReview labels: [Verified: -1], message: "Build UNSTABLE (test failures) $BUILD_URL"
					gerritCheck checks: ['lsp4e-bot@eclipse.org': 'FAILED']
				}
				failure {
					gerritReview labels: [Verified: -1], message: "Build FAILED $BUILD_URL"
					gerritCheck checks: ['lsp4e-bot@eclipse.org': 'FAILED']
				}
			}
		}
		stage('Deploy Snapshot') {
			when {
				branch 'master'
			}
			steps {
				sshagent ( ['projects-storage.eclipse.org-bot-ssh']) {
					sh '''
						DOWNLOAD_AREA=/home/data/httpd/download.eclipse.org/technology/lsp4e/snapshots/
						echo DOWNLOAD_AREA=$DOWNLOAD_AREA
						ssh genie.lsp4e@build.eclipse.org "\
							rm -rf  ${DOWNLOAD_AREA}/* && \
							mkdir -p ${DOWNLOAD_AREA}"
						scp -r repository/target/repository/* genie.lsp4e@build.eclipse.org:${DOWNLOAD_AREA}
					'''
				}
			}
		}
	}
}
