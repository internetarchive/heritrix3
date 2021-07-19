Operating Heritrix
==================

Running Heritrix
----------------

To launch Heritrix with the Web UI enabled, enter the following command.  The username and password for the Web UI
are set to "admin" and "admin", respectively.

.. code-block:: bash

    $HERITRIX_HOME/bin/heritrix -a admin:admin

By default, the Web UI listening address is only bound to the 'localhost' address.  Therefore, the Web UI can only be
accessed on the same machine from which it was launched. The '-b' option may be used to listen on
different/additional addresses.  See `Security Considerations`_ before changing this default.

Command-line Options
~~~~~~~~~~~~~~~~~~~~

-a, --web-admin ARG
            **(Required)** Sets the username and password required to access the Web UI.

            The argument may be a ``USERNAME:PASSWORD`` such as ``admin:admin``. If the argument is a string
            beginning with "@", the rest of the string is interpreted as a local file name containing the operator
            login and password.
-b, --web-bind-hosts HOST
            Specifies a comma-separated list of hostnames/IP-addresses to bind to the Web UI. You may use '/' as a
            shorthand for 'all addresses'.  **Default**: ``localhost/127.0.0.1``
-j, --job-dirs PATH
            Sets the directory Heritrix stores jobs in. **Default:** ``$HERITRIX_HOME/jobs``
-l, --logging-properties PATH
            Reads logging configuration from a file. **Default:** ``$HERITRIX_HOME/conf/logging.properties``
-p, --web-port PORT
            Sets the port the Web UI will listen on. **Default:** ``8443``
-r, --run-job JOBNAME
            Runs the given Job when Heritirx starts. Heritrix will exit when the job finishes.
-s, --ssl-params ARG
            Specifies a keystore path, keystore password, and key password for HTTPS use.  Separate the values with
            commas and do not include whitespace. By default Heritrix will generate a self-signed certificate the
            first time it is run.

Environment Variables
~~~~~~~~~~~~~~~~~~~~~

The Heritrix launch script ``./bin/heritrix`` obeys the following environment variables:

FOREGROUND
    Set to any value -- e.g. 'true' -- if you want to run heritrix in the foreground.
JAVA_HOME
    Directory where Java is installed.
JAVA_OPTS
    Additional options to pass to the JVM. For example specify ``-Xmx1024M`` to allocate 1GB of memory to Heritrix.
HERITRIX_HOME
    Directory where Heritrix is installed.
HERITRIX_OUT
    Path messages will be logged to when running in background mode. **Default:** ``$HERITRIX_HOME/heritrix_out.log``

.. _security-considerations:

Security Considerations
-----------------------

Heritrix is a large and active network application that presents
security implications, both on the local machine, where it runs, and
remotely, on machines it contacts.

Understanding the Risks
~~~~~~~~~~~~~~~~~~~~~~~

It is important to recognize that the Web UI allows remote control of
the crawler in ways that could potentially disrupt a crawl, change the
crawler's behavior, read or write locally-accessible files, and perform
or trigger other actions in the Java VM or local machine by the
execution of arbitrary operator-supplied scripts.

Unauthorized access to the Web UI could end or corrupt a crawl. It could
also change the crawler's behavior to be a nuisance to other network
hosts. Files accessible to the crawler process could potentially be
deleted, corrupted, or replaced, which could cause extensive problems on
the crawling machine.

Another potential risk is that worst-case or maliciously-crafted
content, in conjunction with crawler issues, could disrupt the crawl or
other files and operations on the local system. For example, in the
past, without malicious intent, some rich-media content has caused
runaway memory use in third-party libraries used by the crawler. This
resulted in memory-exhaustion that stopped and corrupted the crawl in
progress. Similarly, atypical input patterns have caused runaway CPU use
by crawler link-extraction regular expressions, causing severely slow
crawls. Crawl operators should monitor their crawls closely and use the
project discussion list and issue database to stay current on crawler
issues.

Network Access Control
~~~~~~~~~~~~~~~~~~~~~~

