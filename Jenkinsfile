// OpenCRM CPQ — Jenkins CI (SSOT: scripts/ci/run-core.sh, mirrors .github/workflows/ci.yml)
// Design: docs/adr/ADR-003-ci-llm-integration.md | Infra: infra/jenkins/README.md

def runAiStage(String scriptName, String extraArgs = '') {
  if (env.AI_CI_ENABLED != 'true') {
    return
  }
  def blocking = env.AI_CI_BLOCKING == 'true'
  def run = {
    def status = sh(
      script: "bash infra/jenkins/ai-assist/run-ai-stage.sh ${scriptName} ${extraArgs}",
      returnStatus: true
    )
    if (status != 0) {
      if (blocking) {
        error("AI stage ${scriptName} failed (AI_CI_BLOCKING=true)")
      } else {
        unstable("AI stage ${scriptName} failed (advisory)")
      }
    }
  }
  withCredentials([string(credentialsId: 'ai-api-key', variable: 'LLM_API_KEY')]) {
    run()
  }
}

pipeline {
  // Dev: omit JENKINS_AGENT_LABEL (defaults to any); JENKINS_NUM_EXECUTORS=2 on controller (compose/JCasC).
  // Prod: set JENKINS_AGENT_LABEL (e.g. linux docker maven node22) and JENKINS_NUM_EXECUTORS=0 on controller.
  agent { label "${env.JENKINS_AGENT_LABEL ?: 'any'}" }

  options {
    timestamps()
    ansiColor('xterm')
    buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '10'))
    timeout(time: 90, unit: 'MINUTES')
  }

  environment {
    SPRING_PROFILES_ACTIVE = 'test'
    AI_CI_ENABLED = "${env.AI_CI_ENABLED ?: 'false'}"
    AI_CI_BLOCKING = "${env.AI_CI_BLOCKING ?: 'false'}"
    GIT_BASE_REF = "${env.CHANGE_TARGET ? "origin/${env.CHANGE_TARGET}" : 'origin/main'}"
    DOCKER_HOST = 'unix:///var/run/docker.sock'
    TESTCONTAINERS_RYUK_DISABLED = 'false'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        sh 'git fetch origin +refs/heads/*:refs/remotes/origin/* || true'
      }
    }

    stage('Toolchain') {
      steps {
        sh 'bash scripts/ci/run-core.sh toolchain'
      }
    }

    stage('Shared Libraries') {
      steps {
        sh 'bash scripts/ci/run-core.sh shared-libraries'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'backend/libs/**/target/surefire-reports/*.xml'
        }
      }
    }

    // GHA parity: backend-unit runs in parallel with cpq_beta; integration needs only shared-libs + unit
    stage('Parallel — Fast checks') {
      parallel {
        stage('Backend Unit') {
          steps {
            sh 'bash scripts/ci/run-core.sh backend-unit'
          }
          post {
            always {
              junit allowEmptyResults: true, testResults: 'backend/services/**/target/surefire-reports/*.xml'
            }
          }
        }

        stage('CPQ Beta Python') {
          steps {
            sh 'bash scripts/ci/run-core.sh cpq-beta-python'
          }
        }

        stage('Frontend') {
          steps {
            sh 'bash scripts/ci/run-core.sh frontend'
          }
        }
      }
    }

  // Integration does not wait on frontend (matches GHA needs: shared-libraries, backend-unit)
    stage('Backend Integration') {
      steps {
        sh 'bash scripts/ci/run-core.sh backend-integration'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'backend/**/target/surefire-reports/*.xml'
        }
      }
    }

    stage('AI Assist') {
      when {
        expression { return env.AI_CI_ENABLED == 'true' }
      }
      options { timeout(time: 15, unit: 'MINUTES') }
      steps {
        script {
          def driftStatus = sh(
            script: 'bash infra/jenkins/ai-assist/run-ai-stage.sh drift_check',
            returnStatus: true
          )
          if (driftStatus != 0 && env.AI_CI_BLOCKING == 'true') {
            unstable('CI drift check failed (AI_CI_BLOCKING=true)')
          } else if (driftStatus != 0) {
            echo 'CI drift check reported issues (see ci-reports/ai-drift-check.md)'
          }
          runAiStage('code_review', "--base-ref \"${env.GIT_BASE_REF}\"")
          runAiStage('test_suggestions', "--base-ref \"${env.GIT_BASE_REF}\"")
        }
      }
    }

    stage('Security Audit') {
      steps {
        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
          sh '''
            mkdir -p ci-reports
            bash scripts/ci/run-core.sh security-audit 2>&1 | tee ci-reports/security-audit-console.txt
          '''
        }
      }
      post {
        success {
          script {
            if (env.AI_CI_ENABLED == 'true') {
              catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                runAiStage('security_triage', "--output \"${WORKSPACE}/ci-reports/ai-security-triage.md\"")
              }
            }
          }
        }
        unstable {
          script {
            if (env.AI_CI_ENABLED == 'true') {
              catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                runAiStage('security_triage', "--output \"${WORKSPACE}/ci-reports/ai-security-triage.md\"")
              }
            }
          }
        }
      }
    }
  }

  post {
    always {
      archiveArtifacts artifacts: 'ci-reports/**/*.md', allowEmptyArchive: true
      archiveArtifacts artifacts: 'backend/**/target/surefire-reports/*.xml', allowEmptyArchive: true
    }
    failure {
      script {
        if (env.AI_CI_ENABLED == 'true') {
          catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
            def blocking = env.AI_CI_BLOCKING == 'true'
            def runLog = {
              sh '''
                mkdir -p ci-reports
                LOG_FILE="${JENKINS_HOME}/jobs/${JOB_NAME}/builds/${BUILD_NUMBER}/log"
                if [ -f "$LOG_FILE" ]; then
                  tail -n 500 "$LOG_FILE" | bash infra/jenkins/ai-assist/run-ai-stage.sh log_analysis \
                    --stage pipeline-failure \
                    --output "${WORKSPACE}/ci-reports/ai-log-analysis.md"
                else
                  bash infra/jenkins/ai-assist/run-ai-stage.sh log_analysis \
                    --stage pipeline-failure \
                    --output "${WORKSPACE}/ci-reports/ai-log-analysis.md" </dev/null
                fi
              '''
            }
            try {
              withCredentials([string(credentialsId: 'ai-api-key', variable: 'LLM_API_KEY')]) { runLog() }
            } catch (err) {
              echo "ai-api-key credential unavailable: ${err}"
              runLog()
            }
          }
        }
      }
    }
    success {
      echo 'Pipeline succeeded.'
    }
    unstable {
      echo 'Pipeline unstable (advisory AI or security audit findings may exist).'
    }
  }
}
