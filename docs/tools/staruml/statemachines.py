"""The state machines, described from the code. Each one belongs to the class
whose behaviour it describes, in the logical model.

  'states':      key -> (name, kind, {'entry'|'do'|'exit': activity})   kind: state, initial, choice, final
  'transitions': (source, target, trigger, guard, effect)
"""
from generate_staruml import element, own, ref, clone_view, graphviz, points_str, text_w, BOLD_W, LINE_H

S, I, C, F = 'state', 'initial', 'choice', 'final'

MACHINES = [
    {
        'name': '30 · States — Tokenizer', 'owner': 'Tokenizer',
        'doc': 'What Tokenizer.nextToken() expects next, decided by the last token emitted. After an operand, + '
               'and - are binary and a number, a name or ( gets an implicit * first; anywhere else + and - are '
               'unary. The character that triggers an implicit * is read again on the next call.',
        'states': {
            'i': ('', I, {}),
            'operand': ('Expecting an operand', S, {'entry': 'start, or after an operator, (, , or a function'}),
            'after': ('After an operand', S, {'entry': 'after a number, a variable or )'}),
            'end': ('', F, {}),
            'err': ('Error', S, {'do': 'throw IllegalArgumentException'}),
        },
        'transitions': [
            ('i', 'operand', 'new Tokenizer(…)', '', ''),
            ('operand', 'after', 'number or variable', '', 'NumberToken / VariableToken'),
            ('operand', 'operand', '+, -, ( or function name', '', 'unary OperatorToken, OpenParenthesesToken, FunctionToken'),
            ('operand', 'after', ')', 'zero-argument function call', 'CloseParenthesesToken'),
            ('after', 'operand', 'operator or ,', '', 'binary OperatorToken / ArgumentSeparatorToken'),
            ('after', 'after', ') or postfix operator', '', 'CloseParenthesesToken / OperatorToken'),
            ('after', 'operand', 'number, name or (', 'implicitMultiplication', 'implicit * OperatorToken'),
            ('operand', 'err', 'unknown name', '', 'UnknownFunctionOrVariableException'),
            ('after', 'err', 'number after a number', '', ''),
            ('after', 'end', '', 'hasNext() = false', ''),
        ],
    },
    {
        'name': '31 · States — Expression', 'owner': 'Expression',
        'doc': 'An Expression knows the constants pi, π, e and φ from the start. evaluate() succeeds once every '
               'variable of the expression has a value; clearVariables() also removes the constants.',
        'states': {
            'i': ('', I, {}),
            'unset': ('Variables missing', S, {}),
            'ready': ('Ready', S, {'entry': 'validate() returns SUCCESS'}),
            'c': ('', C, {}),
        },
        'transitions': [
            ('i', 'c', 'ExpressionBuilder.build()', '', ''),
            ('unset', 'c', 'setVariable(name, value)', '', ''),
            ('c', 'ready', '', 'all variables set', ''),
            ('c', 'unset', '', 'some variable unset', ''),
            ('ready', 'ready', 'evaluate() / setVariable(…)', '', 'returns a double'),
            ('unset', 'unset', 'evaluate()', '', 'IllegalArgumentException'),
            ('ready', 'unset', 'clearVariables()', 'the expression has variables', ''),
        ],
    },
]


def label(trigger, guard, effect):
    out = trigger
    if guard:
        out += f' [{guard}]'
    if effect:
        out += f' / {effect}'
    return out.strip()


def build(model, project):
    for sm in MACHINES:
        build_machine(model, sm)


def build_machine(model, sm):
    owner = model.elements[sm['owner']]
    machine = own(owner, element('UMLStateMachine', owner, sm['name'], documentation=sm['doc']))
    region = own(machine, element('UMLRegion', machine, ''), 'regions')
    d = own(machine, element('UMLStatechartDiagram', machine, sm['name'], documentation=sm['doc']))
    frame = model.node(d, clone_view('UMLFrameView', d, d))

    vertices, views = {}, {}
    for key, (name, kind, acts) in sm['states'].items():
        if kind == S:
            v_el = element('UMLState', region, name)
            for what, field in (('entry', 'entryActivities'), ('do', 'doActivities'), ('exit', 'exitActivities')):
                if what in acts:
                    own(v_el, element('UMLOpaqueBehavior', v_el, acts[what]), field)
        elif kind == F:
            v_el = element('UMLFinalState', region, name)
        else:
            v_el = element('UMLPseudostate', region, name, kind=kind)
        vertices[key] = own(region, v_el, 'vertices')
        vtype = {S: 'UMLStateView', F: 'UMLFinalStateView'}.get(kind, 'UMLPseudostateView')
        v = clone_view(vtype, d, v_el)
        if kind == S:
            lines = [f'{w}/{a}' for w, a in acts.items()]
            w = max([text_w(name, BOLD_W)] + [text_w(x) for x in lines]) + 30
            h = 34 + (LINE_H * len(lines) + 8 if lines else 0)
        else:
            w = h = {I: 20, C: 30, F: 26}[kind]
        w, h = model.size(v, max(w, 110) if kind == S else w, h)
        v['width'], v['height'] = round(w), round(h)
        views[key] = model.node(d, v)

    transitions = []
    for src, tgt, trig, guard, effect in sm['transitions']:
        t = element('UMLTransition', region, '', source=ref(vertices[src]), target=ref(vertices[tgt]), kind='external')
        if trig:
            own(t, element('UMLEvent', t, trig, kind='anyReceive'), 'triggers')
        if guard:
            t['guard'] = guard
        if effect:
            own(t, element('UMLOpaqueBehavior', t, effect), 'effects')
        own(region, t, 'transitions')
        transitions.append((src, tgt, t, label(trig, guard, effect)))

    nodes = {k: (v['width'], v['height']) for k, v in views.items()}
    centers, routes = graphviz(nodes, [(a, b) for a, b, _, _ in transitions], rankdir='TB', nodesep=0.9,
                               ranksep=0.9, labels=[lb for *_, lb in transitions])
    ox, oy = 240, 70          # room on the left for the labels of transitions going left
    for k, v in views.items():
        x, y = centers[k]
        v['left'], v['top'] = round(x - v['width'] / 2 + ox), round(y - v['height'] / 2 + oy)
    for i, (a, b, t, _) in enumerate(transitions):
        pts = [(x + ox, y + oy) for x, y in routes[i]]
        if a == b:
            # A loop on the right side of the state
            v = views[a]
            r, top = v['left'] + v['width'], v['top']
            pts = [(r - 20, top), (r - 20, top - 25), (r + 30, top - 25), (r + 30, top + 15), (r, top + 15)]
        model.edge(d, 'UMLTransitionView', t, views[a], views[b], pts, 1)
    edge_xs = [float(p.split(':')[0]) for v in d['ownedViews'] if v['_type'] == 'UMLTransitionView'
               for p in v['points'].split(';')]
    right = max([v['left'] + v['width'] for v in views.values()] + edge_xs) + 220
    bottom = max(v['top'] + v['height'] for v in views.values()) + 40
    frame['left'], frame['top'], frame['width'], frame['height'] = 20, 16, round(right - 20), round(bottom - 16)
