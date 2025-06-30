# Changelog

## [Unreleased](https://github.com/internetarchive/heritrix3/tree/HEAD)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.10.0...HEAD)

#### Bug fixes

- **FetchHTTP2**
  - HTTP/1.1 is now used on servers that don't support ALPN. Fixes `IOException: frame_size_error/invalid_frame_length`
  - Fixed NullPointerException when the server's IP address isn't available. 

- **Seeds report:** Redirect URIs are now recorded from the `Location` header for HTTP status codes `303 See other`, 
 `307 Temporary Redirect` and `308 Permanent Redirect`.
  Previously this was only done for `301 Moved Permanently` and `302 Found`. 

## [3.10.0](https://github.com/internetarchive/heritrix3/releases/tag/3.10.0)  (2025-06-12)

[Download distribution zip](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.10.0/heritrix-3.10.0-dist.zip) (
or [tar.gz](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.10.0/heritrix-3.10.0-dist.tar.gz))

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.9.0...3.10.0) | [Javadoc](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-engine/3.10.0/index.html) | [Maven Central](https://search.maven.org/artifact/org.archive.heritrix/heritrix/3.10.0/pom)

#### New features

- **BrowserProcessor:** Loads fetched pages in a local browser (Firefox/ChromeDriver), records all browser requests, 
  and runs pluggable behaviors (e.g. scrolling, link extraction). [#653](https://github.com/internetarchive/heritrix3/pull/653)
  - Uses the [WebDriver BiDi protocol](https://www.w3.org/TR/webdriver-bidi/) for browser automation.
  - The recording proxy is built on Jetty's ProxyHandler and the FetchHTTP2 module.
  - **Status:** Working for small crawls but needs more robust error handling (browser crashes, resource limits).

- **Basic web auth:** You can now switch the web interface from Digest authentication to Basic authentication
  with the `--web-auth basic` command-line option. This is useful when running Heritrix behind a reverse proxy that
  adds external authentication. [#654](https://github.com/internetarchive/heritrix3/pull/654)

- **Robots.txt wildcards:** The `*` and `$` wildcard rules from RFC 9309 are now supported.
  [#656](https://github.com/internetarchive/heritrix3/pull/656)

- **FetchHTTP2:** Added HTTP proxy support. [#657](https://github.com/internetarchive/heritrix3/pull/657)

#### Fixes

- **Code editor:** The configuration editor and script console were upgraded to CodeMirror 6. This resolves some browser
  incompatibilities, allowing CodeMirror’s own find function to be re-enabled for reliable text search of content far
  outside the viewport. [#651](https://github.com/internetarchive/heritrix3/pull/651)

- **BDB shutdown interrupt handling:** The thread’s interrupted flag is now cleared before some BDB interactions to reduce the likelihood of environment invalidation when requestCrawlStop() is called repeatedly. [#659](https://github.com/internetarchive/heritrix3/pull/659)

- **FetchHTTP2:** Fixed gzip alert log messages by configuring HttpClient to not decode gzip encoding from response.

#### Removals

- **Removed Apache HttpClient 3**: If you have custom Heritrix modules you may need to update the following
  class references in your code: 
  
  | Removed                                                   | Replacement                          |
  |-----------------------------------------------------------|--------------------------------------|
  | `org.apache.commons.httpclient.URIException`              | `org.archive.url.URIException`       |
  | `org.apache.commons.httpclient.Header`                    | `org.archive.format.http.HttpHeader` |

  Note that Apache HttpClient 4 (`org.apache.http`) was not removed.
  [#652](https://github.com/internetarchive/heritrix3/pull/652)

#### Dependency Upgrades

- **codemirror**: 2.23 → 6
- **easymock**: 5.5.0 → removed
- **groovy**: 4.0.26 → 4.0.27
- **junit**: 5.12.2 → 5.13.1
- **kafka-clients**: 3.9.0 → 3.9.1
- **spring**: 6.2.6 → 6.2.7
- **webarchive-commons**: 1.3.0 → 2.0.1

## [3.9.0](https://github.com/internetarchive/heritrix3/releases/tag/3.9.0) (2025-05-13)

[Download distribution zip](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.9.0/heritrix-3.9.0-dist.zip) (or [tar.gz](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.9.0/heritrix-3.9.0-dist.tar.gz))

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.8.0...3.9.0) | [Javadoc](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-engine/3.9.0/index.html) | [Maven Central](https://search.maven.org/artifact/org.archive.heritrix/heritrix/3.9.0/pom)

#### New features

- **FetchHTTP2**: Added a new fetch module supporting HTTP/2 and HTTP/3. [#649](https://github.com/internetarchive/heritrix3/pull/649)

#### Fixes

- **Fixed HighestUriPrecedenceProvider:** Added Histotable serializer and Kryo autoregistration. [#647](https://github.com/internetarchive/heritrix3/pull/647)

#### Changes

- **JUnit 5:** Upgraded all JUnit 3 and 4 style tests to JUnit 5. [#650](https://github.com/internetarchive/heritrix3/pull/650)

#### Dependency Upgrades

- **commons-io**: 2.18.0 → 2.19.0
- **gson**: 2.12.1 → 2.13.1
- **jetty**: 9.4.19.v20190610 → 12.0.17
- **jsch**: 0.2.24 → 2.27.0
- **junit**: 4.13.2 → 5.12.2
- **pdfbox**: 3.0.4 → 3.0.5
- **restlet**: 2.5.0 → 2.6.0-RC1
- **spring**: 6.2.5 → 6.2.6

## [3.8.0](https://github.com/internetarchive/heritrix3/releases/tag/3.8.0) (2025-04-01)

[Download distribution zip](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.8.0/heritrix-3.8.0-dist.zip) (or [tar.gz](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.8.0/heritrix-3.8.0-dist.tar.gz))

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.7.0...3.8.0) | [Javadoc](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-engine/3.8.0/index.html) | [Maven Central](https://search.maven.org/artifact/org.archive.heritrix/heritrix/3.8.0/pom)

#### New Features

- **ExtractorYoutubeDL processArguments**: New option for overriding the default `yt-dlp` process arguments. [#644](https://github.com/internetarchive/heritrix3/pull/644)

#### Fixes

- **Slow tests**: Fixed `ObjectIdentityBdbManualCacheTest` so it no longer fails when running tests with `-DrunSlowTests=true`.
- **Test stability**: Disabled `FetchHTTPTest.testHostHeaderDefaultPort` due to sporadic test failures.
- **Code cleanup**: Fixed some compiler and IDE warnings. Removed unused utility classes (JavaLiterals, LogUtils).

#### Dependency Upgrades

- **amqp-client**: 5.24.0 → 5.25.0
- **beanshell**: 2.0b5 → 2.0b6
- **commons-codec**: 1.17.2 → 1.18.0
- **dnsjava**: 3.6.2 → 3.6.3
- **groovy**: 4.0.24 → 4.0.26
- **gson**: 2.11.0 → 2.12.1
- **jsch**: 0.2.22 → 0.2.24
- **pdfbox**: 3.0.3 → 3.0.4
- **slf4j**: 2.0.16 → 2.0.17
- **spring**: 6.1.16 → 6.2.5

## [3.7.0](https://github.com/internetarchive/heritrix3/releases/tag/3.7.0) (2025-02-03)

[Download distribution zip](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.7.0/heritrix-3.7.0-dist.zip) (or [tar.gz](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.7.0/heritrix-3.7.0-dist.tar.gz))

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.6.0...3.7.0) | [Javadoc](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-engine/3.7.0/index.html) | [Maven Central](https://search.maven.org/artifact/org.archive.heritrix/heritrix/3.7.0/pom)

#### New Features

- **Groovy crawl configs** (experimental): Groovy Bean Definition DSL can now be used as an experimental alternative to
  Spring XML. This enables more terse and human-readable job configuration with inline scripting capabilities. There is
  no user interface for it in this release. For now, you must manually create a [crawler-beans.groovy](https://github.com/internetarchive/heritrix3/blob/4e8bda1/engine/src/main/resources/org/archive/crawler/restlet/profile-crawler-beans.groovy)
  file in your job directory.
  [#632](https://github.com/internetarchive/heritrix3/pull/632)

- **ExtractorHTML obeyRelNofollow**: This option skips extraction of links marked `rel=nofollow`. This is useful for
  avoiding crawler traps on some sites. [#638](https://github.com/internetarchive/heritrix3/pull/638)

#### Fixes

- **Cookie rejected warning**: The slf4j change in 3.6.0 inadvertently caused a previously hidden warning to be logged
  to `job.log` when a server sends a `Set-Cookie` header with a disallowed domain value. This warning is now suppressed
  since it occurs frequently and does not require any action from the crawl operator. [#640](https://github.com/internetarchive/heritrix3/pull/640)

#### Changes

- **Removed fastutil**: A small number of usages of fastutil were replaced with standard library equivalents in
  webarchive-commons and Heritrix. This reduced the Heritrix distribution size from 51 MB to 34 MB. 
  [iipc/webarchive-commons#101](https://github.com/iipc/webarchive-commons/pull/101)

#### Dependency Upgrades

- amqp-client 5.24.0
- commons-codec 1.17.2
- ftpserver-core 1.2.1
- freemarker 2.3.34
- jetty 9.4.57.v20241219
- jsch 0.2.22
- restlet 2.5.0
- spring 6.1.16
- webarchive-commons 1.3.0

## [3.6.0](https://github.com/internetarchive/heritrix3/releases/tag/3.6.0) (2024-10-29)

[Download distribution zip](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.6.0/heritrix-3.6.0-dist.zip) (or [tar.gz](https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/3.6.0/heritrix-3.6.0-dist.tar.gz))

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.5.0...3.6.0) | [Javadoc](https://www.javadoc.io/doc/org.archive.heritrix/heritrix-engine/3.6.0/index.html) | [Maven Central](https://search.maven.org/artifact/org.archive.heritrix/heritrix/3.6.0/pom)

#### Java Compatibility Notice

This release of Heritrix **requires Java 17 or later**.

#### New Features
- **Automatic Checkpoints on Shutdown**: Added `checkpointOnShutdown` option to `CheckpointService` to enable automatic checkpoints if Heritrix is gracefully terminated. [#626](https://github.com/internetarchive/heritrix3/pull/626)
- **Command-Line Checkpoint Selection**: The `--checkpoint` command-line option restarts from a named checkpoint when using the `--run-job` option. [#626](https://github.com/internetarchive/heritrix3/pull/626)
- **ConfigurableExtractorJS forceStrictIfUrlMatchingRegexList**: URLs matching the regular expressions on this list will be processed in strict mode, with only absolute URLs extracted, not  relative ones. [#624](https://github.com/internetarchive/heritrix3/pull/624)

#### Changes
- **Upgraded to Spring Framework 6.1**: The Spring `@Required` annotation has been removed, so it was replaced with a custom implementation to maintain backward compatibility with existing crawl configurations. Spring 6 requires Java 17 so Heritrix does now too. [#625](https://github.com/internetarchive/heritrix3/pull/625)

#### Fixes
- **Manifest Hop Priority**: Links from sitemaps are now given the same priority as normal navigation links. They were incorrectly being prioritized as transitive hops (embeds). [#623](https://github.com/internetarchive/heritrix3/pull/623)
- **SLF4J Logging**: Heritrix now includes `slf4j-jdk14` to eliminate a startup warning message and fix logging for dependencies (such as crawler-commons) that use SLF4J. Heritrix doesn't use SLF4J itself. [#628](https://github.com/internetarchive/heritrix3/pull/628)

#### Dependency Upgrades
- amqp-client 5.23.0
- commons-cli 1.9.0
- commons-codec 1.17.1
- commons-io 2.18.0
- commons-net 3.11.1
- crawler-commons 1.4
- dnsjava 3.6.2
- easymock 5.5.0
- freemarker 2.3.33
- groovy 4.0.24
- gson 2.11.0
- httpcomponents 4.5.14
- java-socks-proxy-server 4.1.2
- java-websocket removed
- jaxb-runtime 4.0.5
- jsch switched to mwiede fork 0.2.21
- junit 4.13.2
- kafka-clients 3.9.0
- kryo 5.6.2
- pdfbox 3.0.3
- slf4j 2.0.16
- spring-framework 6.1.15
- webarchive-commons 1.2.0

## [3.5.0](https://github.com/internetarchive/heritrix3/releases/3.5.0) (2024-10-29)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20240909...3.5.0)

#### Removals
- Removed HBase modules from contrib. [#621](https://github.com/internetarchive/heritrix3/pull/621)

#### Fixes
- ConfigurableExtractorJS: Set default value (false) for strict property. [#612](https://github.com/internetarchive/heritrix3/pull/612)
- ExtractorHTML: Treat `cite` attribute as a navlink instead of embed. [#608](https://github.com/internetarchive/heritrix3/pull/608)
- Building no longer require the builds.archive.org repository. [#614](https://github.com/internetarchive/heritrix3/pull/614)
- Updated to new URL of the restlet repository.

#### Dependency Upgrades
- Removed hbase, joda-time, log4j 
- commons-io 2.14.0
- kafka-clients 3.8.0
- ftpserver-core 1.2.0
- jetty 9.4.56.v20240826
- webarchive-commons 1.1.10

## [3.4.0-20240909](https://github.com/internetarchive/heritrix3/releases/3.4.0-20240909) (2024-09-09)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20220727...3.4.0-20240909)

#### Compatibility Note

Checkpoints and crawl state created with older versions of Heritrix will not be loadable as kryo has been significantly updated. [Replaying the recovery log](https://heritrix.readthedocs.io/en/latest/operating.html#crawl-recovery) may be an alternative in some cases.

#### New Features
- JDK 22 support
- Added `ConfigurableExtractorJS` for more flexible JavaScript extraction. ([#602](https://github.com/internetarchive/heritrix3/issues/602))
- Added `HostnameQueueAssignmentPolicyWithLimits` with optional name length limits. ([#598](https://github.com/internetarchive/heritrix3/issues/598))
- `ExtractorHTML` can now extract more variants of alternative resolution image URLs. ([#605](https://github.com/internetarchive/heritrix3/issues/605))
- `ExtractorHTTP` can now be configured with extra inferred paths ([#597](https://github.com/internetarchive/heritrix3/issues/597))
- `ExtractorYoutubeDL` metadata records can now be optionally logged to crawl.log ([#593](https://github.com/internetarchive/heritrix3/issues/593))

#### Removals
- Removed `ExtractorChrome` from contrib ([#601](https://github.com/internetarchive/heritrix3/issues/601))

#### Fixes
- Reduced false positive speculative URLs from meta tags ([#595](https://github.com/internetarchive/heritrix3/issues/595))
- Fixed BdbModule resource leak on job teardown ([f4280012ae5f23763f1e19d196a245ae49f9b697](https://github.com/internetarchive/heritrix3/commit/f4280012ae5f23763f1e19d196a245ae49f9b697))
- Corrected function name in `ScriptedProcessor` Javadoc. ([#599](https://github.com/internetarchive/heritrix3/issues/599))
- Updated Maven builds to use HTTPS for resolving dependencies.
- Reset CrawlURI status for hasPrerequisite() so that it isn't preserved between attempts ([#600](https://github.com/internetarchive/heritrix3/issues/600))
- Fixed older junit3 tests not being run ([#592](https://github.com/internetarchive/heritrix3/issues/592))
- Increased DiskSpaceMonitor default pause threshold to 8 GiB ([#499](https://github.com/internetarchive/heritrix3/issues/499))
- Stopping logging authentication failures when header is missing ([#539](https://github.com/internetarchive/heritrix3/issues/539))
- Fixed console still showing job running after crash ([#549](https://github.com/internetarchive/heritrix3/issues/549))

#### Dependency Upgrades
- Transitioned `PDFParser` and `ExtractorPDF` to pdfbox ([#575](https://github.com/internetarchive/heritrix3/issues/575))
- Transitioned `ExtractorYoutubeDL` to yt-dlp
- commons-net 3.9.0
- com.rabbitmq:amqp-client 5.18.0
- dnsjava 3.6.0
- groovy 4.0.21
- kryo 5.6.0
- spring-expression 5.3.39

## [3.4.0-20220727](https://github.com/internetarchive/heritrix3/tree/3.4.0-20220727) (2022-07-27)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20210923...3.4.0-20220727)

**Fixed bugs:**

- ExtractorHTML matches srcset attribute case-sensitively [\#477](https://github.com/internetarchive/heritrix3/issues/477)
- Overcrawling due to sitemap links acting like transclusions [\#469](https://github.com/internetarchive/heritrix3/issues/469)
- "java.lang.NoClassDefFoundError: Could not initialize class org.archive.util.CLibrary" on Apple Silicon [\#467](https://github.com/internetarchive/heritrix3/issues/467)
- Heritrix crasching on malformed Content-Length header [\#449](https://github.com/internetarchive/heritrix3/issues/449)
- Java version check throws StringIndexOutOfBoundsException on exact major versions [\#439](https://github.com/internetarchive/heritrix3/issues/439)
- dnsjava NIO selector thread stuck at 100% after terminating job [\#425](https://github.com/internetarchive/heritrix3/issues/425)
- Do not treat all URLs from link/@href tags as embeds. [\#263](https://github.com/internetarchive/heritrix3/issues/263)
- BdbCookieStore  not implemented  iterator at RetryExec  [\#200](https://github.com/internetarchive/heritrix3/issues/200)
- "RIS already open for ToeThread..." exception during https pages crawl over proxy [\#191](https://github.com/internetarchive/heritrix3/issues/191)

**Closed issues:**

- Heritrix not ignoring robots.txt [\#479](https://github.com/internetarchive/heritrix3/issues/479)
- JDK18: ExtractorMultipleRegexTest fails due to Groovy asm incompatiblity [\#473](https://github.com/internetarchive/heritrix3/issues/473)
- Setting of maxLogFileSize in the BDBModule is ineffective [\#464](https://github.com/internetarchive/heritrix3/issues/464)
- Question about memory usage [\#462](https://github.com/internetarchive/heritrix3/issues/462)
- Build failing via maven-assembly-plugin: group id is too big [\#447](https://github.com/internetarchive/heritrix3/issues/447)
- Do not require DNS when using a web proxy [\#211](https://github.com/internetarchive/heritrix3/issues/211)

**Merged pull requests:**

- Bump jsch from 0.1.52 to 0.1.54 in /commons [\#492](https://github.com/internetarchive/heritrix3/pull/492) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump spring-core from 5.3.19 to 5.3.20 in /commons [\#491](https://github.com/internetarchive/heritrix3/pull/491) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump jsch from 0.1.52 to 0.1.54 in /modules [\#490](https://github.com/internetarchive/heritrix3/pull/490) ([dependabot[bot]](https://github.com/apps/dependabot))
- Add robotsTxtOnly robots policy [\#489](https://github.com/internetarchive/heritrix3/pull/489) ([ato](https://github.com/ato))
- Removed a potential NPE in hashCode method to CrawlURI which was fata… [\#488](https://github.com/internetarchive/heritrix3/pull/488) ([csrster](https://github.com/csrster))
- Bump gson from 2.8.6 to 2.8.9 in /contrib [\#486](https://github.com/internetarchive/heritrix3/pull/486) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump spring-core from 5.3.18 to 5.3.19 in /commons [\#480](https://github.com/internetarchive/heritrix3/pull/480) ([dependabot[bot]](https://github.com/apps/dependabot))
- ExtractorHTML: Fix srcset by normalizing elementContext\(\) to lowercase [\#478](https://github.com/internetarchive/heritrix3/pull/478) ([ato](https://github.com/ato))
- Issue211: support dns over https if local DNS is not working / available [\#476](https://github.com/internetarchive/heritrix3/pull/476) ([ClemensRobbenhaar](https://github.com/ClemensRobbenhaar))
- Bump spring-beans from 5.3.14 to 5.3.18 in /commons [\#475](https://github.com/internetarchive/heritrix3/pull/475) ([dependabot[bot]](https://github.com/apps/dependabot))
- TransclusionDecideRule: Don't treat sitemap links \('M'\) as transclusions [\#470](https://github.com/internetarchive/heritrix3/pull/470) ([ato](https://github.com/ato))
- Use Files.createLink\(\) and Files.createSymbolicLink\(\) instead of JNA [\#468](https://github.com/internetarchive/heritrix3/pull/468) ([ato](https://github.com/ato))
- Fix name of parameter in setMaxLogFileSize [\#465](https://github.com/internetarchive/heritrix3/pull/465) ([ClemensRobbenhaar](https://github.com/ClemensRobbenhaar))
- Add conf to not allow TLDs as seeds found via redirect from other seeds [\#461](https://github.com/internetarchive/heritrix3/pull/461) ([kris-sigur](https://github.com/kris-sigur))
- Bump spring-core from 5.3.3 to 5.3.14 in /commons [\#460](https://github.com/internetarchive/heritrix3/pull/460) ([dependabot[bot]](https://github.com/apps/dependabot))
- ExtractorHTML: Determine LINK tag type by parsing REL attribute [\#459](https://github.com/internetarchive/heritrix3/pull/459) ([ato](https://github.com/ato))
- Fix issue\#191: "RIS already open for ToeThread..." exception during https pages crawl over proxy [\#457](https://github.com/internetarchive/heritrix3/pull/457) ([ClemensRobbenhaar](https://github.com/ClemensRobbenhaar))
- FetchHTTP: Handle null characters in the Content-Length header [\#452](https://github.com/internetarchive/heritrix3/pull/452) ([ato](https://github.com/ato))
- Add Dockerfile [\#450](https://github.com/internetarchive/heritrix3/pull/450) ([Querela](https://github.com/Querela))
- Resolve gid too big [\#448](https://github.com/internetarchive/heritrix3/pull/448) ([ldko](https://github.com/ldko))
- FetchDNS: Keep dnsjava selector thread out of ToePool [\#444](https://github.com/internetarchive/heritrix3/pull/444) ([ato](https://github.com/ato))
- Enabled configurable url-matching and extraction for sitemaps. [\#441](https://github.com/internetarchive/heritrix3/pull/441) ([csrster](https://github.com/csrster))

## [3.4.0-20210923](https://github.com/internetarchive/heritrix3/tree/3.4.0-20210923) (2021-09-23)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20210803...3.4.0-20210923)

**Fixed bugs:**

- ExtractorChrome exception on images as data uris [\#430](https://github.com/internetarchive/heritrix3/issues/430)
- Thread-safely issues with the CookieStore [\#427](https://github.com/internetarchive/heritrix3/issues/427)
- Cookies being sent to wrong site [\#259](https://github.com/internetarchive/heritrix3/issues/259)

**Closed issues:**

- Trying to get in touch regarding a security issue [\#429](https://github.com/internetarchive/heritrix3/issues/429)
- Upgrade HTTP Client to 4.5.x [\#245](https://github.com/internetarchive/heritrix3/issues/245)

**Merged pull requests:**

- Add safer cookie iteration [\#434](https://github.com/internetarchive/heritrix3/pull/434) ([anjackson](https://github.com/anjackson))
- ExtractorChrome bug fixes [\#431](https://github.com/internetarchive/heritrix3/pull/431) ([ato](https://github.com/ato))
- UI: Refactor duplicate template rendering code [\#424](https://github.com/internetarchive/heritrix3/pull/424) ([ato](https://github.com/ato))

## [3.4.0-20210803](https://github.com/internetarchive/heritrix3/tree/3.4.0-20210803) (2021-08-03)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20210621...3.4.0-20210803)

**Fixed bugs:**

- Jobs can get stuck STOPPING with "Interrupt leaving unfinished CrawlURI" [\#420](https://github.com/internetarchive/heritrix3/issues/420)
- Groovy version is incompatible with JDK 16+ [\#419](https://github.com/internetarchive/heritrix3/issues/419)
- module java.base does not export sun.security.tools.keytool to unnamed module @1ece4432 [\#417](https://github.com/internetarchive/heritrix3/issues/417)
- Distribution package has broken filesystem permissions [\#413](https://github.com/internetarchive/heritrix3/issues/413)
- Add WARC-IP-Address header to WARCWriterChainProcessor [\#396](https://github.com/internetarchive/heritrix3/issues/396)

**Merged pull requests:**

- Don't extract data URIs [\#423](https://github.com/internetarchive/heritrix3/pull/423) ([ato](https://github.com/ato))
- ToeThread: ensure currentCuri is finished before exiting [\#421](https://github.com/internetarchive/heritrix3/pull/421) ([ato](https://github.com/ato))
- JDK 16 compatibility [\#418](https://github.com/internetarchive/heritrix3/pull/418) ([ato](https://github.com/ato))
- ExtractorChrome: reduce request duplication between browser and frontier [\#416](https://github.com/internetarchive/heritrix3/pull/416) ([ato](https://github.com/ato))
- Upgrade maven-assembly-plugin to 3.3.0 to fix file permissions [\#414](https://github.com/internetarchive/heritrix3/pull/414) ([ato](https://github.com/ato))
- ExtractorChrome: Capture requests made by the browser [\#411](https://github.com/internetarchive/heritrix3/pull/411) ([ato](https://github.com/ato))
- Warc writer stats fixes [\#410](https://github.com/internetarchive/heritrix3/pull/410) ([ato](https://github.com/ato))
- Fix WARC-IP-Address and use a common server-ip CrawlURI attribute for all protocols [\#409](https://github.com/internetarchive/heritrix3/pull/409) ([ato](https://github.com/ato))
- Add basic syntax highlighting to the crawl.log viewer [\#408](https://github.com/internetarchive/heritrix3/pull/408) ([ato](https://github.com/ato))
- Fix a couple of boring maven warnings [\#407](https://github.com/internetarchive/heritrix3/pull/407) ([ato](https://github.com/ato))
- Fix and document the -r option which runs a named job on startup [\#406](https://github.com/internetarchive/heritrix3/pull/406) ([ato](https://github.com/ato))
- Speed up test suite [\#405](https://github.com/internetarchive/heritrix3/pull/405) ([ato](https://github.com/ato))
- Switch from Travis CI to Github Actions [\#404](https://github.com/internetarchive/heritrix3/pull/404) ([ato](https://github.com/ato))
- Add ExtractorChrome to contrib [\#403](https://github.com/internetarchive/heritrix3/pull/403) ([ato](https://github.com/ato))
- Upgrade httpclient to 4.5 [\#397](https://github.com/internetarchive/heritrix3/pull/397) ([anjackson](https://github.com/anjackson))

## [3.4.0-20210621](https://github.com/internetarchive/heritrix3/tree/3.4.0-20210621) (2021-06-21)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20210618...3.4.0-20210621)

**Merged pull requests:**

- Remove dependency on mg4j [\#402](https://github.com/internetarchive/heritrix3/pull/402) ([ato](https://github.com/ato))
- Graceful UI shutdown [\#401](https://github.com/internetarchive/heritrix3/pull/401) ([kris-sigur](https://github.com/kris-sigur))
- Remove unnecessary fiddling with VIA path in ExtractorRobotsTxt [\#400](https://github.com/internetarchive/heritrix3/pull/400) ([kris-sigur](https://github.com/kris-sigur))

## [3.4.0-20210618](https://github.com/internetarchive/heritrix3/tree/3.4.0-20210618) (2021-06-18)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20210617...3.4.0-20210618)

**Merged pull requests:**

- Switch to properties that enforce Java 8 compatibility. [\#399](https://github.com/internetarchive/heritrix3/pull/399) ([anjackson](https://github.com/anjackson))

## [3.4.0-20210617](https://github.com/internetarchive/heritrix3/tree/3.4.0-20210617) (2021-06-17)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20210527...3.4.0-20210617)

**Closed issues:**

- Ensure valid checkpoints can be created when recovering from errors [\#392](https://github.com/internetarchive/heritrix3/issues/392)

**Merged pull requests:**

- Annotate nested sitemap links as sitemaps [\#398](https://github.com/internetarchive/heritrix3/pull/398) ([kris-sigur](https://github.com/kris-sigur))
- Update AMPQ client library to address security warning. [\#394](https://github.com/internetarchive/heritrix3/pull/394) ([anjackson](https://github.com/anjackson))
- Only update last checkpoint stats if the checkpoint completed, for \#392. [\#393](https://github.com/internetarchive/heritrix3/pull/393) ([anjackson](https://github.com/anjackson))
- Sync changelog with release. [\#391](https://github.com/internetarchive/heritrix3/pull/391) ([anjackson](https://github.com/anjackson))

## [3.4.0-20210527](https://github.com/internetarchive/heritrix3/tree/3.4.0-20210527) (2021-05-27)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20200518...3.4.0-20210527)

**Fixed bugs:**

- Upgrade dnsjava to cope with Azure CNAME lists [\#344](https://github.com/internetarchive/heritrix3/issues/344)
- Spring instantiation broken for MatchesListRegexDecideRule [\#337](https://github.com/internetarchive/heritrix3/issues/337)

**Closed issues:**

- Browse Bean template errors on editing Regex Pattern [\#378](https://github.com/internetarchive/heritrix3/issues/378)
- BrowseBeans broken under Java 11 [\#376](https://github.com/internetarchive/heritrix3/issues/376)
- Usable variables, e.g. for warcWriter template [\#363](https://github.com/internetarchive/heritrix3/issues/363)
- Heritrix 3.3 out-of-the-box archives pages with meta noindex [\#351](https://github.com/internetarchive/heritrix3/issues/351)
- Error Binding hostname or ip to Web UI [\#339](https://github.com/internetarchive/heritrix3/issues/339)
- Add support for the SFTP protocol [\#319](https://github.com/internetarchive/heritrix3/issues/319)
- java.nio.BufferUnderflowException in BdbMultipleWorkQueues.get [\#278](https://github.com/internetarchive/heritrix3/issues/278)
- Upgrade dependencies to spring 4.x.x [\#254](https://github.com/internetarchive/heritrix3/issues/254)

**Merged pull requests:**

- Update changelog. [\#390](https://github.com/internetarchive/heritrix3/pull/390) ([anjackson](https://github.com/anjackson))
- Update dependencies 2021 05 26 [\#389](https://github.com/internetarchive/heritrix3/pull/389) ([anjackson](https://github.com/anjackson))
- Bring changelog up to date [\#386](https://github.com/internetarchive/heritrix3/pull/386) ([anjackson](https://github.com/anjackson))
- Allow tuning of BDB-JE evictor and cleaner threads. [\#384](https://github.com/internetarchive/heritrix3/pull/384) ([anjackson](https://github.com/anjackson))
- Update to latest version of dnsjava, for \#344 [\#383](https://github.com/internetarchive/heritrix3/pull/383) ([anjackson](https://github.com/anjackson))
- Avoid error when bean properties have no url available [\#379](https://github.com/internetarchive/heritrix3/pull/379) ([ldko](https://github.com/ldko))
- Handle empty Optionals when browsing beans [\#377](https://github.com/internetarchive/heritrix3/pull/377) ([ato](https://github.com/ato))
- Fix misspell in comments [\#368](https://github.com/internetarchive/heritrix3/pull/368) ([webdev4422](https://github.com/webdev4422))
- Upgrade to Spring 5.3.3 [\#366](https://github.com/internetarchive/heritrix3/pull/366) ([ato](https://github.com/ato))
- ait youtube-dl options [\#359](https://github.com/internetarchive/heritrix3/pull/359) ([galgeek](https://github.com/galgeek))
- Strip quotes from URL value. [\#352](https://github.com/internetarchive/heritrix3/pull/352) ([BitBaron](https://github.com/BitBaron))
- Fixes leaky file handles [\#348](https://github.com/internetarchive/heritrix3/pull/348) ([adam-miller](https://github.com/adam-miller))
- youtube-dl --no-playlist [\#341](https://github.com/internetarchive/heritrix3/pull/341) ([galgeek](https://github.com/galgeek))
- Revert "Warc convention for storing ftp responses has been to use a WARC reso…" [\#336](https://github.com/internetarchive/heritrix3/pull/336) ([ato](https://github.com/ato))
- Fixes extractor multiple regex matcher recycle [\#335](https://github.com/internetarchive/heritrix3/pull/335) ([adam-miller](https://github.com/adam-miller))
- Warc convention for storing ftp responses has been to use a WARC reso… [\#334](https://github.com/internetarchive/heritrix3/pull/334) ([adam-miller](https://github.com/adam-miller))
- Remove deprecated sudo setting. [\#333](https://github.com/internetarchive/heritrix3/pull/333) ([dengliming](https://github.com/dengliming))
- don't youtubedl receivedFromAMQP [\#330](https://github.com/internetarchive/heritrix3/pull/330) ([galgeek](https://github.com/galgeek))
- youtube-dl no cache dir [\#329](https://github.com/internetarchive/heritrix3/pull/329) ([galgeek](https://github.com/galgeek))
- best medium-ish size [\#327](https://github.com/internetarchive/heritrix3/pull/327) ([galgeek](https://github.com/galgeek))
- Recycle the regex Matcher after use. [\#317](https://github.com/internetarchive/heritrix3/pull/317) ([adam-miller](https://github.com/adam-miller))
- Support for extracting URLs in sitemaps [\#262](https://github.com/internetarchive/heritrix3/pull/262) ([kris-sigur](https://github.com/kris-sigur))

## [3.4.0-20200518](https://github.com/internetarchive/heritrix3/tree/3.4.0-20200518) (2020-05-18)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20200304...3.4.0-20200518)

**Closed issues:**

- Cannot find class \[ExtractorYoutubeDL\] [\#322](https://github.com/internetarchive/heritrix3/issues/322)
- Checkpoints 'spoiled' when used to resume crawls [\#277](https://github.com/internetarchive/heritrix3/issues/277)

**Merged pull requests:**

- Fix match result is always false in MatchesListRegexDecideRule [\#328](https://github.com/internetarchive/heritrix3/pull/328) ([morokosi](https://github.com/morokosi))
- Add real crawlStatus in the crawlReport [\#326](https://github.com/internetarchive/heritrix3/pull/326) ([clawia](https://github.com/clawia))
- youtube-dl: request best medium-ish size format [\#325](https://github.com/internetarchive/heritrix3/pull/325) ([galgeek](https://github.com/galgeek))
- Add parsing for HTML tags \(data-\*\) [\#323](https://github.com/internetarchive/heritrix3/pull/323) ([clawia](https://github.com/clawia))
- Add support for the SFTP protocol [\#320](https://github.com/internetarchive/heritrix3/pull/320) ([bnfleb](https://github.com/bnfleb))

## [3.4.0-20200304](https://github.com/internetarchive/heritrix3/tree/3.4.0-20200304) (2020-03-04)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20190418...3.4.0-20200304)

**Fixed bugs:**

- exception logged when opening/saving crawler-beans.cxml via web interface editor [\#305](https://github.com/internetarchive/heritrix3/issues/305)
- Java interface text editor error when saving crawler-beans.cxml [\#293](https://github.com/internetarchive/heritrix3/issues/293)
- Unable to upload crawler-beans.cxml with curl [\#282](https://github.com/internetarchive/heritrix3/issues/282)
- CookieStoreTest.testConcurrentLoad fails randomly [\#274](https://github.com/internetarchive/heritrix3/issues/274)

**Closed issues:**

- Contrib project has a maven dependency with an older version of guava library.  [\#311](https://github.com/internetarchive/heritrix3/issues/311)
- BloomFilter64bitTest is slow [\#299](https://github.com/internetarchive/heritrix3/issues/299)
- ObjectIdentityBdbManualCacheTest is slow [\#297](https://github.com/internetarchive/heritrix3/issues/297)
- HTTPS console inaccessible via browser [\#279](https://github.com/internetarchive/heritrix3/issues/279)
- JDK11 support: ssl errors from console [\#275](https://github.com/internetarchive/heritrix3/issues/275)
- JDK11 support: FetchHTTPTest: ssl handshake\_failure [\#268](https://github.com/internetarchive/heritrix3/issues/268)
- JDK11 support: org.archive.util.ObjectIdentityBdbCacheTest failures [\#267](https://github.com/internetarchive/heritrix3/issues/267)
- JDK11 support: ClassNotFoundException: javax.transaction.xa.Xid [\#266](https://github.com/internetarchive/heritrix3/issues/266)
- JDK11 support: tools.jar [\#265](https://github.com/internetarchive/heritrix3/issues/265)
- JDK11 support: jaxb [\#264](https://github.com/internetarchive/heritrix3/issues/264)

**Merged pull requests:**

- Use the Wayback Machine to repair a link to Oracle docs. [\#315](https://github.com/internetarchive/heritrix3/pull/315) ([anjackson](https://github.com/anjackson))
- Utilize the `d` parameter [\#314](https://github.com/internetarchive/heritrix3/pull/314) ([hennekey](https://github.com/hennekey))
- Exclude hbase-client's guava 12 transitive dependency [\#312](https://github.com/internetarchive/heritrix3/pull/312) ([ato](https://github.com/ato))
- Fix stream closed exception for Paged view [\#308](https://github.com/internetarchive/heritrix3/pull/308) ([ldko](https://github.com/ldko))
- Fix stream closed exception by not closing output stream [\#306](https://github.com/internetarchive/heritrix3/pull/306) ([ato](https://github.com/ato))
- Replace custom Base32 encoding [\#304](https://github.com/internetarchive/heritrix3/pull/304) ([hennekey](https://github.com/hennekey))
- Replace constant with accessor methods [\#303](https://github.com/internetarchive/heritrix3/pull/303) ([hennekey](https://github.com/hennekey))
- limit ExtractorYoutubeDL heap usage [\#302](https://github.com/internetarchive/heritrix3/pull/302) ([nlevitt](https://github.com/nlevitt))
- fix logging config [\#301](https://github.com/internetarchive/heritrix3/pull/301) ([nlevitt](https://github.com/nlevitt))
- Use Guice instead of custom bloom filter implementation [\#300](https://github.com/internetarchive/heritrix3/pull/300) ([hennekey](https://github.com/hennekey))
- Speed up ObjectIdentityBdbManualCacheTest [\#298](https://github.com/internetarchive/heritrix3/pull/298) ([hennekey](https://github.com/hennekey))
- Set JUnit version to latest [\#296](https://github.com/internetarchive/heritrix3/pull/296) ([hennekey](https://github.com/hennekey))
- Disable test that connects to wwwb-dedup.us.archive.org [\#295](https://github.com/internetarchive/heritrix3/pull/295) ([ato](https://github.com/ato))
- Fix 'Method Not Allowed' on POST of config editor form [\#294](https://github.com/internetarchive/heritrix3/pull/294) ([ato](https://github.com/ato))
- Crawltrap regex timeout [\#290](https://github.com/internetarchive/heritrix3/pull/290) ([csrster](https://github.com/csrster))
- Bdb frontier access [\#289](https://github.com/internetarchive/heritrix3/pull/289) ([csrster](https://github.com/csrster))
- Attempt to filter out embedded images. [\#288](https://github.com/internetarchive/heritrix3/pull/288) ([csrster](https://github.com/csrster))
- change trough dedup `date` type to varchar. [\#287](https://github.com/internetarchive/heritrix3/pull/287) ([nlevitt](https://github.com/nlevitt))
- Add support for forced queue assignment and parallel queues [\#286](https://github.com/internetarchive/heritrix3/pull/286) ([adam-miller](https://github.com/adam-miller))
- Warc writer chain [\#285](https://github.com/internetarchive/heritrix3/pull/285) ([nlevitt](https://github.com/nlevitt))
- Fix jobdir PUT [\#283](https://github.com/internetarchive/heritrix3/pull/283) ([ato](https://github.com/ato))
- Upgrade BDB JE to version 7.5.11 - IMPORTANT CHANGE [\#281](https://github.com/internetarchive/heritrix3/pull/281) ([anjackson](https://github.com/anjackson))
- Mitigate random CookieStore.testConcurrentLoad test failures [\#280](https://github.com/internetarchive/heritrix3/pull/280) ([ato](https://github.com/ato))
- JDK11 support: upgrade to Jetty 9.4.19, Restlet 2.4.0 and drop JDK 7 support [\#276](https://github.com/internetarchive/heritrix3/pull/276) ([ato](https://github.com/ato))
- JDK11 support: remove unused class ObjectIdentityBdbCache and tests [\#273](https://github.com/internetarchive/heritrix3/pull/273) ([ato](https://github.com/ato))
- JDK11 support: upgrade maven-surefire-plugin to 2.22.2 [\#272](https://github.com/internetarchive/heritrix3/pull/272) ([ato](https://github.com/ato))
- JDK11 support: exclude tools.jar from hbase-client dependency [\#271](https://github.com/internetarchive/heritrix3/pull/271) ([ato](https://github.com/ato))
- Travis fixes [\#270](https://github.com/internetarchive/heritrix3/pull/270) ([ato](https://github.com/ato))
- JDK11 support: explicitly depend on JAXB [\#269](https://github.com/internetarchive/heritrix3/pull/269) ([ato](https://github.com/ato))
- WIP: ExtractorYoutubeDL [\#257](https://github.com/internetarchive/heritrix3/pull/257) ([nlevitt](https://github.com/nlevitt))
- Update README and add LICENSE.txt [\#256](https://github.com/internetarchive/heritrix3/pull/256) ([ruebot](https://github.com/ruebot))

## [3.4.0-20190418](https://github.com/internetarchive/heritrix3/tree/3.4.0-20190418) (2019-04-18)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20190207...3.4.0-20190418)

**Fixed bugs:**

- Invalid format exception in scanJobLog [\#239](https://github.com/internetarchive/heritrix3/issues/239)
- Domain name lookup failures get cached forever [\#234](https://github.com/internetarchive/heritrix3/issues/234)
- Allow failed lookups to expire, for \#234. [\#235](https://github.com/internetarchive/heritrix3/pull/235) ([anjackson](https://github.com/anjackson))

**Closed issues:**

- Failed DNS requests remain enqueued [\#252](https://github.com/internetarchive/heritrix3/issues/252)
- SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder" [\#236](https://github.com/internetarchive/heritrix3/issues/236)
- Make FetchHistoryProcessor 304 handler more robust [\#229](https://github.com/internetarchive/heritrix3/issues/229)
- ToeThread death when using HighestUriPrecedenceProvider [\#221](https://github.com/internetarchive/heritrix3/issues/221)
- Google Drive robots.txt broken [\#193](https://github.com/internetarchive/heritrix3/issues/193)

**Merged pull requests:**

- set of frontier management changes to support CrawlHQ module [\#253](https://github.com/internetarchive/heritrix3/pull/253) ([dvanduzer](https://github.com/dvanduzer))
- fix some trough dedup bugs [\#251](https://github.com/internetarchive/heritrix3/pull/251) ([nlevitt](https://github.com/nlevitt))
- Remove suffix from warcWriter since it is no longer used. [\#249](https://github.com/internetarchive/heritrix3/pull/249) ([ruebot](https://github.com/ruebot))
- Revert "Upgrade httpclient to 4.5.7 and handle cookies more compliantly" [\#248](https://github.com/internetarchive/heritrix3/pull/248) ([ato](https://github.com/ato))
- Upgrade httpclient to 4.5.7 and handle cookies more compliantly [\#246](https://github.com/internetarchive/heritrix3/pull/246) ([anjackson](https://github.com/anjackson))
- Update README.md [\#244](https://github.com/internetarchive/heritrix3/pull/244) ([mikeizbicki](https://github.com/mikeizbicki))
- Handle commas more compliantly when parsing srcset [\#243](https://github.com/internetarchive/heritrix3/pull/243) ([ato](https://github.com/ato))
- Trough dedup [\#242](https://github.com/internetarchive/heritrix3/pull/242) ([nlevitt](https://github.com/nlevitt))
- Ensure we start parsing full lines, for \#239. [\#240](https://github.com/internetarchive/heritrix3/pull/240) ([anjackson](https://github.com/anjackson))
- Add CHANGELOG; address \#233. [\#238](https://github.com/internetarchive/heritrix3/pull/238) ([ruebot](https://github.com/ruebot))

## [3.4.0-20190207](https://github.com/internetarchive/heritrix3/tree/3.4.0-20190207) (2019-02-07)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.4.0-20190205...3.4.0-20190207)

**Fixed bugs:**

- Add checks to guard against server sending 304 in error [\#230](https://github.com/internetarchive/heritrix3/pull/230) ([anjackson](https://github.com/anjackson))

**Merged pull requests:**

- Add synchronized statements for internetarchive/heritrix3\#221. [\#231](https://github.com/internetarchive/heritrix3/pull/231) ([anjackson](https://github.com/anjackson))

## [3.4.0-20190205](https://github.com/internetarchive/heritrix3/tree/3.4.0-20190205) (2019-02-05)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.2.0...3.4.0-20190205)

**Fixed bugs:**

- HTML extractor does not handle the base href correctly when it's relative [\#208](https://github.com/internetarchive/heritrix3/issues/208)
- Heritrix3 \(including pre-built binaries\) Fails to Bootstrap with Java8 due to Changes in Java stdlib [\#176](https://github.com/internetarchive/heritrix3/issues/176)
- Heritrix3 Fails to Build from Source [\#175](https://github.com/internetarchive/heritrix3/issues/175)
- Missing OneLineSimpleLayout class file [\#173](https://github.com/internetarchive/heritrix3/issues/173)

**Closed issues:**

- BdbFrontier thread safety [\#212](https://github.com/internetarchive/heritrix3/issues/212)
- HTTP response only results in garbage bytes [\#206](https://github.com/internetarchive/heritrix3/issues/206)
- Possibly stalled crawl [\#203](https://github.com/internetarchive/heritrix3/issues/203)
- Where do i find the crawled information \(Contents\) after crawling is completed  [\#199](https://github.com/internetarchive/heritrix3/issues/199)
- `-j` option can'not handle spaces in directory names? [\#182](https://github.com/internetarchive/heritrix3/issues/182)
- heritrix doesn't scrape rewrite srcset urls correctly [\#177](https://github.com/internetarchive/heritrix3/issues/177)
- Possible race-condition when first using the WARC writers? [\#167](https://github.com/internetarchive/heritrix3/issues/167)
- can you integration with spring boot [\#162](https://github.com/internetarchive/heritrix3/issues/162)
- Noisy alerts about 401s without auth challenge  [\#158](https://github.com/internetarchive/heritrix3/issues/158)
- Can't see all beans in scripts [\#157](https://github.com/internetarchive/heritrix3/issues/157)
- How to configure warcWriter with MirrorWriter?  [\#156](https://github.com/internetarchive/heritrix3/issues/156)
- Requesting inaccurate paths from js causes routing errors [\#155](https://github.com/internetarchive/heritrix3/issues/155)

**Merged pull requests:**

- do not checkpoint if crawl job has not started [\#227](https://github.com/internetarchive/heritrix3/pull/227) ([nlevitt](https://github.com/nlevitt))
- namespace scope log logger to crawl job [\#226](https://github.com/internetarchive/heritrix3/pull/226) ([nlevitt](https://github.com/nlevitt))
- un-threadlocal the HConnection [\#224](https://github.com/internetarchive/heritrix3/pull/224) ([nlevitt](https://github.com/nlevitt))
- reset HBaseAdmin on error [\#223](https://github.com/internetarchive/heritrix3/pull/223) ([nlevitt](https://github.com/nlevitt))
- keep trying to start up hbase dedup forever [\#222](https://github.com/internetarchive/heritrix3/pull/222) ([nlevitt](https://github.com/nlevitt))
- implement PredicatedDecideRule.onlyDecision\(\) [\#220](https://github.com/internetarchive/heritrix3/pull/220) ([nlevitt](https://github.com/nlevitt))
- use non-deprecated hbase api [\#219](https://github.com/internetarchive/heritrix3/pull/219) ([nlevitt](https://github.com/nlevitt))
- Correct spelling mistakes. [\#218](https://github.com/internetarchive/heritrix3/pull/218) ([EdwardBetts](https://github.com/EdwardBetts))
- Update API with note about checkpoint launching. [\#217](https://github.com/internetarchive/heritrix3/pull/217) ([anjackson](https://github.com/anjackson))
- Extend API to simplify using the latest checkpoint [\#215](https://github.com/internetarchive/heritrix3/pull/215) ([anjackson](https://github.com/anjackson))
- Ensure frontier work queues are updated safely across threads. [\#213](https://github.com/internetarchive/heritrix3/pull/213) ([anjackson](https://github.com/anjackson))
- fix exception starting DecideRuleSequence logging [\#210](https://github.com/internetarchive/heritrix3/pull/210) ([nlevitt](https://github.com/nlevitt))
- HtmlExtractor: allow relative hrefs in the base element [\#209](https://github.com/internetarchive/heritrix3/pull/209) ([anjackson](https://github.com/anjackson))
- Fix link to User Guide [\#207](https://github.com/internetarchive/heritrix3/pull/207) ([maurice-schleussinger](https://github.com/maurice-schleussinger))
- Add parameter to allow even distribution for parallel queues. [\#205](https://github.com/internetarchive/heritrix3/pull/205) ([adam-miller](https://github.com/adam-miller))
- catch exceptions scoping outlinks to stop them from derailing process… [\#197](https://github.com/internetarchive/heritrix3/pull/197) ([nlevitt](https://github.com/nlevitt))
- fix for test failures in a workspace on NFS-mounted filesystem [\#196](https://github.com/internetarchive/heritrix3/pull/196) ([kngenie](https://github.com/kngenie))
- limit max size of form input [\#194](https://github.com/internetarchive/heritrix3/pull/194) ([galgeek](https://github.com/galgeek))
- Enforce robots.txt character limit per char not per line [\#192](https://github.com/internetarchive/heritrix3/pull/192) ([ato](https://github.com/ato))
- Allow JavaDNS to be disabled as part of resolving outstanding build and test issues [\#190](https://github.com/internetarchive/heritrix3/pull/190) ([anjackson](https://github.com/anjackson))
- WARCLimitEnforcer.java - Add support for multiple warc writers. [\#189](https://github.com/internetarchive/heritrix3/pull/189) ([adam-miller](https://github.com/adam-miller))
- treat a failed fetch \(e.g. socket timeout\) of robots.txt the same way… [\#187](https://github.com/internetarchive/heritrix3/pull/187) ([nlevitt](https://github.com/nlevitt))
- reduce batch size to 400 and avoid ridiculously long log lines [\#186](https://github.com/internetarchive/heritrix3/pull/186) ([nlevitt](https://github.com/nlevitt))
- escape strings in sql posted to trough [\#185](https://github.com/internetarchive/heritrix3/pull/185) ([nlevitt](https://github.com/nlevitt))
- trough feed [\#180](https://github.com/internetarchive/heritrix3/pull/180) ([nlevitt](https://github.com/nlevitt))
- Add parsing for srcset attributes [\#179](https://github.com/internetarchive/heritrix3/pull/179) ([BitBaron](https://github.com/BitBaron))
- KafkaCrawlLogFeed had been using lots of heap because each callback i… [\#178](https://github.com/internetarchive/heritrix3/pull/178) ([nlevitt](https://github.com/nlevitt))
- AMQP fine control [\#171](https://github.com/internetarchive/heritrix3/pull/171) ([anjackson](https://github.com/anjackson))
- fix for race-condition when first using the WARC writers https://gith… [\#168](https://github.com/internetarchive/heritrix3/pull/168) ([nlevitt](https://github.com/nlevitt))
- Don't wait to receive Umbra urls if Heritrix sends no url to Umbra [\#166](https://github.com/internetarchive/heritrix3/pull/166) ([galgeek](https://github.com/galgeek))
- AMQP URL Waiter [\#165](https://github.com/internetarchive/heritrix3/pull/165) ([galgeek](https://github.com/galgeek))
- Fixes for apparent build errors \(extends \#154\) [\#164](https://github.com/internetarchive/heritrix3/pull/164) ([nlevitt](https://github.com/nlevitt))
- Kafka 0.9 [\#163](https://github.com/internetarchive/heritrix3/pull/163) ([nlevitt](https://github.com/nlevitt))
- No link extraction on URI not successfully downloaded [\#161](https://github.com/internetarchive/heritrix3/pull/161) ([kris-sigur](https://github.com/kris-sigur))
- Fixes issue \#158 : Noisy alerts about 401s without auth challenge [\#159](https://github.com/internetarchive/heritrix3/pull/159) ([kris-sigur](https://github.com/kris-sigur))
- Fixes for apparent build errors [\#154](https://github.com/internetarchive/heritrix3/pull/154) ([anjackson](https://github.com/anjackson))
- Switch to Java 7 [\#152](https://github.com/internetarchive/heritrix3/pull/152) ([anjackson](https://github.com/anjackson))
- Make Content-Location header url INFERRED not REFFER hop type since C… [\#151](https://github.com/internetarchive/heritrix3/pull/151) ([vonrosen](https://github.com/vonrosen))
- various changes to amqp publish and receive [\#150](https://github.com/internetarchive/heritrix3/pull/150) ([nlevitt](https://github.com/nlevitt))
- Update to ExtractorHTML.java for cond. comments [\#149](https://github.com/internetarchive/heritrix3/pull/149) ([eleclerc](https://github.com/eleclerc))
- Don't canonicalize source tag so that SourceSeedDecideRule will work.… [\#148](https://github.com/internetarchive/heritrix3/pull/148) ([vonrosen](https://github.com/vonrosen))
- More fixes for multipart form submission [\#146](https://github.com/internetarchive/heritrix3/pull/146) ([vonrosen](https://github.com/vonrosen))
- Make some urls with whitespace acceptable to JavaScript extractor. [\#145](https://github.com/internetarchive/heritrix3/pull/145) ([vonrosen](https://github.com/vonrosen))
- run received urls through the candidates processor, to check scope an… [\#144](https://github.com/internetarchive/heritrix3/pull/144) ([nlevitt](https://github.com/nlevitt))
- handle login forms with \<input type="text"\> fields in addition to use… [\#143](https://github.com/internetarchive/heritrix3/pull/143) ([nlevitt](https://github.com/nlevitt))
- Form login multipart [\#142](https://github.com/internetarchive/heritrix3/pull/142) ([nlevitt](https://github.com/nlevitt))
- Disable SNI for a request if that request failed due to an SNI error … [\#141](https://github.com/internetarchive/heritrix3/pull/141) ([vonrosen](https://github.com/vonrosen))
- handle multiple clauses for same user agent in robots.txt [\#139](https://github.com/internetarchive/heritrix3/pull/139) ([nlevitt](https://github.com/nlevitt))
- crawl level and host level limits on \*novel\* \(not deduplicated\) bytes and urls [\#138](https://github.com/internetarchive/heritrix3/pull/138) ([nlevitt](https://github.com/nlevitt))
- SourceSeedDecideRule, SeedLimitsEnforcer [\#137](https://github.com/internetarchive/heritrix3/pull/137) ([nlevitt](https://github.com/nlevitt))
- Register seeds send in via AMQP [\#136](https://github.com/internetarchive/heritrix3/pull/136) ([anjackson](https://github.com/anjackson))
- Allow KnowledgableExtractorJS to parse out youtube watch from youtube… [\#135](https://github.com/internetarchive/heritrix3/pull/135) ([vonrosen](https://github.com/vonrosen))
- Add maximum to number of cookies to store for domain to BdbCookieStore [\#133](https://github.com/internetarchive/heritrix3/pull/133) ([vonrosen](https://github.com/vonrosen))
- try very hard to start url consumer, and therefore bind the queue to … [\#132](https://github.com/internetarchive/heritrix3/pull/132) ([nlevitt](https://github.com/nlevitt))
- set isRunning=true so that stop\(\) gets called to avoid leaking connec… [\#131](https://github.com/internetarchive/heritrix3/pull/131) ([nlevitt](https://github.com/nlevitt))
- catch exceptions and log error in StatisticsTracker.run\(\), to make su… [\#130](https://github.com/internetarchive/heritrix3/pull/130) ([nlevitt](https://github.com/nlevitt))
- load keytool utility main class dynamically, trying both the old and … [\#129](https://github.com/internetarchive/heritrix3/pull/129) ([nlevitt](https://github.com/nlevitt))
- AMQPUrlReceiver changes to support RabbitMQ \>= 3.3 [\#128](https://github.com/internetarchive/heritrix3/pull/128) ([anjackson](https://github.com/anjackson))
- 'build.plugins.plugin.version' for org.apache.maven.plugins:maven-compiler-plugin is missing [\#126](https://github.com/internetarchive/heritrix3/pull/126) ([caofangkun](https://github.com/caofangkun))
- Amqp declarations fix [\#125](https://github.com/internetarchive/heritrix3/pull/125) ([ldko](https://github.com/ldko))
- Allow realm to be set by server for basic auth. [\#124](https://github.com/internetarchive/heritrix3/pull/124) ([vonrosen](https://github.com/vonrosen))
- Hosts report [\#123](https://github.com/internetarchive/heritrix3/pull/123) ([kris-sigur](https://github.com/kris-sigur))
- only submit checkbox and radio button form fields if they are on by d… [\#122](https://github.com/internetarchive/heritrix3/pull/122) ([nlevitt](https://github.com/nlevitt))
- new contrib module KnowledgableExtractorJS, a subclass of ExtractorJS th... [\#121](https://github.com/internetarchive/heritrix3/pull/121) ([nlevitt](https://github.com/nlevitt))
- for ARI-4267 accept possible uris with two dots in the filename part if ... [\#120](https://github.com/internetarchive/heritrix3/pull/120) ([nlevitt](https://github.com/nlevitt))
- Fix for HER-2082 [\#119](https://github.com/internetarchive/heritrix3/pull/119) ([adam-miller](https://github.com/adam-miller))
- Fix for ServerNotModified WARC revisit records incorrectly record WARC-Payload-Digest [\#118](https://github.com/internetarchive/heritrix3/pull/118) ([kris-sigur](https://github.com/kris-sigur))
- avoid java.lang.NullPointerException at org.archive.modules.writer.Write... [\#117](https://github.com/internetarchive/heritrix3/pull/117) ([nlevitt](https://github.com/nlevitt))
- make sure log4j is configured when running unit tests, to avoid log4j er... [\#116](https://github.com/internetarchive/heritrix3/pull/116) ([nlevitt](https://github.com/nlevitt))
- Set character set to UTF-8 when passing through files. [\#115](https://github.com/internetarchive/heritrix3/pull/115) ([kris-sigur](https://github.com/kris-sigur))
- remove RecordingOutputStreamTest.java \(moving to webarchive-commons\) [\#114](https://github.com/internetarchive/heritrix3/pull/114) ([nlevitt](https://github.com/nlevitt))
- Amqp receiver deadlock [\#112](https://github.com/internetarchive/heritrix3/pull/112) ([nlevitt](https://github.com/nlevitt))
- somewhat ugly fix to handle exceptions from the bean browser like java.l... [\#111](https://github.com/internetarchive/heritrix3/pull/111) ([nlevitt](https://github.com/nlevitt))
- Upgrade to HttpClient 4.3.6 [\#110](https://github.com/internetarchive/heritrix3/pull/110) ([kris-sigur](https://github.com/kris-sigur))
- so that it can appear in the crawl log, add contentSize to CrawlURI extr... [\#109](https://github.com/internetarchive/heritrix3/pull/109) ([nlevitt](https://github.com/nlevitt))
- kafka crawl log feed [\#108](https://github.com/internetarchive/heritrix3/pull/108) ([nlevitt](https://github.com/nlevitt))
- Handle case where form does not have an action defined. [\#107](https://github.com/internetarchive/heritrix3/pull/107) ([vonrosen](https://github.com/vonrosen))
- seriously, fix extraInfo handling in AMQPCrawlLogFeed [\#106](https://github.com/internetarchive/heritrix3/pull/106) ([nlevitt](https://github.com/nlevitt))
- fix extraInfo handling in AMQPCrawlLogFeed [\#105](https://github.com/internetarchive/heritrix3/pull/105) ([nlevitt](https://github.com/nlevitt))
- change field names to match new druid config [\#104](https://github.com/internetarchive/heritrix3/pull/104) ([nlevitt](https://github.com/nlevitt))
- CandidatesProcessor.java [\#103](https://github.com/internetarchive/heritrix3/pull/103) ([adam-miller](https://github.com/adam-miller))
- avoid deadlock in AMQPUrlReceiver hopefully [\#102](https://github.com/internetarchive/heritrix3/pull/102) ([nlevitt](https://github.com/nlevitt))
- Remove forcefetch for AMQP received urls so they don't get crawled twice... [\#101](https://github.com/internetarchive/heritrix3/pull/101) ([vonrosen](https://github.com/vonrosen))
- Allow discovery of urls in content attribute of meta tags. [\#100](https://github.com/internetarchive/heritrix3/pull/100) ([vonrosen](https://github.com/vonrosen))
- AMQPCrawlLogFeed, DecideRuleSequenceWithAMQPFeed, DecideRuleSequence.logExtraInfo [\#99](https://github.com/internetarchive/heritrix3/pull/99) ([nlevitt](https://github.com/nlevitt))
- Fix for HER-2074 [\#97](https://github.com/internetarchive/heritrix3/pull/97) ([kris-sigur](https://github.com/kris-sigur))
- new cookie store system to address HER-2070 "cookie monster" bug [\#96](https://github.com/internetarchive/heritrix3/pull/96) ([nlevitt](https://github.com/nlevitt))
- FIX corner-case of bean browser failing due to an exception from hashCode\(\) [\#95](https://github.com/internetarchive/heritrix3/pull/95) ([kngenie](https://github.com/kngenie))
- do not require "+" \(plus sign\) before @OPERATOR\_CONTACT\_URL@ in user-age... [\#94](https://github.com/internetarchive/heritrix3/pull/94) ([nlevitt](https://github.com/nlevitt))
- Allow urls in JavaScript between unicode quotes to be detected. [\#93](https://github.com/internetarchive/heritrix3/pull/93) ([vonrosen](https://github.com/vonrosen))
- remove more unused classes [\#92](https://github.com/internetarchive/heritrix3/pull/92) ([nlevitt](https://github.com/nlevitt))
- FetchHTTP.java [\#91](https://github.com/internetarchive/heritrix3/pull/91) ([adam-miller](https://github.com/adam-miller))
- Move Wayback-dedup module to heritrix-contrib [\#90](https://github.com/internetarchive/heritrix3/pull/90) ([kngenie](https://github.com/kngenie))
- Don’t let exception from property getter fail entire bean-browser. [\#89](https://github.com/internetarchive/heritrix3/pull/89) ([kngenie](https://github.com/kngenie))
- fix bug in CrawlURI.compare\(\) discovered by Kenji, add unit test CrawlUR... [\#88](https://github.com/internetarchive/heritrix3/pull/88) ([nlevitt](https://github.com/nlevitt))
- Allow xml extractor to handle urls in CDATA. [\#87](https://github.com/internetarchive/heritrix3/pull/87) ([vonrosen](https://github.com/vonrosen))
- remove unused Transform\* classes [\#86](https://github.com/internetarchive/heritrix3/pull/86) ([nlevitt](https://github.com/nlevitt))
- switch to mainline iipc webarchive-commons latest release [\#84](https://github.com/internetarchive/heritrix3/pull/84) ([nlevitt](https://github.com/nlevitt))
- oops! count novel urls/bytes for hosts report, etc [\#83](https://github.com/internetarchive/heritrix3/pull/83) ([nlevitt](https://github.com/nlevitt))
- Fix for HER-2071 [\#82](https://github.com/internetarchive/heritrix3/pull/82) ([kris-sigur](https://github.com/kris-sigur))
- Hbase cdh5 [\#81](https://github.com/internetarchive/heritrix3/pull/81) ([nlevitt](https://github.com/nlevitt))
- ExtractorHTML when a/@href links include the attribute data-remote="true... [\#80](https://github.com/internetarchive/heritrix3/pull/80) ([nlevitt](https://github.com/nlevitt))
- Revisit redux [\#79](https://github.com/internetarchive/heritrix3/pull/79) ([nlevitt](https://github.com/nlevitt))
- treat content as html and extract links if it looks like html, even if m... [\#78](https://github.com/internetarchive/heritrix3/pull/78) ([nlevitt](https://github.com/nlevitt))
- Force urls received from AMQP to be recrawled so custom http headers can... [\#77](https://github.com/internetarchive/heritrix3/pull/77) ([vonrosen](https://github.com/vonrosen))
- HER-2039 remove class Link, use CrawlURI [\#76](https://github.com/internetarchive/heritrix3/pull/76) ([nlevitt](https://github.com/nlevitt))
- in CrawlURI.createCrawlURI\(\), avoid clobbering inherited data with data ... [\#75](https://github.com/internetarchive/heritrix3/pull/75) ([nlevitt](https://github.com/nlevitt))
- Fix for https://webarchive.jira.com/browse/ARI-3943 [\#74](https://github.com/internetarchive/heritrix3/pull/74) ([vonrosen](https://github.com/vonrosen))
- Treat codebase as link hops, not embeds [\#73](https://github.com/internetarchive/heritrix3/pull/73) ([kris-sigur](https://github.com/kris-sigur))
- add A\_ANNOTATIONS to persistentKeys so that CrawlURI doesn't lose its an... [\#72](https://github.com/internetarchive/heritrix3/pull/72) ([nlevitt](https://github.com/nlevitt))
- avoid calling CheckpointService.hasAvailableCheckpoints\(\) when crawl not... [\#71](https://github.com/internetarchive/heritrix3/pull/71) ([nlevitt](https://github.com/nlevitt))
- for ARI-3712, add extracted links relative to both via and base, and annotate with "extractorSWFRelToVia", "extractorSWFRelToBase", or "extractorSWFRelToBoth" if resulting link is the same whether relative to base or via [\#70](https://github.com/internetarchive/heritrix3/pull/70) ([nlevitt](https://github.com/nlevitt))
- For https://webarchive.jira.com/browse/ARI-3865 [\#69](https://github.com/internetarchive/heritrix3/pull/69) ([vonrosen](https://github.com/vonrosen))
- handle exception determining whether to apply overlay [\#68](https://github.com/internetarchive/heritrix3/pull/68) ([nlevitt](https://github.com/nlevitt))
- don't log severe with stack trace on normal amqp shutdown [\#67](https://github.com/internetarchive/heritrix3/pull/67) ([nlevitt](https://github.com/nlevitt))
- oops, make "exit java process" button work again [\#66](https://github.com/internetarchive/heritrix3/pull/66) ([nlevitt](https://github.com/nlevitt))
- shut down the starter-restarter thread at crawl finish!! [\#65](https://github.com/internetarchive/heritrix3/pull/65) ([nlevitt](https://github.com/nlevitt))
- Via surt prefixed decide rule [\#64](https://github.com/internetarchive/heritrix3/pull/64) ([adam-miller](https://github.com/adam-miller))
- Contrib - ExtractorPDFContent [\#63](https://github.com/internetarchive/heritrix3/pull/63) ([adam-miller](https://github.com/adam-miller))
- Ari 3765 gracefully handle amqp server going up and down [\#62](https://github.com/internetarchive/heritrix3/pull/62) ([nlevitt](https://github.com/nlevitt))
- HER-2065 synchronize on inactiveQueuesByPrecedence inside of synchronize... [\#61](https://github.com/internetarchive/heritrix3/pull/61) ([nlevitt](https://github.com/nlevitt))
- Cosmetics [\#60](https://github.com/internetarchive/heritrix3/pull/60) ([nlevitt](https://github.com/nlevitt))
- fix unit test now that we accept speculative urls with query params with... [\#59](https://github.com/internetarchive/heritrix3/pull/59) ([nlevitt](https://github.com/nlevitt))
- for ARI-3723, accept speculative urls with query params with no value [\#58](https://github.com/internetarchive/heritrix3/pull/58) ([nlevitt](https://github.com/nlevitt))
- AMQPUrlReceiver - improve handling of case where rabbitmq is unreachable... [\#57](https://github.com/internetarchive/heritrix3/pull/57) ([nlevitt](https://github.com/nlevitt))
- fix FormLoginProcessor checkpointing [\#56](https://github.com/internetarchive/heritrix3/pull/56) ([nlevitt](https://github.com/nlevitt))
- oops, update test to expect post data as url-encoded query string [\#54](https://github.com/internetarchive/heritrix3/pull/54) ([nlevitt](https://github.com/nlevitt))
- Fix form login [\#53](https://github.com/internetarchive/heritrix3/pull/53) ([nlevitt](https://github.com/nlevitt))
- Implicitly add the ${} around groovyExpression. When cxml contains ${}, ... [\#52](https://github.com/internetarchive/heritrix3/pull/52) ([nlevitt](https://github.com/nlevitt))
- Expression deciderule [\#51](https://github.com/internetarchive/heritrix3/pull/51) ([nlevitt](https://github.com/nlevitt))
- Replace deprecated routines in guava [\#50](https://github.com/internetarchive/heritrix3/pull/50) ([shriphani](https://github.com/shriphani))
- Youtube march 2014 [\#49](https://github.com/internetarchive/heritrix3/pull/49) ([nlevitt](https://github.com/nlevitt))
- Umbra [\#48](https://github.com/internetarchive/heritrix3/pull/48) ([nlevitt](https://github.com/nlevitt))
- Adjusting Youtube itag priority [\#47](https://github.com/internetarchive/heritrix3/pull/47) ([adam-miller](https://github.com/adam-miller))
- switch dependency from ia-web-commons 1.1.1-SNAPSHOT to webarchive-commo... [\#46](https://github.com/internetarchive/heritrix3/pull/46) ([nlevitt](https://github.com/nlevitt))
- Update youtube itags [\#45](https://github.com/internetarchive/heritrix3/pull/45) ([nlevitt](https://github.com/nlevitt))
- update httpcomponents, should address NPE we've seen https://issues.apac... [\#44](https://github.com/internetarchive/heritrix3/pull/44) ([nlevitt](https://github.com/nlevitt))
- fix job.log file handler was left open when jobdir is removed [\#43](https://github.com/internetarchive/heritrix3/pull/43) ([martinsbalodis](https://github.com/martinsbalodis))
- Adding the queue declaration and binding to the UrlReceiver [\#42](https://github.com/internetarchive/heritrix3/pull/42) ([eldondev](https://github.com/eldondev))
- Fix slow cookies [\#41](https://github.com/internetarchive/heritrix3/pull/41) ([nlevitt](https://github.com/nlevitt))
- For https://webarchive.jira.com/browse/HER-2064 [\#40](https://github.com/internetarchive/heritrix3/pull/40) ([vonrosen](https://github.com/vonrosen))
- progress and formatting changes [\#39](https://github.com/internetarchive/heritrix3/pull/39) ([nlevitt](https://github.com/nlevitt))
- Umbra - AMQPUrlReceiver.java receive urls via amqp and add to frontier, related changes [\#38](https://github.com/internetarchive/heritrix3/pull/38) ([nlevitt](https://github.com/nlevitt))
- fix HER-2063 - omit port in Host request header when it is default for t... [\#37](https://github.com/internetarchive/heritrix3/pull/37) ([nlevitt](https://github.com/nlevitt))
- Avoid the exception below by handling bad charsets in FetchHTTP. Restore... [\#36](https://github.com/internetarchive/heritrix3/pull/36) ([nlevitt](https://github.com/nlevitt))
- whoops! send escaped path+query on http request line; had been sending r... [\#35](https://github.com/internetarchive/heritrix3/pull/35) ([nlevitt](https://github.com/nlevitt))
- fix NullPointerException in case of 401 with no auth challenge \(includes... [\#34](https://github.com/internetarchive/heritrix3/pull/34) ([nlevitt](https://github.com/nlevitt))
- First pass at a processor to publish crawluris to AMQP channels [\#33](https://github.com/internetarchive/heritrix3/pull/33) ([eldondev](https://github.com/eldondev))
- Switch to BasicHttpClientConnectionManager instead of [\#32](https://github.com/internetarchive/heritrix3/pull/32) ([nlevitt](https://github.com/nlevitt))
- make http proxy port configurable in cxml, avoiding this: org.springfram... [\#31](https://github.com/internetarchive/heritrix3/pull/31) ([nlevitt](https://github.com/nlevitt))
- Fix bdb cookie store [\#30](https://github.com/internetarchive/heritrix3/pull/30) ([nlevitt](https://github.com/nlevitt))
- HER-2062 Fix for WorkQueueFrontier.deleteURIs handling of retired queues [\#29](https://github.com/internetarchive/heritrix3/pull/29) ([kris-sigur](https://github.com/kris-sigur))
- switch to httpcomponents, get rid of archive-overlay-commons-httpclient [\#28](https://github.com/internetarchive/heritrix3/pull/28) ([nlevitt](https://github.com/nlevitt))
- rename dist/README.md to dist/README.txt so that maven bundles it in the... [\#27](https://github.com/internetarchive/heritrix3/pull/27) ([nlevitt](https://github.com/nlevitt))

## [3.2.0](https://github.com/internetarchive/heritrix3/tree/3.2.0) (2014-01-10)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.1.1...3.2.0)

**Merged pull requests:**

- update readme for 3.2.0 release [\#26](https://github.com/internetarchive/heritrix3/pull/26) ([nlevitt](https://github.com/nlevitt))
- bump version number to 3.2.0 for release [\#25](https://github.com/internetarchive/heritrix3/pull/25) ([nlevitt](https://github.com/nlevitt))
- for url-agnostic dedup, follow "Proposal for Standardizing the Recording... [\#24](https://github.com/internetarchive/heritrix3/pull/24) ([nlevitt](https://github.com/nlevitt))
- fix HER-1979 so heritrix can run on windows xp [\#23](https://github.com/internetarchive/heritrix3/pull/23) ([nlevitt](https://github.com/nlevitt))
- HER-1726: Templatize HTML [\#21](https://github.com/internetarchive/heritrix3/pull/21) ([adam-miller](https://github.com/adam-miller))
- Her 2031 - Improve login-form submission options [\#20](https://github.com/internetarchive/heritrix3/pull/20) ([gojomo](https://github.com/gojomo))
- BeanLookupBindings for simpler script access to beans [\#19](https://github.com/internetarchive/heritrix3/pull/19) ([travisfw](https://github.com/travisfw))
- Fix for HER-2018: XML representation for /engine/job/\<jobName\>/beans returns incorrect url for named beans [\#17](https://github.com/internetarchive/heritrix3/pull/17) ([adam-miller](https://github.com/adam-miller))
- Fix for HER-2017 XML representation of beans uses root node of type "script" [\#16](https://github.com/internetarchive/heritrix3/pull/16) ([adam-miller](https://github.com/adam-miller))
- Reuse htmllinkcontext [\#15](https://github.com/internetarchive/heritrix3/pull/15) ([kngenie](https://github.com/kngenie))
- suppress unused warnings for serialVersionUid [\#14](https://github.com/internetarchive/heritrix3/pull/14) ([travisfw](https://github.com/travisfw))
- have TooManyPathSegmentsDecideRule count path segments only [\#13](https://github.com/internetarchive/heritrix3/pull/13) ([travisfw](https://github.com/travisfw))
- generics warnings fixes [\#12](https://github.com/internetarchive/heritrix3/pull/12) ([travisfw](https://github.com/travisfw))
- New reports [\#11](https://github.com/internetarchive/heritrix3/pull/11) ([travisfw](https://github.com/travisfw))
- ScriptedDecideRule\#getEngine\(\) rewrite for better synchronization and thread local mgmt [\#10](https://github.com/internetarchive/heritrix3/pull/10) ([travisfw](https://github.com/travisfw))

## [3.1.1](https://github.com/internetarchive/heritrix3/tree/3.1.1) (2012-05-02)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/3.0.0...3.1.1)

**Merged pull requests:**

- Publicsuffixes2 [\#9](https://github.com/internetarchive/heritrix3/pull/9) ([kngenie](https://github.com/kngenie))
- Ip address set decide rule [\#7](https://github.com/internetarchive/heritrix3/pull/7) ([travisfw](https://github.com/travisfw))
- HER-2001: Use the CodeMirror editor for crawl config and script console [\#6](https://github.com/internetarchive/heritrix3/pull/6) ([ato](https://github.com/ato))
- HER-1998 [\#5](https://github.com/internetarchive/heritrix3/pull/5) ([adam-miller](https://github.com/adam-miller))
- sort script engines in script console [\#4](https://github.com/internetarchive/heritrix3/pull/4) ([travisfw](https://github.com/travisfw))

## [3.0.0](https://github.com/internetarchive/heritrix3/tree/3.0.0) (2009-12-05)

[Full Changelog](https://github.com/internetarchive/heritrix3/compare/e047bf68e0508176f2c9ba3ab15a7fa1ced6c1be...3.0.0)



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
