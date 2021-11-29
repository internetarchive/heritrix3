Configuring Crawl Jobs
======================

Basic Job Settings
------------------

Crawl settings are configured by editing a job's crawler-beans.cxml file.  Each job has a ``crawler-beans.cxml`` file
that contains the Spring configuration for the job.

Crawl Limits
~~~~~~~~~~~~

In addition to limits imposed on the scope of the crawl it is possible to enforce arbitrary limits on the duration
and extent of the crawl with the following settings:

maxBytesDownload
    Stop the crawl after a fixed number of bytes have been downloaded. Zero means unlimited.

maxDocumentDownload
    Stop the crawl after downloading a fixed number of documents. Zero means unlimited.

maxTimeSeconds
    Stop the crawl after a certain number of seconds have elapsed. Zero means unlimited. For reference there are 3600
    seconds in an hour and 86400 seconds in a day.

To set these values modify the CrawlLimitEnforcer bean.

.. code-block:: xml

   <bean id="crawlLimitEnforcer" class="org.archive.crawler.framework.CrawlLimitEnforcer">
     <property name="maxBytesDownload" value="100000000" />
     <property name="maxDocumentsDownload" value="100" />
     <property name="maxTimeSeconds" value="10000" />
   </bean>

.. note::

   These are not hard limits. Once one of these limits is hit it will trigger a graceful termination of the crawl job.
   URIs already being crawled will be completed. As a result the set limit will be exceeded by some amount.

maxToeThreads
~~~~~~~~~~~~~

The maximum number of toe threads to run.

If running a domain crawl smaller than 100 hosts, a value approximately twice the number of hosts should be enough.
Values larger then 150-200 are rarely worthwhile unless running on machines with exceptional resources.

.. code-block:: xml

   <bean id="crawlController" class="org.archive.crawler.framework.CrawlController">
     <property name="maxToeThreads" value="50" />
   </bean>

metadata.operatorContactUrl
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The URI of the crawl initiator. This setting gives the administrator of a crawled host a URI to refer to in case of
problems.

.. code-block:: xml

   <bean id="simpleOverrides" class="org.springframework.beans.factory.config.PropertyOverrideConfigurer">
     <property name="properties">
       <value>
         metadata.operatorContactUrl=http://www.archive.org
         metadata.jobName=basic
         metadata.description=Basic crawl starting with useful defaults
       </value>
     </property>
   </bean>

Robots.txt Honoring Policy
~~~~~~~~~~~~~~~~~~~~~~~~~~

The valid values of "robotsPolicyName" are:

obey
    Obey robots.txt directives
classic
    Same as "obey"
ignore
    Ignore robots.txt directives

.. code-block:: xml

   <bean id="metadata" class="org.archive.modules.CrawlMetadata" autowire="byName">
   ...
       <property name="robotsPolicyName" value="obey"/>
   ...
   </bean>

Crawl Scope
-----------

The crawl scope defines the set of possible URIs that can be captured by a crawl. These URIs are determined by
DecideRules, which work in combination to limit or expand the set of crawled URIs. Each DecideRule, when presented
with an object (most often a URI of some form) responds with one of three decisions:

ACCEPT
    the object is ruled in
REJECT
    the object is ruled out
PASS
    the rule has no opinion; retain the previous decision

A URI under consideration begins with no assumed status. Each rule is applied in turn to the candidate URI. If the
rule decides ACCEPT or REJECT, the URI's status is set accordingly. After all rules have been applied, the URI is
determined to be "in scope" if its status is ACCEPT. If its status is REJECT it is discarded.

We suggest starting with the rules in our recommended default configurations and performing small test crawls with
those rules. Understand why certain URIs are ruled in or ruled out under those rules. Then make small individual
changes to the scope to achieve non-default desired effects. Creating a new ruleset from scratch can be difficult and
can easily result in crawls that can't make the usual minimal progress that other parts of the crawler expect.
Similarly, making many changes at once can obscure the importance of the interplay and ordering of the rules.

