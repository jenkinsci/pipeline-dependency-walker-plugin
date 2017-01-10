Dependency Walker Plugin
========================

Licensed under [MIT Licence].


About
-----
Allows you to perform given pipeline step(s) on a job and all its linked jobs.

Linked jobs are the upstreams of the job, derived from maven dependencies. Dependency graph can be visualized using
[Dependency Graph View Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Dependency+Graph+View+Plugin).


Usage scenarios
---------------

### Clean build
The plugin can be used to preform a clean build in the isolated environment for the group of dependent projects.

Example of pipeline configuration:
```groovy
node {
    stage('build') {
        echo "cleanup workspace"
        deleteDir()

        mvnRepo=pwd() + "/m2" // set local maven repo
        echo "use maven repo $mvnRepo"

        walk job: 'parent-project', jobAction: '''
            dir('JOB_NAME') {
                git url: 'JOB_SCM_URL', branch: 'JOB_SCM_BRANCH'
                withMaven(maven: 'mvn', mavenLocalRepo: mvnRepo) {
                    sh "mvn clean install"
                }
            }
        '''
    }
}

```

### Multiple release
Another use case is a multiple release. When the release action 
(see [Release Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Release+Plugin) or 
[M2 Release Plugin](https://wiki.jenkins-ci.org/display/JENKINS/M2+Release+Plugin)) is defined
in a way that it drops SNAPSHOT keyword from dependencies, than using this plugin one can trigger
a consistent release of multiple modules.
In this scenario release should first update dependencies, for example invoking following maven target: 
   `versions:use-releases scm:checkin -Dmessage="drop snapshot versions"` 

In some way the plugin is an alternative to [Maven Cascade Release Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Maven+Cascade+Release+Plugin).

Example of pipeline configuration:
```groovy
node {
    stage('build') {
        walk job: 'parent-project', failOnUnstable: true, jobAction: 'release "JOB_NAME"'
    }
}
```

How to develop
--------------

First run a development instance of jenkins by executing a command
   mvn hpi:run

Go to [jenkins development instance](http://localhost:8080/jenkins) and configure as follwoing:
1. In [system configuration](http://localhost:8080/jenkins/configure) click on `Maven installations...` button and configure:
  * Set `Name` to `mvn`
  * deselect`Install automatically`
  * set `MAVEN_HOME` to your local maven installation (e.g. `/usr/share/maven`)
2. In [plugin manager](http://localhost:8080/jenkins/pluginManager/available) install following plugins with *restart*:
  * [Pipeline](https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Plugin)
  * [Pipeline Maven Integration Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Maven+Plugin)
