:: This is the windows version of the foreground_heritrix shell script
:: The only difference to an invokation with "heritrix.cmd" is that no extra
:: (minimized) console window is created...
:: Caveats, see heritrix.cmd
::
:: This script launches the heritrix crawler and keeps the window in foreground
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
::
:: FOREGROUND       Set to any value -- e.g. 'true' -- if you want to run 
::                  heritrix in foreground (Used by build system when it runs
::                  selftest to see if completed successfully or not)..
::
@echo off

set PRGDIR=%~p0

if "%PRGDIR%"=="~p0" (
  cmd /E:ON /F:ON /V:ON /c %0 %1 %2 %3 %4 %5 %6 %7 %8 %9
  goto:eof
)

:: unset FOREGROUND afterwards if it wasn't set before
:: Won't work if script is aborted with Ctrl+C...
if not defined FOREGROUND set UNSET_FOREGROUND=true
set FOREGROUND=true
call "%PRGDIR%\heritrix.cmd" %*
if not defined UNSET_FOREGROUND goto:eof
set FOREGROUND=
set UNSET_FOREGROUND=

:eof