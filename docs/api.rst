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

Create New Job
~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine [action=create]

   Creates a new crawl job. It uses the default configuration provided
   by the profile-defaults profile.

   :form action: must be ``create``
   :form createpath: the name of the new job

   **HTML Example**:

   .. code:: bash

      curl -v -d "createpath=myjob&action=create" -k -u admin:admin --anyauth --location \
        https://localhost:8443/engine

   **XML Example**:

   .. code:: bash

      curl -v -d "createpath=myjob&action=create" -k -u admin:admin --anyauth --location \
        -H "Accept: application/xml" https://localhost:8443/engine

Add Job Directory
~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine [action=add]

.. _description-1:

Description
^^^^^^^^^^^

This API adds a new job directory to the Heritrix configuration. The
directory must contain a cxml configuration file.

.. _http-data-1:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | addpath            | | (job directory to  | | The job directory  |
|                      |   add)               |   to add.            |
+----------------------+----------------------+----------------------+
| | action             | | add                | | The action to      |
|                      |                      |   invoke             |
+----------------------+----------------------+----------------------+

.. _html-example-1:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=add&addpath=/Users/hstern/job" -k -u admin:admin --anyauth --location https://localhost:8443/engine

.. _xml-example-1:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=add&addpath=/Users/hstern/job" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine

Build Job Configuration
~~~~~~~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=build]

.. _description-2:

Description
^^^^^^^^^^^

This API builds the job configuration for the chosen job. It reads an
XML descriptor file and uses Spring to build the Java objects that are
necessary for running the crawl. Before a crawl can be run it must be
built.

.. _http-data-2:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | build              | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-2:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=build" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-2:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=build" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Launch Job
~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=launch]

.. _description-3:

Description
^^^^^^^^^^^

This API launches a crawl job. The job can be launched in the "paused"
state or the "unpaused" state. If launched in the "unpaused" state the
job will immediately begin crawling.

.. _http-data-3:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | launch             | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-3:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=launch" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-3:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=launch" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Rescan Job Directory
~~~~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine [action=rescan]

.. _description-4:

Description
^^^^^^^^^^^

This API rescans the main job directory and returns an HTML page
containing all the job names. It also returns information about the
jobs, such as the location of the job configuration file and the number
of job launches.

HTTP Data

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | rescan             | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-4:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=rescan" -k -u admin:admin --anyauth --location https://localhost:8443/engine

.. _xml-example-4:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=rescan" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine

Pause Job
~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=pause]

.. _description-5:

Description
^^^^^^^^^^^

This API pauses an unpaused job. No crawling will occur while a job is
paused.

.. _http-data-4:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | pause              | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-5:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=pause" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-5:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=pause" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Unpause Job
~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=unpause]

.. _description-6:

Description
^^^^^^^^^^^

This API unpauses a paused job. Crawling will resume (or begin, in the
case of a job launched in the paused state) if possible.

.. _http-data-5:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | unpause            | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-6:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=unpause" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-6:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=unpause" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Terminate Job
~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=terminate]

.. _description-7:

Description
^^^^^^^^^^^

This API terminates a running job.

.. _http-data-6:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | terminate          | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-7:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=terminate" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-7:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=terminate" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Teardown Job
~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=teardown]

.. _description-8:

Description
^^^^^^^^^^^

This API removes the Spring code that is used to run the job. Once a job
is torn down it must be rebuilt in order to run.

.. _http-data-7:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | teardown           | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-8:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=teardown" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-8:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=teardown" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Copy Job
~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [copyTo]

.. _description-9:

Description
^^^^^^^^^^^

This API copies an existing job configuration to a new job
configuration. If the "as profile" checkbox is selected, than the job
configuration is copied as a non-runnable profile configuration.

HTTP Data

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | copyTo             | (new job or profile  | The name of the new  |
|                      | configuration name)  | job or profile       |
|                      |                      | configuration.       |
+----------------------+----------------------+----------------------+
| asProfile            | | [on]               | Whether to copy the  |
|                      |                      | job as a runnable    |
|                      |                      | configuration or as  |
|                      |                      | a non-runnable       |
|                      |                      | profile. "On" means  |
|                      |                      | the job will be      |
|                      |                      | copied as a profile. |
|                      |                      | If the "asProfile"   |
|                      |                      | parameter is         |
|                      |                      | ommitted, the job    |
|                      |                      | will be copied as a  |
|                      |                      | runnable             |
|                      |                      | configuration.       |
+----------------------+----------------------+----------------------+

.. _html-example-9:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "copyTo=mycopy&asProfile=on" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-9:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "copyTo=mycopy&asProfile=on" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Checkpoint Job
~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname) [action=checkpoint]

.. _description-10:

Description
^^^^^^^^^^^

This API checkpoints the chosen job. Checkpointing writes the current
state of a crawl to the file system so that the crawl can be recovered
if it fails.

