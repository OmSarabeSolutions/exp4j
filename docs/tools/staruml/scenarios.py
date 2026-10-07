"""The interactions: one sequence diagram per scenario, described from the code.

A scenario lists its lifelines - key: (role name, type) where the type is a
class of the logical model or the actor "Client code" - and its steps:

  ('call',   from, to, 'op(args)')     synchronous call - linked to the operation when `to` has it
  ('async',  from, to, 'op(args)')     asynchronous call
  ('reply',  from, to, 'text')         return
  ('create', from, to, 'text')         creation - `to` starts at this message
  ('self',   who, 'op(args)')          call to itself
  ('alt',  [(guard, [steps]), ...])    alternatives
  ('opt',  guard, [steps])             optional
  ('loop', guard, [steps])             loop
"""
import re

from generate_staruml import element, own, ref, clone_view, sub_view, ref_target, points_str, text_w

U = 'Client code'

SCENARIOS = [
    {
        'name': '20 · Sequence — Build an expression',
        'doc': 'ExpressionBuilder collects the variable names and the user functions and operators, then build() '
               'adds the constants (pi, π, e, φ), checks that no variable is named like a function, and has '
               'ShuntingYard convert the text to reverse Polish notation, reading it token by token from a '
               'Tokenizer.',
        'lifelines': {'u': ('', U), 'b': ('', 'ExpressionBuilder'), 'sy': ('', 'ShuntingYard'),
                      'tk': ('', 'Tokenizer'), 'fs': ('', 'Functions'), 'ops': ('', 'Operators'),
                      'ex': ('', 'Expression')},
        'steps': [
            ('create', 'u', 'b', 'new ExpressionBuilder(text)'),
            ('call', 'u', 'b', 'variables("x", "y")'),
            ('opt', '[custom functions or operators]', [
                ('call', 'u', 'b', 'function(f) / operator(op)'),
            ]),
            ('call', 'u', 'b', 'build()'),
            ('self', 'b', 'add constants pi, π, e, φ to the variable names'),
            ('opt', '[a variable has the name of a function]', [
                ('reply', 'b', 'u', 'IllegalArgumentException'),
            ]),
            ('call', 'b', 'sy', 'convertToRPN(expression, userFunctions, userOperators, variableNames, implicitMultiplication)'),
            ('create', 'sy', 'tk', 'new Tokenizer(…)'),
            ('loop', '[tokenizer.hasNext()]', [
                ('call', 'sy', 'tk', 'nextToken()'),
                ('alt', [
                    ('[letter or _ : longest known name]', [
                        ('call', 'tk', 'fs', 'getBuiltinFunction(name)'),
                    ]),
                    ('[operator character]', [
                        ('call', 'tk', 'ops', 'getBuiltinOperator(symbol, numArguments)'),
                    ]),
                    ('[operand right after an operand, implicit multiplication on]', [
                        ('self', 'tk', 'emit an implicit * OperatorToken'),
                    ]),
                ]),
                ('reply', 'tk', 'sy', 'Token'),
                ('self', 'sy', 'output queue / operator stack (precedence, associativity)'),
            ]),
            ('self', 'sy', 'pop the remaining operators; mismatched parentheses → IllegalArgumentException'),
            ('reply', 'sy', 'b', 'Token[] in RPN'),
            ('create', 'b', 'ex', 'new Expression(tokens, userFunctionNames)'),
            ('reply', 'b', 'u', 'Expression'),
        ],
    },
    {
        'name': '21 · Sequence — Evaluate an expression',
        'doc': 'evaluate() walks the RPN tokens once, with an ArrayStack of doubles: operands are pushed, operators '
               'and functions pop their arguments and push their result. The single value left is the result.',
        'lifelines': {'u': ('', U), 'ex': ('', 'Expression'), 'st': ('', 'ArrayStack'),
                      'op': ('', 'Operator'), 'fn': ('', 'Function')},
        'steps': [
            ('loop', '[for each variable]', [
                ('call', 'u', 'ex', 'setVariable(name, value)'),
            ]),
            ('call', 'u', 'ex', 'evaluate()'),
            ('create', 'ex', 'st', 'new ArrayStack()'),
            ('loop', '[for each token, in RPN order]', [
                ('alt', [
                    ('[NumberToken]', [
                        ('call', 'ex', 'st', 'push(value)'),
                    ]),
                    ('[VariableToken]', [
                        ('self', 'ex', 'variables.get(name) — unset → IllegalArgumentException'),
                        ('call', 'ex', 'st', 'push(value)'),
                    ]),
                    ('[OperatorToken]', [
                        ('call', 'ex', 'st', 'pop() — 1 or 2 operands'),
                        ('call', 'ex', 'op', 'apply(args)'),
                        ('reply', 'op', 'ex', 'result'),
                        ('call', 'ex', 'st', 'push(result)'),
                    ]),
                    ('[FunctionToken]', [
                        ('call', 'ex', 'st', 'pop() — numArguments times'),
                        ('call', 'ex', 'fn', 'apply(args)'),
                        ('reply', 'fn', 'ex', 'result'),
                        ('call', 'ex', 'st', 'push(result)'),
                    ]),
                ]),
            ]),
            ('opt', '[more than one value left on the stack]', [
                ('reply', 'ex', 'u', 'IllegalArgumentException'),
            ]),
            ('call', 'ex', 'st', 'pop()'),
            ('reply', 'ex', 'u', 'double result'),
        ],
    },
    {
        'name': '22 · Sequence — Validate an expression',
        'doc': 'validate() checks, without evaluating, that every variable has a value (optional) and that the '
               'operands, operators and function arguments balance: a counter of available operands must stay '
               'at least 1 and end at exactly 1.',
        'lifelines': {'u': ('', U), 'ex': ('', 'Expression'), 'fn': ('', 'Function'), 'op': ('', 'Operator'),
                      'vr': ('', 'ValidationResult')},
        'steps': [
            ('call', 'u', 'ex', 'validate(checkVariablesSet)'),
            ('opt', '[checkVariablesSet]', [
                ('self', 'ex', 'every VariableToken has a value? else error'),
            ]),
            ('loop', '[for each token]', [
                ('alt', [
                    ('[number or variable]', [
                        ('self', 'ex', 'count++'),
                    ]),
                    ('[FunctionToken]', [
                        ('call', 'ex', 'fn', 'getNumArguments()'),
                        ('self', 'ex', 'count -= n − 1 (count++ if n = 0)'),
                    ]),
                    ('[OperatorToken]', [
                        ('call', 'ex', 'op', 'getNumOperands()'),
                        ('self', 'ex', 'count-- if binary'),
                    ]),
                ]),
                ('opt', '[count < 1]', [
                    ('create', 'ex', 'vr', 'new ValidationResult(false, "Too many operators")'),
                ]),
            ]),
            ('reply', 'ex', 'u', 'SUCCESS, or the errors ("Too many operands"…)'),
        ],
    },
    {
        'name': '23 · Sequence — Asynchronous evaluation',
        'doc': 'evaluateAsync submits evaluate() to the caller\'s ExecutorService. An Expression holds its '
               'variables, so threads evaluating with different values each use a copy: new Expression(existing).',
        'lifelines': {'u': ('', U), 'ex': ('', 'Expression'), 'exec': ('', 'ExecutorService'),
                      'fut': ('', 'Future')},
        'steps': [
            ('opt', '[other threads use the same expression]', [
                ('create', 'u', 'ex', 'new Expression(existing) — independent copy'),
            ]),
            ('call', 'u', 'ex', 'setVariable(name, value)'),
            ('call', 'u', 'ex', 'evaluateAsync(executor)'),
            ('call', 'ex', 'exec', 'submit(this::evaluate)'),
            ('reply', 'exec', 'ex', 'Future<Double>'),
            ('reply', 'ex', 'u', 'Future<Double>'),
            ('async', 'exec', 'ex', 'evaluate() on a worker thread'),
            ('call', 'u', 'fut', 'get()'),
            ('reply', 'fut', 'u', 'Double (or ExecutionException)'),
        ],
    },
]

