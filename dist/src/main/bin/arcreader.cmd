:: This is the Windows version of the extractor shell script
:: Caveats, see heritrix.cmd
::
:: This script runs the arcreader main.
::
:: Optional environment variables
::
:: JAVA_HOME        Point at a JDK install to use.
:: 
:: HERITRIX_HOME    Pointer to your heritrix install.  If not present, we 
::                  make an educated guess based of position relative to this
::                  script.
::
:: JAVA_OPTS        Java runtime options.
@echo off

set PRGDIR=%~p0

if "%PRGDIR%"=="~p0" (
  cmd /E:ON /F:ON /V:ON /c %0 %1 %2 %3 %4 %5 %6 %7 %8 %9
  goto:eof
)

:: unset JMX_OFF afterwards if it wasn't set before
:: Won't work if script is aborted with Ctrl+C...
if not defined JMX_OFF set UNSET_JMX_OFF=true
set JMX_OFF=off
set CLASS_MAIN=org.archive.io.arc.ARCReader
call "%PRGDIR%\foreground_heritrix.cmd" %*
set CLASS_MAIN=
if not defined UNSET_JMX_OFF goto:eof
set JMX_OFF=
set UNSET_JMX_OFF=

:eof