<!DOCTYPE html>
<html>
<head>
	<title>Script in ${model.crawlJobShortName}</title>
	<link rel="stylesheet" type="text/css" href="${cssRef}">
	<link rel='stylesheet' href='/engine/static/codemirror/codemirror.css'>
	<link rel='stylesheet' href='/engine/static/codemirror/util/dialog.css'>
	<script src='/engine/static/codemirror/codemirror.js'></script>
	<script src='/engine/static/codemirror/mode/groovy.js'></script>
	<script src='/engine/static/codemirror/mode/clike.js'></script>
	<script src='/engine/static/codemirror/mode/javascript.js'></script>
	<script src='/engine/static/codemirror/util/dialog.js'></script>
	<script src='/engine/static/codemirror/util/searchcursor.js'></script>
	<script src='/engine/static/codemirror/util/search.js'></script>
</head>
<body>
	<h1>Execute script for job <i><a href='/engine/job/${model.crawlJobShortName}'>${model.crawlJobShortName}</a></i></h1>
	<#if (model.linesExecuted > 0)>
	<span class='success'>${model.linesExecuted} ${(model.linesExecuted>1)?string("lines","line")} executed<span>
	</#if>
	<#if model.failure>
	<pre style='color:red; height:150px; overflow:auto'>${model.stackTrace}
	</pre>
	</#if>
	<#assign htmlOutput=model.htmlOutput>
	<#if (htmlOutput?length > 0)>
	<fieldset><legend>htmlOut</legend>
	${htmlOutput}
	</fieldset>
	</#if>
	<#assign rawOutput=model.rawOutput>
	<#if (rawOutput?length > 0)>
	<fieldset><legend>rawOutput</legend>
		<pre style="margin:0;">${rawOutput}
		</pre>
	</fieldset>
	</#if>

	<form method="POST">
		<input type="submit" value="execute">
		<select name="engine" id="selectEngine">
			<#list model.availableScriptEngines as scriptEngine>
			<option<#if selectedEngine=scriptEngine.engine> selected="selected"</#if> value="${scriptEngine.engine}">${scriptEngine.language}</option>
			</#list>
		</select>
		<textarea rows='20' style='width:100%' name='script' id='editor'>${(model.script)!""}</textarea>
		<input type='submit' value='execute'></input>
	</form>
	The script will be executed in an engine preloaded
	with (global) variables:
	<ul>
	<#list model.availableGlobalVariables as v>
	<li><code>${v.variable}</code>: ${v.description?html}</li>
	</#list>
	</ul>
	<script>
		var modemap = {beanshell: 'text/x-java', groovy: 'groovy', js: 'javascript'};
		var selectEngine = document.getElementById('selectEngine');
		var editor = document.getElementById('editor');
		var cmopts = {
			    mode: modemap[selectEngine.value],
		        lineNumbers: true, autofocus: true, indentUnit: 4
			    }
		var cm = CodeMirror.fromTextArea(editor, cmopts);
		selectEngine.onchange = function(e) { cm.setOption('mode', modemap[selectEngine.value]); }
	</script>
</body>
</html>
