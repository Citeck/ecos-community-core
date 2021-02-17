properties([
    buildDiscarder(logRotator(daysToKeepStr: '', numToKeepStr: '7')),
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
          userRemoteConfigs: [[credentialsId: 'bc074014-bab1-4fb0-b5a4-4cfa9ded5e66', url: 'git@bitbucket.org:citeck/pipelines.git']]
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
          userRemoteConfigs: [[credentialsId: 'bc074014-bab1-4fb0-b5a4-4cfa9ded5e66', url: repoUrl]]
        ])
      }

      def project_version = readMavenPom().getVersion()

      if ((env.BRANCH_NAME != "master") && (!project_version.contains('SNAPSHOT')))  {
        echo "Assembly of release artifacts is allowed only from the master branch!"
        //currentBuild.result = 'SUCCESS'
        //return
      }

      buildTools.notifyBuildStarted(repoUrl, project_version, env)
      // build-info
      def buildData = buildTools.getBuildInfo(repoUrl, "${env.BRANCH_NAME}", project_version)
      dir('build/build-info') {
        buildTools.writeBuildInfoToFiles(buildData)
      }
      // /build-info

      stage('Assembling and publishing project artifacts') {
        withMaven(mavenLocalRepo: '/opt/jenkins/.m2/repository', tempBinDir: '') {
          sh "mvn clean deploy -DskipTests=true"
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
