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
            codemirror
            @codemirror/autocomplete
            @codemirror/commands
            @codemirror/language
            @codemirror/lang-xml
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
        import {xml} from "@codemirror/lang-xml"
        import {EditorView, keymap} from "@codemirror/view"
        import {indentWithTab} from "@codemirror/commands"

        const theme = EditorView.theme({
           "&": { flex: "1 1 auto", minHeight: "0" },
           ".cm-scroller": {overflow: "auto"}
        });

        let editorView;

        function onUpdate(update) {
            if (!update.docChanged) return;
            document.getElementById('saveButton').disabled = false;
        }

        async function initEditor() {
            try {
                const response = await fetch(window.location.pathname);
                const text = await response.text();
                editorView = new EditorView({
                    doc: text,
                    extensions: [
                        basicSetup,
                        theme,
                        keymap.of(indentWithTab),
                        xml(),
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
        <div class="flash${flash.kind}">${flash.message}</div>
    </#list>
</footer>
</body>
</html>