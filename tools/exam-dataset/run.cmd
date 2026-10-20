@echo off
setlocal
call mvn.cmd -B -f "%~dp0pom.xml" test dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt
if errorlevel 1 exit /b %errorlevel%
set /p EX01_RUNTIME_CP=<"%~dp0target\runtime-classpath.txt"
java.exe -cp "%~dp0target\classes;%EX01_RUNTIME_CP%" vn.edu.toeic.dataset.ExamDataset --root "%~dp0..\.." %*
exit /b %errorlevel%
