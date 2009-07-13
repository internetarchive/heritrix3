-------------------------------------------------------------------------------
$Id$
-------------------------------------------------------------------------------
0.0 Contents

1.0 Introduction
2.0 Online Reference
3.0 Getting Started Tips
4.0 License

1.0 Introduction

Heritrix is the Internet Archive's open-source, extensible, web-scale,
archival-quality web crawler project. Heritrix (sometimes spelled 
heretrix, or misspelled or missaid as heratrix/heritix/heretix/heratix) 
is an archaic word for heiress (woman who inherits). Our crawler seeks 
to collect and preserve the digital artifacts of our culture for the 
benefit of future researchers and generations. 

2.0 Online Reference

The most up-to-date information about Heritrix is on the project wiki:

 http://webarchive.jira.com/wiki/display/Heritrix
 
3.0 Getting Started Tips

The shell script 'heritrix' in the 'bin' directory is usually 
sufficient to launch Heritrix. You must use the '-a' launch flag to set 
an authentication password on the web user interface. You may use the 
'-b' launch flag if you want the web user interface to accept non-local 
connections. 

Upon launch, information for contacting the operator UI via a web 
browser will be displayed to the console. (Note: this will be an 
'https' URL, and will use a generated-when-first-needed self-signed 
certificate. You will likely need to tell your web browser to accept
this certificate after encountering a warning page.)

The bundled job profile is a good starting point for designing your 
own crawl configurations. However, a bundled profile requires several 
changes before it will work for crawling:

- You must configure an 'operator-contact-url' on the job's global 
  settings sheet. This URL will be added to the 'User-Agent' included 
  on your crawl's outbound traffic, and should be an HTTP URL supplying 
  information about  the purpose of your crawl and containing contact 
  information if visited  sites need to report problems.
- You must supply one or more 'seed' URLs to serve as crawl starting 
  points. 

4.0 License

Heritrix is free software based on the contributions and sponsorship
of many individuals and organizations, as stewarded and licensed by 
the Internet Archive. 

Starting with Heritrix version 3, and except where otherwise noted
with respect to individual files or third-party libraries, Heritrix 
is licensed under the terms of the Apache License, Version 2.0:

  http://www.apache.org/licenses/LICENSE-2.0
                                                  
Heritrix includes a variety of other free and open source libraries 
under the terms of their respective licenses. Please consult those 
individual licenses to learn whether the libraries are usable and 
redistributable in contexts other than the Heritrix distribution.
