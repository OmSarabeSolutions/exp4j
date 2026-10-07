"""Merges a freshly generated model into the existing .mdj, keeping what was
done by hand in StarUML.

Every element and view gets a semantic key - its type and name (or, for an
unnamed one, what it links), under its parent's key - computed the same way
in both models. Then:

- a generated element whose key is in the existing file takes back its id,
  so every reference made by hand to it stays valid; a new one gets an id
  derived from its key, stable from one generation to the next;
- in a diagram that already exists, the existing views are kept as they are
  - position, size, route, colors, fonts, hidden compartments -; only views
  of things new to the model are added. A generated view the user deleted is
  not put back: the manifest (exp4j.generated.json) lists what the
  previous generation produced;
- the diagram's own display settings are kept;
- anything the generator doesn't produce - diagrams such as 00-Overview,
  hyperlinks, notes, text boxes - is carried over under its parent;
- what no longer resolves (a view or hyperlink of a class removed from the
  code) is dropped, and reported.
"""
import base64
import copy
import hashlib

DEFAULTS = {'UMLPseudostate': {'kind': 'initial'}, 'UMLMessage': {'messageSort': 'synchCall'},
            'UMLCombinedFragment': {'interactionOperator': 'seq'}, 'UMLParameter': {'direction': 'in'}}
RELATIONS = ('UMLGeneralization', 'UMLInterfaceRealization', 'UMLDependency', 'UMLRealization', 'UMLAbstraction')
DIAGRAM_KEEP_OUT = {'_type', '_id', '_parent', 'name', 'documentation', 'ownedViews', 'ownedElements'}


# ============================================================================ indexing

def index(root):
    """{id: object} and {id: (parent object, field)}, in containment order."""
    objs, parent = {}, {}

    def walk(o, par, field):
        if isinstance(o, dict):
            if '$ref' in o:
                return
            if '_id' in o:
                objs[o['_id']] = o
                parent[o['_id']] = (par, field)
                for k, v in o.items():
                    if k != '_parent':
                        walk(v, o, k)
            else:
                for v in o.values():
                    walk(v, par, field)
        elif isinstance(o, list):
            for v in o:
                walk(v, par, field)

    walk(root, None, None)
    return objs, parent


def is_view(field):
    return field in ('ownedViews', 'subViews')


def get(o, field, default=''):
    return o.get(field, DEFAULTS.get(o['_type'], {}).get(field, default))


def keys_of(root):
    """{id: semantic key}, the same for the same thing in any version of the file."""
    objs, parent = index(root)
    raw = {}

    def rk(r):
        return raw_key(r['$ref']) if isinstance(r, dict) and r.get('$ref') in objs else '?'

    def type_name(t):
        if isinstance(t, dict):
            return objs.get(t.get('$ref'), {}).get('name', '?')
        return str(t or '')

    def element_part(o):
        t, name = o['_type'], o.get('name', '')
        if t == 'UMLOperation':
            return name + '(' + ','.join(type_name(p.get('type')) for p in o.get('parameters', [])
                                         if get(p, 'direction') != 'return') + ')'
        if t in RELATIONS:
            return rk(o.get('source')) + '->' + rk(o.get('target'))
        if t == 'UMLAssociation':
            e1, e2 = o.get('end1', {}), o.get('end2', {})
            return rk(e1.get('reference')) + '->' + rk(e2.get('reference')) + ':' + e2.get('name', '')
        if t == 'UMLAttribute':
            return name + ':' + type_name(o.get('type'))
        if t == 'UMLParameter':
            return name + ':' + get(o, 'direction')
        if t == 'UMLTransition':
            return (rk(o.get('source')) + '->' + rk(o.get('target')) + '|' +
                    ','.join(x.get('name', '') for x in o.get('triggers', [])) + '|' + o.get('guard', ''))
        if t == 'UMLMessage':
            return rk(o.get('source')) + '->' + rk(o.get('target')) + '|' + name + '|' + get(o, 'messageSort')
        if t == 'UMLLifeline':
            return name + '|' + rk(o.get('represent'))
        if t == 'UMLPseudostate':
            return get(o, 'kind')
        if t == 'UMLCombinedFragment':
            return get(o, 'interactionOperator')
        if t == 'UMLInteractionOperand':
            return o.get('guard', '')
        if t == 'Hyperlink':
            return rk(o.get('reference')) or o.get('url', '')
        return name

    def view_part(o):
        if isinstance(o.get('model'), dict):
            return rk(o['model'])
        if 'head' in o or 'tail' in o:
            return rk(o.get('tail')) + '->' + rk(o.get('head'))
        return o.get('text', '')

    def raw_key(oid):
        if oid in raw:
            return raw[oid]
        raw[oid] = '…'                     # guards against reference cycles
        o = objs[oid]
        par, field = parent[oid]
        if par is None:
            k = 'root'
        elif field == 'subViews':
            k = f"{raw_key(par['_id'])}/sub{par['subViews'].index(o)}:{o['_type']}"
        else:
            part = view_part(o) if is_view(field) else element_part(o)
            k = f"{raw_key(par['_id'])}/{field}:{o['_type']}:{part}"
        raw[oid] = k
        return k

    for oid in objs:
        raw_key(oid)
    # Same key twice under one parent: number them, in containment order
    seen, keys = {}, {}
    for oid in objs:
        k = raw[oid]
        n = seen.get(k, 0)
        seen[k] = n + 1
        keys[oid] = k if n == 0 else f'{k}#{n}'
    return keys