Decide Rules
~~~~~~~~~~~~

:deciderule:`AcceptDecideRule`
    This DecideRule accepts any URI.
:deciderule:`ContentLengthDecideRule`
    This DecideRule accepts a URI if the content-length is less than the threshold.  The default threshold is 2^63,
    meaning any document will be accepted.
:deciderule:`PathologicalPathDecideRule`
    This DecideRule rejects any URI that contains an excessive number of identical, consecutive path-segments.  For
    example, ``http://example.com/a/a/a/a/a/foo.html``.
:deciderule:`PredicatedDecideRule`
    This DecideRule applies a configured decision only if a test evaluates to true.
:deciderule:`ExternalGeoLocationDecideRule`
    This DecideRule accepts a URI if it is located in a particular country.
:deciderule:`FetchStatusDecideRule`
    This DecideRule applies the configured decision to any URI that has a fetch staus equal to the "target-status" setting.
:deciderule:`HasViaDecideRule`
    This DecideRule applies the configured decision to any URI that has a "via."  A via is any URI that is a seed or some kind of mid-crawl addition.
:deciderule:`HopCrossesAssignmentLevelDomainDecideRule`
    This DecideRule applies the configured decision to any URI that differs in the portion of its hostname/domain that is assigned/sold by registrars.  The portion is referred to as the "assignment-level-domain" (ALD).
:deciderule:`IdenticalDigestDecideRule`
    This DecideRule applies the configured decision to any URI whose prior-history content-digest matches the latest fetch.
:deciderule:`MatchesListRegexDecideRule`
    This DecideRule applies the configured decision to any URI that matches the supplied regular expressions.
:deciderule:`NotMatchesListRegexDecideRule`
    This DecideRule applies the configured decision to any URI that does not match the supplied regular expressions.
