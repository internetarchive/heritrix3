#
# This Sphinx extension defines some directives for generating bean documentation from Java source code.
#

import re
from html.parser import HTMLParser

import bs4
import json, traceback, sys
from docutils import nodes
from docutils.parsers.rst import Directive
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


def format_example(source_file):
    model = parse_java(source_file)
    code = '<bean id="' + model['bean_id'] + '" class="' + model['class'] + '">\n'
    for prop in model['properties']:
        code += '  <!-- <property name="' + prop['name'] + '" value="' + prop['value'] + '" /> -->\n'
    code += '</bean>'
    return code


class BeanExample(Directive):
    required_arguments = 1

    def run(self):
        code = format_example(self.arguments[0])
        literal = nodes.literal_block(code, code, language='xml')
        return [literal]


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
            example = nodes.literal_block('', format_example(self.arguments[0]), language='xml')
            properties = format_properties(model)
            return [text, example, properties]
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

    format_example(sys.argv[1])
