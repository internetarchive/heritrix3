# Heritrix
[![Build Status](https://travis-ci.org/internetarchive/heritrix3.svg?branch=master)](https://travis-ci.org/internetarchive/heritrix3)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.archive/heritrix/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.archive/heritrix)
[![Javadoc](https://javadoc-badge.appspot.com/org.archive/heritrix.svg?label=javadoc)](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-engine)
[![LICENSE](https://img.shields.io/badge/license-Apache-blue.svg?style=flat-square)](./LICENSE)

## Introduction

Heritrix is the Internet Archive's open-source, extensible, web-scale, archival-quality web crawler project. Heritrix (sometimes spelled heretrix, or misspelled or missaid as heratrix/heritix/heretix/heratix) is an archaic word for heiress (woman who inherits). Since our crawler seeks to collect and preserve the digital artifacts of our culture for the benefit of future researchers and generations, this name seemed apt.

## Crawl Operators!

Heritrix is designed to respect the [`robots.txt`](http://www.robotstxt.org/robotstxt.html) exclusion directives<sup>†</sup> and [META nofollow tags](http://www.robotstxt.org/meta.html). Please consider the
load your crawl will place on seed sites and set politeness policies accordingly. Also, always identify your crawl with contact information in the `User-Agent` so sites that may be adversely affected by your crawl can contact you or adapt their server behavior accordingly.

<sup>†</sup> The newer wildcard extension to robots.txt is [not yet](https://github.com/internetarchive/heritrix3/issues/250) supported.

## Getting Started

- [User Manual](https://github.com/internetarchive/heritrix3/wiki)

## Developer Documentation

- [Developer Manual](http://crawler.archive.org/articles/developer_manual/index.html)
- [REST API documentation](https://heritrix.readthedocs.io/en/latest/api.html)
- JavaDoc: [engine](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-engine), [modules](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-modules), [commons](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-commons), [contrib](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-contrib)


## Latest Releases

Information about releases can be found [here](https://github.com/internetarchive/heritrix3/wiki#latest-releases).

## License

Heritrix is free software; you can redistribute it and/or modify it under the terms of the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Some individual source code files are subject to or offered under other licenses. See the included [`LICENSE.txt`](./LICENSE) file for more information.

Heritrix is distributed with the libraries it depends upon. The libraries can be found under the `lib` directory in the release distribution, and are used under the terms of their respective licenses, which are included alongside the libraries in the `lib` directory.