:deciderule:`MatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI that matches the supplied regular expression.
:deciderule:`ClassKeyMatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI class key that matches the supplied regular expression.  A URI class key is a string that specifies the name of the Frontier queue into which a URI should be placed.
:deciderule:`ContentTypeMatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI whose content-type is present and matches the supplied regular expression.
:deciderule:`ContentTypeNotMatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI whose content-type does not match the supplied regular expression.
:deciderule:`FetchStatusMatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI that has a fetch status that matches the supplied regular expression.
:deciderule:`FetchStatusNotMatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI that has a fetch status that does not match the suppllied regular expression.
:deciderule:`HopsPathMatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI whose "hops-path" matches the supplied regular expression.  The hops-path is a string that consists of characters representing the path that was taken to access the URI.  An example of a hops-path is "LLXE".
:deciderule:`MatchesFilePatternDecideRule`
    This DecideRule applies the configured decision to any URI whose suffix matches the supplied regular expression.
:deciderule:`NotMatchesFilePatternDecideRule`
    This DecideRule applies the configured decision to any URI whose suffix does not match the supplied regular expression.
:deciderule:`NotMatchesRegexDecideRule`
    This DecideRule applies the configured decision to any URI that does not match the supplied regular expression.
:deciderule:`NotExceedsDocumentLengthThresholdDecideRule`
    This DecideRule applies the configured decision to any URI whose content-length does not exceed the configured threshold.  The content-length comes from either the HTTP header or the actual downloaded content length of the URI.  As of Heritrix 3.1, this rule has been renamed to ResourceNoLongerThanDecideRule.
:deciderule:`ExceedsDocumentLengthThresholdDecideRule`
    This DecideRule applies the configured decision to any URI whose content length exceeds the configured threshold.  The content-length comes from either the HTTP header or the actual downloaded content length of the URI.  As of Heritrix 3.1, this rule has been renamed to ResourceLongerThanDecideRule.
:deciderule:`SurtPrefixedDecideRule`
    This DecideRule applies the configured decision to any URI (expressed in SURT form) that begins with one of the
    prefixes in the configured set. This DecideRule returns true when the prefix of a given URI matches any of the
    listed SURTs. The list of SURTs may be configured in different ways: the surtsSourceFile parameter specifies a file
    to read the SURTs list from.  If seedsAsSurtPrefixes parameter is set to true, SurtPrefixedDecideRule adds all seeds
    to the SURTs list. If alsoCheckVia property is set to true (default false), SurtPrefixedDecideRule will also
    consider Via URIs in the match.
    As of Heritrix 3.1, the "surtsSource" parameter may be any ReadSource, such as a ConfigFile or a ConfigString.
    This gives the SurtPrefixedDecideRule the flexibility of the TextSeedModule bean's "textSource" property.
:deciderule:`NotSurtPrefixedDecideRule`
    This DecideRule applies the configured decision to any URI (expressed in SURT form) that does not begin with one of the prefixes in the configured set.
:deciderule:`OnDomainsDecideRule`
    This DecideRule applies the configured decision to any URI that is in one of the domains of the configured set.
:deciderule:`NotOnDomainsDecideRule`
    This DecideRule applies the configured decision to any URI that is not in one of the domains of the configured set.
:deciderule:`OnHostsDecideRule`
    This DecideRule applies the configured decision to any URI that is in one of the hosts of the configured set.
:deciderule:`NotOnHostsDecideRule`
    This DecideRule applies the configured decision to any URI that is not in one of the hosts of the configured set.
:deciderule:`ScopePlusOneDecideRule`
    This DecideRule applies the configured decision to any URI that is one level beyond the configured scope.
:deciderule:`TooManyHopsDecideRule`
    This DecideRule rejects any URI whose total number of hops is over the configured threshold.
:deciderule:`TooManyPathSegmentsDecideRule`
    This DecideRule rejects any URI whose total number of path-segments is over the configured threshold.  A
    path-segment is a string in the URI separated by a "/" character, not including the first "//".
:deciderule:`TransclusionDecideRule`
    This DecideRule accepts any URI whose path-from-seed ends in at least one non-navlink hop. A navlink hop is
    represented by an "L".  Also, the number of non-navlink hops in the path-from-seed cannot exceed the configured
    value.
:deciderule:`PrerequisiteAcceptDecideRule`
    This DecideRule accepts all "prerequisite" URIs.  Prerequisite URIs are those whose hops-path has a "P" in the
    last position.
:deciderule:`RejectDecideRule`
    This DecideRule rejects any URI.
:deciderule:`ScriptedDecideRule`
    This DecideRule applies the configured decision to any URI that passes the rules test of a JSR-223 script.  The
    script source must be a one-argument function called decisionFor."  The function returns the appropriate
    DecideResult. Variables available to the script include object (the object to be evaluated, such as a URI),
    "self" (the ScriptDecideRule instance), and context (the crawl's ApplicationContext, from which all named crawl
    beans are reachable).
:deciderule:`SeedAcceptDecideRule`
    This DecideRule accepts all "seed" URIs (those for which isSeed is true).

DecideRuleSequence Logging
~~~~~~~~~~~~~~~~~~~~~~~~~~

Enable ``FINEST`` logging on the class ``org.archive.crawler.deciderules.DecideRuleSequence`` to watch each
DecideRule's evaluation of the processed URI. This can be done in the ``logging.properties`` file:

.. code-block:: bash

   org.archive.modules.deciderules.DecideRuleSequence.level = FINEST

in conjunction with the ``-Dsysprop`` VM argument,

.. code-block::

   -Djava.util.logging.config.file=/path/to/heritrix3/dist/src/main/conf/logging.properties

Frontier
--------

Politeness
~~~~~~~~~~

A combination of several settings control the politeness of the Frontier. It is important to note that at any given
time only one URI from any given host is processed. The following politeness rules impose additional wait time
between the end of processing one URI and the start of the next one.

delayFactor
    This setting imposes a delay between the fetching of URIs from the same host. The delay is a multiple of the
    amount of time it took to fetch the last URI downloaded from the host. For example, if it took 800 milliseconds to
    fetch the last URI from a host and the ``delayFactor`` is 5 (a very high value), then the Frontier will wait 4000
    milliseconds (4 seconds) before allowing another URI from that host to be processed.

maxDelayMs
    This setting imposes a maximum upper limit on the wait time created by the ``delayFactor``. If set to 1000
    milliseconds, then the maximum delay between URI fetches from the same host will never exceed this value.

minDelayMs
    This setting imposes a minimum limit on politeness. It takes precedence over the value calculated by the
    ``delayFactor``. For example, the value of ``minDelayMs`` can be set to 100 milliseconds. If the ``delayFactor`` only
    generates a 20 millisecond wait, the value of ``minDelayMs`` will override it and the URI fetch will be delayed for
    100 milliseconds.

.. code-block:: xml

    <bean id="disposition" class="org.archive.crawler.postprocessor.DispositionProcessor">
      <property name="delayFactor" value="5.0" />
      <property name="maxDelayMs" value="30000" />
      <property name="minDelayMs" value="3000" />
    </bean>

Retry Policy
~~~~~~~~~~~~

The Frontier can be used to limit the number of fetch retries for a URI.  Heritrix will retry fetching a URI because
the initial fetch error may be a transitory condition.

maxRetries
    This setting limits the number of fetch retries attempted on a URI due to transient errors.
retryDelaySeconds
    This setting determines how long the wait period is between retries.

.. code-block:: xml

   <bean id="frontier" class="org.archive.crawler.frontier.BdbFrontier">
     <property name="retryDelaySeconds" value="900" />
     <property name="maxRetries" value="30" />
   </bean>

Bandwidth Limits
~~~~~~~~~~~~~~~~

The Frontier allows the user to limit bandwidth usage. This is done by holding back URIs when bandwidth usage has
exceeded certain limits. Because bandwidth usage limitations are calculated over a period of time, there can still be
spikes in usage that greatly exceed the limits.

maxPerHostBandwidthUsageKbSec
    This setting limits the maximum bandwidth to use for any host. This setting limits the load placed by Heritrix on the
    host. It is therefore a politeness setting.

    .. code-block:: xml

       <bean id="disposition" class="org.archive.crawler.postprocessor.DispositionProcessor">
         <property name="maxPerHostBandwidthUsageKbSec" value="500" />
       </bean>

Extractor Parameters
~~~~~~~~~~~~~~~~~~~~

The Frontier's behavior with regard to link extraction can be controlled by the following parameters.

extract404s
    This setting allows the operator to avoid extracting links from 404 (Not Found) pages. The default is true, which
    maintains the pre-3.1 behavior of extracting links from 404 pages.

    .. code-block:: xml

       <bean id="frontier" class="org.archive.crawler.frontier.BdbFrontier">
       <property name="extract404s" value="true" />
       </bean>

extractIndependently
    This setting encourages extractor processors to always perform their best-effort extraction, even if a previous
    extractor has marked a URI as already-handled. Set the value to true for best-effort extraction. The default is
    false, which maintains the pre-3.1 behavior.

    .. code-block:: xml

       <bean id="frontier" class="org.archive.crawler.frontier.BdbFrontier">
          <property name="extractIndependently" value="false" />
       </bean>

Sheets (Site-specific Settings)
-------------------------------

Sheets provide the ability to replace default settings on a per domain basis. Sheets are collections of overrides.
They contain alternative values for object properties that should apply in certain contexts. The target is specified
as an arbitrarily-long property-path, which is a string describing how to access the property starting from a
beanName in a BeanFactory.

Sheets allow settings to be overlaid with new values that apply by top level domains (com, net, org, etc), by
second-level domains (yahoo.com, archive.org, etc.), by subdomains (crawler.archive.org, tech.groups.yahoo.com, etc.)
, and leading URI paths (directory.google.com/Top/Computers/, etc.). There is no limit for how long the domain/path
prefix which specifies overlays can go; the `SURT Prefix <Glossary_5735753.html#Glossary-Glossary-SURTPrefix>`_
syntax is used.

