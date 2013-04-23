<!DOCTYPE html>
<html>
<head>
	<title>${job.shortName} - ${job.statusDescription} - Job main page</title>
	<base href='${baseRef}'/>
	<link rel="stylesheet" type="text/css" href="${cssRef}">
</head>
<body>
	<h1 style="margin-bottom:0">
		Job <i>${job.shortName}</i> (<#if job.isLaunchInfoPartial>at least </#if>${job.launchCount} 
		launches<#if job.lastLaunch??>, last ${job.lastLaunchTime} ago</#if>)
	</h1>
	<div style="margin-bottom:1em"><a href="/engine">up to engine</a></div>

	<!--flashed message -->
	<#list flashes as flash>
		<div class='flash${flash.kind}'>
			${flash.message}
		</div>
	</#list>

	 <#if job.isProfile>
		<p>
			As a <i>profile</i>, this job may be built for testing purposes but not launched. Use the 'copy job to' 
			functionality at bottom to copy this profile to a launchable job.
		</p>
	</#if>

	<form style='white-space:nowrap' method='POST'>
		<div>
			<span class="bgroup">
				<input type='submit' name='action' value='build' <#if job.hasApplicationContext> disabled='disabled' title='build job'</#if> />
				<input type='submit' name='action' value='launch' 
				<#if job.isProfile> disabled='disabled' title='profiles cannot be launched'
				</#if>
				<#if !job.availableActions?seq_contains("launch")>
				 disabled='disabled'
				</#if>
				 />
			</span>
			<span class="bgroup">
				<input <#if !job.availableActions?seq_contains("pause")> disabled</#if> type='submit' name='action' value='pause' />
				<input <#if !job.availableActions?seq_contains("unpause")> disabled</#if> type='submit' name='action' value='unpause' />
				<input <#if !job.isRunning> disabled</#if>  type='submit' name='action' value='checkpoint' />
			</span>
			<span class="bgroup">
				<input <#if !job.isRunning> disabled</#if> type='submit' name='action' value='terminate' />
				<input type='submit' name='action' value='teardown' <#if !job.hasApplicationContext> disabled='disabled' title='no instance'</#if> />
			</span>
		</div>
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
	</form>

	<div>
		configuration: <a href="/engine/job/${job.shortName}/jobdir/${job.configurationFilePath.name}" >${job.configurationFilePath}</a> [<a href="/engine/job/${job.shortName}/jobdir/${job.configurationFilePath.name}?format=textedit">edit</a>]
	</div>

	<h2>Job Log (<a href ='/engine/job/${job.shortName}/jobdir/job.log?format=paged&pos=-1&lines=-128&reverse=y'><i>more</i></a>)</h2>
	<div class="log">
		<#list job.jobLogTail as line>
		<div>${line?html}</div>
		</#list>
	</div>
	<h2>Job is ${job.statusDescription}</h2>
	<#if job.hasApplicationContext>
		<dl id="jobstats">
			<dt>Totals</dt>
			<dd>
				<div>
					<#if !job.uriTotalsReport??>
					<i>n/a</i>
					<#else>
					${job.uriTotalsReport.downloadedUriCount} downloaded + ${job.uriTotalsReport.queuedUriCount} queued = ${job.uriTotalsReport.totalUriCount} total
					<#if (job.uriTotalsReport.futureUriCount > 0)> (${job.uriTotalsReport.futureUriCount} future)</#if>
					</#if>
				</div>
				<div>
					<#if !job.sizeTotalsReport??>
						<i>n/a</i>
					<#else>
						${job.formatBytes(job.sizeTotalsReport.total)} crawled (${job.formatBytes(job.sizeTotalsReport.novel)} novel, ${job.formatBytes(job.sizeTotalsReport.dupByHash)} dupByHash, ${job.formatBytes(job.sizeTotalsReport.notModified)} notModified)
					</#if>

				</div>
			</dd>
			<dt>Alerts</dt>
			<dd>
				<#if job.alertCount == 0 ><i>none</i><#else>${job.alertCount}
				<a href="/engine/anypath/${job.alertLogFilePath}?format=paged&amp;pos=-1&amp;lines=-128">tail alert log...</a>
				</#if>
			</dd>
			<dt>Rates</dt>
			<dd><#if !job.rateReport??><i>n/a</i>
				<#else>
				${job.doubleToString(job.rateReport.currentDocsPerSecond,2)} URIs/sec (${job.doubleToString(job.rateReport.averageDocsPerSecond,2)} avg); ${job.rateReport.currentKiBPerSec} KB/sec (${job.rateReport.averageKiBPerSec} avg)
				</#if>
			</dd>
			<dt>Load</dt>
			<dd>
				<#if !job.loadReport??><i>n/a</i>
				<#else>
				${job.loadReport.busyThreads} active of ${job.loadReport.totalThreads} threads; ${job.doubleToString(job.loadReport.congestionRatio,2)}  congestion ratio; ${job.loadReport.deepestQueueDepth}  deepest queue; ${job.loadReport.averageQueueDepth}  average depth
				</#if>
			</dd>
			<dt>Elapsed</dt>
			<dd>
				<#if !job.elapsedReport??><i>n/a</i>
				<#else>
				${job.elapsedReport.elapsedPretty}
				</#if>
			</dd>
			<dt><a href="report/ToeThreadsReport">Threads</a></dt>
			<dd>
				<#if !job.threadReport??><i>n/a</i>
				<#else>
				${job.threadReport.toeCount} threads: 
				<#list job.threadReport.steps as step>${step}<#if step_has_next>, </#if></#list>;
				<#list job.threadReport.processors as proc>${proc}<#if proc_has_next>, </#if></#list>
				</#if>
			</dd>
			<dt><a href="report/FrontierSummaryReport">Frontier</a></dt>
			<dd>
				<#if !job.frontierReport??><i>n/a</i>
				<#else>
				${job.frontierReport.lastReachedState} - ${job.frontierReport.totalQueues} URI queues: ${job.frontierReport.activeQueues} active (${job.frontierReport.inProcessQueues} in-process; ${job.frontierReport.readyQueues} ready; ${job.frontierReport.snoozedQueues} snoozed); ${job.frontierReport.inactiveQueues} inactive; ${job.frontierReport.ineligibleQueues} ineligible; ${job.frontierReport.retiredQueues} retired; ${job.frontierReport.exhaustedQueues} exhausted
				</#if>
			</dd>
			<dt>Memory</dt>
			<dd>
				${(heapReport.usedBytes/1024)?string("0")} KiB used; ${(heapReport.totalBytes/1024)?string("0")} KiB current heap; ${(heapReport.maxBytes/1024)?string("0")} KiB max heap

			</dd>
		</dl>

		<#if (job.isRunning || (job.hasApplicationContext && !job.isLaunchable))>
		<h3>Crawl Log <a href="/engine/anypath/${job.crawlLogFilePath}?format=paged&amp;pos=-1&amp;lines=-128&amp;reverse=y"><i>more</i></a></h3>
		<pre style='overflow:auto'>
		<#list job.crawlLogTail as line>
${line?html}
		</#list>
		</pre>
		</#if>
	</#if>

	<#if job.hasApplicationContext>
	<h2>Reports</h2>
	<#list job.reports as report>
	<a href='report/${report.className}'>${report.shortName}</a>
	</#list>
	</#if>

	<h2>Files</h2>
	<h3>Browse <a href='jobdir'>Job Directory</a></h3>
	<h3>Configuration-referenced Paths</h3>
	<#assign configRefPaths=job.configFiles! />
	<#if !configRefPaths?has_content >
		<i>build the job to discover referenced paths</i>
	<#else>
		<dl>
			<#list configRefPaths as config>
			<dt>
				${config.key}: ${config.name}
			</dt>
			<dd>
				<#if config.path??>
				<a href='/engine/anypath/${config.path}<#if config.path?ends_with("log")>?format=paged&amp;pos=-1&amp;lines=-128&amp;reverse=y</#if>'>${config.path}</a><#if config.editable> [<a href="/engine/anypath/${config.path}?format=textedit">edit</a>]</#if>
				<#else>
				<i>unset</i>
				</#if>
			</dd>
			</#list>
		</dl>
	</#if>

	<h2>Advanced</h2>
	<h3><a href='script'>Scripting console</a></h3>
	<#if !job.hasApplicationContext>
		<i>build the job to browse bean instances</i>
	<#else>
		<h3><a href='beans'>Browse beans</a></h3>
	</#if>
	<h2>Copy</h2>
	<form method="POST">Copy job to <input name='copyTo'>
		<input value='copy' type='submit'>
		<input id='asProfile' type='checkbox' name='asProfile'>
		<label for='asProfile'>as profile</label>
	</form>
	</body>
</html>




