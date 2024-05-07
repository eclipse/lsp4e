pipeline {
	options {
		timeout(time: 15, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: '10'))
		disableConcurrentBuilds(abortPrevious: true)
	}
	agent {
		label 'centos-latest'
	}
	tools {
		maven 'apache-maven-latest'
		jdk 'temurin-jdk17-latest'
	}
	stages {
		stage('Build') {
			steps {
				wrap([$class: 'Xvnc', useXauthority: true]) {
					sh """#!/bin/bash

						# output some system settings to troubleshoot OOMs
						if [ -f "/proc/1/cgroup" ] && grep -q docker /proc/1/cgroup; then
							local container_id=$(cat /proc/self/cgroup | grep "docker" | sed 's/^.*\///' | tail -n1)
							if [ -z "$container_id" ]; then
								echo "Error: Could not find Docker container ID."
							else
								echo "Memory Limit: $(cat "/sys/fs/cgroup/memory/docker/$container_id/memory.limit_in_bytes")"
							fi
						elif [ -f "/proc/1/environ" ] && grep -q containerd /proc/1/environ; then
							local container_id=$(cat /proc/self/cgroup | grep "containerd" | grep "pod" | sed 's/^.*\///' | tail -n1)
							if [ -z "$container_id" ]; then
								echo "Error: Could not find containerd container ID."
							else
								echo "Memory Limit: $(cat "/sys/fs/cgroup/memory/containerd/$container_id/memory.limit_in_bytes")"
							fi
						else
							echo "Memory Limit: unknown - unsupported container runtime"
						fi
						set -eux
						# https://www.elastic.co/cn/blog/we-are-out-of-memory-systemd-process-limits
						ulimit -a
						cat /proc/sys/kernel/pid_max
						cat /proc/sys/kernel/threads-max
						java -XX:+PrintFlagsFinal -version | findstr HeapSize

						mvn clean verify \
							org.eclipse.dash:license-tool-plugin:license-check \
							-B ${env.BRANCH_NAME=='master' ? '-Psign': ''} \
							-Dmaven.test.failure.ignore=true \
							-Ddash.fail=false \
							-Dsurefire.rerunFailingTestsCount=3
					"""
				}
			}
			post {
				always {
					archiveArtifacts artifacts: 'repository/target/repository/**/*,repository/target/*.zip,*/target/work/data/.metadata/.log'
					junit '*/target/surefire-reports/TEST-*.xml'
				}
			}
		}
		stage('Deploy Snapshot') {
			when {
				branch 'master'
			}
			steps {
				sshagent (['projects-storage.eclipse.org-bot-ssh']) {
					sh '''
						DOWNLOAD_AREA=/home/data/httpd/download.eclipse.org/lsp4e/snapshots/
						echo DOWNLOAD_AREA=$DOWNLOAD_AREA
						ssh genie.lsp4e@projects-storage.eclipse.org "\
							rm -rf ${DOWNLOAD_AREA}/* && \
							mkdir -p ${DOWNLOAD_AREA}"
						scp -r repository/target/repository/* genie.lsp4e@projects-storage.eclipse.org:${DOWNLOAD_AREA}
					'''
				}
			}
		}
	}
}