Creating a new sheet involves configuring the ``crawler-beans.cxml`` file, which contains the Spring configuration of
a job.

For example, if you have explicit permission to crawl certain domains without the usual polite rate-limiting, then a
Sheet can be used to create a less polite crawling policy that is associated with a few such target domains. The
configuration of such a Sheet for the domains example.com and example1.com are shown below. This example allows up to
5 parallel outstanding requests at a time (rather than the default 1), and eliminates any usual pauses between
requests.

.. warning::

    Unless a target site has given you explicit permission to crawl extra-aggressively, the typical Heritrix defaults,
    which limit the crawler to no more than one outstanding request at a time, with multiple-second waits between
    requests, and longer waits when the site is responding more slowly, are the safest course. Less-polite crawling
    can result in your crawler being blocked entirely by webmasters.

    Finally, even with permission, be sure your crawler's User-Agent string includes a link to valid crawl-operator
    contact information so you can be alerted to, and correct, any unintended side-effects.

.. code-block:: xml

    <bean id="sheetOverlaysManager" autowire="byType" class="org.archive.crawler.spring.SheetOverlaysManager">
    </bean>

    <bean class='org.archive.crawler.spring.SurtPrefixesSheetAssociation'>
      <property name='surtPrefixes'>
        <list>
          <value>http://(com,example,www,)/</value>
          <value>http://(com,example1,www,)/</value>
        </list>
      </property>
      <property name='targetSheetNames'>
        <list>
          <value>lessPolite</value>
        </list>
      </property>
    </bean>

    <bean id='lessPolite' class='org.archive.spring.Sheet'>
      <property name='map'>
        <map>
          <entry key='disposition.delayFactor' value='0.0'/>
          <entry key='disposition.maxDelayMs' value='0'/>
          <entry key='disposition.minDelayMs' value='0'/>
          <entry key='queueAssignmentPolicy.parallelQueues' value='5'/>
        </map>
      </property>
    </bean>

