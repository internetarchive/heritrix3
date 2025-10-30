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
        import {keymap, highlightSpecialChars, drawSelection, highlightActiveLine, dropCursor,
            rectangularSelection, crosshairCursor,
            lineNumbers, highlightActiveLineGutter, EditorView} from "@codemirror/view"
        import {EditorState} from "@codemirror/state"
        import {defaultHighlightStyle, syntaxHighlighting, indentOnInput, bracketMatching,
            foldGutter, foldKeymap, LanguageSupport, syntaxTree} from "@codemirror/language"
        import {defaultKeymap, history, historyKeymap, indentWithTab} from "@codemirror/commands"
        import {searchKeymap, highlightSelectionMatches} from "@codemirror/search"
        import {autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap} from "@codemirror/autocomplete"
        import {lintKeymap} from "@codemirror/lint"
        import {autoCloseTags, xmlLanguage} from "@codemirror/lang-xml"

        const theme = EditorView.theme({
           "&": { flex: "1 1 auto", minHeight: "0" },
           ".cm-scroller": {overflow: "auto"}
        });

        let editorView;

        function onUpdate(update) {
            if (!update.docChanged) return;
            document.getElementById('saveButton').disabled = false;
        }

        /**
         * Map of fully-qualified bean class names to their definitions.
         * @typedef {Object<string, Bean>} BeanDoc
         */

        /**
         * One bean entry describing the bean and its properties.
         * @typedef {Object} Bean
         * @property {string} description
         * @property {Object<string, Property>} properties - Map of property name to metadata.
         */

        /**
         * Metadata for a single property on a bean.
         * @typedef {Object} Property
         * @property {string} description
         * @property {string} type - The Java type
         * @property {*} [default] - Optional default value; may be number, boolean, string, etc.
         */

        function makeDiv(html) {
            if (!html) return null;
            const div = document.createElement('div');
            div.innerHTML = html.replace(/\n\n/g, "<br><br>").replace(/\n/g, " ");
            return div;
        }

        const TAG_COMPLETIONS = {
            "beans": ["bean"],
            "bean": ["constructor-arg", "property"],
            "constructor-arg": ["bean", "list", "map", "ref", "value"],
            "property": ["bean", "list", "map", "ref", "value"],
            "map": ["entry"],
            "list": ["bean", "list", "map", "ref", "value"],
        };

        const ATTRIBUTE_COMPLETIONS = {
            "bean": ["class", "id"],
            "constructor-arg": ["value"],
            "property": ["name", "value"],
            "ref": ["bean"],
            "entry": ["key", "value"],
        };

        /**
         * Calculate auto-completions for Heritrix XML.
         *
         * @param {BeanDoc} beandoc
         * @param {CompletionContext} context
         */
        function completeHeritrixXml(beandoc, context) {
            /**
             * Get the given node's text content.
             */
            function slice(node) {
                if (!node) return null;
                return context.state.sliceDoc(node.from, node.to);
            }

            /**
             * Get the value of the given attribute on the given element.
             */
            function getAttrValue(element, attr) {
                console.assert(element.name === 'Element', "Expected element node");
                let openTag = element.getChild('OpenTag');
                for (let attribute of openTag.getChildren('Attribute')) {
                    if (slice(attribute.getChild('AttributeName')) === attr) {
                        let text = slice(attribute.getChild('AttributeValue'));
                        if (text.startsWith("\"") || text.startsWith("\'")) {
                            text = text.substring(1, text.length - 1);
                        }
                        return text;
                    }
                }
                return null;
            }

            /**
             * Get the names of all attributes on the given element.
             */
            function getAttrNames(element) {
                console.assert(element.name === 'Element', "Expected element node");
                let openTag = element.getChild('OpenTag');
                let names = [];
                for (let attribute of openTag.getChildren('Attribute')) {
                    names.push(slice(attribute.getChild('AttributeName')));
                }
                return names;
            }

            /**
             * Get the tag name of the given element.
             */
            function getElementName(element) {
                if (!element) return null;
                let openTag = element.getChild('OpenTag');
                if (openTag) return slice(openTag.getChild('TagName'));
                return null;
            }

            /**
             * Find the closest ancestor element of the given node that has the given tag.
             */
            function closest(node, tag) {
                while (node) {
                    if (node.name === 'Element') {
                        if (!tag || getElementName(node) === tag) return node;
                    }
                    node = node.parent;
                }
                return null;
            }

            /**
             * @typedef {Object} DeclaredBean
             * @property {string} id
             * @property {string} class
             */
            /**
             * Get all beans declared in the document that have an id.
             * @returns {DeclaredBean[]}
             */
            function getDeclaredBeans() {
                const beans = []
                const cursor = syntaxTree(context.state).cursor();
                while (cursor.next()) {
                    if (cursor.name === "Element") {
                        let openTag = cursor.node.getChild('OpenTag');
                        if (openTag && slice(openTag.getChild('TagName')) === "bean") {
                            let id = getAttrValue(cursor.node, "id");
                            if (id) beans.push({id, class: getAttrValue(cursor.node, "class")});
                        }
                    }
                }
                beans.sort((a, b) => a.id.localeCompare(b.id));
                return beans;
            }

            /**
             * Strips the package from a fully qualified class name (including generic types).
             * @param {string} className e.g. "java.util.List<java.lang.String>"
             * @returns {string} e.g. "List<String>"
             */
            function stripPackage(className) {
                return className.replace(/\b[^<>, ]+\./g, "");
            }

            let node = syntaxTree(context.state).resolveInner(context.pos, -1)
            if (node.name === "TagName" || node.name === "StartTag") {
                let parentElement = closest(node.parent.parent.parent);
                let tag = getElementName(parentElement);
                let completions = TAG_COMPLETIONS[tag];
                if (!completions) return;
                let text = node.name === "StartTag" ? "" : context.state.sliceDoc(node.from, context.pos);
                return {
                    from: context.pos - text.length,
                    options: completions.filter(c => c.startsWith(text))
                        .map(c => ({label: c, apply: c}))
                }
            } else if (node.name === "OpenTag" || node.name === "AttributeName") {
                let element = node.name === "OpenTag" ? node.parent : node.parent.parent.parent;
                const tag = getElementName(element);
                const existingAttrs = getAttrNames(element);
                let completions = ATTRIBUTE_COMPLETIONS[tag];
                if (!completions) return;
                let text = node.name === "OpenTag" ? "" : context.state.sliceDoc(node.from, context.pos);
                return {
                    from: context.pos - text.length,
                    options: completions.filter(c => c.startsWith(text) && !existingAttrs.includes(c))
                        .map(c => ({label: c, apply: c + '="', reactivate: true}))
                }
            } else if (node.name === "AttributeValue") {
                let text = context.state.sliceDoc(node.from, context.pos);
                let quote = "";
                if (text.startsWith("\"") || text.startsWith("\'")) {
                    quote = text.charAt(0);
                    text = text.substring(1, text.length);
                }

                let matches = [];
                const tag = slice(node.parent.parent.getChild('TagName'));
                const attr = slice(node.parent.getChild('AttributeName'));
                if (tag === "bean" && attr === "class") {
                    matches = Object.entries(beandoc)
                        .filter(([className]) => className.includes(text))
                        .map(([className, bean]) => ({
                            label: className,
                            type: "class",
                            info: () => makeDiv(bean.description),
                            apply: className + quote
                        }));
                } else if (tag === "property" && attr === "name") {
                    const beanElement = closest(node, "bean");
                    if (!beanElement) return;
                    const beanClass = getAttrValue(beanElement, "class");
                    if (!beanClass) return;
                    const bean = beandoc[beanClass];
                    if (!bean) return;
                    matches = Object.entries(bean.properties)
                        .filter(([name]) => name.includes(text))
                        .map(([name, prop]) => ({
                            label: name,
                            type: "property",
                            detail: (!prop.type ? "" : stripPackage(prop.type)) +
                                (prop.default ? " = " + prop.default : ""),
                            info: () => makeDiv(prop.description),
                        }));
                } else if (tag === "ref" && attr === "bean") {
                    matches = getDeclaredBeans()
                        .filter(bean => bean.id.includes(text))
                        .map(bean => ({
                            label: bean.id,
                            type: "variable",
                            detail: bean.class ? stripPackage(bean.class) : null,
                        }));
                }
                return {
                    from: context.pos - text.length,
                    options: matches
                };
            }
        }

        /**
         * Language support for Heritrix XML configuration files with autocomplete functionality.
         *
         * @param {BeanDoc} beandoc The bean documentation object.
         * @return {LanguageSupport}
         */
        function heritrixXml(beandoc) {
            return new LanguageSupport(xmlLanguage, [xmlLanguage.data.of({
                autocomplete: context => completeHeritrixXml(beandoc, context),
            }), autoCloseTags]);
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
                        heritrixXml(beandoc),
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