Launched without any specified bind-address ('-b' flag), the crawler's
Web UI only binds to the localhost/loopback address (127.0.0.1), and
therefore is only network-accessible from the same machine on which it
was launched.

If practical, this default setting should be maintained. A technique
such as SSH tunneling could be used by authorized users of the crawling
machine to enable Web access from their local machine to the crawling
machine.For example, consider Heritrix running on a machine
'crawler.example.com', with its Web UI only listening/bound on its
localhost address. Assuming a user named 'crawloperator' has SSH access
to 'crawler.example.com', she can issue the following SSH command from
her local machine:

.. code-block:: bash

   ssh -L localhost:9999:localhost:8443 crawloperator@crawler.example.com -N

This tells SSH to open a tunnel which forwards conections to
"localhost:9999" (on the local machine) to the remote machines' own idea
of "localhost:8443". As a result, the crawler's Web UI will be available
via "https://localhost:9999/" for as long as the tunnel exists (until
the ssh command is killed or connection otherwise broken). No one else
on the network may directly connect to port 8443 on
'crawler.example.com' (since it is only listening on the local loopback
address), and no one elsewhere on the net may directly connect to the
operator's port 9999 (since it also is only listening on the local
loopback address).

If you need Heritrix's listening port bound to a public address, the
'-b' command-line flag may be used. This flag takes, as an argument,
the hostname/address to use. The '/' character can be used to indicate
all addresses.

If you use this option, you should take special care to choose an even
more unique/unguessable/brute-force-search-resistant set of login
credentials. You may still want to consider using other network/firewall
policies to block access from unauthorized origins.

Login Authentication Access Control
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The administrative login and password only offer rudimentary protection
against unauthorized access. For best security, you should be sure to:

#. Use a strong, unique username and password combination to secure the
   Web UI. Heritrix uses HTTPS to encrypt communication between the
   client and the Web UI. Keep in mind that setting the username and
   password on the command-line may result in their values being
   visible to other users of the crawling machine – for example, via
   the output of a tool like 'ps' that shows the command-lines used to
   launch processes. Additionally, note that these values are echoed in
   plain text in the ``heritrix_out.log`` for operator reference. As of
   Heritrix 3.1, the administrative username and password are no longer
   echoed to ``heritrix_out.log``. Also, if the
   parameter supplied to the -a command line option is a string
   beginning with "@", the rest of the string is interpreted as a local
   file name containing the operator login and password. Thus, the
   credentials are not visible to other machines that use the process
   listing (ps) command.
#. Launch the Heritrix-hosting Java VM with a user-account that has the
   minimum privileges necessary for operating the crawler. This will
   limit the damage in the event that the Web UI is accessed
   maliciously.

Log Files
---------

Each crawl job has its own set of log files found in the ``logs`` subdirectory of a job launch directory.

Logging can be configured by modifying the ``logging.properties`` file
that is located under the ``$HERITRIX_HOME/conf`` directory. For information on using
logging properties, visit http://logging.apache.org/log4j/.

alerts.log
~~~~~~~~~~

This log contains alerts that indicate problems with a crawl.

crawl.log
~~~~~~~~~

Each URI that Heritrix attempts to fetch will cause a log line to be
written to the ``crawl.log`` file. Below is a two line extract from the
log.

.. code-block::

   2011-06-23T17:12:08.802Z   200       1299 http://content-5.powells.com/robots.txt LREP http://content-5.powells.com/cgi-bin/imageDB.cgi?isbn=9780385518635 text/plain #014 20110623171208574+225 sha1:YIUOKDGOLGI5JYHDTXRFFQ5FF4N2EJRV - -
   2011-06-23T17:12:09.591Z   200      15829 http://www.identitytheory.com/etexts/poetics.html L http://www.identitytheory.com/ text/html #025 20110623171208546+922 sha1:7AJUMSDTOMT4FN7MBFGGNJU3Z56MLCMW - -

Field 1. Timestamp
    The timestamp in ISO8601 format, to millisecond resolution. The time is the instant of logging.
Field 2. :ref:`Fetch Status Code <status-codes>`
    Usually this is the HTTP response code but it can also be a negative number if URI processing was unexpectedly
    terminated.