Loading Cookies
---------------

Heritrix can be configured to load a set of cookies from a file. This can be used for example to crawl a website behind
a login form by manually logging in through the browser and then copying the session cookie.

To enable loading of cookies set the cookiesLoadFile property of the BdbCookieStore bean to a ConfigFile:

.. code-block:: xml

    <bean id="cookieStore" class="org.archive.modules.fetcher.BdbCookieStore">
      <property name="cookiesLoadFile">
         <bean class="org.archive.spring.ConfigFile">
           <property name="path" value="cookies.txt" />
         </bean>
      </property>
    </bean>

The cookies.txt should be in the 7-field tab-separated Netscape cookie file format. An entry might look like::

    www.example.org FALSE / FALSE 1311699995 sessionid 60ddb868550a

.. list-table:: Cookie file tab-separated fields

   * - 1
     - DOMAIN
     - The domain that created and has access to the cookie.
   * - 2
     - FLAG
     - A TRUE or FALSE value indicating if subdomains within the given domain can access the cookie.
   * - 3
     - PATH
     - The path within the domain that the cookie is valid for.
   * - 4
     - SECURE
     - A TRUE or FALSE value indicating if the cookie should be sent over HTTPS only.
   * - 5
     - EXPIRATION
     - Expiration time in seconds since 1970-01-01T00:00:00Z, or -1 for no expiration
   * - 6
     - NAME
     - The name of the cookie.
   * - 7
     - VALUE
     - The value of the cookie.

Other Protocols
---------------

In addition to HTTP Heritrix can be configured to fetch resources using several other internet protocols.

FTP
~~~

