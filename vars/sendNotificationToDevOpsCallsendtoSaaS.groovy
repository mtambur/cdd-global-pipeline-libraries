def call() {
 try {
  environmentSetUp()
  sendNotificationToDevOps()
  processDevOpsReleases(evaluate("${currentBuild.description}"))
 } catch (ex) {
  echo "Exception occurred. Skipping notification to DEVOPS. Error is [" + ex.toString() + "]"
 }
}

void environmentSetUp() {
 getAPIKeyFromCredentials()
 setGitEnvironmentVariables()
 env.DEVOPS_SERVER_NAME = "nginx-plus-route-443-cdd-bizops.app.gdue4.saasdev.broadcom.com"
 env.DEVOPS_SERVER_PORT = "443"
 env.DEVOPS_TENANT_ID = "cecdf0b6-bac8-4305-b8a6-3ec26c8e4916"
 echo "DEVOPS_TENANT_ID: [$env.DEVOPS_TENANT_ID]"
 env.DEVOPS_USE_SSL = true
 env.DEVOPS_APPLICATION_NAME = "${env.GIT_URL.replaceFirst(/^.*\/([^\/]+?).git$/, '$1')}"
 env.DEVOPS_APPLICATION_VERSION = "$env.BRANCH_NAME"
 env.DEVOPS_GIT_COMMIT_ID = "$env.GIT_COMMIT"
 env.DEVOPS_PREVIOUS_GIT_COMMIT_ID = "$env.GIT_PREVIOUS_SUCCESSFUL_COMMIT"
}

void getAPIKeyFromCredentials() {
 def credentials = com.cloudbees.plugins.credentials.CredentialsProvider.findCredentialById('DEVOPS_API_KEY',
  com.cloudbees.plugins.credentials.Credentials.class,
  currentBuild.rawBuild, null
 );
 env.DEVOPS_API_KEY = credentials.secret
}

void setGitEnvironmentVariables() {
 if (!env.GIT_URL) env.GIT_URL = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
 if (!env.GIT_COMMIT) env.GIT_COMMIT = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
 if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) env.GIT_PREVIOUS_SUCCESSFUL_COMMIT = getLastSuccessfulCommit()
}

String getLastSuccessfulCommit() {
 String lastSuccessfulHash = null
 def lastSuccessfulBuild = currentBuild.rawBuild.getPreviousSuccessfulBuild()
 println "Previous Successful Build: [$lastSuccessfulBuild]"
 if (lastSuccessfulBuild) {
  def scmAction = lastSuccessfulBuild?.actions.find {
   action -> action instanceof jenkins.scm.api.SCMRevisionAction
  }
  lastSuccessfulHash = scmAction?.revision?.hash
  println "Previous Successful Build Revision: [${scmAction?.revision}], Hash: [$lastSuccessfulHash], Source Id: [${scmAction?.sourceId}]"
 }
 return lastSuccessfulHash
}

void sendNotificationToDevOps() {
 echo '----------Sending Build Notification to DEVOPS--------------'
 echo "Environment variables: GIT_URL: [$env.GIT_URL], GIT_BRANCH: [$env.GIT_BRANCH], BRANCH_NAME: [$env.BRANCH_NAME], GIT_LOCAL_BRANCH: [$env.GIT_LOCAL_BRANCH], DEVOPS_APPLICATION_NAME: [${DEVOPS_APPLICATION_NAME}], DEVOPS_APPLICATION_VERSION: [${DEVOPS_APPLICATION_VERSION}], GIT_COMMIT: [${env.GIT_COMMIT}], GIT_PREVIOUS_SUCCESSFUL_COMMIT: [${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}]"
 echo "DEVOPS_TENANT_ID: [${DEVOPS_TENANT_ID}]"
 sendNotificationToCDD useSourceCodeRepositoryNameAsApplicationName: true,
  appName: "${DEVOPS_APPLICATION_NAME}",
  useSourceCodeRepositoryBranchNameAsApplicationVersionName: true,
  appVersion: "${DEVOPS_APPLICATION_VERSION}",
  gitCommit: "${DEVOPS_GIT_COMMIT_ID}",
  gitPrevSuccessfulCommit: "${DEVOPS_PREVIOUS_GIT_COMMIT_ID}",
  overrideCDDConfig: [
   customApiKey: "${DEVOPS_API_KEY}",
   customProxyPassword: '',
   customProxyUrl: '',
   customProxyUsername: '',
   customServerName: "${DEVOPS_SERVER_NAME}",
   customServerPort: "${DEVOPS_SERVER_PORT}",
   customTenantId: "${DEVOPS_TENANT_ID}",
   customUseSSL: "${DEVOPS_USE_SSL}"
  ],
  releaseTokens: '{}',
  scope: "APPLICATION",
  fileSourceName: '${DEVOPS_APPLICATION_NAME}',
  fileSourceParameters: '{"branch":"main"}',
  dslFilename: 'release.json',
  dslParameters: '{ "DIL_REPOSITORY_NAME": "${DEVOPS_APPLICATION_NAME}", "DIL_REPOSITORY_BRANCH": "${DEVOPS_APPLICATION_VERSION}" }',
  ignoreNonexistentApplication: true
 echo '----------Jenkins Pipeline completed successfully--------------'
}

void processDevOpsReleases(Map devopsReleaseMap) {
 if (devopsReleaseMap) {
  echo '----------Process DEVOPS Releases--------------'
  devopsReleaseMap.eachWithIndex {
   entry,
   index ->
   println "[$entry.key] = [$entry.value]"
   env.
   "$entry.key" = "$entry.value";
  }
 }
}
