:: This script launches the Heritrix crawler on windows.  While Heritrix
:: is not officially supported on Windows, as a Java application it 
:: generally works (and a number of active contributors primarily use
:: Windows for Heritrix development). 
::
:: This script was originally provided by Eric Jensen as a convenience 
:: to the Windows-afflicted. Its proper functioning/functionality may
:: lag the officially-supported Linux start script. Contributions of
:: improvements are always welcome.
::
:: See also:
:: http://webarchive.jira.com/wiki/display/Heritrix/Heritrix3+on+Windows
::
::
::  Optional environment variables
:: 
::  JAVA_HOME        Point at a JDK install to use.
::  
::  HERITRIX_HOME    Pointer to your heritrix install.  If not present, we 
::                   make an educated guess based of position relative to this
::                   script.
:: 
::  HERITRIX_OUT     Pathname to the Heritrix log file written when run in
::                   daemon mode.
::                   Default setting is %HERITRIX_HOME%\heritrix_out.log
:: 
::  JAVA_OPTS        Java runtime options.  Default setting is '-Xmx256m'.
:: 
::  FOREGROUND       Set to any value -- e.g. 'true' -- if you want to run 
::                   heritrix in foreground (Used by build system when it runs
::                   selftest to see if completed successfully or not).
:: 
@echo off

set PRG=%0
set PRGDIR=%~p0
:: windows doesn't have a sleep command build-in
set SLEEP=ping 127.0.0.1 -n 2 -w 1000

if "%1"=="RUN" goto run
if "%1"=="BGR" goto run_in_background
:: preserve original command line arguments
if "%*"=="*" (
	:: windows separates things like --digest=false into "--digest" and "false" if using %1 %2 %3...
	:: But as command extensions are enabled by default, this should be no problem for most users
    echo NOTICE:  Try starting your console with "cmd /E:ON" if you are experiencing
    echo          problems passing command line arguments to Heritrix
	echo.
	set HERITRIX_CMDLINE=%1 %2 %3 %4 %5 %6 %7 %8 %9
) else (
	set HERITRIX_CMDLINE=%*
)
:: Enabling command extensions and delayed variable expansion
cmd /E:ON /F:ON /V:ON /c %PRG% RUN
goto :end

:run
:: Read local heritrix properties if any.
:: To do this on Windows, tempor. rename .heritrixrc to heritrixrc.cmd
:: This is of course only useful if .heritrixrc contains Windows style "set VAR=value" statements
set RC_PATH=%HOMEPATH%
if "%RC_PATH%"=="\" set RC_PATH=\.
if defined HOMEDRIVE set RC_PATH=%HOMEDRIVE%!RC_PATH!
if exist "!RC_PATH!\.heritrixrc" (
    ren "!RC_PATH!\.heritrixrc" heritrixrc.cmd
    call "!RC_PATH!\heritrixrc.cmd"
    ren "!RC_PATH!\heritrixrc.cmd" .heritrixrc
)
set RC_PATH=

:: Set HERITRIX_HOME.
if defined HERITRIX_HOME goto find_java
set HERITRIX_HOME=%PRGDIR:~0,-4%
if "%PRGDIR:~-1%"=="\" set HERITRIX_HOME=%PRGDIR:~0,-5%

:: Find JAVA_HOME or java if JAVACMD is not defined.
:find_java
if defined JAVACMD goto java_found
if defined JAVA_HOME goto set_javacmd

:: Try to find java if neither JAVACMD nor JAVA_HOME is set:
java -version >nul 2>&1
:: 9009 means "command not found"
if errorlevel 9009 goto no_java_home
:: something else is wrong with executing java
if errorlevel 1 goto no_java_home

:: java seems to be in PATH
set JAVACMD=java -Dje.disable.java.adler32=true
:set_javacmd
if not defined JAVACMD set JAVACMD="%JAVA_HOME%\bin\java" -Dje.disable.java.adler32=true
:: It may be defined in env - including flags!!
:: See '[ 1482761 ] BDB Adler32 gc-lock OOME risk' for why we include the
:: 'je.disable.java.adler32'.
:java_found

:: Ignore previous classpath.  Build one that contains heritrix jar and content
:: of the lib directory into the variable CP.
set CP=
set OLD_CLASSPATH=%CLASSPATH%
for %%j in ("%HERITRIX_HOME%\lib\*.jar" "%HERITRIX_HOME%\*.jar") do set CP=!CP!;%%j
set CLASSPATH=!CP!

:: DONT cygwin path translation
:: if expr `uname` : 'CYGWIN*' > /dev/null; then
::    CP=`cygpath -p -w "%CP"`
::    HERITRIX_HOME=`cygpath -p -w "%HERITRIX_HOME"`
:: fi

:: Make sure of java opts.
if not defined JAVA_OPTS set JAVA_OPTS= -Xmx256m

