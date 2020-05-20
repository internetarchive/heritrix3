REST API
========

This manual describes the REST application programming interface (API)
of the Heritrix Web crawler.  Heritrix is the Internet Archive's open
source, extensible, Web-scale, archival-quality Web crawler. For more
information about Heritrix, visit \ http://crawler.archive.org/.

This document is intended for application developers and administrators
interested in controlling the Heritrix Web crawler through its REST API.

Any client that supports HTTPS can be used to invoke the Heritrix API.
The examples in this document use the command line tool curl which
is typically found in most unix environments. Curl is 
\ `available <https://curl.haxx.se/download.html>`__ for many systems
including Windows.

Get Engine Status
~~~~~~~~~~~~~~~~~

.. http:get:: https://(heritrixhost):8443/engine

   Returns information about this instance of Heritrix such as version number, memory usage and the list of crawl jobs.

   **XML Example:**

   .. code:: bash

       curl -v -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine

   Response:

   .. code:: xml

      <engine>
        <heritrixVersion>3.3.0-SNAPSHOT-2017-07-12T04:17:56Z</heritrixVersion>
        <heapReport>
          <usedBytes>69529904</usedBytes>
          <totalBytes>589824000</totalBytes>
          <maxBytes>2885681152</maxBytes>
        </heapReport>
        <jobsDir>/heritrix/jobs</jobsDir>
        <jobsDirUrl>https://localhost:8443/engine/jobsdir/</jobsDirUrl>
        <availableActions>
          <value>rescan</value>
          <value>add</value>
          <value>create</value>
        </availableActions>
        <jobs>
          <value>
            <shortName>myjob</shortName>
            <url>https://localhost:8443/engine/job/myjob</url>
            <isProfile>false</isProfile>
            <launchCount>0</launchCount>
            <lastLaunch/>
            <hasApplicationContext>false</hasApplicationContext>
            <statusDescription>Unbuilt</statusDescription>
            <isLaunchInfoPartial>false</isLaunchInfoPartial>
            <primaryConfig>/heritrix/jobs/myjob/crawler-beans.cxml</primaryConfig>
            <primaryConfigUrl>https://localhost:8443/engine/jobdir/crawler-beans.cxml</primaryConfigUrl>
            <key>myjob</key>
          </value>
        </jobs>
      </engine>

Create New Job
~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine [action=create]

   Creates a new crawl job. It uses the default configuration provided
   by the profile-defaults profile.

   :form action: must be ``create``
   :form createpath: the name of the new job

   **HTML Example:**

   .. code:: bash

      curl -v -d "createpath=myjob&action=create" -k -u admin:admin --anyauth --location \
        https://localhost:8443/engine

   **XML Example:**

   .. code:: bash

      curl -v -d "createpath=myjob&action=create" -k -u admin:admin --anyauth --location \
        -H "Accept: application/xml" https://localhost:8443/engine

Add Job Directory
~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine [action=add]

   Adds a new job directory to the Heritrix configuration. The directory must
   contain a cxml configuration file.

   :form action: must be ``add``
   :form addpath: the job directory to add


   **HTML Example:**

   .. code:: bash

      curl -v -d "action=add&addpath=/Users/hstern/job" -k -u admin:admin --anyauth --location https://localhost:8443/engine

   **XML Example:**

   .. code:: bash

      curl -v -d "action=add&addpath=/Users/hstern/job" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine

Get Job Status
~~~~~~~~~~~~~~

