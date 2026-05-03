#
# This Sphinx extension defines some directives for generating bean documentation from Java source code.
#

import re
from html.parser import HTMLParser

import bs4
import json, traceback, sys
from docutils import nodes
from docutils.parsers.rst import Directive
from docutils.statemachine import ViewList
from six import iteritems

BEANS_FILES = [
    "../commons/target/classes/META-INF/heritrix-beans.json",
    "../modules/target/classes/META-INF/heritrix-beans.json",
    "../engine/target/classes/META-INF/heritrix-beans.json",
    "../contrib/target/classes/META-INF/heritrix-beans.json",
]

# These beans will be ignored when resolving inherited properties
# This omits common boilerplate properties from the output (like enabled)
NO_INHERIT_BEANS = {
    'org.archive.modules.Processor',
    'org.archive.modules.deciderules.DecideRule',
}

def load_beans():
    beandoc = {}
    for filename in BEANS_FILES:
        with open(filename) as f:
            beandoc.update(json.load(f))
    return beandoc

beans = load_beans()

def java_value(value):
    if value is None: return ''
    if value is True: return 'true'
    if value is False: return 'false'
    return str(value)

def parse_java(class_name):
    print('Parsing', class_name)
    bean = beans.get(class_name)
    name = class_name.split('.')[-1]
    print(repr(bean))

    model = {}
    model['class'] = class_name
    model['bean_id'] = name[0].lower() + name[1:]
    model['properties'] = []
    model['doc'] = None

    if bean:
        model['doc'] = bean.get('description')

        cur_bean = bean
        while cur_bean is not None:
            for name, property in iteritems(cur_bean['properties']):
                if name == 'beanName': continue
                model['properties'].append({
                    'name': name,
                    'value': java_value(property.get('default', '')),
                    'type': property['type'],
                    'doc': property.get('description')
                })
            if cur_bean.get('superclass') in NO_INHERIT_BEANS: break
            cur_bean = beans.get(cur_bean.get('superclass'))

        model['properties'].sort(key=lambda prop: prop['name'])

    return model


class HtmlConverter(HTMLParser):
    def __init__(self):
        super(HtmlConverter, self).__init__()
        self.node = nodes.inline()
        self.stack = []

    def handle_starttag(self, tag, attrs):
        if tag == 'p':
            self.stack.append(self.node)
            new_node = nodes.paragraph()
            self.node.append(new_node)
            self.node = new_node

    def handle_endtag(self, tag):
        if tag == 'p':
            self.node = self.stack.pop()

    def handle_data(self, data):
        self.node.append(nodes.inline('', data))


def convert_html(soup):
    if isinstance(soup, str):
        if soup.startswith('NOTE:'):
            return nodes.note('', nodes.inline('', soup[5:]))
        if soup.startswith('TODO:'):
            soup = ''
        return nodes.inline('', soup)
    if soup.name == 'p':
        return nodes.paragraph('', '', *[convert_html(child) for child in soup.childGenerator()])
    elif soup.name == 'i':
        return nodes.emphasis('', '', *[convert_html(child) for child in soup.childGenerator()])
    elif soup.name == 'b':
        return nodes.strong('', '', *[convert_html(child) for child in soup.childGenerator()])
    elif soup.name == 'code':
        return nodes.literal('', '', *[convert_html(child) for child in soup.childGenerator()])
    elif soup.name == 'pre':
        return nodes.literal_block('', '', *[convert_html(child) for child in soup.childGenerator()])
    else:
        return nodes.inline('', '', *[convert_html(child) for child in soup.childGenerator()])


def format_javadoc(doc):
    if doc is None: return nodes.inline('', '')
    doc = doc.replace('\n\n', '<p>').replace('<p><p>', '<p>')
    doc = doc.replace('\n', '<br>')
    doc = doc.replace('  ', ' &nbsp;')
    doc = re.sub(r'{@link ([^}]+)}', r'\1', doc)
    doc = re.sub(r'{@code ([^}]+)}', r'<code>\1</code>', doc)
    soup = bs4.BeautifulSoup(doc, features="html.parser")
    return convert_html(soup)


def format_example_xml(source_file):
    model = parse_java(source_file)
    code = '<bean id="' + model['bean_id'] + '" class="' + model['class'] + '">\n'
    for prop in model['properties']:
        code += '  <!-- <property name="' + prop['name'] + '" value="' + prop['value'] + '" /> -->\n'
    code += '</bean>'
    return code


