Dependency Walker Plugin
========================

Licensed under [MIT Licence].


About
-----
Allows you to perform given pipeline step(s) on a job and all its linked jobs.

Linked jobs are the upstreams of the job, derived from maven dependencies. Dependency graph can be visualized using
[Dependency Graph View Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Dependency+Graph+View+Plugin).

The plugin can be used to preform a clean build in the isolated environment for the group of dependent projects.

Another use case is a multiple release. When the release action 
(see [Release Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Release+Plugin) or 
[M2 Release Plugin](https://wiki.jenkins-ci.org/display/JENKINS/M2+Release+Plugin)) is defined
in a way that it drops SNAPSHOT keyword from dependencies, than using this plugin one can trigger
a consistent release of multiple modules.
In this scenario release should first update dependencies, for example invoking following maven target: 
   `versions:use-releases scm:checkin -Dmessage="drop snapshot versions"` 

In some way the plugin is an alternative to [Maven Cascade Release Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Maven+Cascade+Release+Plugin).