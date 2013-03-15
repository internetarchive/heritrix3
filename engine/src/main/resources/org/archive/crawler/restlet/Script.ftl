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

	<#if model.linesExecuted??>
	<span class='success'>${model.linesExecuted} lines executed<span>
	</#if>
	<#if model.ex??>
	<pre style='color:red; height:150px; overflow:auto'>
	${model.ex.printStackTrace()}
	</pre>
	</#if>
	<#if model.htmlOutput??>
	<fieldset><legend>htmlOut</legend>
	${model.htmlOutput}
	</fieldset>
	</#if>
	<#if model.rawOutput??>
	<fieldset><legend>rawOutput</legend>
		<pre>
	${model.rawOutput?html}
		</pre>
	</fieldset>
	</#if>

	<form method="POST">
		<input type="submit" value="execute">
		<select name="engine" id="selectEngine">
			<#list model.availableScriptEngines as scriptEngine>
			<option<#if selectedEngine=scriptEngine.engine> selected='selected'</#if> value='${scriptEngine.engine}'>${scriptEngine.language}</option>
			</#list>
		</select>
		<textarea rows='20' style='width:100%' name='script' id='editor'>${script}</textarea>
		<input type='submit' value='execute'></input>
	</form>
	The script will be executed in an engine preloaded
	with (global) variables:
	<ul>
	<li><code>rawOut</code>: a PrintWriter for arbitrary text output to this page</li>
	<li><code>htmlOut</code>: a PrintWriter for HTML output to this page</li>
	<li><code>job</code>: the current CrawlJob instance</li>
	<li><code>appCtx</code>: current job ApplicationContext, if any</li>
	<li><code>scriptResource</code>: the ScriptResource implementing this page, which offers utility methods</li>
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




