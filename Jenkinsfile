properties([
    buildDiscarder(logRotator(daysToKeepStr: '', numToKeepStr: '3')),
])
timestamps {
  node {

    def repoUrl = "git@bitbucket.org:citeck/ecos-community-core.git"

    stage('Checkout Script Tools SCM') {
      dir('jenkins-script-tools') {
        checkout([
          $class: 'GitSCM',
          branches: [[name: "script-tools"]],
          doGenerateSubmoduleConfigurations: false,
          extensions: [],
          submoduleCfg: [],
          userRemoteConfigs: [[credentialsId: 'awx.integrations', url: 'git@bitbucket.org:citeck/pipelines.git']]
        ])
      }
    }
    currentBuild.changeSets.clear()
    def buildTools = load "jenkins-script-tools/scripts/build-tools.groovy"

    try {

      stage('Checkout SCM') {
        checkout([
          $class: 'GitSCM',
          branches: [[name: "${env.BRANCH_NAME}"]],
          doGenerateSubmoduleConfigurations: false,
          extensions: [],
          submoduleCfg: [],
          userRemoteConfigs: [[credentialsId: 'awx.integrations', url: repoUrl]]
        ])
      }

      def project_version = readMavenPom().getProperties().getProperty("revision")

      if (!(env.BRANCH_NAME ==~ /master(-\d)?/) && (!project_version.contains('SNAPSHOT'))) {
        def tag = ""
        try {
          tag = sh(script: "git describe --exact-match --tags", returnStdout: true).trim()
        } catch (Exception e) {
          // no tag
        }
        def buildStopMsg = ""
        if (tag == "") {
          buildStopMsg = "You should add tag with version to build release from non-master branch. Version: " + project_version
        } else if (tag != project_version) {
          buildStopMsg = "Release tag doesn't match version. Tag: " + tag + " Version: " + project_version
        }
        if (buildStopMsg != "") {
          echo buildStopMsg
          buildTools.notifyBuildWarning(repoUrl, buildStopMsg, env)
          currentBuild.result = 'NOT_BUILT'
          return
        }
      }
      step([$class: 'MavenSnapshotCheck', check: 'true'])

      buildTools.notifyBuildStarted(repoUrl, project_version, env)
      // build-info
      def buildData = buildTools.getBuildInfo(repoUrl, "${env.BRANCH_NAME}", project_version)
      dir('build/build-info') {
        buildTools.writeBuildInfoToFiles(buildData)
      }
      // /build-info

      stage('Assembling and publishing project artifacts') {
        withMaven(mavenLocalRepo: '/opt/jenkins/.m2/repository', tempBinDir: '') {
          sh "mvn clean deploy"
          sh "mvn clean"
        }
      }
    }
    catch (Exception e) {
      currentBuild.result = 'FAILURE'
      error_message = e.getMessage()
      echo error_message
    }
    script {
      if (currentBuild.result != 'FAILURE') {
        buildTools.notifyBuildSuccess(repoUrl, env)
      } else {
        buildTools.notifyBuildFailed(repoUrl, error_message, env)
      }
    }
  }
}
