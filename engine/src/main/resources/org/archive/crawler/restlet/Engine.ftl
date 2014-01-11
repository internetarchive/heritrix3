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
		<!--flashed message -->
		<#list flashes as flash>
			<div class='flash${flash.kind}'>
				${flash.message}
			</div>
		</#list>
		<h2>Engine</h2>
		<div class="row">
			<form method='POST'>
				<div class="large-12 columns">
					<div class="panel">
						<ul class="no-bullet">
							<li>Memory:
								<ul class="no-bullet">
									<li>${(engine.heapReport.usedBytes/1024)?string("0")} KiB used; ${(engine.heapReport.totalBytes/1024)?string("0")} KiB current heap; ${(engine.heapReport.maxBytes/1024)?string("0")} KiB max heap 
										<button class="small" type='submit' name='action' value='gc'>run garbage collector</button>
									</li>
								</ul>
							</li>
							<li>Jobs Directory: 
								<ul class="no-bullet">
									<li><a href='jobsdir'>${engine.jobsDir}</a></li>
								</ul>
							</li>
						</ul>
					</div>
				</form>
			</div>
		</div>
	</div>
</div>

<div class="row">
	<form method='POST'>
	<div class="large-12 columns">
		<h2>Job Directories</h2>
		<div class="row">
			<div class="large-12 columns">
				<div class="panel">
					(${engine.jobs?size}) detected <input class="small" type='submit' name='action' value='rescan' />
					<hr />
					<ul class="no-bullet">
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
							<ul class="no-bullet">
								<li>
								<div style="color:#666">
									${crawlJob.primaryConfig}
								</div>
								</li>
								<#if crawlJob.lastLaunch??>
									<li><div>(last at ${crawlJob.lastLaunch})</div></li>
								</#if>
							</ul>
						</li>
						</#list>
					</ul>
				</div>
			</div>
		</div>
	</div>
	</form>
</div>

<div class="row">
	<div class="large-12 columns">
		<h2>Add Job Directory</h2>
		<div class="row">
			<div class="large-12 columns">
				<div class="panel">
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
				</div>
			</div>
		</div>
	</div>
</div>

<div class="row">
	<div class="large-12 columns">
		<h2>Exit Java</h2>
		<div class="row">
			<div class="large-12 columns">
				<div class="panel">
					<p>This exits the Java process running Heritrix. To restart 
					will then require access to the hosting machine. You should 
					cleanly terminate and teardown any jobs in progress first.</p>
					<hr />
					<form method='POST'>
						<ul class="no-bullet">
							<#list engine.jobs as crawlJob>
							<#if crawlJob.hasApplicationContext>
							<li>
								<h6>Job ${crawlJob.key} still &laquo; ${crawlJob.statusDescription} &raquo;</h6>
								<ul class="no-bullet">
									<li>
										<label for='ignore__${crawlJob.key}'>
											<input type='checkbox' id="ignore__${crawlJob.key}" name='ignore__${crawlJob.key}'> Ignore job '${crawlJob.key}' and exit anyway
										</label>
									</li>
								</ul>
							</li>
							</#if>
							</#list>
							<li>
								<input type='submit' name='action' value='Exit Java Process'>
								<ul class="no-bullet">
									<li>
										<label for='im_sure'>
											<input class="inline" type='checkbox' name='im_sure' id='im_sure'> I'm sure
										</label>
									</li>
								</ul>
							</li>
						</ul>
					</form>
				</div>
			</div>
		</div>
	</div>
</div>

</body>
</html>
