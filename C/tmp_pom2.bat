@echo off
setlocal enabledelayedexpansion
for /f "tokens=*" %%a in ('findstr /i "packaging\|mainClass\|spring-boot" "C:\Users\sahti\OneDrive\Desktop\sem3\opps\javaproject\pom.xml"') do echo %%a