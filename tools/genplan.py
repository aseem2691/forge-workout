#!/usr/bin/env python3
"""Build Forge's multi-week plan.json from the exercises-dataset.

Usage:
    curl -L -o exercises.json \
      https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json
    python3 tools/genplan.py app/src/main/assets/plan.json

Writes media_list.txt next to this script; fetch each name from the dataset's
videos/ (gif) or images/ (jpg) directory into app/src/main/assets/media/.
"""
import json, os, sys, re

SRC = json.load(open(os.path.join(os.path.dirname(__file__), 'exercises.json')))
BY_ID = {e['id']: e for e in SRC}

# slot: (id, sets, reps_or_time, weight_kg, rest_s)
# reps for type=reps; seconds for type=time (HIIT is always 40/20)


def S(i, sets, reps, w, rest):
    return dict(id=i, sets=sets, reps=reps, weight=w, rest=rest, type='reps')


def H(i, sets=2):
    return dict(id=i, sets=sets, time=40, rest=20, weight=0, type='time', block='hiit')


WEEKS = [
 dict(label="A", focus="Dumbbell strength — heaviest loads of the block.", days=[
   dict(num=1, day="Mon", title="Upper Body\nStrength", mins=48,
        coach="Push-dominant upper day. Drive hard on the presses, then a 40/20 finisher to spike the burn.",
        strength=[S('0426',4,8,14,90), S('0293',4,10,18,90), S('0334',3,14,6,60),
                  S('0662',3,15,0,60), S('0430',3,12,10,60), S('0416',3,12,10,60)],
        hiit=[H('0699'), H('0630'), H('1160'), H('0464')]),
   dict(num=2, day="Tue", title="Lower Body\n& Glutes", mins=46,
        coach="Heavy legs on the mat and floor. Walkpad incline intervals close it out.",
        strength=[S('1760',4,12,20,90), S('1459',4,10,22,90), S('0381',3,10,14,75),
                  S('3013',3,15,0,60), S('0417',3,20,16,45)],
        hiit=[H('0685'), H('0514'), H('3361'), H('0459')]),
   dict(num=3, day="Thu", title="Push · Pull\nUpper", mins=45,
        coach="Pull-dominant balance day. Rows and pullovers build the back your posture wants.",
        strength=[S('0292',4,10,20,75), S('0375',3,12,14,75), S('2137',3,10,12,75),
                  S('0259',3,12,0,60), S('2292',3,14,6,60), S('0313',3,12,12,60)],
        hiit=[H('0660'), H('3655'), H('0295'), H('0274')]),
   dict(num=4, day="Sat", title="Full Body\nHIIT Burner", mins=42,
        coach="Fat-loss day. Big compound moves at speed, walkpad bookends. Keep the weights light and honest.",
        strength=[S('0413',3,12,14,60), S('0300',3,12,20,60), S('0437',3,12,10,60)],
        hiit=[H('1160',3), H('0630',3), H('0501',3), H('0685',3)]),
 ]),

 dict(label="B", focus="Body weight & tempo — no loading, slow reps, high control.", days=[
   dict(num=1, day="Mon", title="Body Weight\nUpper", mins=44,
        coach="Zero dumbbells. Slow tempo push-ups and towel rows — control every rep instead of adding load.",
        strength=[S('0662',4,12,0,75), S('0283',3,10,0,75), S('3166',4,12,0,75),
                  S('1311',3,12,0,60), S('0672',3,12,0,60), S('3162',3,10,0,60)],
        hiit=[H('0699'), H('0630'), H('3224'), H('0464')]),
   dict(num=2, day="Tue", title="Body Weight\nLower", mins=42,
        coach="Single-leg work exposes the weak side. Full range, pause at the bottom, no bouncing.",
        strength=[S('2368',4,12,0,75), S('3470',3,12,0,75), S('3769',3,12,0,60),
                  S('3013',3,20,0,60), S('3561',3,16,0,45), S('1373',3,25,0,45)],
        hiit=[H('0514'), H('3361'), H('3582'), H('0459')]),
   dict(num=3, day="Thu", title="Body Weight\nPush · Pull", mins=43,
        coach="Rows and pike push-ups. Light dumbbells only where body weight cannot load the delts.",
        strength=[S('3158',4,12,0,75), S('0259',3,12,0,60), S('3662',3,10,0,75),
                  S('0334',3,15,6,60), S('0378',3,15,6,60), S('0664',3,10,0,60)],
        hiit=[H('0660'), H('3655'), H('0687'), H('0274')]),
   dict(num=4, day="Sat", title="Body Weight\nFull Body", mins=40,
        coach="Pure conditioning. Nothing but your own weight and the mat — move fast, breathe hard.",
        strength=[S('2368',3,15,0,60), S('0662',3,15,0,60), S('3013',3,20,0,60)],
        hiit=[H('1160',3), H('0501',3), H('0630',3), H('3220',3)]),
 ]),

 dict(label="C", focus="Dumbbell volume — lighter loads, more reps, new movement patterns.", days=[
   dict(num=1, day="Mon", title="Upper Body\nVolume", mins=47,
        coach="Different angles from week A. Lighter bells, longer sets, short rests — chase the pump.",
        strength=[S('0286',4,12,10,75), S('0296',3,12,12,75), S('0310',3,14,6,60),
                  S('0378',3,15,6,60), S('0406',3,15,20,60), S('0285',3,14,8,60)],
        hiit=[H('0699'), H('0630'), H('1160'), H('0687')]),
   dict(num=2, day="Tue", title="Lower Body\nUnilateral", mins=46,
        coach="One leg at a time. Lower weight than week A on purpose — balance is the point.",
        strength=[S('0336',4,12,14,90), S('0432',4,12,18,90), S('1757',3,10,12,75),
                  S('0431',3,12,12,75), S('0409',3,20,12,45)],
        hiit=[H('0685'), H('3582'), H('3361'), H('0459')]),
   dict(num=3, day="Thu", title="Push · Pull\nVolume", mins=45,
        coach="Straight-arm pullovers and cuban presses. Shoulders get healthier, not just bigger.",
        strength=[S('3162',4,12,0,75), S('0433',3,14,12,75), S('0299',3,12,8,75),
                  S('0283',3,12,0,60), S('0359',3,14,6,60), S('0313',3,14,10,60)],
        hiit=[H('0660'), H('3655'), H('0295'), H('0274')]),
   dict(num=4, day="Sat", title="Full Body\nComplex", mins=44,
        coach="Complexes: squat, clean, press without putting the bells down. Light weight, no ego.",
        strength=[S('0371',3,12,10,75), S('0295',3,10,12,75), S('1201',3,10,8,75)],
        hiit=[H('1160',3), H('0630',3), H('0514',3), H('0685',3)]),
 ]),

 dict(label="D", focus="Hybrid conditioning — deload the loads, raise the heart rate.", days=[
   dict(num=1, day="Mon", title="Upper Body\nHybrid", mins=44,
        coach="Deload week for the joints. Moderate bells, longer finishers, keep the quality high.",
        strength=[S('0426',3,10,10,75), S('0292',3,12,14,75), S('0334',3,15,4,60),
                  S('0662',3,15,0,60), S('0296',3,12,10,60)],
        hiit=[H('0699',3), H('0630',3), H('3224',3), H('0464',3)]),
   dict(num=2, day="Tue", title="Lower Body\nHybrid", mins=43,
        coach="Lighter legs, faster pace. The finisher is the session — save something for it.",
        strength=[S('1760',3,15,14,75), S('1459',3,12,16,75), S('3470',3,12,0,60),
                  S('3561',3,16,0,45), S('1373',3,25,0,45)],
        hiit=[H('0514',3), H('3361',3), H('3582',3), H('0459',3)]),
   dict(num=3, day="Thu", title="Push · Pull\nHybrid", mins=43,
        coach="Rows and presses at a conversational weight. Finish every set with reps left over.",
        strength=[S('3166',3,15,0,60), S('0375',3,14,10,75), S('2137',3,12,8,75),
                  S('0259',3,15,0,60), S('2292',3,15,4,60)],
        hiit=[H('0660',3), H('3655',3), H('0687',3), H('0274',3)]),
   dict(num=4, day="Sat", title="Full Body\nBurner", mins=42,
        coach="Last session of the block. Empty the tank, then start week A heavier than you finished.",
        strength=[S('0413',3,15,10,60), S('0300',3,15,16,60), S('0437',3,15,8,60)],
        hiit=[H('1160',3), H('0501',3), H('0630',3), H('0685',3)]),
 ]),
]


