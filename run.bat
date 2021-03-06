@ECHO OFF

REM To run in debug mode, add to the MAVEN_OPTS: -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000

IF "%MAVEN_OPTS%" == "" (
    ECHO The environment variable 'MAVEN_OPTS' is not set, setting it for you
    SET MAVEN_OPTS=-Xms256m -Xmx2G -XX:PermSize=300m
)
ECHO MAVEN_OPTS is set to '%MAVEN_OPTS%'
mvn clean install ecos:run -DskipTests=true -Pdevelopment
