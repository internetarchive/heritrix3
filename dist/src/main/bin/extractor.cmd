:: This is the Windows version of the extractor shell script
:: Caveats, see heritrix.cmd
::
:: This script runs the org.archive.crawler.extractor.ExtractorTool main.
:: Pass '--help' to get usage message.
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

set CLASS_MAIN=org.archive.crawler.extractor.ExtractorTool
call "%PRGDIR%\foreground_heritrix.cmd" %*
set CLASS_MAIN=

:eof