def media_name(url):
    return url.rsplit('/', 1)[-1]


def build(slot):
    e = BY_ID.get(slot['id'])
    if e is None:
        raise SystemExit(f"MISSING id {slot['id']}")
    steps = e.get('instruction_steps') or []
    if isinstance(steps, dict):
        steps = steps.get('en') or []
    if not steps:
        text = (e.get('instructions') or {}).get('en', '')
        steps = [s.strip() + '.' for s in text.split('. ') if s.strip()]
    gif, img = e.get('gif_url'), e.get('image')
    if not gif or not img:
        raise SystemExit(f"NO MEDIA for {slot['id']} {e['name']}")
    out = dict(
        id=e['id'], name=e['name'], target=e.get('target', ''),
        secondary=[m for m in (e.get('secondary_muscles') or []) if m][:5],
        equipment=e.get('equipment', ''), category=e.get('category', ''),
        steps=steps,
        gif='assets/' + media_name(gif), thumb='assets/' + media_name(img),
        sets=slot['sets'], weight=slot['weight'], rest=slot['rest'], type=slot['type'],
    )
    if slot['type'] == 'time':
        out['time'] = slot['time']
        out['block'] = 'hiit'
    else:
        out['reps'] = slot['reps']
        out['last'] = None
    return out


weeks_out, media = [], set()
for w in WEEKS:
    days = []
    for d in w['days']:
        day = dict(num=d['num'], day=d['day'], title=d['title'], mins=d['mins'], coach=d['coach'],
                   strength=[build(s) for s in d['strength']],
                   hiit=[build(s) for s in d['hiit']])
        for e in day['strength'] + day['hiit']:
            media.add(e['gif'].split('/')[-1]); media.add(e['thumb'].split('/')[-1])
        days.append(day)
    weeks_out.append(dict(label=w['label'], focus=w['focus'], days=days))

# Carry week A's seeded "last time" values so the very first session still reads true.
seed = {'0426': '12 kg × 8', '0293': '16 kg × 10', '1760': '18 kg × 12',
        '1459': '20 kg × 10', '0292': '18 kg × 10'}
for e in weeks_out[0]['days'][0]['strength'] + weeks_out[0]['days'][1]['strength'] + weeks_out[0]['days'][2]['strength']:
    if e['id'] in seed:
        e['last'] = seed[e['id']]

out_path = sys.argv[1]
with open(out_path, 'w') as f:
    json.dump(dict(weeks=weeks_out), f, indent=1, ensure_ascii=False)

uniq_ex = {e['id'] for w in weeks_out for d in w['days'] for e in d['strength'] + d['hiit']}
print(f"weeks={len(weeks_out)} unique exercises={len(uniq_ex)} media files={len(media)}")
print(f"wrote {out_path} ({os.path.getsize(out_path)//1024} KB)")
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'media_list.txt'), 'w') as f:
    f.write('\n'.join(sorted(media)))
