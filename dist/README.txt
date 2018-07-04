Readme for Heritrix
====================

1. Introduction
2. Crawl Operators!
3. Getting Started
4. Developer Documentation
5. Release History
6. License


1. Introduction
----------------
Heritrix is the Internet Archive's open-source, extensible, web-scale,
archival-quality web crawler project. Heritrix (sometimes spelled heretrix, or
misspelled or missaid as heratrix/heritix/heretix/heratix) is an archaic word
for heiress (woman who inherits). Since our crawler seeks to collect and
preserve the digital artifacts of our culture for the benefit of future
researchers and generations, this name seemed apt.


2. Crawl Operators!
--------------------
Heritrix is designed to respect the robots.txt
<http://www.robotstxt.org/wc/robots.html> exclusion directives and META robots
tags <http://www.robotstxt.org/wc/exclusion.html#meta>.  Please consider the
load your crawl will place on seed sites and set politeness policies
accordingly. Also, always identify your crawl with contact information in the
User-Agent so sites that may be adversely affected by your crawl can contact
you or adapt their server behavior accordingly.


3. Getting Started
-------------------
See the User Manual at <https://github.com/internetarchive/heritrix3/wiki/Heritrix%203.0%20and%203.1%20User%20Guide>


4. Developer Documentation
---------------------------
See <http://crawler.archive.org/articles/developer_manual/index.html>.
For API documentation, see <https://webarchive.jira.com/wiki/display/Heritrix/Heritrix+3.x+API+Guide>
and <http://builds.archive.org/javadoc/heritrix-3.2.0/>


5. Latest Releases
-------------------
Information about releases can be found at <https://github.com/internetarchive/heritrix3/wiki#latest-releases>


6. License
-----------
Heritrix is free software; you can redistribute it and/or modify it
under the terms of the Apache License, Version 2.0:

 http://www.apache.org/licenses/LICENSE-2.0

Some individual source code files are subject to or offered under other
licenses. See the included LICENSE.txt file for more information.

Heritrix is distributed with the libraries it depends upon.  The
libraries can be found under the 'lib' directory, and are used under
the terms of their respective licenses, which are included alongside
the libraries in the 'lib' directory.

