<!DOCTYPE html>
<html>
<head><title>Heritrix Engine ${engine.heritrixVersion}</title>
<base href='${baseRef}'/>
<link rel="stylesheet" type="text/css" href="${cssRef}">
</head>

<body>

<h1>Heritrix Engine ${engine.heritrixVersion}</h1>
  
<!--flashed message -->
<#list flashes as flash>
	<div class='flash${flash.kind}'>
		${flash.message}
	</div>
</#list>
  
<form method='POST'>
	<b>Memory: </b>
	${(engine.heapReport.usedBytes/1024)?string("0")} KiB used; ${(engine.heapReport.totalBytes/1024)?string("0")} KiB current heap; ${(engine.heapReport.maxBytes/1024)?string("0")} KiB max heap
	<button type='submit' name='action' value='gc'>run garbage collector</button>
</form>

<p>
<b>Jobs Directory</b>: <a href='jobsdir'>${engine.jobsDir}</a>
</p>
         
<form method='POST'><h2>Job Directories (${engine.jobs?size})
       <input type='submit' name='action' value='rescan'></h2>
</form>
        
<ul>
<#list engine.jobs as crawlJob> 
<li>
<div>
<a href="/engine/job/${crawlJob.shortName}">${crawlJob.shortName}</a>
<#if crawlJob.hasApplicationContext>
	&laquo;${crawlJob.statusDescription}&raquo;
</#if>
<#if crawlJob.isLaunchInfoPartial>
	<span> at least </span>
</#if>
${crawlJob.launchCount} launches
</div>
<div style="color:#666">
${crawlJob.primaryConfig}
</div>
<#if crawlJob.lastLaunch??>
	<div>(last at ${crawlJob.lastLaunch})</div>
</#if>
</li>
</#list>
</ul>
        
<h2>Add Job Directory</h2>
        
<form method='POST'>
Create new job directory with recommended starting configuration<br/>
<b>Path:</b> ${engine.jobsDir}${fileSeparator}
<input name='createpath'/>
<input type='submit' name='action' value='create'>
</form>

<form method='POST'>
       Specify a path to a pre-existing job directory<br/>
       <b>Path:</b> <input size='53' name='addpath'/>
       <input type='submit' name='action' value='add'>
</form>

<p>
You may also compose or copy a valid job directory into the main jobs directory via outside means, then use the 'rescan' button above to make it appear in this interface. Or, use the 'copy' functionality at the botton of any existing job's detail page.
</p>

<h2>Exit Java</h2><div>This exits the Java process running Heritrix. To restart 
will then require access to the hosting machine. You should 
cleanly terminate and teardown any jobs in progress first.<div>
<form method='POST'>

<#list engine.jobs as crawlJob>
<#if crawlJob.hasApplicationContext>
<div>Job ${crawlJob.key} still &laquo; ${crawlJob.statusDescription} &raquo;</div>
<input type='checkbox' id="ignore__${crawlJob.key}" name='ignore__${crawlJob.key}'>
<label for='ignore__${crawlJob.key}'> Ignore job '${crawlJob.key}' and exit anyway</label>
<br>
</#if>
</#list>

<div>
<input type='submit' name='action' value='Exit Java Process'>
<input type='checkbox' name='im_sure' id='im_sure'><label for='im_sure'> I'm sure</label>
</div>
</form>
</body>
</html>
