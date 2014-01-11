<!DOCTYPE html>
<!--[if IE 8]> <html class="no-js lt-ie9" lang="en" > <![endif]-->
<!--[if gt IE 8]><!--> <html class="no-js" lang="en" > <!--<![endif]-->
<head>
	<meta charset="utf-8" />
  	<meta name="viewport" content="width=device-width" />
 	<link rel="stylesheet" href="/engine/static/css/normalize.css" />
  	<link rel="stylesheet" href="/engine/static/css/foundation.min.css" />
  	<link rel="stylesheet" href="/engine/static/css/heritrix.css" />
  	<script src="/engine/static/js/vendor/custom.modernizr.js"></script>
  
	<title>${job.shortName} - ${job.statusDescription} - Job main page</title>
	<base href='${baseRef}'/>
	
</head>
<body>
<form method='POST'>
	<div class="contain-to-grid">
		<nav class="top-bar">
	 		<ul class="title-area">
				<li class=" name" >
					<h1><img alt="Heritrix" class="hide-for-small" style="padding-top:4px;" src="/engine/static/img/heritrix-logo.gif" />
						<span class="hide-for-medium-up">Heritrix</span>
		    		</h1>
				</li>
				<li class="toggle-topbar menu-icon left"><a href="#"><span>Menu</span></a></li>
			</ul>
			<section class="top-bar-section">
			<!-- Left Nav Section -->
				<ul class="right">
					<li class="has-dropdown hide-for-medium-up">
						<a href="#">Crawl Actions</a>
						<ul class="button-group dropdown even-2">
							<li class="divider"></li>
							<li><div><button class="button" type='submit' name='action' value='build' ${(!job.hasApplicationContext)?string("", "disabled=\"disabled\"")} >build</button></div></li>
							<li class="divider"></li>
							<li><div><button class="button" type='submit' name='action' value='launch' ${((!job.isProfile) && job.availableActions?seq_contains("launch"))?string("", "disabled=\"disabled\"")} ${(!job.isProfile)?string("","title=\"profiles cannot be launched\"")}>launch</button></div></li>
							<li class="divider"></li>
							<li><div><button class="button" ${job.availableActions?seq_contains("pause")?string("", "disabled=\"disabled\"")} type='submit' name='action' value='pause'>pause</button></div></li>
							<li class="divider"></li>
							<li><div><button class="button" ${job.availableActions?seq_contains("unpause")?string("", "disabled=\"disabled\"")} type='submit' name='action' value='unpause'>unpause</button></div></li>
							<li class="divider"></li>
							<li><div><button class="button" ${job.isRunning?string("", "disabled=\"disabled\"")}  type='submit' name='action' value='checkpoint'>checkpoint</button></div></li>
							<li class="divider"></li>
							<li><div><button class="button" ${job.isRunning?string("", "disabled=\"disabled\"")} type='submit' name='action' value='terminate'>terminate</button></div></li>
							<li class="divider"></li>
							<li><div><button class="button" type='submit' name='action' value='teardown' ${(job.hasApplicationContext)?string("", "disabled=\"disabled\" title=\"no instance\"")}>teardown</button></div></li>
							<li class="divider"></li>
						</ul>
					</li>
					<li class="divider"></li>
					<li><a href="/engine">Engine</a></li>
					<li class="divider"></li>
					<li><a href="jobdir">Job Dir</a></li>
					<li class="divider"></li>
					<li ><a href="/engine/job/${job.shortName}/jobdir/${job.configurationFilePath.name}?format=textedit">Configuration</a></li>
					<li class="divider"></li>
					<li ><a href="#" data-reveal-id="copyJobModal">Copy Job</a></li>
					<li class="divider"></li>
					<li><a href="script">Scripting Console</a></li>
		   			<li class="divider"></li>
		   			<li>
					   	<#if !job.hasApplicationContext>
				  		<a href="#">Browse Beans (disabled)</a>
				  		<#else>
					   	<a href="beans">Browse Beans</a>
					   	</#if>
		  			</li>
					<li class="divider"></li>
				</ul>
			</section>
		</nav>
	</div>
	<div class="row">
		<div class="large-12 columns">
			<h2>Job <i>${job.shortName}</i></h2><p>(<#if job.isLaunchInfoPartial>at least </#if>${job.launchCount} 
			launches<#if job.lastLaunch??>, last ${job.lastLaunchTime} ago</#if>)</p>
			<hr />
			<div class="button-bar show-for-medium-up">
			
			<ul class=" button-group">
					<li><button class="small button" type='submit' name='action' value='build' ${(!job.hasApplicationContext)?string("", "disabled=\"disabled\"")}>build</button></li>
					<li><button class="small button" type='submit' name='action' value='launch' ${((!job.isProfile) && job.availableActions?seq_contains("launch"))?string("", "disabled=\"disabled\"")} ${(!job.isProfile)?string("","title=\"profiles cannot be launched\"")}>launch</button></li>
				</ul>
				<ul class=" button-group">
					<li><button class="small button" ${job.availableActions?seq_contains("pause")?string("", "disabled=\"disabled\"")} type='submit' name='action' value='pause'>pause</button></li>
					<li><button class="small button" ${job.availableActions?seq_contains("unpause")?string("", "disabled=\"disabled\"")} type='submit' name='action' value='unpause'>unpause</button></li>
					<li><button class="small button" ${job.isRunning?string("", "disabled=\"disabled\"")} type='submit' name='action' value='checkpoint'>checkpoint</button></li>
				</ul>
				<ul class=" button-group">
					<li><button class="small button" ${job.isRunning?string("", "disabled=\"disabled\"")} type='submit' name='action' value='terminate'>terminate</button></li>
					<li><button class="small button" type='submit' name='action' value='teardown' ${(job.hasApplicationContext)?string("", "disabled=\"disabled\" title=\"no instance\"")} >teardown</button></li>
			</ul>
			</div>
			<div class="row">
				<div class="large-6 columns">
					<#assign checkpointName=job.checkpointName! />
					<#assign checkpoints=job.checkpointFiles! />
					<#if checkpointName?has_content >
						<div>recover from <i>${checkpointName}</i></div>
					<#elseif checkpoints?has_content >
						<div>select an available checkpoint before launch to recover:
							<select name='checkpoint'>
								<option></option>
								<#list checkpoints as checkpoint>
								<option>${checkpoint}</option>
								</#list>
							</select>
						</div>
					</#if>
				</div>
			</div>
		</div>
	</div>
</form>
<div class="row">
	<div class="large-12 columns">
		<!--flashed message -->
		<#list flashes as flash>
			<div class='flash${flash.kind}'>
				${flash.message}
			</div>
		</#list>
		<#if job.isProfile>
		<p>
			As a <i>profile</i>, this job may be built for testing purposes but not launched. Use the 'copy job' 
			functionality in the menu to copy this profile to a launchable job.
		</p>
	</#if>
	</div>
</div>

<div class="row">
	<div class="large-12 columns">
		<h3>Job Log <a href="/engine/job/${job.shortName}/jobdir/job.log?format=paged&pos=-1&lines=-128&reverse=y">more</a></h3>
		<div class="row">
			<div class="large-12 columns">
				<div class="panel">
					<ul class="no-bullet scroll_y monospace">
						<#list job.jobLogTail as line>
						<li>${line?html}</li>
						</#list>
					</ul>
				</div>
			</div>
		</div>
	</div>
</div>
<div class="row">
	<div class="large-12 columns">
		<h3>Job is ${job.statusDescription}</h3>
		<#if job.hasApplicationContext>
		<div class="row">
			<div class="large-12 columns">
				<div class="panel">
					<ul class="no-bullet">
						<li>Totals
							<ul class="no-bullet">
							<#if !job.uriTotalsReport??>
								<li><i>n/a</i></li>
							<#else>
								<li>${job.uriTotalsReport.downloadedUriCount} downloaded + ${job.uriTotalsReport.queuedUriCount} queued = ${job.uriTotalsReport.totalUriCount} total
								<#if (job.uriTotalsReport.futureUriCount > 0)> (${job.uriTotalsReport.futureUriCount} future)</#if>
								</li>
							</#if>
							<#if !job.sizeTotalsReport??>
								<li><i>n/a</i></li>
							<#else>
								<li>${job.formatBytes(job.sizeTotalsReport.total)} crawled (${job.formatBytes(job.sizeTotalsReport.novel)} novel, ${job.formatBytes(job.sizeTotalsReport.dupByHash)} dupByHash, ${job.formatBytes(job.sizeTotalsReport.notModified)} notModified)</li>
							</#if>
							</ul>
						</li>
						<li>Alerts
							<ul class="no-bullet">
								<#if job.alertCount == 0 >
								<li><i>none</i></li>
								<#else>
								<li>${job.alertCount}
								<a href="/engine/anypath/${job.alertLogFilePath}?format=paged&amp;pos=-1&amp;lines=-128">tail alert log...</a></li>
								</#if>
							</ul>
						</li>
						<li>Rates
							<ul class="no-bullet">
								<#if !job.rateReport??>
									<li><i>n/a</i></li>
								<#else>
								<li>${job.doubleToString(job.rateReport.currentDocsPerSecond,2)} URIs/sec (${job.doubleToString(job.rateReport.averageDocsPerSecond,2)} avg); ${job.rateReport.currentKiBPerSec} KB/sec (${job.rateReport.averageKiBPerSec} avg)</li>
								</#if>
							</ul>
						</li>
						<li>Load
							<ul class="no-bullet">
								<#if !job.loadReport??>
									<li><i>n/a</i></li>
								<#else>
									<li>${job.loadReport.busyThreads} active of ${job.loadReport.totalThreads} threads; ${job.doubleToString(job.loadReport.congestionRatio,2)}  congestion ratio; ${job.loadReport.deepestQueueDepth}  deepest queue; ${job.loadReport.averageQueueDepth}  average depth</li>
								</#if>
							</ul>
						</li>
						<li>Elapsed
							<ul class="no-bullet">
								<#if !job.elapsedReport??>
									<li><i>n/a</i></li>
								<#else>
									<li>${job.elapsedReport.elapsedPretty}</li>
								</#if>
							</ul>
						</li>
						<li><a href="report/ToeThreadsReport">Threads</a>
							<ul class="no-bullet">
								<#if !job.threadReport??>
									<li><i>n/a</i></li>
								<#else>
									<li>
									${job.threadReport.toeCount} threads: 
									<#list job.threadReport.steps as step>${step}<#if step_has_next>, </#if></#list>;
									<#list job.threadReport.processors as proc>${proc}<#if proc_has_next>, </#if></#list>
									</li>
								</#if>
							</ul>
						</li>
						<li><a href="report/FrontierSummaryReport">Frontier</a>
							<ul class="no-bullet">
								<#if !job.frontierReport??>
									<li><i>n/a</i></li>
								<#else>
									<li>${job.frontierReport.lastReachedState} - ${job.frontierReport.totalQueues} URI queues: ${job.frontierReport.activeQueues} active (${job.frontierReport.inProcessQueues} in-process; ${job.frontierReport.readyQueues} ready; ${job.frontierReport.snoozedQueues} snoozed); ${job.frontierReport.inactiveQueues} inactive; ${job.frontierReport.ineligibleQueues} ineligible; ${job.frontierReport.retiredQueues} retired; ${job.frontierReport.exhaustedQueues} exhausted</li>
								</#if>
							</ul>
						</li>
						<li>Memory
							<ul class="no-bullet">
								<li>${(heapReport.usedBytes/1024)?string("0")} KiB used; ${(heapReport.totalBytes/1024)?string("0")} KiB current heap; ${(heapReport.maxBytes/1024)?string("0")} KiB max heap</li>
							</ul>
						</li>
					</ul>

				</div>
			</div>
		</div>
		</#if>
	</div>
</div>

<#if (job.isRunning || (job.hasApplicationContext && !job.isLaunchable))>
<div class="row">
	<div class="large-12 columns">
		<h3>Crawl Log <a href="/engine/anypath/${job.crawlLogFilePath}?format=paged&amp;pos=-1&amp;lines=-128&amp;reverse=y">more</a></h3>
		<div class="row">
			<div class="large-12 columns">
				<div class="panel" >
					<ul class="no-bullet scroll_y monospace">
					<#list job.crawlLogTail as line>
						<li>${line?html}</li>
					</#list>
					</ul>
				</div>
			</div>
		</div>
	</div>
</div>
</#if>

<#if job.hasApplicationContext>
<div class="row">
	<div class="large-12 columns">
		<h3>Reports</h3>
		<ul class="button-group">
			<#list job.reports as report>
				<li><a class="small button" href="report/${report.className}">${report.shortName}</a></li>
			</#list>
		</ul>
	</div>
</div>
</#if>

<div class="row">
	<div class="large-12 columns">
		<h3>Configuration-referenced Paths</h3>
		<div class="row">
			<div class="large-12 columns">
				<div class="panel scroll_y">
					<#assign configRefPaths=job.configFiles! />
					<#if !configRefPaths?has_content >
						<i>build the job to discover referenced paths</i>
					<#else>
					<ul class="no-bullet">
						<#list configRefPaths as config>
						<li>${config.key}: ${config.name}
							<ul class="no-bullet">
								<li>
									<#if config.path??>
										<a href='/engine/anypath/${config.path}<#if config.path?ends_with("log")>?format=paged&amp;pos=-1&amp;lines=-128&amp;reverse=y</#if>'>${config.path}</a><#if config.editable> [<a href="/engine/anypath/${config.path}?format=textedit">edit</a>]</#if>
									<#else>
										<i>unset</i>
									</#if>
								</li>
							</ul>
						</li>
						</#list>
					</ul>
					</#if>
				</div>
			</div>
		</div>
	</div>
</div>
	
<script>
  document.write('<script src=' +
  ('__proto__' in {} ? '/engine/static/js/vendor/zepto' : 'js/vendor/jquery') +
  '.js><\/script>')
</script>
  
<script src="/engine/static/js/foundation.min.js"></script>
<script>
  $(document).foundation();
</script>
<div id="copyJobModal" class="reveal-modal">
	<h2>Copy Job</h2>
	<form method="POST">
	<fieldset>
		<div class="row">
			<div class="large-12 columns">
				<label for="copyTo">Copy job to </label>
				<input name='copyTo' placeholder="<name> - New Crawl Job Configuration" type="text">
			</div>
		</div>
		<div class="row">
			<div class="large-12 columns">
				<div class="row">
					<div class="small-12 columns"><label class="left" for='asProfile'><input id='asProfile' type='checkbox' name='asProfile'> as profile</label></div>
				</div>
			</div>
		</div>
		<div class="row">
			<div class="large-12 columns">
				<input class="left" value='copy' type='submit'>
			</div>
		</div>
	</fieldset>
	</form>
	<a class="close-reveal-modal">&#215;</a>
</div>
</body>
</html>




