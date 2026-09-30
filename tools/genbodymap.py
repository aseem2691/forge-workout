#!/usr/bin/env python3
"""Build assets/bodymap.json — the front/back muscle map drawn on the player and how-to sheet.

The polygons come from react-body-highlighter (MIT, © 2020 GV79), converted from its TypeScript
source so the app draws them natively on a Canvas:

    npm pack react-body-highlighter@2.0.5 && tar -xzf react-body-highlighter-2.0.5.tgz
    python3 tools/genbodymap.py package/src/assets/index.ts app/src/main/assets/bodymap.json

Output: {"width": 100, "height": 220, "front": [{"m": region, "p": [[x, y, ...], ...]}], "back": [...]}.
Soleus polygons are folded into "calves", which is the only name the plan uses for them.
"""
import json, re, sys

src = open(sys.argv[1]).read()
names = {'TRAPEZIUS': 'trapezius', 'UPPER_BACK': 'upper-back', 'LOWER_BACK': 'lower-back',
         'CHEST': 'chest', 'BICEPS': 'biceps', 'TRICEPS': 'triceps', 'FOREARM': 'forearm',
         'BACK_DELTOIDS': 'back-deltoids', 'FRONT_DELTOIDS': 'front-deltoids', 'ABS': 'abs',
         'OBLIQUES': 'obliques', 'ABDUCTOR': 'adductor', 'ABDUCTORS': 'abductors',
         'HAMSTRING': 'hamstring', 'QUADRICEPS': 'quadriceps', 'CALVES': 'calves',
         'GLUTEAL': 'gluteal', 'HEAD': 'head', 'NECK': 'neck', 'KNEES': 'knees',
         'LEFT_SOLEUS': 'calves', 'RIGHT_SOLEUS': 'calves'}


def parse(block):
    out = []
    for muscle, body in re.findall(r"muscle:\s*MuscleType\.(\w+),\s*svgPoints:\s*\[(.*?)\]", block, re.S):
        polys = [[round(float(v), 2) for v in pts.split()] for pts in re.findall(r"'([^']*)'", body)]
        out.append(dict(m=names[muscle], p=polys))
    return out


front = src[src.index('anteriorData'):src.index('posteriorData')]
back = src[src.index('posteriorData'):]
data = dict(front=parse(front), back=parse(back))
# The source draws both figures in one 100-wide space; the back one runs taller (to y≈220).
# Both are scaled by the same factor so their proportions stay as drawn.
ys = [v for side in ('front', 'back') for part in data[side] for poly in part['p'] for v in poly[1::2]]
data = dict(width=100, height=round(max(ys)), **data)
print(f"front={len(data['front'])} back={len(data['back'])} regions, {data['width']}×{data['height']}")
with open(sys.argv[2], 'w') as f:
    json.dump(data, f, separators=(',', ':'))