HEAD_H = 40        # lifeline head
ROW = 34
SELF_ROW = 46
FRAG_HEAD = 44
FRAG_END = 14


def build(model, project):
    dyn = own(project, element('UMLModel', project, 'Dynamic View — interactions',
                               documentation='The main scenarios of the library, as sequence diagrams.'))
    actor = own(dyn, element('UMLActor', dyn, U, documentation='The Java code that uses exp4j.'))
    collab = own(dyn, element('UMLCollaboration', dyn, 'Scenarios',
                              documentation='The roles the classes play in the scenarios.'))
    roles = {}

    def role(name, type_name):
        key = (name, type_name)
        if key not in roles:
            t = actor if type_name == U else model.elements[type_name]
            roles[key] = own(collab, element('UMLAttribute', collab, name, type=ref(t)), 'attributes')
        return roles[key]

    for sc in SCENARIOS:
        build_scenario(model, collab, sc, role)


def build_scenario(model, collab, sc, role):
    inter = own(collab, element('UMLInteraction', collab, sc['name'], documentation=sc['doc']))
    d = own(inter, element('UMLSequenceDiagram', inter, sc['name'], documentation=sc['doc'],
                           showSequenceNumber=False, showSignature=True, showActivation=False))
    frame = clone_view('UMLFrameView', d, d)
    model.node(d, frame)

    # Lifelines
    lifelines, lviews, x = {}, {}, 40
    for key, (rname, tname) in sc['lifelines'].items():
        life = own(inter, element('UMLLifeline', inter, rname, represent=ref(role(rname, tname))), 'participants')
        lifelines[key] = life
        label = f'{rname}: {tname}' if tname != U else f': {U}'
        w = max(text_w(label) + 24, 90)
        v = clone_view('UMLSeqLifelineView', d, life)
        v['left'], v['top'], v['width'] = round(x), 50, round(w)
        if tname == U:
            v['stereotypeDisplay'] = 'icon'
        lviews[key] = v
        x += w + 36
    center = {k: v['left'] + v['width'] / 2 for k, v in lviews.items()}

    created = {}
    msgs, frags = [], []
    y = [50 + HEAD_H + 40]

    def involved(steps):
        out = set()
        for s in steps:
            if s[0] in ('call', 'async', 'reply', 'create'):
                out |= {s[1], s[2]}
            elif s[0] == 'self':
                out.add(s[1])
            elif s[0] == 'alt':
                for _, sub in s[1]:
                    out |= involved(sub)
            else:
                out |= involved(s[2])
        return out

    def walk(steps, depth):
        for s in steps:
            kind = s[0]
            if kind in ('alt', 'opt', 'loop'):
                operands = s[1] if kind == 'alt' else [(s[1], s[2])]
                frag = element('UMLCombinedFragment', inter, '', interactionOperator=kind)
                own(inter, frag, 'fragments')   # StarUML: every fragment belongs to the interaction
                y[0] += 10
                top = y[0] - 20
                y[0] += FRAG_HEAD            # frame header + first guard
                ops = []
                for i, (guard, sub) in enumerate(operands):
                    operand = own(frag, element('UMLInteractionOperand', frag, '', guard=guard.strip('[]')),
                                  'operands')
                    otop = top + 20
                    if i:
                        otop = y[0] - 12      # separator, then this operand's guard
                        y[0] += FRAG_HEAD - 8
                    walk(sub, depth + 1)
                    ops.append((operand, otop))
                bottom_f = y[0] - 8
                keys = involved([s])
                frags.append((frag, kind, keys, depth, top, bottom_f, ops))
                y[0] += FRAG_END + 10
                continue
            if kind == 'self':
                src = tgt = s[1]
                label = s[2]
            else:
                src, tgt, label = s[1], s[2], s[3]
            sort = {'call': 'synchCall', 'async': 'asynchCall', 'reply': 'reply', 'create': 'createMessage',
                    'self': 'synchCall'}[kind]
            m = element('UMLMessage', inter, '', source=ref(lifelines[src]), target=ref(lifelines[tgt]),
                        messageSort=sort)
            name, args = label, None
            mm = re.match(r'^(\w+)\((.*)\)$', label)
            target_type = sc['lifelines'][tgt][1]
            if mm and kind != 'reply' and (target_type, mm.group(1)) in model.ops:
                name, args = mm.group(1), mm.group(2)
                m['signature'] = ref(model.ops[(target_type, mm.group(1))])
                if args:
                    m['arguments'] = args
            m['name'] = name
            own(inter, m, 'messages')
            if kind == 'create':
                created[tgt] = y[0]
            msgs.append((m, kind, src, tgt, y[0]))
            y[0] += SELF_ROW if kind == 'self' else ROW + 14 if kind == 'reply' else ROW

    walk(sc['steps'], 0)
    bottom = y[0] + 20

    # Lifeline views (created ones start at their creation message)
    for key, v in lviews.items():
        if key in created:
            v['top'] = round(created[key] - 20)
        v['height'] = round(bottom - v['top'])
        name_c = ref_target(v, 'nameCompartment')
        name_c['left'], name_c['top'], name_c['width'], name_c['height'] = v['left'], v['top'], v['width'], HEAD_H
        line = ref_target(v, 'linePart')
        line['left'], line['top'] = round(center[key]), v['top'] + HEAD_H
        line['width'], line['height'] = 1, v['height'] - HEAD_H
        model.node(d, v)

    # Combined fragments (outer ones first, so they sit behind)
    for frag, kind, keys, depth, top, bottom_f, ops in sorted(frags, key=lambda f: f[3]):
        lefts = [lviews[k]['left'] for k in keys]
        rights = [lviews[k]['left'] + lviews[k]['width'] for k in keys]
        pad = 14 - 5 * depth
        left, right = min(lefts) - pad, max(rights) + pad
        if any(k for k in keys if any(mk == 'self' and ms == k for _, mk, ms, _, _ in msgs)):
            right += 60
        fv = clone_view('UMLCombinedFragmentView', d, frag)
        fv['left'], fv['top'] = round(left), round(top)
        fv['width'], fv['height'] = round(right - left), round(bottom_f - top)
        comp = ref_target(fv, 'operandCompartment')
        comp['left'], comp['top'] = fv['left'], fv['top'] + 20
        comp['width'], comp['height'] = fv['width'], fv['height'] - 20
        tops = [t for _, t in ops] + [bottom_f]
        for i, (operand, otop) in enumerate(ops):
            ov = clone_view('UMLInteractionOperandView', comp, operand)
            ov['left'], ov['top'] = fv['left'], round(otop if i else fv['top'] + 20)
            ov['width'], ov['height'] = fv['width'], round(tops[i + 1] - ov['top'])
            comp.setdefault('subViews', []).append(ov)
        model.node(d, fv)

    # Message views
    for m, kind, src, tgt, my in msgs:
        tail = ref_target(lviews[src], 'linePart')
        head = ref_target(lviews[tgt], 'linePart')
        if kind == 'self':
            cx = center[src]
            pts = [(cx, my), (cx + 40, my), (cx + 40, my + 16), (cx, my + 16)]
            style = 0
        else:
            x1, x2 = center[src], center[tgt]
            if kind == 'create':
                x2 = lviews[tgt]['left'] if x2 > x1 else lviews[tgt]['left'] + lviews[tgt]['width']
            pts = [(x1, my), (x2, my)]
            style = 0
        mv = model.edge(d, 'UMLSeqMessageView', m, tail, head, pts, style)
        act = sub_view(mv, 'UMLActivationView')
        act['left'], act['top'], act['width'], act['height'] = round(center[tgt] - 7), round(my), 14, 25
        act['visible'] = False

    right = max([v['left'] + v['width'] for v in lviews.values()] +
                [v['left'] + v['width'] for v in d['ownedViews'] if v['_type'] == 'UMLCombinedFragmentView']) + 30
    frame['left'], frame['top'], frame['width'], frame['height'] = 20, 16, round(right - 20), round(bottom + 10 - 16)
