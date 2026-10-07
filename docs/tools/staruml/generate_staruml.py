#!/usr/bin/env python3
"""Updates the StarUML model of the project: docs/uml/exp4j.mdj

    python3 docs/tools/staruml/generate_staruml.py [--sizes sizes.json] [--base in.mdj] [--out out.mdj]

(run from the exp4j project root - build.sh runs the whole update). Needs
Graphviz (`dot`) for the layout.

The logical model - packages, classes, interfaces, records, their attributes,
operations and relationships - is read from src/main/java. The component
view, the interactions (sequence diagrams) and the state machines are
described in components.py, scenarios.py and statemachines.py, from the
code they document.

The freshly built model is then MERGED into the existing file (merge.py):
what was arranged or added by hand in StarUML - layout, styles, notes, the
overview diagram - is kept; only what is new in the code is added, and
what disappeared from it removed. `--fresh` ignores the existing file and
loses all of that.

Views are built from view-templates.json: the views exactly as StarUML's own
constructors create them (see dump-view-templates.js). StarUML recomputes
label texts and minimum sizes when it draws a diagram, so the generator only
has to place the nodes. Sizes are estimated from the texts; `--sizes` takes
the exact ones measured by measure.js on a trial merge.
"""
import argparse
import base64
import copy
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

# The library's sources, by package relative to net.objecthunter.exp4j
SRCS = ['src/main/java/net/objecthunter/exp4j']
OUT = 'docs/uml/exp4j.mdj'
TEMPLATES = json.load(open(os.path.join(HERE, 'view-templates.json')))

# ============================================================================ ids

_counter = [0]


def new_id():
    """Stable, unique ids - the same project always yields the same file."""
    _counter[0] += 1
    return base64.b64encode(b'Exp4jM' + _counter[0].to_bytes(6, 'big')).decode()


def ref(element):
    return {'$ref': element['_id']}


def element(type_, parent, name=None, **fields):
    e = {'_type': type_, '_id': new_id()}
    if parent is not None:
        e['_parent'] = ref(parent)
    if name is not None:
        e['name'] = name
    e.update(fields)
    return e


def own(parent, child, field='ownedElements'):
    parent.setdefault(field, []).append(child)
    return child


# ============================================================================ java extraction

def strip_code(s):
    s = re.sub(r'/\*.*?\*/', lambda m: ' ' * len(m.group(0)), s, flags=re.S)
    s = re.sub(r'//[^\n]*', '', s)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', s)


def javadoc_before(raw, pos):
    """First paragraph of the Javadoc right before `pos`, as plain text."""
    before = raw[:pos].rstrip()
    before = re.sub(r'(@\w+(\([^)]*\))?\s*)+$', '', before).rstrip()
    m = re.search(r'/\*\*(.*?)\*/\s*$', before, re.S)
    if not m:
        return ''
    text = re.sub(r'^\s*\*\s?', '', m.group(1), flags=re.M).strip()
    text = text.split('\n\n')[0]
    text = re.sub(r'</?(p|b|i|em|strong|code|ul|ol|li|br|pre)\s*/?>', '', text)
    text = re.sub(r'\{@(?:link|code)\s+([^}]*)\}', r'\1', text)
    return ' '.join(text.split())


def split_top(s, sep=','):
    """Splits on `sep` outside <>, () and []."""
    out, depth, buf = [], 0, ''
    for ch in s:
        if ch in '<([':
            depth += 1
        elif ch in '>)]':
            depth -= 1
        if ch == sep and depth == 0:
            out.append(buf)
            buf = ''
        else:
            buf += ch
    if buf.strip():
        out.append(buf)
    return [x.strip() for x in out]


def members_of(body):
    """Yields the depth-1 declarations of a type body (method bodies skipped)."""
    depth, seg, pending_field = 0, '', False
    i = 0
    while i < len(body):
        ch = body[i]
        if ch == '{':
            if depth == 0:
                if '=' in seg.split('(')[0] or pending_field or re.search(r'=\s*$', seg):
                    pending_field = True          # field initializer block / anonymous class
                else:
                    yield ('block', seg)
                    seg = ''
            depth += 1
        elif ch == '}':
            depth -= 1
        elif depth == 0:
            if ch == ';':
                yield ('stmt', seg)
                seg = ''
                pending_field = False
            else:
                seg += ch
        i += 1


MODIFIERS = {'public', 'protected', 'private', 'static', 'final', 'abstract', 'default',
             'synchronized', 'sealed', 'non-sealed', 'native', 'transient', 'volatile', 'strictfp'}


