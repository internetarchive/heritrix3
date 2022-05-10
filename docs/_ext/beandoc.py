#
# This Sphinx extension defines some directives for generating bean documentation from Java source code.
#

import re
from html.parser import HTMLParser

import bs4
import javalang
import javalang.tree
from docutils import nodes
from docutils.parsers.rst import Directive


def evaluate_expression(expr):
    if isinstance(expr, javalang.tree.Literal):
        match = re.match("(-?[0-9]+)[Ll]", expr.value)
        if match:
            return match.group(1)
        return expr.value
    elif isinstance(expr, javalang.tree.BinaryOperation) and expr.operator == '*':
        lhs = evaluate_expression(expr.operandl)
        rhs = evaluate_expression(expr.operandr.value)
        if lhs is None or rhs is None: return None
        return int(lhs) * int(rhs)
    else:
        return None


def parse_java(source_file):
    model = {}
    with open(source_file) as f:
        tree = javalang.parse.parse(f.read())
    clazz = tree.types[0]
    model['class'] = tree.package.name + '.' + clazz.name
    model['bean_id'] = clazz.name[0].lower() + clazz.name[1:]
    model['properties'] = []
    model['doc'] = javalang.javadoc.parse(clazz.documentation) if clazz.documentation else None

    property_defaults = {}
    property_docs = {}

    for member in clazz.body:
        if (isinstance(member, list) and len(member) > 0 and
                isinstance(member[0], javalang.tree.StatementExpression) and
                isinstance(member[0].expression, javalang.tree.MethodInvocation) and
                member[0].expression.member.startswith('set')):
            value = evaluate_expression(member[0].expression.arguments[0])
            if value is None: continue
            setter_name = member[0].expression.member
            property_name = setter_name[3:4].lower() + setter_name[4:]
            property_defaults[property_name] = value

    for field in clazz.fields:
        if field.declarators:
            field_name = field.declarators[0].name
            field_value = evaluate_expression(field.declarators[0].initializer)
            if field_value is not None:
                property_defaults[field_name] = field_value
            if field.documentation is not None:
                property_docs[field_name] = field.documentation

    for method in clazz.methods:
        if ('public' in method.modifiers and
                'static' not in method.modifiers and
                method.name.startswith('set')):
            property_name = method.name[3:4].lower() + method.name[4:]
            value = property_defaults.get(property_name, "")
            doc = method.documentation or property_docs.get(property_name)
            model['properties'].append({
                'name': property_name,
                'value': str(value).replace('"', ''),
                'type': method.parameters[0].type.name,
                'doc': javalang.javadoc.parse(doc) if doc else None
            })
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


def format_javadoc(javadoc):
    doc = javadoc.description if javadoc else ''
    doc = doc.replace('\n\n', '<p>').replace('<p><p>', '<p>')
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
        model = parse_java(self.arguments[0])
        text = nodes.paragraph('', '', format_javadoc(model['doc']))
        example = nodes.literal_block('', format_example(self.arguments[0]), language='xml')
        properties = format_properties(model)
        return [text, example, properties]


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
