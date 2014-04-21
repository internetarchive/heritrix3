<!DOCTYPE html>
<!--[if IE 8]> 				 <html class="no-js lt-ie9" lang="en" > <![endif]-->
<!--[if gt IE 8]><!--> <html class="no-js" lang="en" > <!--<![endif]-->

<head>
	<meta charset="utf-8" />
	<meta name="viewport" content="width=device-width" />
	<title>Heritrix Engine ${engine.heritrixVersion}</title>
	<link rel="stylesheet" href="/engine/static/css/normalize.css" />
	<link rel="stylesheet" href="/engine/static/css/foundation.min.css" />
	<link rel="stylesheet" href="/engine/static/css/heritrix.css" />
	<script src="/engine/static/js/vendor/custom.modernizr.js"></script>
	
	<base href='${baseRef}'/>
	
</head>

<body>
<div class="contain-to-grid">
	<nav class="top-bar">
	  <ul class="title-area">
	    <!-- Title Area -->
	    <li class=" name" >
	    	<h1>
	    		<span style="color:#FFFFFF"><span class="hide-for-medium-up" >Heritrix</span><img alt="Heritrix" class="hide-for-small" style="padding-top:4px; padding-right:10px;" src="/engine/static/img/heritrix-logo.gif" /> ${engine.heritrixVersion}</span>
    		</h1>
	    </li>
	  </ul>
	</nav>
</div>

<div class="row">
	<div class="large-12 columns">
		<h2>Engine</h2>
	</div>
</div>

<div class="row">
	<div class="large-12 columns">
		<!--flashed message -->
		<#list flashes as flash>
		<div data-alert class="alert-box ${(flash.kind=='ACK')?string('success', 'alert')}">
			${flash.message} <a href="#" class="close">&times;</a>
		</div>
		</#list>
	</div>

	<div class="large-6 columns">
		<form method='POST'>
			<h3>Memory</h3>
			<div class="progress secondary" style="margin:0.125em 0">
				<span style="width:${100*engine.heapReport.totalBytes/engine.heapReport.maxBytes}%;padding:0;border:0" class="meter progress">
					<span style="width:${100*engine.heapReport.usedBytes/engine.heapReport.totalBytes}%;background-color:#ed9c28" class="meter"></span>
				</span>
			</div>
			<div style="margin:0.125em 0">
				${(engine.heapReport.usedBytes/1024/1024)?string("0")} MiB used; 
				${(engine.heapReport.totalBytes/1024/1024)?string("0")} MiB current heap; 
				${(engine.heapReport.maxBytes/1024/1024)?string("0")} MiB max heap 
			</div>
			<div>
				<button class="small button" style="margin:1em 0;" type='submit' name='action' value='gc'>run garbage collector</button>
			</div>
		</form>
	</div>

	<div class="large-1 columns">
	</div>
	<div class="large-5 columns">
		<div class="panel">
			<h3>Exit Java</h3>
			<form method='POST'>
				<#list engine.jobs as crawlJob>
				<#if crawlJob.hasApplicationContext>
				<label for='ignore__${crawlJob.key}' style="color:#d9534f">
					<input type='checkbox' id="ignore__${crawlJob.key}" name='ignore__${crawlJob.key}'> ignore &laquo;${crawlJob.statusDescription}&raquo; job <a href="/engine/job/${crawlJob.shortName}">${crawlJob.key}</a> 
				</label>
				</#if>
				</#list>
				<label for='im_sure'>
					<input class="inline" type='checkbox' name='im_sure' id='im_sure'> I'm sure
				</label>
				<input class="small button" style="margin:1em 0;" type='submit' name='action' value='exit java process'>
			</form>
		</div>
	</div>
</div>

<div class="row">
	<div class="large-12 columns">
		<hr/>
	</div>
</div>

<div class="row">
	<div class="large-12 columns">
		<div class="row">
			<div class="large-12 columns">
				<form method='POST'>
					<h2 style="margin-bottom:0;display:inline;line-height:1">Job Directories</h2>
					<input class="small inline button text-bottom" type='submit' name='action' value='rescan' />
					<h5 style="margin:0">parent directory <a href='jobsdir'>${engine.jobsDir}</a></h5>
					<div>${engine.jobs?size} known job directories</div>
				</form>
			</div>
		</div>
		
		<div class="row">
			<div class="large-6 columns" style="margin-bottom:1em">
				<form method='POST' style="margin:0">
					<h5 style="margin-bottom:0">create new job</h5>
					<input style="display:inline;margin:0;width:50%" name='createpath' type="text" placeholder="myJob"/>
					<input class="small inline button" type='submit' name='action' value='create'>
				</form>
			</div>
			<div class="large-6 columns" style="margin-bottom:1em">
				<form method='POST'>
					<h5 style="margin-bottom:0">add existing job</h5>
					<input style="display:inline;margin:0;width:50%" name='addpath' type="text" placeholder="/path/to/job"/>
					<input class="small inline button" type='submit' name='action' value='add'>
				</form>
			</div>
		</div>
		<#list engine.jobs?chunk(2) as twoJobs>
		<div class="row">
			<#list twoJobs as crawlJob>
			<div class="large-6 columns" style="margin-bottom:1em">
				<h5 style="margin-bottom:0">
					<a href="/engine/job/${crawlJob.shortName}">${crawlJob.shortName}</a>
					<#if crawlJob.hasApplicationContext>
					&laquo;${crawlJob.statusDescription}&raquo;
					</#if>
				</h5>
				<div style="color:#666">
					${crawlJob.primaryConfig}
				</div>
				<div style="color:#666">
					<#if crawlJob.isLaunchInfoPartial>at least</#if> ${crawlJob.launchCount} 
					launches<#if crawlJob.lastLaunch??>, last at ${crawlJob.lastLaunch}</#if>
				</div>
			</div>
			</#list>
		</div>
		</#list>
	</div>
</div>

</body>
</html>