def parse_decl(seg, kind, cls_name):
    seg = re.sub(r'@\w+(\.\w+)*(\([^)]*\))?', ' ', seg)
    seg = ' '.join(seg.split())
    if not seg or re.match(r'(.*\s)?(class|interface|enum|record)\s+\w+', seg.split('(')[0]):
        return None
    words = seg.split(' ')
    mods = set()
    while words and words[0] in MODIFIERS:
        mods.add(words.pop(0))
    rest = ' '.join(words)
    if kind == 'interface':
        if not mods & {'private'}:
            mods.add('public')
        if '(' not in rest:
            mods |= {'static', 'final'}
        elif 'default' not in mods and 'static' not in mods:
            mods.add('abstract')
    vis = 'public' if 'public' in mods else 'protected' if 'protected' in mods else \
        'private' if 'private' in mods else 'package'
    m = re.match(r'(?:<[^()]*?>\s+)?(.*?)\b(\w+)\s*\((.*)\)\s*(throws [\w., ]+)?\s*$', rest)
    if m and '=' not in rest.split('(')[0]:
        rtype, name, params = m.group(1).strip(), m.group(2), m.group(3)
        if not rtype and name != cls_name:
            return None
        ps = []
        for p in split_top(params):
            p = re.sub(r'\bfinal\s+', '', p).strip()
            if not p:
                continue
            ptype, pname = p.rsplit(' ', 1)
            ps.append((pname, ptype.strip()))
        return {'kind': 'ctor' if not rtype else 'op', 'name': name, 'type': rtype, 'params': ps,
                'visibility': vis, 'static': 'static' in mods, 'abstract': 'abstract' in mods}
    m = re.match(r'(.+?)\s+(\w+)\s*(=.*)?$', rest)
    if m:
        return {'kind': 'field', 'name': m.group(2), 'type': m.group(1).strip(), 'visibility': vis,
                'static': 'static' in mods, 'final': 'final' in mods,
                'default': (m.group(3) or '').lstrip('= ').strip()}
    return None


def walk_sources():
    """(source root, directory, files) for every directory of every root in SRCS."""
    for root in SRCS:
        for d, _, files in os.walk(root):
            yield root, d, files


def read_classes():
    classes = {}
    for SRC, d, files in walk_sources():
        for f in sorted(files):
            if not f.endswith('.java') or f == 'module-info.java':
                continue
            name = f[:-5]
            raw = open(os.path.join(d, f)).read()
            src = strip_code(raw)
            pkg = os.path.relpath(d, SRC).replace(os.sep, '.')
            m = re.search(r'^(?:public\s+)?((?:\w+\s+)*?)(class|interface|record|enum)\s+' + name + r'\b',
                          src, re.M)
            kind, mods = m.group(2), m.group(1)
            brace = src.index('{', m.end())
            header = src[m.start():brace]
            # body = text between the type's braces
            depth, end = 0, brace
            for i in range(brace, len(src)):
                if src[i] == '{':
                    depth += 1
                elif src[i] == '}':
                    depth -= 1
                    if depth == 0:
                        end = i
                        break
            body = src[brace + 1:end]
            ext = re.search(r'\bextends\s+([\w.<>, ]+?)(?=\s+implements|\s+permits|\s*$)', header)
            impl = re.search(r'\bimplements\s+([\w.<>, ]+?)(?=\s+permits|\s*$)', header)
            comps = []
            if kind == 'record':
                rc = re.search(name + r'\s*\((.*?)\)\s*(implements|\{|$)', header, re.S)
                for c in split_top(rc.group(1)) if rc and rc.group(1).strip() else []:
                    ctype, cname = c.rsplit(' ', 1)
                    comps.append({'kind': 'field', 'name': cname, 'type': ctype.strip(), 'visibility': 'private',
                                  'static': False, 'final': True, 'default': ''})
            members = []
            if kind == 'enum':
                consts = body.split(';')[0]
                members += [{'kind': 'literal', 'name': c.split('(')[0].strip()}
                            for c in split_top(consts) if re.match(r'^\w+', c.strip())]
                body = body[len(consts) + 1:]
            for what, seg in members_of(body):
                decl = parse_decl(seg, kind, name)
                if decl:
                    members.append(decl)
            classes[name] = {
                'name': name, 'pkg': pkg, 'kind': kind, 'abstract': 'abstract' in mods,
                'final': 'final' in mods,
                'extends': [re.sub(r'<.*', '', x) for x in split_top(ext.group(1))] if ext else [],
                'implements': [re.sub(r'<.*', '', x) for x in split_top(impl.group(1))] if impl else [],
                'members': comps + members, 'doc': javadoc_before(raw, m.start()), 'src': src,
            }
    names = set(classes)
    for c in classes.values():
        c['deps'] = sorted(n for n in names if n != c['name'] and re.search(r'\b' + n + r'\b', c['src']))
    return classes


# ============================================================================ views

