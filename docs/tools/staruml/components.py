"""The component view: the library's logical components - one per Java
package -, the interfaces they provide and require, a typical client, and the
libraries the build relies on."""
from generate_staruml import element, own, ref, clone_view, text_w, BOLD_W
from model_builder import layout_and_link

# key: (name, stereotype, content, provided interfaces, required interfaces)
COMPONENTS = {
    'client': ('Client application', 'application', 'Any Java code that evaluates expressions with exp4j. It may '
               'also contribute its own functions and operators.',
               ['Custom functions and operators'], ['Expression API']),
    'api': ('API', 'subsystem', 'net.objecthunter.exp4j — ExpressionBuilder, EvaluationStrategy, Expression, '
            'ValidationResult, and the evaluators CompiledExpression (interpreter) and BytecodeCompiler (hidden '
            'classes). Builds expressions, holds variables, validates and evaluates.',
            ['Expression API'], ['Custom functions and operators']),
    'shunting': ('Shunting yard', 'subsystem', 'net.objecthunter.exp4j.shuntingyard — ShuntingYard: infix tokens '
                 'to reverse Polish notation.', [], []),
    'tokenizer': ('Tokenizer', 'subsystem', 'net.objecthunter.exp4j.tokenizer — Tokenizer, Token and its '
                  'subclasses, UnknownFunctionOrVariableException.', [], []),
    'function': ('Functions', 'subsystem', 'net.objecthunter.exp4j.function — Function (extension point) and '
                 'Functions (31 built-ins).', [], []),
    'operator': ('Operators', 'subsystem', 'net.objecthunter.exp4j.operator — Operator (extension point) and '
                 'Operators (8 built-ins).', [], []),
    'internal': ('Built-ins', 'subsystem', 'net.objecthunter.exp4j.internal (not exported) — Builtins, the single '
                 'implementation of every built-in function and operator, and BuiltinFunction / BuiltinOperator.',
                 [], []),
}

LIBRARIES = {
    'jdk': ('Java SE', 'library', 'java.base only: java.util, java.util.concurrent (ExecutorService, Future), '
            'java.lang.Math, and java.lang.classfile + java.lang.invoke for the bytecode strategy. exp4j has no '
            'runtime dependency and works in jlink-ed images.'),
    'junit': ('JUnit 4', 'library', 'junit:junit 4.13.2 — test scope.'),
    'logback': ('Logback', 'library', 'ch.qos.logback:logback-classic 1.6.5 — test scope (logback-test.xml).'),
    'tests': ('Unit tests', 'test', 'src/test/java — ExpressionBuilderTest, ExpressionTest, FunctionsTest, '
              'TokenizerTest, ShuntingYardTest, BytecodeStrategyTest, BuiltinsTest, ConcurrencyTests, '
              'PerformanceTest…'),
}

INTERFACES = {
    'Expression API': 'new ExpressionBuilder(text)…build(), then setVariable(…), validate(), evaluate() or '
                      'evaluateAsync(executor).',
    'Custom functions and operators': 'Subclasses of Function and Operator implementing apply(double...), '
                                      'passed to ExpressionBuilder.function(…) / operator(…).',
}

# Usages that don't go through an interface: (client, supplier)
USES = [('api', 'shunting'), ('api', 'tokenizer'), ('api', 'function'), ('api', 'operator'),
        ('shunting', 'tokenizer'), ('shunting', 'function'), ('shunting', 'operator'),
        ('tokenizer', 'function'), ('tokenizer', 'operator'), ('client', 'function'), ('client', 'operator'),
        ('api', 'internal'), ('function', 'internal'), ('operator', 'internal')]

LIBRARY_USES = [('api', 'jdk'), ('shunting', 'jdk'), ('tokenizer', 'jdk'), ('tests', 'api'), ('tests', 'junit'),
                ('tests', 'logback')]


def build(model, project):
    view_model = own(project, element('UMLModel', project, 'Component View',
                                      documentation='The logical components of exp4j - one per Java package -, '
                                                    'the interfaces between the library and its clients, and the '
                                                    'libraries the build depends on.'))
    comps = {}
    for key, (name, stereo, doc, *_rest) in list(COMPONENTS.items()) + list(LIBRARIES.items()):
        comps[key] = own(view_model, element('UMLComponent', view_model, name, stereotype=stereo, documentation=doc))
    interfaces = {n: own(view_model, element('UMLInterface', view_model, n, documentation=doc))
                  for n, doc in INTERFACES.items()}

    relations = []
    for key, (name, stereo, doc, provided, required) in COMPONENTS.items():
        c = comps[key]
        for itf in provided:
            r = own(c, element('UMLInterfaceRealization', c, None, source=ref(c), target=ref(interfaces[itf])))
            relations.append(('realization', key, 'itf:' + itf, r))
        for itf in required:
            r = own(c, element('UMLDependency', c, None, source=ref(c), target=ref(interfaces[itf]),
                               stereotype='use'))
            relations.append(('dependency', key, 'itf:' + itf, r))
    uses = []
    for a, b in USES + LIBRARY_USES:
        r = own(comps[a], element('UMLDependency', comps[a], None, source=ref(comps[a]), target=ref(comps[b]),
                                  stereotype='use'))
        uses.append(('dependency', a, b, r))

    def component_view(d, key):
        c = comps[key]
        v = clone_view('UMLComponentView', d, c)
        v['stereotypeDisplay'] = 'decoration-label'
        v['suppressAttributes'] = True
        v['suppressOperations'] = True
        w, h = model.size(v, max(text_w(c['name'], BOLD_W) + 60, 150), 56)
        v['width'], v['height'] = round(w), round(h)
        return model.node(d, v)

    def interface_view(d, name):
        v = clone_view('UMLInterfaceView', d, interfaces[name])
        v['stereotypeDisplay'] = 'icon'
        v['suppressAttributes'] = True
        v['suppressOperations'] = True
        w, h = model.size(v, max(text_w(name, BOLD_W) + 10, 60), 50)
        v['width'], v['height'] = round(w), round(h)
        return model.node(d, v)

    d = own(view_model, element('UMLComponentDiagram', view_model, '11 · Components — provided and required '
                                                                   'interfaces',
                                documentation='The client requires the Expression API, which the API component '
                                              'provides; in return exp4j accepts the client\'s own functions and '
                                              'operators.'))
    views = {k: component_view(d, k) for k, c in COMPONENTS.items() if c[3] or c[4]}
    for name in interfaces:
        views['itf:' + name] = interface_view(d, name)
    layout_and_link(model, d, views, relations, ranksep=0.9, nodesep=0.6)

    d = own(view_model, element('UMLComponentDiagram', view_model, '12 · Components — dependencies and libraries',
                                documentation='Direct dependencies between the components, and towards the '
                                              'libraries. At runtime exp4j needs only the JDK.'))
    views = {k: component_view(d, k) for k in list(COMPONENTS) + list(LIBRARIES)}
    layout_and_link(model, d, views, uses, ranksep=0.8, nodesep=0.4)
