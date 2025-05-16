<!DOCTYPE html>
<!--[if IE 8]> <html class="no-js lt-ie9" lang="en" > <![endif]-->
<!--[if gt IE 8]><!--> <html class="no-js" lang="en" > <!--<![endif]-->
<html>
<head>
	<meta charset="utf-8" />
	<meta name="viewport" content="width=device-width" />
	<link rel="stylesheet" href="/engine/static/css/normalize.css" />
	<link rel="stylesheet" href="/engine/static/css/foundation.min.css" />
	<link rel="stylesheet" href="/engine/static/css/heritrix.css" />
	<script src="/engine/static/js/vendor/custom.modernizr.js"></script>
	
	<title>Script in ${model.crawlJobShortName}</title>

	<script type="importmap">
        ${webJars.importMap("
            codemirror
            @codemirror/autocomplete
            @codemirror/commands
            @codemirror/language
            @codemirror/legacy-modes/
            @codemirror/lint
            @codemirror/search
            @codemirror/state
            @codemirror/view
            crelt index.js
            @lezer/common
            @lezer/highlight
            @lezer/lr
            @lezer/xml
            @marijn/find-cluster-break src/index.js
            style-mod src/style-mod.js
            w3c-keyname index.js")}
    </script>
	<script type="module">
		import {basicSetup} from "codemirror"
		import {StreamLanguage, indentUnit} from "@codemirror/language"
		import {groovy} from "@codemirror/legacy-modes/mode/groovy.js"
		import {EditorView, keymap} from "@codemirror/view"
		import {indentWithTab} from "@codemirror/commands"

		const theme = EditorView.theme({
			"&": { height: "400px", backgroundColor: "#ffffff" },
			".cm-scroller": {overflow: "auto"}
		});
		const textarea = document.getElementById('editor');
		const editorView = new EditorView({
			doc: textarea.value,
			extensions: [
				basicSetup,
				theme,
				keymap.of([indentWithTab]),
				StreamLanguage.define(groovy),
				indentUnit.of("    ")
			]
		});
		textarea.parentNode.insertBefore(editorView.dom, textarea);
		textarea.style.display = 'none';
		textarea.form.addEventListener('submit', () => textarea.value = editorView.state.doc.toString())
		editorView.focus();
	</script>
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
			<h3>Execute script for job <i><a href='/engine/job/${model.crawlJobShortName}'>${model.crawlJobShortName}</a></i></h3>
			<div class="row">
				<div class="large-12 columns">
					<div class="panel">
						<#if (model.linesExecuted > 0)>
						<span class='success'>${model.linesExecuted} ${(model.linesExecuted>1)?string("lines","line")} executed</span>
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
							<div style="margin-bottom:1em">
								<label class="inline" for="engine">Script Engine: </label>
								<select class="inline" style="width:auto" name="engine" id="selectEngine">
									<#list model.availableScriptEngines as scriptEngine>
									<option<#if selectedEngine=scriptEngine.engine> selected='selected'</#if> value='${scriptEngine.engine}'>${scriptEngine.language}</option>
									</#list>
								</select>
								<input class="small inline button" type="submit" value="execute">
							</div>
							<textarea rows='20' style='width:100%' name='script' id='editor'>${(model.script)!""}</textarea>
						</form>
					</div>
				</div>
			</div>
		</div>
	</div>

	<div class="row">
		<div class="large-12 columns">
			The script will be executed in an engine preloaded with (global) variables:
			<ul class="no-bullet">
				<#list model.availableGlobalVariables as v>
				<li style="line-height:1"><code>${v.variable}</code>: ${v.description?html}</li>
				</#list>
			</ul>
		</div>
	</div>
</body>
</html>

