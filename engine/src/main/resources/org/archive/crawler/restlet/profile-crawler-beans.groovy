/*
 * HERITRIX 3 CRAWL JOB CONFIGURATION FILE
 *
 * This is a relatively minimal configuration suitable for many crawls.
 *
 * Commented-out beans and properties are provided as an example; values
 * shown in comments reflect the actual defaults which are in effect
 * if not otherwise specified specification. (To change from the default
 * behavior, uncomment AND alter the shown values.)
 */

import org.archive.bdb.BdbModule
import org.archive.crawler.framework.*
import org.archive.crawler.frontier.*
import org.archive.crawler.monitor.DiskSpaceMonitor
import org.archive.crawler.postprocessor.*
import org.archive.crawler.prefetch.*
import org.archive.crawler.reporting.*
import org.archive.crawler.spring.*
import org.archive.crawler.util.BdbUriUniqFilter
import org.archive.modules.*
import org.archive.modules.canonicalize.*
import org.archive.modules.deciderules.surt.SurtPrefixedDecideRule
import org.archive.modules.extractor.*
import org.archive.modules.fetcher.*
import org.archive.modules.net.BdbServerCache
import org.archive.modules.seeds.TextSeedModule
import org.archive.modules.writer.WARCWriterChainProcessor
import org.springframework.beans.factory.config.PropertyOverrideConfigurer
import org.archive.modules.deciderules.*
import org.archive.spring.*

