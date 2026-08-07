@echo off
set JAVA_HOME=C:\Users\47042\android_studio\jbr
set ANDROID_HOME=C:\Users\47042\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

"C:\Users\47042\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5\bin\gradle.bat" %*