Heritrix supports crawling `FTP <https://en.wikipedia.org/wiki/File_Transfer_Protocol>`_ sites.  Seeds should be added
in the following format: ```ftp://sftp.example.org/directory``.

The FetchFTP bean needs to be defined:

.. bean-example:: ../modules/src/main/java/org/archive/modules/fetcher/FetchFTP.java

and added to the FetchChain:

.. code-block:: xml

    <bean id="fetchProcessors" class="org.archive.modules.FetchChain">
      <property name="processors">
        <list>...
        <ref bean="fetchFTP"/>
        ...
       </list>
      </property>
    </bean>

SFTP
~~~~

An optional fetcher for `SFTP <https://en.wikipedia.org/wiki/SSH_File_Transfer_Protocol>`_ is provided.  Seeds should
be added in the following format:``sftp://sftp.example.org/directory``.

The FetchSFTP bean needs to be defined:

.. bean-example:: ../modules/src/main/java/org/archive/modules/fetcher/FetchSFTP.java

and added to the FetchChain:

.. code-block:: xml

    <bean id="fetchProcessors" class="org.archive.modules.FetchChain">
      <property name="processors">
        <list>
          ...
          <ref bean="fetchSFTP"/>
          ...
        </list>
      </property>
    </bean>

WHOIS
~~~~~

An optional fetcher for domain `WHOIS <https://en.wikipedia.org/wiki/WHOIS>`_ data is provided. A small set of
well-established WHOIS servers are preconfigured. The fetcher uses an ad-hoc/intuitive interpretation of a 'whois:'
scheme URI.

Define the fetchWhois bean:

.. code-block:: xml

    <bean id="fetchWhois" class="org.archive.modules.fetcher.FetchWhois">
      <property name="specialQueryTemplates">
        <map>
          <entry key="whois.verisign-grs.com" value="domain %s" />
          <entry key="whois.arin.net" value="z + %s" />
          <entry key="whois.denic.de" value="-T dn %s" />
        </map>
      </property>
    </bean>

and add it to the FetchChain:

.. code-block:: xml

    <bean id="fetchProcessors" class="org.archive.modules.FetchChain">
      <property name="processors">
        <list>
          ...
          <ref bean="fetchWhois"/>
          ...
        </list>
      </property>
    </bean>

To configure a whois seed, enter the seed in the following format: ``whois://hostname/path``.  For example,
``whois://archive.org``.  The whois fetcher will attempt to resolve each host that the crawl encounters using the
topmost assigned domain and the ip address of the url crawled. So if you crawl ``http://www.archive.org/details/texts``,
the whois fetcher will attempt to resolve ``whois:archive.org`` and ``whois:207.241.224.2``.

At this time, whois functionality is experimental.  The fetchWhois bean is commented out in the default profile.


Modifying a Running Job
-----------------------

While changing a job's XML configuration normally requires relaunching it, some settings can be modified while the crawl
is running. This is done through the `Browse Beans`_ or the `Scripting Console`_ link on the job page. The Bean Browser
allows you to edit runtime properties of beans. You can also use the scripting console to programmatically edit
a running job.

If changing a non-atomic value, it is a good practice to pause the crawl prior to making the change, as some
modifications to composite configuration entities may not occur in a thread-safe manner. An example of a non-atomic
change is adding a new Sheet.

Browse Beans
~~~~~~~~~~~~

The WUI provides a way to view and edit the Spring beans that make up a crawl configuration. It is important to note
that changing the crawl configuration using the Bean Browser will not update the ``crawler-beans.cxml`` file. Thus,
changing settings with the Bean Browser is not permanent. The Bean Browser should only by used to change the settings
of a running crawl. To access the Bean Browser click on the Browse Beans link from the jobs page. The hierarchy of
Spring beans will be displayed.

.. image:: https://raw.githubusercontent.com/wiki/internetarchive/heritrix3/attachments/5735725/5865655.png

You can drill down on individual beans by clicking on them. The example below shows the display after clicking on the
seeds bean.

.. image:: https://raw.githubusercontent.com/wiki/internetarchive/heritrix3/attachments/5735725/5865656.png

Scripting Console
~~~~~~~~~~~~~~~~~

[This section to be written. For now see the
`Heritrix3 Useful Scripts <https://github.com/internetarchive/heritrix3/wiki/Heritrix3%20Useful%20Scripts>`_ wiki page.]