beans {
    /*
     * OVERRIDES
     * Values elsewhere in the configuration may be replaced ('overridden')
     * by a Properties map declared in a PropertiesOverrideConfigurer,
     * using a dotted-bean-path to address individual bean properties.
     * This allows us to collect a few of the most-often changed values
     * in an easy-to-edit format here at the beginning of the model
     * configuration.
     */

    /**
     * overrides from a text property list
     */
    simpleOverrides(PropertyOverrideConfigurer) {
        properties = '''
# This Properties map is specified in the Java 'property list' text format
# http://java.sun.com/javase/6/docs/api/java/util/Properties.html#load%28java.io.Reader%29

metadata.operatorContactUrl=ENTER_AN_URL_WITH_YOUR_CONTACT_INFO_HERE_FOR_WEBMASTERS_AFFECTED_BY_YOUR_CRAWL
metadata.jobName=basic
metadata.description=Basic crawl starting with useful defaults

##..more?..##
'''
    }

    /**
     * overrides from declared <prop> elements, more easily allowing
     * multiline values or even declared beans
     */
    longerOverrides(PropertyOverrideConfigurer) {
        properties = ['seeds.textSource.value': ''''

# URLS HERE
http://example.example/example

        ''']
    }

    /**
     * CRAWL METADATA: including identification of crawler/operator
     */
    metadata(CrawlMetadata) { bean ->
        bean.autowire = 'byName'
        operatorContactUrl = '[see override above]'
        jobName = '[see override above]'
        description = '[see override above]'
        // robotsPolicyName = 'obey'
        // operator = ''
        // operatorFrom = ''
        // organization = ''
        // audience = ''
        // userAgentTemplate = 'Mozilla/5.0 (compatible; heritrix/@VERSION@ +@OPERATOR_CONTACT_URL@)'
    }

    /**
     * SEEDS: crawl starting points
     *
     * ConfigString allows simple, inline specification of a moderate
     * number of seeds; see below comment for example of using an
     * arbitrarily-large external file.
     */
    seeds(TextSeedModule) {
        textSource = new ConfigString('''
# [see override above]
        ''')
        // sourceTagSeeds = false
        // blockAwaitingSeedLines = -1
    }

    /**
     * SEEDS ALTERNATE APPROACH: specifying external seeds.txt file in
     * the job directory, similar to the H1 approach.
     * Use either the above, or this, but not both.
     */
    /*
    seeds(TextSeedModule) {
        textSource = new ConfigFile(path: 'seeds.txt')
        // sourceTagSeeds = false
        // blockAwaitingSeedLines = -1
    }
    */

    acceptSurts(SurtPrefixedDecideRule) {
        // decision = 'ACCEPT'
        // seedsAsSurtPrefixes = true
        // alsoCheckVia = false
        // surtsSourceFile = ''
        // surtsDumpFile = '${launchId}/surts.dump'
        /*
        surtsSource = new ConfigString('''
# example.com
# http://www.example.edu/path1/
# +http://(org,example,
            ''')
        }
        */
    }

    /**
     * SCOPE: rules for which discovered URIs to crawl; order is very
     * important because last decision returned other than 'NONE' wins.
     */
    scope(DecideRuleSequence) {
        logToFile = false
        rules = [
                // Begin by REJECTing all...
                new RejectDecideRule(),
                // ...then ACCEPT those within configured/seed-implied SURT prefixes...
                new TooManyHopsDecideRule(
                        // maxHops: 20,
                ),
                // ...but ACCEPT those more than a configured link-hop-count from start...
                new TransclusionDecideRule(
                        // maxTransHops: 2,
                        // maxSpeculativeHops: 1,
                ),
                // ...but REJECT those from a configurable (initially empty) set of REJECT SURTs...
                new SurtPrefixedDecideRule(
                        decision: 'REJECT',
                        seedsAsSurtPrefixes: false,
                        surtsDumpFile: new ConfigFile(path: '${launchId}/negative-surts.dump'),
                        // surtsSource: new ConfigFile(path: 'negative-surts.txt'),
                ),
                // ...and REJECT those from a configurable (initially empty) set of URI regexes...
                new MatchesListRegexDecideRule(
                        decision: 'REJECT',
                        // listLogicalOr: false,
                        // regexList: [],
                ),
                // ...and REJECT those with suspicious repeating path-segments...
                new PathologicalPathDecideRule(
                        // maxRepetitions: 2,
                ),
                // ...and REJECT those with more than threshold number of path-segments...
                new TooManyPathSegmentsDecideRule(
                        // maxPathDepth: 20,
                ),
                // ...but always ACCEPT those marked as prerequisitee for another URI...
                new PrerequisiteAcceptDecideRule(),
                // ...but always REJECT those with unsupported URI schemes
                new SchemeNotInSetDecideRule(),
        ]
    }

    /*
     * PROCESSING CHAINS
     * Much of the crawler's work is specified by the sequential
     * application of swappable Processor modules. These Processors
     * are collected into three 'chains'. The CandidateChain is applied
     * to URIs being considered for inclusion, before a URI is enqueued
     * for collection. The FetchChain is applied to URIs when their
     * turn for collection comes up. The DispositionChain is applied
     * after a URI is fetched and analyzed/link-extracted.
     */

    /*
     * CANDIDATE CHAIN
     */
    // first, processors are declared as top-level named beans
    candidateScoper(CandidateScoper)
    preparer(FrontierPreparer) {
        // preferenceDepthHops = -1
        // preferenceEmbedHops = 1
        // canonicalizationPolicy = ref('canonicalizationPolicy')
        // queueAssignmentPolicy = ref('queueAssignmentPolicy')
        // uriPrecedencePolicy = ref('uriPrecedencePolicy')
        // costAssignmentPolicy = ref('costAssignmentPolicy')
    }
    // now, processors are assembled into ordered CandidateChain bean
    candidateProcessors(CandidateChain) {
        processors = [
                // apply scoping rules to each individual candidate URI...
                ref('candidateScoper'),
                // ...then prepare those ACCEPTed to be enqueued to frontier.
                ref('preparer'),
        ]
    }

    /*
     * FETCH CHAIN
     */
    // first, processors are declared as top-level named beans
    preselector(Preselector) {
        // recheckScope = false
        // blockAll = false
        // blockByRegex = ''
        // allowByRegex = ''
    }
    preconditions(PreconditionEnforcer) {
        // ipValidityDurationSeconds = 21600
        // robotsValidityDurationSeconds = 86400
        // calculateRobotsOnly = false
    }
    fetchDns(FetchDNS) {
        // acceptNonDnsResolves = false
        // digestContent = true
        // digestAlgorithm = 'sha1'
        // dnsOverHttpServer = 'https://dns.google/dns-query'
    }
    /*
    fetchWhois(FetchWhois) {
     specialQueryTemplates = [
         'whois.verisign-grs.com': 'domain %s',
         'whois.arin.net': 'z + %s',
         'whois.denic.de': '-T dn %s'
     ]
    }
    */
    fetchHttp(FetchHTTP) {
        // useHTTP11 = false
        // maxLengthBytes = 0
        // timeoutSeconds = 1200
        // maxFetchKBSec = 0
        // defaultEncoding = 'ISO-8859-1'
        // shouldFetchBodyRule = new AcceptDecideRule()
        // soTimeoutMs = 20000
        // sendIfModifiedSince = true
        // sendIfNoneMatch = true
        // sendConnectionClose = true
        // sendReferer = true
        // sendRange = false
        // ignoreCookies = false
        // sslTrustLevel = 'OPEN'
        // acceptHeaders = [
        //     'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
        // ]
        // httpBindAddress = ''
        // httpProxyHost = ''
        // httpProxyPort = 0
        // httpProxyUser = ''
        // httpProxyPassword = ''
        // socksProxyHost = ''
        // socksProxyPort = ''
        // digestContent = true
        // digestAlgorithm = 'sha1'
    }
    extractorHttp(ExtractorHTTP)
    extractorRobotsTxt(ExtractorRobotsTxt)
    extractorSitemap(ExtractorSitemap)
    extractorHtml(ExtractorHTML) {
        // extractJavascript = true
        // extractValueAttributes = true
        // ignoreFormActionUrls = false
        // extractOnlyFormGets = true
        // treatFramesAsEmbedLinks = true
        // ignoreUnexpectedHtml = true
        // maxElementLength = 1024
        // maxAttributeNameLength = 1024
        // maxAttributeValueLength = 16384
        // obeyRelNofollow = false
    }
    extractorCss(ExtractorCSS)
    extractorJs(ExtractorJS)
    extractorSwf(ExtractorSWF)
    // now, processors are assembled into ordered FetchChain bean
    fetchProcessors(FetchChain) {
        processors = [
                // re-check scope, if so enabled...
                ref('preselector'),
                // ...then verify or trigger prerequisite URIs fetched, allow crawling...
                ref('preconditions'),
                // ...fetch if DNS URI...
                ref('fetchDns'),
                // ref('fetchWhois'),
                // ...fetch if HTTP URI...
                ref('fetchHttp'),
                // ...extract outlinks from HTTP headers...
                ref('extractorHttp'),
                // ...extract sitemap urls from robots.txt...
                ref('extractorRobotsTxt'),
                // ...extract links from sitemaps...
                ref('extractorSitemap'),
                // ...extract outlinks from HTML content...
                ref('extractorHtml'),
                // ...extract outlinks from CSS content...
                ref('extractorCss'),
                // ...extract outlinks from Javascript content...
                ref('extractorJs'),
                // ...extract outlinks from Flash content...
                ref('extractorSwf')
        ]
    }

    /*
     * DISPOSITION CHAIN
     */
    // first, processors are declared as top-level named beans
    warcWriter(WARCWriterChainProcessor) {
        // compress = true
        // prefix = 'IAH'
        // maxFileSizeBytes = 1000000000
        // poolMaxActive = 1
        // MaxWaitForIdleMs = 500
        // skipIdenticalDigests = false
        // maxTotalBytesToWrite = 0
        // directory = '${launchId}'
        // storePaths = ['warcs']
        // template = '${prefix}-${timestamp17}-${serialno}-${heritrix.pid}~${heritrix.hostname}~${heritrix.port}'
        // startNewFilesOnCheckpoint = true
        /*
        chain = [
            new org.archive.modules.warc.DnsResponseRecordBuilder(),
            new org.archive.modules.warc.HttpResponseRecordBuilder(),
            new org.archive.modules.warc.WhoisResponseRecordBuilder(),
            new org.archive.modules.warc.FtpControlConversationRecordBuilder(),
            new org.archive.modules.warc.FtpResponseRecordBuilder(),
            new org.archive.modules.warc.RevisitRecordBuilder(),
            new org.archive.modules.warc.HttpRequestRecordBuilder(),
            new org.archive.modules.warc.MetadataRecordBuilder()
        ]
        */
    }
    candidates(CandidatesProcessor) {
        // seedsRedirectNewSeeds = true
        // processErrorOutlinks = false
    }
    disposition(DispositionProcessor) {
        // delayFactor = 5.0
        // minDelayMs = 3000
        // respectCrawlDelayUpToSeconds = 300
        // maxDelayMs = 30000
        // maxPerHostBandwidthUsageKbSec = 0
    }
    /*
    rescheduler(ReschedulingProcessor) {
        rescheduleDelaySeconds = -1
    }
    */
    // now, processors are assembled into ordered DispositionChain bean
    dispositionProcessors(DispositionChain) {
        processors = [
                // write to aggregate archival files...
                ref('warcWriter'),
                // ...send each outlink candidate URI to CandidateChain,
                // and enqueue those ACCEPTed to the frontier...
                ref('candidates'),
                // ...then update stats, shared-structures, frontier decisions
                ref('disposition')
                // ref('rescheduler')
        ]
    }

    /**
     * CRAWLCONTROLLER: Control interface, unifying context
     */
    crawlController(CrawlController) {
        // maxToeThreads = 25
        // pauseAtStart = true
        // runWhileEmpty = false
        // recorderInBufferBytes = 524288
        // recorderOutBufferBytes = 16384
        // scratchDir = 'scratch'
    }

    /**
     * FRONTIER: Record of all URIs discovered and queued-for-collection
     */
    frontier(BdbFrontier) {
        // queueTotalBudget = -1
        // balanceReplenishAmount = 3000
        // errorPenaltyAmount = 100
        // precedenceFloor = 255
        // queuePrecedencePolicy = new org.archive.crawler.frontier.precedence.BaseQueuePrecedencePolicy()
        // snoozeLongMs = 300000
        // retryDelaySeconds = 900
        // maxRetries = 30
        // recoveryLogEnabled = true
        // maxOutlinks = 6000
        // extractIndependently = false
        // outbound = new ArrayBlockingQueue(200, true)
        // inbound = new ArrayBlockingQueue(40000, true)
        // dumpPendingAtClose = false
    }

    /**
     * URI UNIQ FILTER: Used by frontier to remember already-included URIs
     */
    uriUniqFilter(BdbUriUniqFilter)

    /*
     * EXAMPLE SETTINGS OVERLAY SHEETS
     * Sheets allow some settings to vary by context - usually by URI context,
     * so that different sites or sections of sites can be treated differently.
     * Here are some example Sheets for common purposes. The SheetOverlaysManager
     * (below) automatically collects all Sheet instances declared among the
     * original beans, but others can be added during the crawl via the scripting
     * interface.
     */

    /**
     * forceRetire: any URI to which this sheet's settings are applied
     * will force its containing queue to 'retired' status.
     */
    forceRetire(Sheet) {
        map = [
                'disposition.forceRetire': 'true'
        ]
    }

    /**
     * smallBudget: any URI to which this sheet's settings are applied
     * will give its containing queue small values for balanceReplenishAmount
     * (causing it to have shorter 'active' periods while other queues are
     * waiting) and queueTotalBudget (causing the queue to enter 'retired'
     * status once that expenditure is reached by URI attempts and errors)
     */
    smallBudget(Sheet) {
        map = [
                'frontier.balanceReplenishAmount': '20',
                'frontier.queueTotalBudget': '100'
        ]
    }

    /**
     * veryPolite: any URI to which this sheet's settings are applied
     * will cause its queue to take extra-long politeness snoozes
     */
    veryPolite(Sheet) {
        map = [
                'disposition.delayFactor': '10',
                'disposition.minDelayMs': '10000',
                'disposition.maxDelayMs': '1000000',
                'disposition.respectCrawlDelayUpToSeconds': '3600'
        ]
    }

    /**
     * highPrecedence: any URI to which this sheet's settings are applied
     * will give its containing queue a slightly-higher than default
     * queue precedence value. That queue will then be preferred over
     * other queues for active crawling, never waiting behind lower-
     * precedence queues.
     */
    highPrecedence(Sheet) {
        map = [
                'frontier.balanceReplenishAmount': '20',
                'frontier.queueTotalBudget': '100'
        ]
    }

    /*
     * EXAMPLE SETTINGS OVERLAY SHEET-ASSOCIATION
     * A SheetAssociation says certain URIs should have certain overlay Sheets
     * applied. This example applies two sheets to URIs matching two SURT-prefixes.
     * New associations may also be added mid-crawl using the scripting facility.
     */

    /*
    surtPrefixesSheetAssociation(SurtPrefixesSheetAssociation) {
        surtPrefixes = [
            'http://(org,example,',
            'http://(com,example,www,)/'
        ]
        targetSheetNames = [
            'veryPolite',
            'smallBudget'
        ]
    }
    */

    /*
     * OPTIONAL BUT RECOMMENDED BEANS
     */

    /**
     * ACTIONDIRECTORY: disk directory for mid-crawl operations
     * Running job will watch directory for new files with URIs,
     * scripts, and other data to be processed during a crawl.
     */
    actionDirectory(ActionDirectory) {
        // actionDir = 'action'
        // doneDir = '${launchId}/actions-done'
        // initialDelaySeconds = 10
        // delaySeconds = 30
    }

    /**
     * CRAWLLIMITENFORCER: stops crawl when it reaches configured limits
     */
    crawlLimiter(CrawlLimitEnforcer) {
        // maxBytesDownload = 0
        // maxDocumentsDownload = 0
        // maxTimeSeconds = 0
    }

    /**
     * CHECKPOINTSERVICE: checkpointing assistance
     */
    checkpointService(CheckpointService) {
        // checkpointIntervalMinutes = -1
        // checkpointOnShutdown = true
        // checkpointsDir = 'checkpoints'
        // forgetAllButLatest = true
    }

    /*
     * OPTIONAL BEANS
     *
     * Uncomment and expand as needed, or if non-default alternate
     * implementations are preferred.
     */

    /**
     * CANONICALIZATION POLICY
     */
    /*
    canonicalizationPolicy(RulesCanonicalizationPolicy) {
        rules = [
            new LowercaseRule(),
            new StripUserinfoRule(),
            new StripWWWNRule(),
            new StripSessionIDs(),
            new StripSessionCFIDs(),
            new FixupQueryString()
        ]
    }
    */

    /**
     * QUEUE ASSIGNMENT POLICY
     */
    /*
    queueAssignmentPolicy(SurtAuthorityQueueAssignmentPolicy) {
        forceQueueAssignment = ''
        deferToPrevious = true
        parallelQueues = 1
    }
    */

    /**
     * URI PRECEDENCE POLICY
     */
    // uriPrecedencePolicy(CostUriPrecedencePolicy)

    /**
     * COST ASSIGNMENT POLICY
     */
    costAssignmentPolicy(UnitCostAssignmentPolicy)

    /**
     * CREDENTIAL STORE: HTTP authentication or FORM POST credentials
     */
    // credentialStore(org.archive.modules.credential.CredentialStore)

    /**
     * DISK SPACE MONITOR:
     * Pauses the crawl if disk space at monitored paths falls below minimum threshold
     * Note: If there's less than 5 GiB free for state directory BDB will throw
     * an error which the crawl job will likely not be able to fully recover from.
     */
    /*
    diskSpaceMonitor(DiskSpaceMonitor) {
        pauseThresholdMiB = 8192
        monitorConfigPaths = true
        monitorPaths = [
                'PATH'
        ]
    }
    */

    /*
     * REQUIRED STANDARD BEANS
     * It will be very rare to replace or reconfigure the following beans.
     */

    /**
     * STATISTICSTRACKER: standard stats/reporting collector
     */
    statisticsTracker(StatisticsTracker) { bean ->
        bean.autowire = 'byName'
        /*
        reports = [
                new CrawlSummaryReport(),
                new SeedsReport(),
                new HostsReport(
                        maxSortSize: -1,
                        suppressEmptyHosts: false,
                ),
                new SourceTagsReport(),
                new MimetypesReport(),
                new ResponseCodeReport(),
                new ProcessorsReport(),
                new FrontierSummaryReport(),
                new FrontierNonemptyReport(),
                new ToeThreadsReport(),
        ]
        */
        // reportsDir = '${launchId}/reports'
        // liveHostReportSize = 20
        // intervalSeconds = 20
        // keepSnapshotsCount = 5
        // liveHostReportSize = 20
    }

    /**
     * CRAWLERLOGGERMODULE: shared logging facility
     */
    loggerModule(CrawlerLoggerModule) {
        // path = '${launchId}/logs'
        // crawlLogPath = 'crawl.log'
        // alertsLogPath = 'alerts.log'
        // progressLogPath = 'progress-statistics.log'
        // uriErrorsLogPath = 'uri-errors.log'
        // runtimeErrorsLogPath = 'runtime-errors.log'
        // nonfatalErrorsLogPath = 'nonfatal-errors.log'
        // logExtraInfo = false
    }

    /**
     * SHEETOVERLAYMANAGER: manager of sheets of contextual overlays
     * Autowired to include any SheetForSurtPrefix or
     * SheetForDecideRuled beans
     */
    sheetOverlaysManager(SheetOverlaysManager) { bean ->
        bean.autowire = 'byType'
    }

    /**
     * BDBMODULE: shared BDB-JE disk persistence manager
     */
    bdb(BdbModule) {
        // dir = 'state'
        /*
         * if neither cachePercent or cacheSize are specified (the default), bdb
         * uses its own default of 60%
         */
        // cachePercent = 0
        // cacheSize = 0
        // useSharedCache = true
        // expectedConcurrency = 25
    }

    /**
     * BDBCOOKIESTORE: disk-based cookie storage for FetchHTTP
     */
    cookieStore(BdbCookieStore) {
        // cookiesLoadFile = null
        // cookiesSaveFile = null
        // bdbModule = ref('bdb')
    }

    /**
     * SERVERCACHE: shared cache of server/host info
     */
    serverCache(BdbServerCache) {
        // bdb = ref('bdb')
    }

    /**
     * CONFIG PATH CONFIGURER: required helper making crawl paths relative
     * to crawler-beans.cxml file, and tracking crawl files for web U
     */
    configPathConfigurer(ConfigPathConfigurer)
}