.. http:get:: https://(heritrixhost):8443/engine/job/(jobname)

   Returns status information and statistics about the chosen job.

   **XML Example:**

   .. code:: bash

       curl -v -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

   Response:

   .. code:: xml

      <job>
        <shortName>myjob</shortName>
        <crawlControllerState>FINISHED</crawlControllerState>
        <crawlExitStatus>FINISHED</crawlExitStatus>
        <statusDescription>Finished: FINISHED</statusDescription>
        <availableActions>
          <value>teardown</value>
        </availableActions>
        <launchCount>1</launchCount>
        <lastLaunch>2020-04-01T02:07:42.531Z</lastLaunch>
        <isProfile>false</isProfile>
        <primaryConfig>/heritrix/jobs/myjob/crawler-beans.cxml</primaryConfig>
        <primaryConfigUrl>https://localhost:8443/engine/job/myjob/jobdir/crawler-beans.cxml</primaryConfigUrl>
        <url>https://localhost:8443/engine/job/myjob/job/myjob</url>
        <jobLogTail>
          <value>2020-04-01T03:50:44.708Z INFO FINISHED 20200401020744</value>
          <value>2020-04-01T03:50:42.670Z INFO EMPTY 20200401020744</value>
          <value>2020-04-01T03:50:42.669Z INFO STOPPING 20200401020744</value>
        </jobLogTail>
        <uriTotalsReport>
          <downloadedUriCount>3920</downloadedUriCount>
          <queuedUriCount>0</queuedUriCount>
          <totalUriCount>3920</totalUriCount>
          <futureUriCount>0</futureUriCount>
        </uriTotalsReport>
        <sizeTotalsReport>
          <dupByHash>0</dupByHash>
          <dupByHashCount>0</dupByHashCount>
          <notModified>0</notModified>
          <notModifiedCount>0</notModifiedCount>
          <novel>2177235508</novel>
          <novelCount>3920</novelCount>
          <total>2177235508</total>
          <totalCount>3920</totalCount>
          <warcNovelContentBytes>2177235508</warcNovelContentBytes>
          <warcNovelUrls>3920</warcNovelUrls>
        </sizeTotalsReport>
        <rateReport>
          <currentDocsPerSecond>0.0</currentDocsPerSecond>
          <averageDocsPerSecond>0.6354171124312226</averageDocsPerSecond>
          <currentKiBPerSec>0</currentKiBPerSec>
          <averageKiBPerSec>344</averageKiBPerSec>
        </rateReport>
        <loadReport>
          <busyThreads>0</busyThreads>
          <totalThreads>0</totalThreads>
          <congestionRatio>NaN</congestionRatio>
          <averageQueueDepth>0</averageQueueDepth>
          <deepestQueueDepth>0</deepestQueueDepth>
        </loadReport>
        <elapsedReport>
          <elapsedMilliseconds>6169176</elapsedMilliseconds>
          <elapsedPretty>1h42m49s176ms</elapsedPretty>
        </elapsedReport>
        <threadReport/>
        <frontierReport>
          <totalQueues>1</totalQueues>
          <inProcessQueues>0</inProcessQueues>
          <readyQueues>0</readyQueues>
          <snoozedQueues>0</snoozedQueues>
          <activeQueues>0</activeQueues>
          <inactiveQueues>0</inactiveQueues>
          <ineligibleQueues>0</ineligibleQueues>
          <retiredQueues>0</retiredQueues>
          <exhaustedQueues>1</exhaustedQueues>
          <lastReachedState>FINISH</lastReachedState>
        </frontierReport>
        <crawlLogTail>
          ...
        </crawlLogTail>
        <configFiles>
          ...
        </configFiles>
        <isLaunchInfoPartial>false</isLaunchInfoPartial>
        <isRunning>false</isRunning>
        <isLaunchable>false</isLaunchable>
        <hasApplicationContext>true</hasApplicationContext>
        <alertCount>549</alertCount>
        <checkpointFiles></checkpointFiles>
        <alertLogFilePath>/heritrix/jobs/myjob/20200401020744/logs/alerts.log</alertLogFilePath>
        <crawlLogFilePath>/heritrix/jobs/myjob/20200401020744/logs/crawl.log</crawlLogFilePath>
        <reports>
          <value>
            <className>CrawlSummaryReport</className>
            <shortName>CrawlSummary</shortName>
          </value>
          ...
        </reports>
        <heapReport>
          <usedBytes>66893400</usedBytes>
          <totalBytes>589824000</totalBytes>
          <maxBytes>2885681152</maxBytes>
        </heapReport>
      </job>

Build Job Configuration
~~~~~~~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=build]

   Builds the job configuration for the chosen job. It reads an XML descriptor
   file and uses Spring to build the Java objects that are necessary for
   running the crawl. Before a crawl can be run it must be built.
   
   :form action: must be ``build``

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=build" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "action=build" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Launch Job
~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=launch]

   Launches a crawl job. The job can be launched in the "paused" state or the
   "unpaused" state. If launched in the "unpaused" state the job will
   immediately begin crawling.

   :form action: must be ``launch``

   :form checkpoint: optional field: If supplied, Heritrix will attempt to launch from a checkpoint. Should be the name of a checkpoint (e.g. ``cp00001-20180102121229``) or (since version 3.3) the special value ``latest``, which will automatically select the most recent checkpoint. If no ``checkpoint`` is specified (or if the ``latest`` checkpoint is requested and there are no valid checkpoints) a new crawl will be launched.

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=launch" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "action=launch" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Rescan Job Directory
~~~~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine [action=rescan]

   Rescans the main job directory and returns an HTML page containing all the
   job names. It also returns information about the jobs, such as the location
   of the job configuration file and the number of job launches.

   :form action: must be ``rescan``

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=rescan" -k -u admin:admin --anyauth --location https://localhost:8443/engine

   **XML Example:**

   .. code:: bash

       curl -v -d "action=rescan" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine

