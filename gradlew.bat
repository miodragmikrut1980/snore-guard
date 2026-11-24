@ECHO OFF
SETLOCAL
SET "DIR=%~dp0"
CD /D "%DIR%"
java -cp "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
EXIT /B %ERRORLEVEL%