def clone_view(type_, parent_view_or_diagram, model=None, **fields):
    """A view of `type_` - a fresh copy of StarUML's own template, with new ids."""
    tpl = copy.deepcopy(TEMPLATES[type_])
    ids = {}

    def collect(o):
        if isinstance(o, dict):
            if '_id' in o:
                ids[o['_id']] = new_id()
            for v in o.values():
                collect(v)
        elif isinstance(o, list):
            for v in o:
                collect(v)

    def remap(o):
        if isinstance(o, dict):
            if '_id' in o:
                o['_id'] = ids[o['_id']]
            if '$ref' in o and o['$ref'] in ids:
                o['$ref'] = ids[o['$ref']]
            for v in o.values():
                remap(v)
        elif isinstance(o, list):
            for v in o:
                remap(v)

    collect(tpl)
    remap(tpl)
    tpl['_parent'] = ref(parent_view_or_diagram)
    # Every sub view shows the same model element; edge labels belong to their edge
    def set_parents(v):
        for s in v.get('subViews', []):
            s['_parent'] = ref(v)
            if model is not None and 'model' not in s and s['_type'] != 'LabelView':
                s['model'] = ref(model)
            set_parents(s)
    set_parents(tpl)
    if model is not None:
        tpl['model'] = ref(model)
    tpl.update(fields)
    return tpl


def sub_view(view, type_):
    return next(s for s in view['subViews'] if s['_type'] == type_)


def ref_target(view, field):
    rid = view[field]['$ref']

    def find(v):
        if v.get('_id') == rid:
            return v
        for s in v.get('subViews', []):
            r = find(s)
            if r:
                return r
    return find(view)


# ============================================================================ layout

CHAR_W = 7.2      # Arial 13, average - a little generous on purpose
BOLD_W = 8.0
LINE_H = 15


def text_w(s, w=CHAR_W):
    return len(s) * w


def graphviz(nodes, edges, rankdir='TB', nodesep=0.5, ranksep=0.7, extra='', labels=None):
    """nodes: {key: (w, h)} in px; edges: [(a, b)], with optional `labels` (one text
    per edge, to leave room for it). Returns centers {key: (x, y)} and edge
    polylines [[(x, y), ...]] in px, y downwards."""
    lines = [f'digraph G {{ rankdir={rankdir}; nodesep={nodesep}; ranksep={ranksep}; '
             f'splines=polyline; {extra}',
             '  node [shape=box fixedsize=true label=""];']
    for k, (w, h) in nodes.items():
        lines.append(f'  "{k}" [width={w / 72:.3f} height={h / 72:.3f}];')
    for i, (a, b) in enumerate(edges):
        label = (labels or [''] * len(edges))[i].replace('"', "'")
        lab = f' label="{label}" fontname="Arial" fontsize=13' if label else ''
        lines.append(f'  "{a}" -> "{b}" [id="e{i}"{lab}];')
    lines.append('}')
    out = subprocess.run(['dot', '-Tjson'], input='\n'.join(lines), capture_output=True, text=True,
                         check=True).stdout
    g = json.loads(out)
    H = float(g['bb'].split(',')[3])
    centers = {}
    for o in g.get('objects', []):
        if 'pos' in o:
            x, y = map(float, o['pos'].split(','))
            centers[o['name']] = (x, H - y)
    routes = {}
    for e in g.get('edges', []):
        pts = []
        for p in e['pos'].split(' '):
            if p.startswith(('e,', 's,')):
                continue
            x, y = map(float, p.split(','))
            pts.append((x, H - y))
        # a polyline spline repeats each corner - keep the corners only
        corners = pts[::3] if len(pts) > 4 else [pts[0], pts[-1]]
        routes[int(e['id'][1:])] = corners
    return centers, routes


def points_str(pts):
    return ';'.join(f'{round(x)}:{round(y)}' for x, y in pts)


# ============================================================================ main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--sizes', help='exact view sizes measured by measure.js on the current file')
    ap.add_argument('--out', default=OUT)
    ap.add_argument('--base', help='the existing model to merge into (default: --out)')
    ap.add_argument('--fresh', action='store_true',
                    help='ignore the existing file - discards every change made in StarUML')
    args = ap.parse_args()

    import merge
    import model_builder

    base = args.base or args.out
    old = None
    if os.path.exists(base) and not args.fresh:
        old = json.load(open(base))
    base_manifest = os.path.splitext(base)[0] + '.generated.json'
    manifest = json.load(open(base_manifest)) if os.path.exists(base_manifest) and old else None
    manifest_file = os.path.splitext(args.out)[0] + '.generated.json'

    def generate(sizes):
        _counter[0] = 0
        return model_builder.build(read_classes(), sizes)

    project = generate({})
    if args.sizes:
        # Sizes are measured on the merged file: look them up by the id each view will get
        measured = json.load(open(args.sizes))
        ids = merge.final_ids(project, old)
        project = generate({g: measured[f] for g, f in ids.items() if f in measured})

    project, new_manifest, report = merge.merge(project, old, manifest)
    merge.update_overview(project, report)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, 'w') as f:
        json.dump(project, f, indent='\t', ensure_ascii=False)
    with open(manifest_file, 'w') as f:
        json.dump(new_manifest, f, indent=1, ensure_ascii=False)
    for line in report:
        print('  ' + line)
    print('written', args.out)


if __name__ == '__main__':
    main()