Pause Job
~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=pause]

   Pauses an unpaused job. No crawling will occur while a job is paused.

   :form action: must be ``pause``

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=pause" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "action=pause" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Unpause Job
~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=unpause]

   This API unpauses a paused job. Crawling will resume (or begin, in the case
   of a job launched in the paused state) if possible.

   :form action: must be ``unpause``

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=unpause" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "action=unpause" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Terminate Job
~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=terminate]

   Terminates a running job.

   :form action: must be ``terminate``

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=terminate" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "action=terminate" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Teardown Job
~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=teardown]

   Removes the Spring code that is used to run the job. Once a job is torn down
   it must be rebuilt in order to run.

   :form action: must be ``teardown``

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=teardown" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "action=teardown" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Copy Job
~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [copyTo]

   Copies an existing job configuration to a new job configuration. If the "as
   profile" checkbox is selected, than the job configuration is copied as a
   non-runnable profile configuration.

   :form copyTo: the name of the new job or profile configuration

   :form asProfile: whether to copy the job as a runnable configuration or as a
     non-runnable profile. The value ``on`` means the job will be copied as a
     profile. If omitted the job will be copied as a runnable configuration.

   **HTML Example:**

   .. code:: bash

       curl -v -d "copyTo=mycopy&asProfile=on" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "copyTo=mycopy&asProfile=on" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Checkpoint Job
~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=checkpoint]

   This API checkpoints the chosen job. Checkpointing writes the current state
   of a crawl to the file system so that the crawl can be recovered if it
   fails.

   :form action: must be ``checkpoint``

   **HTML Example:**

   .. code:: bash

       curl -v -d "action=checkpoint" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

   **XML Example:**

   .. code:: bash

       curl -v -d "action=checkpoint" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Execute Script in Job
~~~~~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname)/script

   Executes a script. The script can be written as Beanshell, ECMAScript,
   Groovy, or AppleScript.

   :form engine: the script engine to use. One of ``beanshell``, ``js``,
     ``groovy`` or ``AppleScriptEngine``.

   :form script: the script code to execute

   **HTML Example:**

   .. code:: bash

       curl -v -d "engine=beanshell&script=System.out.println%28%22test%22%29%3B" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob/script

   **XML Example:**

   .. code:: bash

       curl -v -d "engine=beanshell&script=System.out.println%28%22test%22%29%3B" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob/script

Submitting a CXML Job Configuration File
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. http:put:: https://(heritrixhost):8443/engine/job/(jobname)/jobdir/crawler-beans.cxml

   Submits the contents of a CXML file for a chosen job. CXML files are the
   configuration files used to control a crawl job. Each job has a single CXML
   file.

   **Example:**

   .. code:: bash

       curl -v -T my-crawler-beans.cxml -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob/jobdir/crawler-beans.cxml

   :statuscode 200: On success, the Heritrix REST API will return a HTTP 200 with no body.

Conventions and Assumptions
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The following curl parameters are used when invoking the API.

+-----------------------------------+-----------------------------------+
| | curl Parameter                  | | Description                     |
+===================================+===================================+
| -v                                | Verbose. Output a detailed        |
|                                   | account of the curl command to    |
|                                   | standard out.                     |
+-----------------------------------+-----------------------------------+
| -d                                | Data. These are the name/value    |
|                                   | pairs that are send in the body   |
|                                   | of a POST.                        |
+-----------------------------------+-----------------------------------+
| -k                                | Insecure. Allows connections to   |
|                                   | SSL sites without certificates.   |
+-----------------------------------+-----------------------------------+
| | -u                              | User. Allows the submission of a  |
|                                   | username and password to          |
|                                   | authenticate the HTTP request.    |
+-----------------------------------+-----------------------------------+
| --anyauth                         | Any authentication type. Allows   |
|                                   | authentication of the request     |
|                                   | based on any type of              |
|                                   | authentication method.            |
+-----------------------------------+-----------------------------------+
| --location                        | Follows HTTP redirects. This      |
|                                   | option is used so that API calls  |
|                                   | that return data (such as HTML)   |
|                                   | will not halt upon receipt of a   |
|                                   | redirect code (such as an HTTP    |
|                                   | 303).                             |
+-----------------------------------+-----------------------------------+
| | -H                              | Set the value of an HTTP header.  |
|                                   | For example, "Accept:             |
|                                   | application/xml".                 |
+-----------------------------------+-----------------------------------+

It is assumed that the reader has a working knowledge of the HTTP
protocol and Heritrix functionality.  Also, the examples assume that
Heritrix is run with an administrative username and password of "admin."

About the REST implementation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Representational State Transfer (REST) is a software architecture for
distributed hypermedia systems such as the World Wide Web (WWW). REST is
built on the concept of representations of resources. Resources can be
any coherent and meaningful concept that may be addressed. A URI is an
example of a resource. The representation of the resource is typically a
document that captures the current or intended state of the resource. An
example of a representation of a resource is an HTML page.

Heritrix uses REST to expose its functionality. The REST implementation
used by Heritrix is Restlet. Restlet implements the concepts defined by
REST, including resources and representations. It also provides a REST
container that processes RESTful requests. The container is the Noelios
Restlet Engine. For detailed information on Restlet,
visit \ http://www.restlet.org/.

Heritrix exposes its REST functionality through HTTPS. The HTTPS
protocol is used to send requests to retrieve or modify configuration
settings and manage crawl jobs.