# ============================================================================ helpers

def new_ids(keys, taken):
    out = {}
    for oid, k in keys.items():
        h = hashlib.sha1(k.encode()).digest()
        i = 0
        while True:
            cand = base64.b64encode(h[:14] + bytes([i])).decode()
            if cand not in taken:
                break
            i += 1
        taken.add(cand)
        out[oid] = cand
    return out


def remap(o, idmap):
    if isinstance(o, dict):
        if '_id' in o and o['_id'] in idmap:
            o['_id'] = idmap[o['_id']]
        if '$ref' in o and o['$ref'] in idmap:
            o['$ref'] = idmap[o['$ref']]
        for v in o.values():
            remap(v, idmap)
    elif isinstance(o, list):
        for v in o:
            remap(v, idmap)


def all_ids(o, out=None):
    out = set() if out is None else out
    if isinstance(o, dict):
        if '_id' in o:
            out.add(o['_id'])
        for k, v in o.items():
            if k != '_parent' and not (isinstance(v, dict) and '$ref' in v):
                all_ids(v, out)
    elif isinstance(o, list):
        for v in o:
            all_ids(v, out)
    return out


def node_bounds(views):
    nodes = [v for v in views if 'left' in v and 'points' not in v]
    if not nodes:
        return None
    return (min(v['left'] for v in nodes), min(v['top'] for v in nodes),
            max(v['left'] + v.get('width', 0) for v in nodes), max(v['top'] + v.get('height', 0) for v in nodes))


def shift(view, dx, dy):
    if 'left' in view:
        view['left'] += dx
        view['top'] += dy
    if 'points' in view:
        view['points'] = ';'.join(f'{float(x) + dx:g}:{float(y) + dy:g}'
                                  for x, y in (p.split(':') for p in view['points'].split(';')))
    for s in view.get('subViews', []):
        shift(s, dx, dy)


# ============================================================================ merge

