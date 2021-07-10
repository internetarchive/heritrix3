import re
import javalang
import javalang.tree
from docutils import nodes
from docutils.parsers.rst import Directive


def format_example(source_file):
    with open(source_file) as f:
        tree = javalang.parse.parse(f.read())
        clazz = tree.types[0]
        full_classname = tree.package.name + '.' + clazz.name
        bean_id = clazz.name[0].lower() + clazz.name[1:]

        property_defaults = {}

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
                value = evaluate_expression(field.declarators[0].initializer)
                if value is None: continue
                property_defaults[field.declarators[0].name] = value

        code = '<bean id="' + bean_id + '" class="' + full_classname + '">\n'

        for method in clazz.methods:
            if ('public' in method.modifiers and
                    'static' not in method.modifiers and
                    method.name.startswith('set')):
                property_name = method.name[3:4].lower() + method.name[4:]
                value = property_defaults.get(property_name, "")
                code += ('  <!-- <property name="' + property_name + '" value="' + str(value).replace('"', '') +
                         '" /> -->\n')
        code += '</bean>'
        return code


class BeanExample(Directive):
    required_arguments = 1

    def run(self):
        code = format_example(self.arguments[0])
        literal = nodes.literal_block(code, code)
        literal['language'] = 'xml'
        return [literal]


def evaluate_expression(expr):
    if isinstance(expr, javalang.tree.Literal):
        match = re.match("(-?[0-9]+)[Ll]", expr.value)
        if match:
            return match.group(1)
        return expr.value
    elif isinstance(expr, javalang.tree.BinaryOperation) and expr.operator == '*':
        return int(expr.operandl.value) * int(expr.operandr.value)
    else:
        return None


if __name__ == '__main__':
    import sys
    format_example(sys.argv[1])


def setup(app):
    app.add_directive("bean-example", BeanExample)
    return {
        'version': '0.1',
        'parallel_read_safe': True,
        'parallel_write_safe': True,
    }