# Java types that should render as bare numeric Groovy literals.
NUMERIC_TYPES = {
    'byte', 'short', 'int', 'long', 'float', 'double',
    'java.lang.Byte', 'java.lang.Short', 'java.lang.Integer',
    'java.lang.Long', 'java.lang.Float', 'java.lang.Double',
    'java.math.BigDecimal', 'java.math.BigInteger', 'java.lang.Number',
}

BOOLEAN_TYPES = {'boolean', 'java.lang.Boolean'}


def groovy_value(value, type_):
    """Render a property default as a Groovy literal."""
    if type_ in BOOLEAN_TYPES:
        # value already normalised to 'true'/'false'/'' by java_value
        return value if value in ('true', 'false') else 'false'
    if type_ in NUMERIC_TYPES:
        return value if value != '' else '0'
    escaped = value.replace('\\', '\\\\').replace("'", "\\'")
    return "'" + escaped + "'"


def format_example_groovy(source_file):
    model = parse_java(source_file)
    short_class = model['class'].split('.')[-1]
    lines = []
    lines.append('import ' + model['class'])
    lines.append('')
    lines.append(model['bean_id'] + '(' + short_class + ') {')
    for prop in model['properties']:
        lines.append('    // ' + prop['name'] + ' = ' +
                     groovy_value(prop['value'], prop['type']))
    lines.append('}')
    return '\n'.join(lines)


def emit_tab_pair(directive, xml_code, groovy_code):
    """Emit a sphinx-inline-tabs XML/Groovy pair as docutils nodes."""
    lines = []
    lines.append('.. tab:: XML')
    lines.append('')
    lines.append('   .. code-block:: xml')
    lines.append('')
    for line in xml_code.splitlines():
        lines.append('      ' + line)
    lines.append('')
    lines.append('.. tab:: Groovy')
    lines.append('')
    lines.append('   .. code-block:: groovy')
    lines.append('')
    for line in groovy_code.splitlines():
        lines.append('      ' + line)

    vl = ViewList(lines, source='<beandoc>')
    container = nodes.container()
    directive.state.nested_parse(vl, directive.content_offset, container)
    return container.children


class BeanExample(Directive):
    required_arguments = 1

    def run(self):
        xml_code = format_example_xml(self.arguments[0])
        groovy_code = format_example_groovy(self.arguments[0])
        return list(emit_tab_pair(self, xml_code, groovy_code))


def format_properties(model):
    list = nodes.definition_list()
    for prop in model['properties']:
        term = nodes.term('', prop['name'])
        definition = nodes.definition('')
        definition += nodes.strong('', '(' + prop['type'] + ') ')
        definition += format_javadoc(prop['doc'])
        item = nodes.definition_list_item('', term, definition)
        list.append(item)
    return list


class BeanProperties(Directive):
    required_arguments = 1

    def run(self):
        model = parse_java(self.arguments[0])
        list = format_properties(model)

        return [list]


class BeanDoc(Directive):
    required_arguments = 1

    def run(self):
        try:
            model = parse_java(self.arguments[0])
            text = nodes.paragraph('', '', format_javadoc(model['doc']))
            xml_code = format_example_xml(self.arguments[0])
            groovy_code = format_example_groovy(self.arguments[0])
            example_nodes = list(emit_tab_pair(self, xml_code, groovy_code))
            properties = format_properties(model)
            return [text] + example_nodes + [properties]
        except Exception as e:
            exc_type, exc_value, exc_tb = sys.exc_info()
            stacktrace_str = ''.join(traceback.format_exception(exc_type, exc_value, exc_tb))
            print('Error parsing', self.arguments[0], repr(e))
            return [nodes.literal_block('', 'Error generating documentation: ' + repr(e) + "\n\n" + stacktrace_str)]


def setup(app):
    app.add_directive("bean-doc", BeanDoc)
    app.add_directive("bean-example", BeanExample)
    app.add_directive("bean-properties", BeanProperties)
    return {
        'version': '0.1',
        'parallel_read_safe': True,
        'parallel_write_safe': True,
    }


if __name__ == '__main__':
    import sys

    print('--- XML ---')
    print(format_example_xml(sys.argv[1]))
    print()
    print('--- Groovy ---')
    print(format_example_groovy(sys.argv[1]))