def merge(gen, old, manifest):
    """Returns (merged project, new manifest, report lines)."""
    report = []
    gkeys = keys_of(gen)
    new_manifest = {}
    gobjs, gpar = index(gen)
    for oid, (par, field) in gpar.items():
        if field == 'ownedViews':
            new_manifest.setdefault(gkeys[par['_id']], []).append(gkeys[oid])
    if old is None:
        return gen, new_manifest, ['nouveau fichier']

    okeys = keys_of(old)
    oobjs, opar = index(old)
    old_by_key = {k: i for i, k in okeys.items()}

    # 1. Ids: the existing one when the thing already exists, else one derived from its key
    taken = set(oobjs)
    fresh = new_ids({i: k for i, k in gkeys.items() if k not in old_by_key}, taken)
    idmap = {i: old_by_key.get(k) or fresh[i] for i, k in gkeys.items()}
    remap(gen, idmap)
    fobjs, fpar = index(gen)
    fkey = {idmap[i]: k for i, k in gkeys.items()}
    gen_types = {o['_type'] for i, o in fobjs.items() if not is_view(fpar[i][1])}

    # 2. Diagrams that exist: keep their views, add those of new things
    added_views = {}
    for did, d in list(fobjs.items()):
        if not d['_type'].endswith('Diagram') or did not in oobjs:
            continue
        od = oobjs[did]
        for k, v in od.items():                          # the diagram's display settings
            if k not in DIAGRAM_KEEP_OUT:
                d[k] = v
        previous = set(manifest.get(fkey[did], [])) if manifest is not None else None
        old_view_ids = {v['_id'] for v in od.get('ownedViews', [])}
        views = [copy.deepcopy(v) for v in od.get('ownedViews', [])]
        for v in d.get('ownedViews', []):
            if v['_id'] in old_view_ids:
                continue
            vkey = fkey[v['_id']]
            model_existed = isinstance(v.get('model'), dict) and v['model']['$ref'] in oobjs
            deleted_by_user = vkey in previous if previous is not None else model_existed
            if deleted_by_user:
                continue
            views.append(v)
            added_views.setdefault(did, []).append(v)
        d['ownedViews'] = views

    # 3. Carry over what the generator doesn't produce
    grafted = set()
    for oid, o in oobjs.items():
        if oid in fobjs or oid in grafted:
            continue
        par, field = opar[oid]
        if par is None or is_view(field) or par['_id'] in grafted:
            continue
        if o['_type'].endswith('Diagram') or o['_type'] not in gen_types:
            target = fobjs.get(par['_id'])
            if target is None:
                report.append(f"conservé à la racine (parent disparu) : {o['_type']} {o.get('name', '')}")
                target, field = gen, 'ownedElements'
            obj = copy.deepcopy(o)
            if isinstance(target.get(field), list) or field not in target:
                target.setdefault(field, []).append(obj)
            else:
                target[field] = obj
            obj['_parent'] = {'$ref': target['_id']}
            grafted |= all_ids(obj)
            report.append(f"conservé : {o['_type']} {o.get('name', '')}".rstrip())
        else:
            report.append(f"retiré (n'existe plus dans le code) : {okeys[oid]}")

    # 4. Drop what no longer resolves, until nothing changes
    while True:
        ids = all_ids(gen)
        removed = drop_dangling(gen, ids, report)
        if not removed:
            break

    # 5. New views in a diagram that existed go below what is already there
    fobjs, _ = index(gen)
    for did, new in added_views.items():
        d = fobjs.get(did)
        if not d:
            continue
        new_ids_ = {v['_id'] for v in new}
        kept = [v for v in d['ownedViews'] if v['_id'] not in new_ids_]
        nb, kb = node_bounds([v for v in new if v in d['ownedViews']]), node_bounds(kept)
        if nb and kb:
            dy = kb[3] + 60 - nb[1]
            for v in new:
                shift(v, 0, dy)
        report.append(f"{len(new)} vue(s) ajoutée(s) dans « {d.get('name')} »")
    return gen, new_manifest, report


