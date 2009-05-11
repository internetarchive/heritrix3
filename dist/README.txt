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

 http://webteam.archive.org/confluence/display/Heritrix/2.0.0
 
3.0 Getting Started Tips

The shell script 'heritrix' in the 'bin' directory is usually 
sufficient to launch Heritrix. You must use the '-a' launch flag to set 
an authentication password on the web user interface. You must use the 
'-b' launch flag if you want the web user interface to accept non-local 
connections. 

The bundled job profiles are good starting points for designing your 
own crawl configurations. However, they each require several changes 
before they will work for crawling:

- You must configure an 'operator-contact-url' on the job's global 
  settings sheet. This URL will be added to the 'User-Agent' included 
  on your crawl's outbound traffic, and should be an HTTP URL supplying 
  information about  the purpose of your crawl and containing contact 
  information if visited  sites need to report problems.
- You must supply one or more 'seed' URLs to serve as crawl starting 
  points. 

4.0 License

Heritrix is free software; you can redistribute it and/or modify it
under the terms of the GNU Lesser Public License as published by the
Free Software Foundation; either version 2.1 of the License, or any
later version.
                                                                                
Heritrix is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser Public License for more details.
                                                                                
You should have received a copy of the GNU Lesser Public License
along with Heritrix (See LICENSE.txt); if not, write to the Free
Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
02111-1307  USA
                                                                                
Heritrix includes a variety of other open source libraries under the
terms of their respective licenses. Please consult those individual
licenses to learn whether the libraries are usable and redistributable 
in contexts other than the Heritrix distribution.
