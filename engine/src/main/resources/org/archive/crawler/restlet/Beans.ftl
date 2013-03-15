<!DOCTYPE html>
<html>
<head>
	<title>Crawl beans in ${model.crawlJobShortName}</title>
	<link rel="stylesheet" type="text/css" href="${cssRef}">
</head>
<body>
	<h1>Crawl beans in built job <i><a href='/engine/job/${model.crawlJobShortName}'>${model.crawlJobShortName}</a></i></h1>
	Enter a bean path of the form <i>beanName</i>, <i>beanName.property</i>, <i>beanName.property[indexOrKey]</i>, etc.
	<form method='POST'>
		<input type='text' name='beanPath' style='width:400px' value='${model.beanPath}'>
		<input type='submit' value='view'>
	</form>


<#if model.beanPath?? && (model.beanPath?length >0)>
	<h2>Bean path <i>${model.beanPath}</i></h2>
	<#if model.problem??>
		<i style='color:red'>problem: ${model.problem}</i>
	<#elseif model.editable>
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
	<#else>
		<@beanTemplate bean=model.bean />
	</#if>
</#if>
<h2>All named crawl beans</h2>
<ul>
<#list model.allNamedCrawlBeans as bean>
	<@beanListItem bean=bean />
</#list>
</ul>

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
	<td style='text-align:right;vertical-align:top'>
		<b><#if bean.field?contains("#")>${bean.field}<#else><a href='${bean.url}'>${bean.field}</a></#if>:</b>
	</td>
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
<fieldset style='display:inline;vertical-align:top'>
	<legend>${bean.get("class")}</legend>
	<table>
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