.. _http-data-8:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | action             | | checkpoint         | | The action to      |
|                      |                      |   invoke.            |
+----------------------+----------------------+----------------------+

.. _html-example-10:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=checkpoint" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob

.. _xml-example-10:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "action=checkpoint" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob

Execute Shell Script in Job
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. http:post:: https://(heritrixhost):8443/engine/job/(jobname)/script

.. _description-11:

Description
^^^^^^^^^^^

This API executes a shell script. The script can be written as
Beanshell, ECMAScript, Groovy, or AppleScript.

.. _http-data-9:

HTTP Data
^^^^^^^^^

+----------------------+----------------------+----------------------+
| | Name               | | Value              | | Description        |
+======================+======================+======================+
| | engine             | | [beanshell,js,groo | The script engine to |
|                      | vy,AppleScriptEngine | use.                 |
|                      | ]                    |                      |
+----------------------+----------------------+----------------------+
| script               | (code to execute)    | The script code to   |
|                      |                      | execute.             |
+----------------------+----------------------+----------------------+

.. _html-example-11:

HTML Example
^^^^^^^^^^^^

.. code:: bash

    curl -v -d "engine=beanshell&script=System.out.println%28%22test%22%29%3B" -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob/script

.. _xml-example-11:

XML Example
^^^^^^^^^^^

.. code:: bash

    curl -v -d "engine=beanshell&script=System.out.println%28%22test%22%29%3B" -k -u admin:admin --anyauth --location -H "Accept: application/xml" https://localhost:8443/engine/job/myjob/script

Submitting a CXML Job Configuration File
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. http:put:: https://(heritrixhost):8443/engine/job/(jobname)/jobdir/crawler-beans.cxml

.. _description-12:

Description
^^^^^^^^^^^

This API submits the contents of a CXML file for a chosen job. CXML
files are the configuration files used to control a crawl job. Each job
has a single CXML file.

.. _http-data-10:

HTTP Data
^^^^^^^^^

+-----------------------------------+-----------------------------------+
| (CXML file content)               | The XML-based text of the CXML    |
|                                   | file.                             |
+-----------------------------------+-----------------------------------+

Example
^^^^^^^

.. code:: bash

    curl -v -T my-crawler-beans.cxml -k -u admin:admin --anyauth --location https://localhost:8443/engine/job/myjob/jobdir/crawler-beans.cxml

API Response
^^^^^^^^^^^^

On success, the Heritrix REST API will return a HTTP 200 with no body.

Conventions and Assumptions
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The following conventions are used in this document.

+-----------------------------------+-----------------------------------+
| | Convention                      | | Description                     |
+===================================+===================================+
| (identifier)                      | A identifier surrounded by        |
|                                   | parenthesis indicates a           |
|                                   | user-defined value. For example,  |
|                                   | (heritrixhostname) indicates a    |
|                                   | user-defined hostname that is     |
|                                   | running Heritrix.                 |
+-----------------------------------+-----------------------------------+
| [identifier1,identifier2,...]     | Multiple identifiers surrounded   |
|                                   | by brackets indicate a predefined |
|                                   | set of values. For example,       |
|                                   | [on,off] indicates a set of       |
|                                   | values comprised of the literals, |
|                                   | "on" and "off".                   |
+-----------------------------------+-----------------------------------+

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

API Format
^^^^^^^^^^

The format used to describe each API is as follows.

+-----------------------------------+-----------------------------------+
| | Name                            | | Description                     |
+===================================+===================================+
| | API Name                        | The name assigned to the API. The |
|                                   | name is a single word or short    |
|                                   | phrase that encapsulates the      |
|                                   | purpose of the API call.          |
+-----------------------------------+-----------------------------------+
| URI                               | The URI to call when invoking the |
|                                   | API.                              |
+-----------------------------------+-----------------------------------+
| Description                       | The description of the API. The   |
|                                   | description provides a detailed   |
|                                   | overview of what the API          |
|                                   | accomplishes and when the API     |
|                                   | should be called.                 |
+-----------------------------------+-----------------------------------+
| HTTP Method                       | The HTTP method to use when       |
|                                   | invoking the API.                 |
+-----------------------------------+-----------------------------------+
| HTTP Data                         | The name/value pairs that are     |
|                                   | submitted with the HTTP request.  |
+-----------------------------------+-----------------------------------+
| HTML Example                      | | An example call to the API. The |
|                                   |   curl command line utility is    |
|                                   |   the HTTPS client used in the    |
|                                   |   examples. The call returns HTML |
|                                   |   output.                         |
+-----------------------------------+-----------------------------------+
| | XML Example                     | An example call to the API that   |
|                                   | returns XML output.  The curl     |
|                                   | command line utility is the HTTPS |
|                                   | client used in the examples.      |
+-----------------------------------+-----------------------------------+

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
