<!DOCTYPE html>
<html>
<head>
	<meta charset="utf-8" />
	<meta name="viewport" content="width=device-width" />
	<link rel="stylesheet" href="/engine/static/css/normalize.css" />
	<link rel="stylesheet" href="/engine/static/css/foundation.min.css" />
	<link rel="stylesheet" href="/engine/static/css/heritrix.css" />
	<script src="/engine/static/js/vendor/custom.modernizr.js"></script>
	
	<title>Crawl beans in ${model.crawlJobShortName}</title>
	
</head>
<body>
	<div class="contain-to-grid">
		<nav class="top-bar">
			<ul class="title-area">
				<li class=" name">
					<h1>
						<img alt="Heritrix" class="hide-for-small"
							style="padding-top: 4px;"
							src="/engine/static/img/heritrix-logo.gif" /> <span
							class="hide-for-medium-up">Heritrix</span>
					</h1>
				</li>
				<li class="toggle-topbar menu-icon left"><a href="#"><span>Menu</span></a></li>
			</ul>

			<section class="top-bar-section">
				<!-- Left Nav Section -->
				<ul class="right">

					<li class="divider"></li>
					<li><a href="/engine">Engine</a></li>
					<li class="divider"></li>
					<li><a href="/engine/job/${model.crawlJobShortName}">Job "${model.crawlJobShortName}"</a></li>
					<li class="divider"></li>
				</ul>
			</section>
		</nav>
	</div>
	<div class="row">
		<div class="large-12 columns">
			<h3>Crawl beans in built job <i><a href='/engine/job/${model.crawlJobShortName}'>${model.crawlJobShortName}</a></i></h3>
			<div class="row">
				<div class="large-12 columns">
					<div class="panel">
							Enter a bean path of the form <i>beanName</i>, <i>beanName.property</i>, <i>beanName.property[indexOrKey]</i>, etc.
							<form method='POST'>
								<input type='text' name='beanPath' style='width:400px' value='${model.beanPath}'>
								<input type='submit' value='view'>
							</form>
					</div>
				</div>
			</div>
		</div>
	</div>
	
<#if model.beanPath?? && (model.beanPath?length >0)>
	<div class="row">
		<div class="large-12 columns">
			<h3>Bean path <i>${model.beanPath}</i></h3>
			<div class="row">
				<div class="small-12 columns">
					<#if model.problem??>
						<div class="panel">
							<i style='color:red'>problem: ${model.problem}</i>
						</div>
					<#elseif model.editable>
						<div class="panel">
							<div>
								${model.beanPath} = 
								<@beanTemplate bean=model.bean />  <a href="javascript:document.getElementById('editform').style.display='block';void(0);">edit</a>
							</div>
							<form id='editform' style='display:none' method='POST'>
								<div>Note: it may not be appropriate/effective to change this value in an already-built crawl context.</div>
								<div><input type='hidden' name='beanPath' value='${model.beanPath}'>
									${model.beanPath} = <input type='text' name='newVal' style='width:400px' value='<#if model.target?is_number>${model.target?c}<#else>${model.target?string}</#if>'>
									<input type='submit' value='update'>
								</div>
							</form>
						</div>
					<#else>
						<@beanTemplate bean=model.bean />
					</#if>
				</div>
				
			</div>
		</div>
	</div>
							
							
</#if>
	<div class="row">
		<div class="large-12 columns">
			<h3>All named crawl beans</h3>
			<div class="row">
				<div class="small-12 columns">
					<div class="panel">
						<ul>
						<#list model.allNamedCrawlBeans as bean>
							<@beanListItem bean=bean />
						</#list>
						</ul>
					</div>
				</div>
			</div>
		</div>
	</div>

</body>
</html>

<#macro beanListItem bean>
	<li>	<#-- have to use .get("class") instead of .class due to freemarker conflict with method getClass()-->
		<a href='${bean.url}'>${bean.name}</a><span style='color:#999'> ${bean.get("class")}</span>
		<ul>
			<#list bean.children as child>
			<@beanListItem bean=child />
			</#list>
		</ul>
	</li>
</#macro>

<#macro beanTemplate bean>
<#if bean.field?? && (bean.field?length>0)>
	<th>
		<b><#if bean.field?contains("#")>${bean.field}<#else><a href='${bean.url}'>${bean.field}</a></#if>:</b>
	</th>
</#if>
<td>
<#if bean.propValuePreviouslyDescribed??>
	&uarr;
<#elseif !bean.propValue?? && !bean.properties?? && !bean.get("class")??>
	<#if bean.key?? && bean.field??>
	<a href='../beans/${bean.key}'>${bean.key}</a>
	<#else>
	<i>null</i>
	</#if>
<#elseif bean.propValue?? && !bean.propValue?is_collection>
	<#if bean.get("class")?? && bean.get("class")?contains("String")>
	"${bean.propValue}"
	<#elseif bean.propValue?is_number>
	${bean.propValue?c}
	<#else>
	${bean.propValue?string}
	</#if>
</#if>

<#if (!bean.propValue?? && !bean.properties?? && bean.get("class")??) || bean.properties?? || (bean.propValue?? && bean.propValue?is_collection)>
<fieldset>
	<legend>${bean.get("class")}</legend>
	<table class="beans">
	<#if bean.properties??>
	<#list bean.properties as property>
		<tr><@beanTemplate bean=property /></tr>
	</#list>
	</#if>
	<#if bean.propValue?? && bean.propValue?is_collection>
		<#list bean.propValue as element>
		<tr><@beanTemplate bean=element /></tr>
		</#list>
	</#if>
	</table>
</fieldset>
</td>
</#if>
</#macro>