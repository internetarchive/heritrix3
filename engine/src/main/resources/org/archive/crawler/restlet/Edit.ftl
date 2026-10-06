<!doctype html>
<html lang="en">
<head>
    <title>${file.name}</title>
    <style>
        html, body {
            height: 100%;
            margin: 0;
            padding: 0;
        }

        body {
            display: flex;
            flex-direction: column;
        }

        main {
            flex: 1 1 auto;
            overflow-y: auto;
            padding: 1em;
        }

        footer {
            flex-shrink: 0;
            padding: 5px;
            background: #ddd;
        }
    </style>
    <script type="importmap">
        ${webJars.importMap("
            @codemirror/autocomplete
            @codemirror/commands
            @codemirror/language
            @codemirror/lang-xml
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
            w3c-keyname index.js")?no_esc}
    </script>
    <script type="module">
        import {keymap, highlightSpecialChars, drawSelection, highlightActiveLine, dropCursor,
            rectangularSelection, crosshairCursor,
            lineNumbers, highlightActiveLineGutter, EditorView} from "@codemirror/view"
        import {EditorState} from "@codemirror/state"
        import {defaultHighlightStyle, syntaxHighlighting, indentOnInput, bracketMatching,
            foldGutter, foldKeymap} from "@codemirror/language"
        import {defaultKeymap, history, historyKeymap, indentWithTab} from "@codemirror/commands"
        import {searchKeymap, highlightSelectionMatches} from "@codemirror/search"
        import {autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap} from "@codemirror/autocomplete"
        import {lintKeymap} from "@codemirror/lint"
        import {heritrixXml} from "/engine/static/js/heritrix-xml.js"
        import {heritrixGroovy} from "/engine/static/js/heritrix-groovy.js"

        const theme = EditorView.theme({
           "&": { flex: "1 1 auto", minHeight: "0" },
           ".cm-scroller": {overflow: "auto"}
        });

        const isGroovy = window.location.pathname.endsWith('.groovy');

        let editorView;

        function onUpdate(update) {
            if (!update.docChanged) return;
            document.getElementById('saveButton').disabled = false;
        }

        async function initEditor() {
            try {
                const [text, beandoc] = await Promise.all([
                    fetch(window.location.pathname).then(r => r.text()),
                    fetch("/engine/beandoc").then(r => r.json())]);
                editorView = new EditorView({
                    doc: text,
                    extensions: [
                        lineNumbers(),
                        highlightActiveLineGutter(),
                        highlightSpecialChars(),
                        history(),
                        foldGutter(),
                        drawSelection(),
                        dropCursor(),
                        EditorState.allowMultipleSelections.of(true),
                        indentOnInput(),
                        syntaxHighlighting(defaultHighlightStyle, {fallback: true}),
                        bracketMatching(),
                        closeBrackets(),
                        autocompletion({activateOnCompletion: completion => !!completion.reactivate}),
                        rectangularSelection(),
                        crosshairCursor(),
                        highlightActiveLine(),
                        highlightSelectionMatches(),
                        keymap.of([
                            ...closeBracketsKeymap,
                            ...defaultKeymap,
                            ...searchKeymap,
                            ...historyKeymap,
                            ...foldKeymap,
                            ...completionKeymap,
                            ...lintKeymap
                        ]),
                        theme,
                        keymap.of(indentWithTab),
                        isGroovy ? heritrixGroovy(beandoc) : heritrixXml(beandoc),
                        EditorView.updateListener.of(onUpdate)
                    ]
                });
                document.querySelector('main').replaceWith(editorView.dom);
                editorView.focus();
            } catch (err) {
                document.write("Failed to load file: " + err.message);
            }
        }

        async function saveChanges() {
            if (!editorView) return;
            const button = document.getElementById('saveButton')
            button.disabled = true;
            button.textContent = "Saving..."
            try {
                const result = await fetch(window.location.pathname, {
                    method: 'PUT',
                    headers: {'Content-Type': 'text/plain; charset=utf-8'},
                    body: editorView.state.doc.toString()
                });
                if (!result.ok) {
                    alert("Save failed: " + result.status + " " + result.statusText);
                    button.disabled = false;
                } else {
                    button.disabled = true;
                }
            } finally {
                button.textContent = "Save changes";
            }
        }

        initEditor();
        document.getElementById('saveButton').addEventListener('click', saveChanges);

        // Shows the standard browser warning dialog before unloading if there are unsaved changes.
        addEventListener('beforeunload', (event) => {
            if (!document.getElementById('saveButton').disabled) {
                event.preventDefault(); // show warning
            }
        });
    </script>
</head>
<body>
<main></main>
<footer>
    <button id="saveButton" disabled>Save changes</button>
    ${file}
    <a href="${viewRef}">view</a>
    <#list flashes as flash>
        <div class="flash${flash.kind}">${flash.message?no_esc}</div>
    </#list>
</footer>
</body>
</html>