Bean Reference
==============

.. note::
    This reference is a work in progress and does not yet cover all available beans. For a more complete list of
    Heritrix beans please refer to the `javadoc <https://www.javadoc.io/doc/org.archive.heritrix/heritrix-modules>`_.

Core Beans
----------

ActionDirectory
~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/framework/ActionDirectory.java

BdbCookieStore
~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/fetcher/BdbCookieStore.java

BdbFrontier
~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/frontier/BdbFrontier.java

BdbModule
~~~~~~~~~

.. bean-doc:: ../commons/src/main/java/org/archive/bdb/BdbModule.java

BdbServerCache
~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/net/BdbServerCache.java

BdbUriUniqFilter
~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/util/BdbUriUniqFilter.java

CheckpointService
~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/framework/CheckpointService.java

CrawlController
~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/framework/CrawlController.java

CrawlerLoggerModule
~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/reporting/CrawlerLoggerModule.java

CrawlLimitEnforcer
~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/framework/CrawlLimitEnforcer.java

CrawlMetadata
~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/CrawlMetadata.java

CredentialStore
~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/credential/CredentialStore.java

DiskSpaceMonitor
~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/monitor/DiskSpaceMonitor.java

RulesCanonicalizationPolicy
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/canonicalize/RulesCanonicalizationPolicy.java

SheetOverlaysManager
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/spring/SheetOverlaysManager.java

StatisticsTracker
~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/reporting/StatisticsTracker.java

TextSeedModule
~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/seeds/TextSeedModule.java

Decide Rules
------------

AcceptDecideRule
~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/AcceptDecideRule.java

ClassKeyMatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/deciderules/ClassKeyMatchesRegexDecideRule.java

ContentLengthDecideRule
~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ContentLengthDecideRule.java

ContentTypeMatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ContentTypeMatchesRegexDecideRule.java

ContentTypeNotMatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ContentTypeNotMatchesRegexDecideRule.java

ExpressionDecideRule (contrib)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../contrib/src/main/java/org/archive/modules/deciderules/ExpressionDecideRule.java

ExternalGeoLocationDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ExternalGeoLocationDecideRule.java

FetchStatusDecideRule
~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/FetchStatusDecideRule.java

FetchStatusMatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/FetchStatusMatchesRegexDecideRule.java

FetchStatusNotMatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/FetchStatusNotMatchesRegexDecideRule.java

HasViaDecideRule
~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/HasViaDecideRule.java

HopCrossesAssignmentLevelDomainDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/HopCrossesAssignmentLevelDomainDecideRule.java

HopsPathMatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/HopsPathMatchesRegexDecideRule.java

IdenticalDigestDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/recrawl/IdenticalDigestDecideRule.java

IpAddressSetDecideRule
~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/IpAddressSetDecideRule.java

MatchesFilePatternDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/MatchesFilePatternDecideRule.java

MatchesListRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/MatchesListRegexDecideRule.java

MatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/MatchesRegexDecideRule.java

MatchesStatusCodeDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/MatchesStatusCodeDecideRule.java

NotMatchesFilePatternDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/NotMatchesFilePatternDecideRule.java

NotMatchesListRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/NotMatchesListRegexDecideRule.java

NotMatchesRegexDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/NotMatchesRegexDecideRule.java

NotMatchesStatusCodeDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/NotMatchesStatusCodeDecideRule.java

NotOnDomainsDecideRule
~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/surt/NotOnDomainsDecideRule.java

NotOnHostsDecideRule
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/surt/NotOnHostsDecideRule.java

NotSurtPrefixedDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/surt/NotSurtPrefixedDecideRule.java

OnDomainsDecideRule
~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/surt/OnDomainsDecideRule.java

OnHostsDecideRule
~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/surt/OnHostsDecideRule.java

PathologicalPathDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/PathologicalPathDecideRule.java

PredicatedDecideRule
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/PredicatedDecideRule.java

PrerequisiteAcceptDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/PrerequisiteAcceptDecideRule.java

RejectDecideRule
~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/RejectDecideRule.java

ResourceLongerThanDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ResourceLongerThanDecideRule.java

ResourceNoLongerThanDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ResourceNoLongerThanDecideRule.java

ResponseContentLengthDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ResponseContentLengthDecideRule.java

SchemeNotInSetDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/SchemeNotInSetDecideRule.java

ScriptedDecideRule
~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ScriptedDecideRule.java

SeedAcceptDecideRule
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/SeedAcceptDecideRule.java

SourceSeedDecideRule
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/SourceSeedDecideRule.java

SurtPrefixedDecideRule
~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/surt/SurtPrefixedDecideRule.java

TooManyHopsDecideRule
~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/TooManyHopsDecideRule.java

TooManyPathSegmentsDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/TooManyPathSegmentsDecideRule.java

TransclusionDecideRule
~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/TransclusionDecideRule.java

ViaSurtPrefixedDecideRule
~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/deciderules/ViaSurtPrefixedDecideRule.java

Candidate Processors
--------------------

CandidateScoper
~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/prefetch/CandidateScoper.java

FrontierPreparer
~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/prefetch/FrontierPreparer.java

Pre-Fetch Processors
--------------------

PreconditionEnforcer
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/prefetch/PreconditionEnforcer.java

Preselector
~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/prefetch/Preselector.java

Fetch Processors
----------------

FetchDNS
~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/fetcher/FetchDNS.java

FetchFTP
~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/fetcher/FetchFTP.java

FetchHTTP
~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/fetcher/FetchHTTP.java

FetchSFTP
~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/fetcher/FetchSFTP.java

FetchWhois
~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/fetcher/FetchWhois.java

Link Extractors
---------------

ExtractorChrome (contrib)
~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../contrib/src/main/java/org/archive/modules/extractor/ExtractorChrome.java

ExtractorCSS
~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorCSS.java

ExtractorDOC
~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorDOC.java

ExtractorHTML
~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorHTML.java

AggressiveExtractorHTML
~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/AggressiveExtractorHTML.java

JerichoExtractorHTML
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/JerichoExtractorHTML.java

ExtractorHTMLForms
~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/forms/ExtractorHTMLForms.java

ExtractorHTTP
~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorHTTP.java

ExtractorImpliedURI
~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorImpliedURI.java

ExtractorJS
~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorJS.java

KnowledgableExtractorJS (contrib)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../contrib/src/main/java/org/archive/modules/extractor/KnowledgableExtractorJS.java

ExtractorMultipleRegex
~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorMultipleRegex.java

ExtractorPDF
~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorPDF.java

ExtractorPDFContent (contrib)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../contrib/src/main/java/org/archive/modules/extractor/ExtractorPDFContent.java

ExtractorRobotsTxt
~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorRobotsTxt.java

ExtractorSitemap
~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorSitemap.java

ExtractorSWF
~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorSWF.java

ExtractorUniversal
~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorUniversal.java

ExtractorURI
~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorURI.java

ExtractorXML
~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/ExtractorXML.java

ExtractorYoutubeDL (contrib)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../contrib/src/main/java/org/archive/modules/extractor/ExtractorYoutubeDL.java

ExtractorYoutubeFormatStream (contrib)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../contrib/src/main/java/org/archive/modules/extractor/ExtractorYoutubeFormatStream.java

ExtractorYoutubeChannelFormatStream (contrib)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../contrib/src/main/java/org/archive/modules/extractor/ExtractorYoutubeChannelFormatStream.java

TrapSuppressExtractor
~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/extractor/TrapSuppressExtractor.java

Post-Processors
---------------

CandidatesProcessor
~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/postprocessor/CandidatesProcessor.java

DispositionProcessor
~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/postprocessor/DispositionProcessor.java

ReschedulingProcessor
~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../engine/src/main/java/org/archive/crawler/postprocessor/ReschedulingProcessor.java

WARCWriterChainProcessor
~~~~~~~~~~~~~~~~~~~~~~~~

.. bean-doc:: ../modules/src/main/java/org/archive/modules/writer/WARCWriterChainProcessor.java