Field 3. Document Size
    The size of the downloaded document in bytes. For HTTP, this is the size of content only. The size excludes the
    HTTP response headers. For DNS, the size field is the total size for the DNS response.
Field 4. Downloaded URI
    The URI of the document downloaded.
Field 5. Discovery Path
    The breadcrumb codes (discovery path) showing the trail of downloads that lead to the downloaded URI. The length
    of the discovery path is limited to the last 50 hop-types. For example, a  62-hop path
    might appear as "12+LLRLLLRELLLLRLLLRELLLLRLLLRELLLLRLLLRELLLLRLLLRELE".

    The breadcrumb codes are as follows.

    =  ========
    R  Redirect
    E  Embed
    X  Speculative embed (aggressive/Javascript link extraction)
    L  Link
    P  Prerequisite (as for DNS or robots.txt before another URI)
    =  ========
Field 6. Referrer
    The URI that immediately preceded the downloaded URI. This is the referrer. Both the discovery path and the
    referrer will be empty for seed URIs.
Field 7. Mime Type
    The downloaded document mime type.
Field 8. Worker Thread ID
    The id of the worker thread that downloaded the document.
Field 9. Fetch Timestamp
    The timestamp in RFC2550/ARC condensed digits-only format indicating when the network fetch was started. If
    appropriate the millisecond duration of the fetch is appended to the timestamp with a ";" character as
    separator.
Field 10. SHA1 Digest
    The SHA1 digest of the content only (headers are not digested).
Field 11. Source Tag
    The source tag inherited by the URI, if source tagging is enabled.
Field 12. Annotations
    If an annotation has been set, it will be displayed. Possible annotations include: the number of times the URI
    was tried, the literal "lenTrunc"; if the download was truncanted due to exceeding configured size limits,
    the literal "timeTrunc"; if the download was truncated due to exceeding configured time limits or
    "midFetchTrunc"; if a midfetch filter determined the download should be truncated.
Field 13. WARC Filename
    The name of the WARC/ARC file to which the crawled content is written. This value will only be written if
    thelogExtraInfo property of the loggerModule bean is set to true. This logged information will be written in
    JSON format.

progress-statistics.log
~~~~~~~~~~~~~~~~~~~~~~~

This log is written by the StatisticsTracker bean. At configurable
intervals, a log line detailing the progress of the crawl is written to
this file.


Field 1. timestamp
    Timestamp in ISO8601 format indicating when the log line was written.
Field 2. discovered
    Number of URIs discovered to date.
Field 3. queued
    Number of URIs currently queued.
Field 3. downloaded
    Number of URIs downloaded to date.
Field 4. doc/s(avg)
    Number of document downloaded per second since the last snapshot. The value in parenthesis is measured since the
    crawl began.
Field 5. KB/s(avg)
    Amount in kilobytes downloaded per second since the last snapshot. The value in parenthesis is measured since the
    crawl began.
Field 6. dl-failures
    Number of URIs that Heritrix has failed to download.
Field 7. busy-thread
    Number of toe threads busy processing a URI.
Field 8. mem-use-KB
    Amount of memory in use by the Java Virtual Machine.
Field 9. heap-size-KB
    The current heap size of the Java Virtual Machine.
Field 10. congestion
    The congestion ratio is a rough estimate of how much initial capacity, as a multiple of current capacity, would
    be necessary to crawl the current workload at the maximum rate available given politeness settings. This value is
    calculated by comparing the number of internal queues that are progressing against those that are waiting for a
    thread to become available.
Field 11. max-depth
    The size of the Frontier queue with the largest number of queued URIs.
Field 12. avg-depth
    The average size of all the Frontier queues.

runtime-errors.log
~~~~~~~~~~~~~~~~~~

This log captures unexpected exceptions and errors that occur during the
crawl. Some may be due to hardware limitations (out of memory, although
that error may occur without being written to this log), but most are
probably due to software bugs, either in Heritrix's core but more likely
in one of its pluggable classes.

uri-errors.log
~~~~~~~~~~~~~~