def drop_dangling(root, ids, report):
    removed = [0]

    def bad(o):
        for k, v in o.items():
            if k != '_parent' and isinstance(v, dict) and '$ref' in v and v['$ref'] not in ids:
                return k
        return None

    def walk(o, field=None):
        if isinstance(o, dict):
            for k, v in list(o.items()):
                if isinstance(v, list):
                    keep = []
                    for x in v:
                        b = bad(x) if isinstance(x, dict) and '_id' in x else None
                        if b and (is_view(k) or x.get('_type') == 'Hyperlink' or b in ('model', 'head', 'tail')):
                            removed[0] += 1
                            if not is_view(k):
                                report.append(f"retiré (référence disparue) : {x['_type']} {x.get('name', '')}")
                            continue
                        if b:
                            del x[b]
                            removed[0] += 1
                        keep.append(x)
                        walk(x, k)
                    o[k] = keep
                elif isinstance(v, dict) and '_id' in v:
                    b = bad(v)
                    if b:
                        del v[b]
                        removed[0] += 1
                    walk(v, k)

    walk(root)
    return removed[0]


# ============================================================================ overview

def final_ids(gen, old):
    """What merge() will make each generated id - to look up sizes measured on the merged file."""
    gkeys = keys_of(gen)
    if old is None:
        return {i: i for i in gkeys}
    okeys = keys_of(old)
    old_by_key = {k: i for i, k in okeys.items()}
    taken = set(okeys)
    fresh = new_ids({i: k for i, k in gkeys.items() if k not in old_by_key}, taken)
    return {i: old_by_key.get(k) or fresh[i] for i, k in gkeys.items()}


def update_overview(root, report, name_prefix='00'):
    """Keeps the hand-made overview diagram (00-…) in step with the model: a
    hyperlink to every other diagram, each new one under the others of its
    kind - links to diagrams that no longer exist are already gone."""
    objs, parent = index(root)
    overview = next((o for o in objs.values() if o['_type'].endswith('Diagram')
                     and o.get('name', '').startswith(name_prefix)), None)
    if overview is None:
        return
    views = overview.setdefault('ownedViews', [])
    link_views = [v for v in views if v['_type'] == 'HyperlinkView' and v.get('model', {}).get('$ref') in objs]
    linked = {}
    for v in link_views:
        target = objs[v['model']['$ref']].get('reference', {}).get('$ref')
        if target in objs:
            linked[target] = v
    diagrams = [o for o in objs.values() if o['_type'].endswith('Diagram') and o is not overview]
    missing = sorted((d for d in diagrams if d['_id'] not in linked), key=lambda d: d.get('name', ''))
    if not missing or not link_views:
        if missing:
            report.append('00-Overview : aucun hyperlien existant à imiter, diagrammes non ajoutés')
        return
    sample = link_views[0]
    owner = objs[objs[sample['model']['$ref']]['_parent']['$ref']]
    taken = all_ids(root)
    n = sum(1 for o in objs.values() if o['_type'] == 'Hyperlink')
    for d in missing:
        same_kind = [v for t, v in linked.items() if objs[t]['_type'] == d['_type']]
        if same_kind:
            left = same_kind[0]['left']
            tops = sorted(v['top'] for v in same_kind)
            step = min((b - a for a, b in zip(tops, tops[1:]) if b > a), default=44)
            top = tops[-1] + step
        else:
            left = max(v['left'] + v.get('width', 0) for v in link_views) + 60
            top = min(v['top'] for v in link_views)
        n += 1
        link = {'_type': 'Hyperlink', '_id': '', '_parent': {'$ref': owner['_id']}, 'name': f'Hyperlink{n}',
                'reference': {'$ref': d['_id']}}
        link['_id'] = new_ids({'l': 'overview-link:' + d.get('name', '')}, taken)['l']
        owner.setdefault('ownedElements', []).append(link)
        v = copy.deepcopy(sample)
        ids = {}
        for oid in all_ids(v):
            ids[oid] = new_ids({'v': f'overview-view:{d.get("name", "")}:{oid}'}, taken)['v']
        remap(v, ids)
        v['model'] = {'$ref': link['_id']}
        dx, dy = left - v['left'], top - v['top']
        shift(v, dx, dy)
        for s in v.get('subViews', []):
            if s.get('text') and s['text'] != 'link':
                s['text'] = d.get('name', '')
        views.append(v)
        linked[d['_id']] = v
        report.append(f"00-Overview : lien ajouté vers « {d.get('name')} »")
