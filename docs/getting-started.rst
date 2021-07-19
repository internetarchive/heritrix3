Getting Started with Heritrix
=============================

System Requirements
^^^^^^^^^^^^^^^^^^^

Heritrix is primarily used on Linux. It may run on other platforms but is not regularly tested or supported on them.

Heritrix requires Java 8 or 11. We recommend using your Linux distribution's OpenJDK 11 packages. Alternatively up to
date builds of OpenJDK 8 and 11 for several platforms are available from `AdoptOpenJDK <https://adoptopenjdk.net/>`__.

The default Java heap for Heritrix is 256MB RAM, which is usually suitable for crawls that range over hundreds of
hosts.  Assign more of your available RAM to the heap if you are crawling thousands of hosts or experience Java
out-of-memory problems.  You can use the JAVA_OPTS variable to configure memory

Installation
^^^^^^^^^^^^

Download the latest Heritrix distribution package linked from the `Heritrix releases page
<https://github.com/internetarchive/heritrix3/releases>`__ and unzip it somewhere.

The installation will contain the following subdirectories:

bin
    contains shell scripts/batch files for launching Heritrix.
lib
    contains the third-party .jar files the Heritrix application requires to run.
conf
    contains various configuration files (such as the configuration for Java logging, and pristine versions of the bundled profiles)
jobs
    the default location where operator-created jobs are stored

Environment Variables
^^^^^^^^^^^^^^^^^^^^^

#. Set the ``JAVA_HOME`` environment variable. The value should point to your Java installation.

   .. code-block:: bash

      export JAVA_HOME=/usr/lib/jvm/java-11-openjdk

#. Set the ``HERITRIX_HOME`` environment variable. The value should be set to the path where Heritrix is installed.

   .. code-block:: bash

      export HERITRIX_HOME=/home/user/heritrix3.1

#. Set execute permission on the Heritirix startup file.

   .. code-block:: bash

      chmod u+x $HERITRIX_HOME/bin/heritrix

#. To change the amount of memory allocated to Heritrix (the Java heap size), set the ``JAVA_OPTS`` environment
   variable. The following example allocates 1GB of memory to Heritrix.

   .. code-block:: bash

      export JAVA_OPTS=-Xmx1024M

Runnning Heritrix
^^^^^^^^^^^^^^^^^

To launch Heritrix with the Web UI enabled, enter the following command. The username and password for the Web UI are
set to "admin" and "admin", respectively.

.. code-block:: bash

   $HERITRIX_HOME/bin/heritrix -a admin:admin

By default, the Web UI listening address is only bound to the 'localhost' address. Therefore, the Web UI can only be
accessed on the same machine from which it was launched. The '-b' option may be used to listen on
different/additional addresses. See :ref:`Security Considerations <security-considerations>` before changing this
default.

If the parameter supplied to the ``-a`` option is a file path beginning with "@", the admin username and password
will be read from a file. This adds an additional layer of protection to the admin username and password by ensuring
they don't appear directly in the command-line and can't be seen by other users running the ``ps`` command.

Accessing the User Interface
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

After Heritrix has been launched, the Web-based user interface (WUI) becomes accessible.

The URI to access the Web UI is typically

https://localhost:8443/

The initial login page prompts for the username and password. After login, your session will time-out after a period
of non-use.

Access to the WUI is through HTTPS. Heritrix is installed with a keystore containing a self-signed certificate. This
will cause your browser to display a prompt, warning that a self-signed certificate is being used. Follow the steps
below for your browser to login to Heritrix for the first time.

**Chrome:** The message "Your connection is not private" is displayed. Click the "Advanced" button and then click
"Proceed to localhost (unsafe)."

**Firefox:** The message "Warning: Potential Security Risk Ahead" is displayed. Click the "Advanced..." button and then
click "Accept the Risk and Continue."

Your First Crawl
^^^^^^^^^^^^^^^^

#. Enter the name of the new job in the text box with the "create new job" label. Then click "create".

#. Click on the name of the new job and you will be taken to the job page.

#. Click on the "Configuration" link at the top and the contents of the job configuration file will be displayed.

#. At this point you must enter several properties to make the job runnable.

   #. First, add the URL of page explaining how webmasters can contact you to the metadata.operatorContactUrl property.

   #. Next, populate the ``<prop>`` element of the ``longerOverrides`` bean with the seed values for the crawl. A
      test seed is configured for reference.

   #. When done click "save changes" at the top of the page.

   For more detailed information on configuring
   jobs see `Configuring Jobs and Profiles <https://github
   .com/internetarchive/heritrix3/wiki/Configuring%20Jobs%20and%20Profiles>`__

#. From the job screen, click "build." This command will validate the job configuration and load it into memory. In
   the Job Log the following message will display: "INFO JOB instantiated."

#. Next, click the "launch" button.  This command launches the job in "paused" mode. At this point the job is ready
   to run.

#. To run the job, click the "unpause" button. The job will now begin sending requests to the seeds of your crawl.
   The status of the job will be set to "Running." Refresh the page to see updated statistics.

#. When you want to stop your crawl, click the "terminate" button to finish and then "teardown" to unload the job
   configuration from memory.

Exiting Heritrix
^^^^^^^^^^^^^^^^

To exit Heritrix get back to the main page by clicking "Engine" in the top bar. Then check the "I'm sure" box under
"Exit Java" and click the "exit java process" button.
