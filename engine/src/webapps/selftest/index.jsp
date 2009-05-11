<%@ page contentType="text/html; charset=ISO-8859-1" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="org.archive.crawler.Heritrix" %>
<% 
	// This code looks for all subdirs -- each test occupies its own subdir.
	// Assumption is that the war file has been extracted else this technique
	// will fail.  We exclude CVS and WEB-INF dirs as well as all selftests
    // not yet implemented.
	File cwd = new File(pageContext.getServletContext().
	    getRealPath(File.separator));
	ArrayList dirs = new ArrayList();
	File [] files = cwd.listFiles();
	if (files != null) {
		for (int i = 0; i < files.length; i++) {
	    	if (files[i].isDirectory() &&
	    		!files[i].getName().equals("TrickyRelativeURIs") &&
	    		!files[i].getName().equals("SpacesInHrefPath") &&
	    		!files[i].getName().equals("SimpleJavascriptExtraction") &&
	    		!files[i].getName().equals("RobotsExclusion") &&
	    		!files[i].getName().equals("Refresh") &&
	    		!files[i].getName().equals("FormTagExtraction") &&
	    		!files[i].getName().equals("SimpleDocumentTypes") &&
	    		!files[i].getName().equals("WEB-INF") &&
	    		!files[i].getName().equals("CVS")) {
	    		dirs.add(files[i].getName());
	    	}
		}
	}
	Iterator dirsIterator = dirs.iterator();
%>


<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
    
<html xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <title>Heritrix Crawler Garden Home Page</title>
        <meta http-equiv="content-type" content="text/html; charset=UTF-8"/>
    </head>
    <body>
        <h1>Heritrix Crawler Garden Home Page</h1>
        
        <p>This is the home page for the serverside of the Heritrix crawler 
            integration self test.  The clientside of 
            the integration self test can be found in the 
            <i>org.archive.crawler.selftest</i> package. See the javadoc for
	this package for more on the integration self test including how to add 
            new tests.</p>
            
            <p>The integration self test is run from the command line. This 
                will start a crawler that will meander here, 
		in this <i>selftest</i> webapp. Code on the
                client validates successful crawler traversal of all tests. </p>
            
        <p>Below are the tests to run. Each test is totally contained in a 
            subdirectory named for the test.  This page lists all test 
            subdirectories.  The crawler in integration self test mode is 
            pointed at this page.  It runs the tests in no particular order.</p>
            
        <h2>Integration Tests</h2>
        <p>
          	<ul>
     		<%
     			String dir = null;
     			while (dirsIterator.hasNext()) {
     				dir = (String)dirsIterator.next();
     		%>
     			<li><a href="<%=dir%>/"><%=dir%></a></li>
     		<%
     			}
     		%>
     		</ul>
        </p>
        <hr>
            <small>Heritrix version <%=Heritrix.getVersion()%>, $Id$</small>
        </hr>
    </body>
</html>