This log stores errors that resulted from attempted URI fetches.
Usually the cause is non-existent URIs. This log is usually only of
interest to advanced users trying to explain unexpected crawl behavior.

Reports
-------

Reports are found in the "reports" directory, which exists under the
directory of a specific job launch.

Crawl Summary (crawl-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This file contains useful metrics about completed jobs.  The report is created by the StatisticsTracker bean.  This
file is written at the end of the crawl.

Below is sample output from this file::

    Crawl Name: basic
    Crawl Status: Finished
    Duration Time: 1h33m38s651ms
    Total Seeds Crawled: 1
    Total Seeds not Crawled: 0
    Total Hosts Crawled: 1
    Total URIs Processed: 1337
    URIs Crawled successfully: 1337
    URIs Failed to Crawl: 0
    URIs Disregarded: 0
    Processed docs/sec: 0.24
    Bandwidth in Kbytes/sec: 4
    Total Raw Data Size in Bytes: 23865329 (23 MB)
    Novel Bytes: 23877375 (23 MB)

Crawl Name
    The user-defined name of the crawl.
Crawl Status
    The status of the crawl, such as "Aborted" or "Finished."
Duration Time
    The duration of the crawl to the nearest millisecond.
Total Seeds Crawled
    The number of seeds that were successfully crawled.
Total Seeds Not Crawled
    The number of seeds that were not successfully crawled.
Total Hosts Crawled
    The number of hosts that were crawled.
Total URIs Processed
    The number of URIs that were processed.
URIs Crawled Successfully
    The number of URIs that were crawled successfully.
URIs Failed to Crawl
    The number of URIs that could not be crawled.
URIs Disregarded
    The number of URIs that were not selected for crawling.
Processed docs/sec
    The average number of documents processed per second.
Bandwidth in Kbytes/sec
    The average number of kilobytes processed per second.
Total Raw Data Size in Bytes
    The total amount of data crawled.
Novel Bytes
    New bytes since last crawl.

Seeds (seeds-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~

This file contains the crawling status of each seed.

This file is created by the StatisticsTracker bean and is written at the end of the crawl.

Below is sample output from this report::

    [code] [status] [seed] [redirect]
    200 CRAWLED http://www.smokebox.net

code
    :ref:`Status code <status-codes>` for the seed URI
status
    Human readable description of whether the seed was crawled. For example, "CRAWLED."
seed
    The seed URI.
redirect
    The URI to which the seed redirected.

Hosts (hosts-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~

This file contains an overview of the hosts that were crawled.  It also displays the number of documents crawled and the bytes downloaded per host.

This file is created by the StatisticsTracker bean and is written at the end of the crawl.

Below is sample output from this file::

    1337 23877316 www.smokebox.net 0 0
    1 59 dns: 0 0
    0 0 dns: 0 0

#urls
    The number of URIs crawled for the host.
#bytes
    The number of bytes crawled for the host.
host
    The hostname.
#robots
    The number of URIs, for this host, excluded because of ``robots.txt`` restrictions. This number does not include linked URIs from the specifically excluded URIs.
#remaining
    The number of URIs, for this host, that have not been crawled yet, but are in the queue.
#novel-urls
    The number of new URIs crawled for this host since the last crawl.
#novel-bytes
    The amount of new bytes crawled for this host since the last crawl.
#dup-by-hash-urls
    The number of URIs, for this host, that had the same hash code and are essentially duplicates.
#dup-by-hash-bytes
    The number of bytes of content, for this host, having the same hashcode.
#not-modified-urls
    The number of URIs, for this host, that returned a `304 <http://en.wikipedia
    .org/wiki/List_of_HTTP_status_codes#3xx_Redirection>`_ status code.
#not-modified-bytes
    The amount of of bytes of content, for this host, whose URIs returned a `304 <http://en.wikipedia
    .org/wiki/List_of_HTTP_status_codes#3xx_Redirection>`_ status code.

SourceTags (source-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This report contains a line item for each host, which includes the seed from which the host was reached.

Below is a sample of this report::

    [source] [host] [#urls]
    http://www.fizzandpop.com/ dns: 1
    http://www.fizzandpop.com/ www.fizzandpop.com 1

source
    The seed.
host
    The host that was accessed from the seed.
#urls
    The number of URIs crawled for this seed host combination.

Note that the SourceTags report will only be generated if the
``sourceTagSeeds`` property of the ``TextSeedModule`` bean is set to true.

.. code-block:: xml

   <bean id="seeds" class="org.archive.modules.seeds.TextSeedModule">
     <property name="sourceTagsSeeds" value="true" />
   </bean>

Mimetypes (mimetype-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This file contains a report displaying the number of documents downloaded per mime type.  Also, the amount of data downloaded per mime type is displayed.

This file is created by the StatisticsTracker bean and is written at the end of the crawl.

Below is sample output from this report::

    624 13248443 image/jpeg
    450 8385573 text/html
    261 2160104 image/gif
    1 74708 application/x-javascript
    1 59 text/dns
    1 8488 text/plain

#urls
    The number of URIs crawled for a given mime-type.
#bytes
    The number of bytes crawled for a given mime-type.
mime-types
    The mime-type.

ResponseCode (responsecode-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This file contains a report displaying the number of documents downloaded per status code.  It covers successful
codes only.  For failure codes see the crawl.log file.

This file is created by the StatisticsTracker bean and is written at the end of the crawl.

Below is sample output from this report::

    [#urls] [rescode]
    1306 200
    31 404
    1 1

#urls
    The number of URIs crawled for a given response code.
rescode
    The response code.

Processors (processors-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This report shows the activity of each processor involved in the crawl.
For example, the ``FetchHTTP`` processor is included in the report. For
this processor the number of URIs fetched is displayed. The report is
organized to report on each Chain (Candidate, Fetch, and Disposition)
and each processor in each chain. The order of the report is per the
configuration order in the ``crawler-beans.cxml`` file.

Below is sample output from this report::

    CandidateChain - Processors report - 200910300032
      Number of Processors: 2

    Processor: org.archive.crawler.prefetch.CandidateScoper

    Processor: org.archive.crawler.prefetch.FrontierPreparer

    FetchChain - Processors report - 200910300032
      Number of Processors: 9

    Processor: org.archive.crawler.prefetch.Preselector

    Processor: org.archive.crawler.prefetch.PreconditionEnforcer

    Processor: org.archive.modules.fetcher.FetchDNS

    Processor: org.archive.modules.fetcher.FetchHTTP
      Function:          Fetch HTTP URIs
      CrawlURIs handled: 1337
      Recovery retries:   0

    Processor: org.archive.modules.extractor.ExtractorHTTP
      Function:          Extracts URIs from HTTP response headers
      CrawlURIs handled: 1337  Links extracted:   0

    Processor: org.archive.modules.extractor.ExtractorHTML
      Function:          Link extraction on HTML documents
      CrawlURIs handled: 449
      Links extracted:   6894
    ...

FrontierSummary (frontier-summary-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This link displays a report showing the hosts that are queued for
capture. The hosts are contained in multiple queues. The details of
each Frontier queue is reported.

ToeThreads (threads-report.txt)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This link displays a report showing the activity of each thread used by
Heritrix. The amount of time the thread has been running is displayed
as well as thread state and thread Blocked/Waiting status.


Action Directory
----------------

Each job directory contains an action directory. By placing files in the
action directory you can trigger actions in a running crawl job, such as
the addition of new URIs to the crawl.

At a regular interval (by default less than a minute), the crawl will
notice any new files in this directory, and take action based on their
filename suffix and their contents. When the action is done, the file
will be moved to the nearby 'done' directory. (For this reason, files
should be composed outside the action directory, then moved there as an
atomic whole. Otherwise, a file may be processed-and-moved while still
being composed.)

The following file suffixes are supported:

.seeds
    A .seeds file should contain seeds that the Heritrix operator wants to include in the crawl. Placing a .seeds
    file in the action directory will add the seeds to the running crawl. The same directives as may be used in
    seeds-lists during initial crawl configuration may be used here.

    If seeds introduced into the crawl this way were already in the frontier (perhaps already a seed) this method
    does not force them.

.recover
    .recover file will be treated as a traditional recovery journal. (The recovery journal can approximately reproduce
    the state of a crawl's queues and already-included set, by repeating all URI-completion and URI-discovery events. A
    recovery journal reproduces less state than a proper checkpoint.) In a first pass, all lines beginning with Fs in the
    recovery journal will be considered included, so that they can not be enqueued again. Then in a second pass, lines
    starting with F+ will be re-enqueued for crawling (if not precluded by the first pass).

.include
    A .include file will be treated as a recovery journal, but all URIs no matter what their line-prefix will be marked
    as already included, preventing them from being re-enqueued from that point on. (Already-enqueued URIs will still be
    eligible for crawling when they come up.) Using a .include file is a way to suppress the re-crawling of URIs.

.schedule
    A .schedule file will be treated as a recovery journal, but all URIs no matter what their line-prefix will be offered
    for enqueueing. (However, if they are recognized as already-included, they will not be enqueued.) Using a .schedule
    file is a way to include URIs in a running crawl by inserting them into the Heritrix crawling queues.

.force
    A .force file will be treated as a recovery journal with all the URIs marked for force scheduling.  Using a .force
    file is a way to guarantee that already-included URIs will be re-enqueued and (and thus eventually re-crawled).

Any of these files may be gzipped. Any of the files in recovery journal
format (\ ``.recover``\ , ``.include``\ , ``.schedule``\ , ``.force``\ ) may have a ``.s``
inserted prior to the functional suffix (for example,
``frontier.s.recover.gz``\ ), which will cause the URIs to be scope-tested
before any other insertion occurs.

For example you could place the following ``example.schedule`` file in the action directory
to schedule a URL::

    F+ http://example.com

In order to use the action directory, the ``ActionDirectory`` bean must be
configured in the ``crawler-beans.cxml`` file as illustrated below.

.. code-block:: xml

   <bean id="actionDirectory" class="org.archive.crawler.framework.ActionDirectory">
     <property name="actionDir" value="action" />
     <property name="initialDelaySeconds" value="10" />
     <property name="delaySeconds" value="30" />
   </bean>

The recovery journal directives are listed below:

==  ===========
F+  Add
Fe  Emit
Fi  Include
Fd  Disregard
Fr  Re-enqueued
Fs  Success
Ff  Failure
==  ===========

Note that the recovery journal format's 'F+' lines may include a
'hops-path' and 'via URI', which are preserved when a URI is enqueued
via the above mechanisms, but that this may not be a complete
representation of all URI state from its discovery in a normal crawl.

Crawl Recovery
--------------

During normal operation, the Heritrix Frontier keeps a journal. The
journal is kept in the logs directory. It is named
``frontier.recovery.gz``. If a crash occurs during a crawl, the
``frontier.recover.gz`` journal can be used to recreate the approximate
status of the crawler at the time of the crash. In some cases, recovery
may take an extended period of time, but it is usually much quicker than
repeating the crashed crawl.

If using this process, you are starting an all-new crawl, with your same
(or modified) configuration, but this new crawl will take an extended
detour at the beginning where it uses the prior crawl's
frontier-recover.gz output(s) to simulate the frontier status
(discovered-URIs, enqueued-URIs) of the previous crawl. You would move
aside all ARC/WARCs, logs, and checkpoints from the earlier crawl,
retaining the logs and ARC/WARCs as a record of the crawl so far.

Any ARC/WARC files that exist with the ``.open`` suffix were not properly
closed by the previous run, and may include corrupt/truncated data in
their last partial record. You may rename files with a ``.warc.gz.open``
suffix to ``.warc.gz``, but consider validating such ARC/WARCs (by
zcat'ing the file to /dev/null to check gzip validity, or other ARC/WARC
tools for record completeness) before removing the ".open" suffix.

Full recovery
~~~~~~~~~~~~~

To run the recovery process, relaunch the crashed crawler and copy the ``frontier.recover.gz`` file into the `Action
Directory`_. Then re-start the crawl. Heritrix will automatically load the recovery file and begin placing its URIs
into the Frontier for crawling.

If using a ``.recover.gz`` file, a single complete file must be used.
(This is so that the action directory processing of one file at a time
can do the complete first pass of 'includes', then the complete full
pass of 'schedules', from one file. Supplying multiple ``.recover.gz``
files in series will result in an includes/schedules,
includes/schedules, etc. cycle which will not produce the desired effect
on the frontier.)

While the file is being processed, any checkpoints (manual or
auto-periodic) will **not** be a valid snapshot of the crawler state.
(The frontier-recovery log process happens via a separate thread/path
outside the newer checkpointing system.) Only when the file processing
is completed (file moved to 'done') will the crawler be in an accurately
checkpointable state.

Once URIs start appearing in the queues (the recovery has entered the
'schedules' pass), the crawler may be unpaused to begin fetching URIs
while the rest of the 'schedules' recovery pass continues. However, the
above note about checkpoints still applies: only when the
frontier-recovery file-processing is finished may an accurate checkpoint
occur. Also, unpausing the crawl in this manner may result in some URIs
being rediscovered via new paths before the original discovery is
replayed via the recovery process. (Many crawls may not mind this slight
deviation from the recovered' crawls state, but if your scoping is very
path- or hop- dependent it could make a difference in what is
scope-included.)

.. note::

    Feeding the entire frontier back to the crawler is likely to
    produce many *"Problem line"* warnings in the job log. Some operators
    find it useful to allow the entire recovery file to be ingested by the
    crawler before attempting to resume (unpause), to help isolate this
    chatter, and to minimize generating duplicate crawldata during recovery.

Split Recovery
~~~~~~~~~~~~~~

An alternate way to run the recovery process is illustrated below. By
eliminating irrelevant lines early (outside the recovery process), it
may allow the recovery process to complete more quickly than the
standard process. It also allows the process to proceed from many files,
rather than a single file, so may give a better running indication of
progress, and chances to checkpoint the recover.

To run the alternate recovery process:

#. move aside prior logs and ARCs/WARCs as above
#. relaunch the crashed crawler
#. Split any source ``frontier.recover.gz`` files using commands like the
   following:

    .. code-block:: bash

       zcat frontier.recover.gz | grep '^Fs' | gzip > frontier.include.gz
       zcat frontier.recover.gz | grep '^F+' | gzip > frontier.schedule.gz

#. Build and launch the previously failed job (with the same or
   adjusted configuration). The job will now be paused.
#. Move the ``frontier.include.gz`` file(s) into the action directory.
   The ``action`` directory is located at the same level in the file
   structure hierarchy as the ``bin`` directory. (If you have many, you
   may move them all in at once, or in small batches to better monitor
   their progress. At any point when all previously-presented files are
   processed – that is, moved to the 'done' directory – it is possible
   to make a valid checkpoint.)
#. You may watch the progress of this 'includes' phase by viewing the
   web UI or ``progress-statistics.log`` and seeing the ``discovered``
   count rise.
#. When all ``.includes`` are finished loading, you can repeat the
   process with all the ``.schedule`` logs.
#. When you notice a large number (many thousands) of URIs in the
   ``queued`` count, you may unpause the crawl to let new crawling
   proceed in parallel to the enqueueing of older URIs.

You **may** drop all ``.include`` and ``.schedule`` files into the action
directory before launch, if you are confident that the lexicographic
ordering of their names will do the right thing (present all
``.include`` files first, and the ``.schedule`` files in the same order as the
original crawl). But, that leave little opportunity to adjust/checkpoint
the process: the action directory will discover them all and process
them all in one tight loop.

.. note::

    To be sure of success and current crawl status against any sort
    of possible IO/format errors, in large recoveries of millions of
    records, you may want to wait for each step to complete before moving a
    file, or unpausing the job. Instead of looking at progress-statistics,
    simply wait for the file to move from action to action/done. Then add
    the second file. Wait again. Finally unpause the crawler.

    A recovery of 100M URIs may take days, so please be patient.
