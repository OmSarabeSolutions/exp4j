#!/usr/bin/env python3
"""Checks that a regenerated .mdj kept what was there before: every view of
the old file still there, unchanged (position, size, route, style), and
every element still there, under the same id.

    python3 docs/tools/staruml/check_preserved.py <before.mdj> <after.mdj>
"""
import json
import sys

from merge import index, is_view


def main(before, after):
    old, new = json.load(open(before)), json.load(open(after))
    oobjs, opar = index(old)
    nobjs, _ = index(new)
    views_changed, views_gone, elements_gone = [], [], []
    for oid, o in oobjs.items():
        field = opar[oid][1]
        if field == 'ownedViews':
            if oid not in nobjs:
                views_gone.append(o)
            elif nobjs[oid] != o:
                views_changed.append(o)
        elif not is_view(field) and oid not in nobjs:
            elements_gone.append(o)
    print(f'vues : {sum(1 for i in oobjs if opar[i][1] == "ownedViews")} avant, '
          f'{len(views_changed)} modifiée(s), {len(views_gone)} disparue(s)')
    print(f'éléments disparus : {len(elements_gone)}')
    for o in (views_changed + views_gone)[:20]:
        print('  vue', o['_type'], o['_id'])
    for o in elements_gone[:20]:
        print('  élément', o['_type'], o.get('name', ''))
    return 0 if not (views_changed or views_gone) else 1


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:3]))