:: Setting environment vars in nested IFs is error prone, thus using GOTOs

:: Main heritrix class.
if not defined CLASS_MAIN set CLASS_MAIN=org.archive.crawler.Heritrix

:: heritrix_dmesg.log contains startup output from the crawler main class. 
:: As soon as content appears in this log, this shell script prints the 
:: successful (or failed) startup content and moves off waiting on heritrix
:: startup. This technique is done so we can show on the console startup 
:: messages emitted by java subsequent to the redirect of stdout and stderr.
set startMessage=%HERITRIX_HOME%\heritrix_dmesg.log

:: Remove any file that may have been left over from previous starts.
if exist "%startMessage%" del "%startmessage%"

:: Run heritrix as daemon.  Redirect stdout and stderr to a file.
:: Print start message with date, java version, java opts, ulimit, and uname.
if not defined HERITRIX_OUT set HERITRIX_OUT=%HERITRIX_HOME%\heritrix_out.log

set stdouterrlog=%HERITRIX_OUT%
echo %DATE% %TIME% Starting heritrix >>"%stdouterrlog%"
:: uname -a >> %stdouterrlog%
%JAVACMD% %JAVA_OPTS% -version >>"%stdouterrlog%"  2>&1
echo JAVA_OPTS=%JAVA_OPTS% >>"%stdouterrlog%"
:: ulimit -a >> %stdouterrlog 2>&1

:: DONT If FOREGROUND is set, run heritrix in foreground.
:: if defined FOREGROUND
:start_heritrix
if not defined FOREGROUND goto run_in_background
%JAVACMD% "-Dheritrix.home=%HERITRIX_HOME%" -Djava.protocol.handler.pkgs=org.archive.net "-Dheritrix.out=%HERITRIX_OUT%" %JAVA_OPTS% %CLASS_MAIN% %HERITRIX_CMDLINE%
:: errorlevel 130 if aborted with Ctrl+c (at least my sun jvm 1.5_07...)
if errorlevel 130 goto :end
if errorlevel 1 goto fix_problems
goto :end

:run_in_background
if not "%1"=="BGR" (
    start /MIN cmd /E:ON /F:ON /V:ON /c %PRG% BGR
    goto wait_for_log_file
) else (
    title Heritrix
    :: adding  ">>%stdouterrlog% 2>&1" causes an access denied error as heritrix writes also to this file	
    %JAVACMD% "-Dheritrix.home=%HERITRIX_HOME%" -Djava.protocol.handler.pkgs=org.archive.net "-Dheritrix.out=%HERITRIX_OUT%" %JAVA_OPTS% %CLASS_MAIN% %HERITRIX_CMDLINE%	
    if errorlevel 130 goto :end
    if errorlevel 1 echo.!ERRORLEVEL! >"%HERITRIX_HOME%\heritrix_launch_problems"
	pause
	)
goto :end

:wait_for_log_file
SET HERITRIX_COUNTER=
echo WARNING: It's currently not possible to run Heritrix in background
echo          on Windows. It was just started minimized in a new Window
echo          and will be shut down as soon as you log off.
echo.
echo %DATE% %TIME% Starting heritrix
:print_logfile
%SLEEP%>nul
if exist "%HERITRIX_HOME%\heritrix_launch_problems" (
    del "%HERITRIX_HOME%\heritrix_launch_problems"
    goto fix_problems
)
if exist "%startMessage%" (
    %SLEEP%>nul
    type "%startMessage%"
    :: can happen when heritrix writes to the file at the same time
    if errorlevel 1 goto print_logfile
    goto delete_logfile
)
:: keep trying for 30 more seconds
if "!HERITRIX_COUNTER!"==".............................." goto start_may_failed
set HERITRIX_COUNTER=.!HERITRIX_COUNTER!
echo .
goto print_logfile

:delete_logfile
set HERITRIX_COUNTER=
%SLEEP%>nul
%SLEEP%>nul
del "%startMessage%" >nul 2>&1
:: del doesn't set the ERRORLEVEL var if unsuccessful, so we can't try again
goto :end

:fix_problems
if not "%CLASS_MAIN%"=="org.archive.crawler.Heritrix" goto :start_may_failed
echo.
echo Heritrix failed to start properly. Possible causes:
echo.
echo - another program uses the port for the web inferface (8443 default)
echo   (e.g. another Heritrix instance)
goto :end

:start_may_failed
set HERITRIX_COUNTER=
echo Starting Heritrix seems to have failed
goto :end

:no_java_home
echo Please define either JAVA_HOME or JAVACMD or make sure java.exe is in PATH
goto :end

:: needed if initially called without command extensions
:end
:: do some cleanup
set HERITRIX_CMDLINE=
if defined OLD_CLASSPATH set CLASSPATH=%OLD_CLASSPATH%
set CP=
set SLEEP=
set PRGDIR=
set PRG=
