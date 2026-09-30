#!/usr/bin/env python3
"""Build Forge's multi-week plan.json from the exercises-dataset.

Usage:
    curl -L -o tools/exercises.json \
      https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json
    python3 tools/genplan.py app/src/main/assets/plan.json

Writes media_list.txt next to this script: the dataset's 180×180 GIFs (videos/) and thumbnails
(images/) the plan uses. Thumbnails go into app/src/main/assets/media/ as they are; the GIFs are
upscaled into the .webp demos the plan points at by tools/upscale_media.py.
"""
import json, os, sys, re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = json.load(open(os.path.join(HERE, 'exercises.json')))
BY_ID = {e['id']: e for e in SRC}

# No strength rest runs longer than a minute. The designed values are kept below for the record
# (and for the session-length adjustment); the plan ships the capped ones.
REST_CAP = 60

# slot: (id, sets, reps_or_time, weight_kg, rest_s)
# reps for type=reps; seconds for type=time (HIIT is always 40/20)


def S(i, sets, reps, w, rest):
    return dict(id=i, sets=sets, reps=reps, weight=w, rest=min(rest, REST_CAP), design_rest=rest,
                type='reps')


def H(i, sets=2):
    return dict(id=i, sets=sets, time=40, rest=20, weight=0, type='time', block='hiit')


# ── warm-up ──────────────────────────────────────────────────────────────────────────────────
# Timed moves run hands-free before the strength block: a walkpad ramp, then mobility for the
# joints the day loads. ~7 minutes of work plus short switch-overs. Every move is an
# exercises-dataset entry, so each has the same animated 3D demo as the training moves.

WARMUP_SWITCH = 10  # seconds to get into the next move
WARMUP_HANDOFF = 20  # after the last move: set up the bells for the first set


def W(src, secs, cue, name=None, equipment=None, steps=None):
    """steps replaces the dataset's instructions where they don't describe the move as done here."""
    return dict(src=src, time=secs, cue=cue, name=name, equipment=equipment, steps=steps)


WALK = W('0684', 120,
         "Brisk walk on the walkpad. Build to a pace you could still hold a conversation at.",
         name='walkpad brisk walk', equipment='walkpad',
         steps=["Start the walkpad at an easy pace and stand tall, arms swinging naturally.",
                "Every 30 seconds nudge the speed up a notch.",
                "Finish at a brisk pace: breathing harder, but still able to talk."])
REACH = W('1687', 40, "Step back into a short lunge as both arms reach overhead. Alternate legs.",
          name='step back and reach')
SCAP = W('3021', 40, "Arms stay straight. Let the chest sink between the shoulder blades, then push "
                     "the floor away.",
         steps=["High plank: hands under the shoulders, body in one straight line.",
                "Keeping the arms locked, let the chest sink so the shoulder blades squeeze together.",
                "Push the floor away until the shoulder blades spread apart and the upper back "
                "rounds slightly.",
                "Small, controlled range — only the shoulder blades move."])
UPDOG = W('1366', 40, "From a plank, lower the hips and lift the chest, then press back to plank.",
          name='plank to upward dog',
          steps=["Start in a high plank, hands under the shoulders.",
                 "Lower the hips toward the floor while pressing the chest forward and up, arms "
                 "straight.",
                 "Roll the shoulders back and lift the gaze.",
                 "Press the hips back up to plank and repeat, slow and smooth."])
INCH = W('1471', 40, "Walk the hands out to a plank and back in. Legs as straight as you can.")
CHEST = W('1167', 40, "Swing the arms wide open, then hug across the chest. Loose and rhythmic.",
          name='dynamic chest stretch')
WGS = W('1604', 60, "Lunge, elbow to instep, then rotate that arm to the ceiling. Switch sides at "
                    "30 s.", name="world's greatest stretch")
WINDMILL = W('3214', 40, "Hinge and rotate: hand to the opposite foot, other arm to the ceiling. "
                         "Alternate sides.",
             name='windmill toe touch',
             steps=["Feet wide, arms reaching overhead.",
                    "Hinge at the hips and rotate to bring one hand down to the opposite foot.",
                    "Stand tall again and repeat to the other side.",
                    "Soft knees, smooth pace — this is mobility, not a stretch to force."])
SQUAT = W('1685', 40, "Sit into the squat, then drive up and reach both arms to the ceiling.",
          name='squat to overhead reach')
BRIDGE = W('3013', 40, "Squeeze the glutes at the top and lower slowly.", name='glute bridge')
LUNGE = W('3470', 40, "Alternate legs. Upright torso, back knee hovering above the floor.",
          name='forward lunge')
JACKS = W('3224', 30, "Light and springy — heart rate up before the first set.",
          name='jumping jacks')

# ── cool-down & rest-day mobility ────────────────────────────────────────────────────────────
# Held stretches from the same animated dataset. One-sided stretches run 60 s with a switch at
# 30 s; two-sided ones 45 s in the cool-down. Mobility flows run every move for 60 s.

RECOVERY_SWITCH = 10  # seconds to get into the next stretch

CHEST_SH = W('1271', 45, "Towel held wide, lift it overhead and ease it back behind you. Slow and smooth.",
             name='towel shoulder opener',
             steps=["Hold a towel with both hands, wider than the shoulders.",
                    "Keeping the arms straight, lift it overhead.",
                    "Ease it back behind the head as far as is comfortable, then return.",
                    "Move slowly and stop before any pinch in the shoulders."])
LAT_KNEEL = W('1346', 45, "Sit the hips back towards the heels and let the chest sink. Breathe slowly.",
              name='kneeling lat stretch')
TRI_OH = W('0643', 60, "Hand down the back, ease the elbow behind the head. Switch arms at 30 s.",
           name='overhead triceps stretch')
REAR_DELT = W('0669', 60, "Pull the arm across the chest, shoulder down. Switch arms at 30 s.",
              name='rear delt stretch')
NECK = W('1403', 60, "Ear towards the shoulder, the other shoulder heavy. Switch sides at 30 s.",
         name='neck side stretch')
HAM = W('1511', 60, "On your back, hold behind the raised leg and ease it towards you. Switch legs at 30 s.",
        name='hamstring stretch')
QUAD = W('1512', 60, "On all fours, draw one heel towards the glute. Switch legs at 30 s.",
         name='quad stretch')
PIRI = W('2567', 60, "On a chair, ankle over the opposite knee, lean forward with a long back. Switch sides at 30 s.",
         name='seated piriformis stretch')
CALF = W('1377', 60, "Back heel down, lean into the wall. Switch legs at 30 s.", name='calf stretch')
BUTTERFLY = W('1494', 45, "Soles together, knees falling open. Sit tall and breathe.",
              name='butterfly stretch')
RUNNERS = W('1585', 60, "Hips back over the heel, toes up, chest long. Switch legs at 30 s.",
            name="runner's stretch")
GLUTE_SEAT = W('1424', 60, "Hug the knee across the body and sit tall. Switch sides at 30 s.",
               name='seated glute stretch')
SPINE = W('1363', 45, "Sit tall with the legs long and reach slowly towards the toes.", name='seated forward fold')
KNEE_CIRCLES = W('0257', 60, "Hands on the knees, slow circles. Change direction at 30 s.",
                 name='knee circles')
FROG = W('2571', 60, "Knees wide, rock the hips back and forward gently.", name='rocking frog')
IRON_CROSS = W('1419', 60, "On the back, sweep one leg across the body. Alternate sides.", name='iron cross')
SIDE_LYING = W('1358', 60, "On one side, reach long through the top arm. Switch sides at 30 s.",
               name='side-lying stretch')
LOW_BACK = W('0690', 60, "Sit tall on a chair, reach one arm overhead and lean away. Switch sides at 30 s.",
             name='seated side reach')
WRISTS = W('1428', 60, "Slow wrist circles. Change direction at 30 s.", name='wrist circles')
NECK_PUSH = W('0716', 60, "Ease the head to the side with the hand. Switch sides at 30 s.",
              name='neck stretch')
BACK_PEC = W('1405', 60, "Hands on a chair back, walk the feet back and let the chest sink between the arms.",
             name='chair chest stretch',
             steps=["Place both hands on a chair back or bench at hip height.",
                    "Walk the feet back until the arms are straight and you hinge at the hips.",
                    "Let the chest sink towards the floor between the arms and hold.",
                    "Breathe slowly; ease out before the shoulders pinch."])
LATERAL = W('0794', 60, "Reach one arm overhead and lean away. Switch sides at 30 s.",
            name='standing side stretch')
UPPER_BACK = W('1365', 60, "Arms forward, round the upper back and spread the shoulder blades.",
               name='upper back stretch')
WIDE_ANGLE = W('1587', 60, "Legs wide, walk the hands forward, then to each side.",
               name='seated wide-angle stretch')

COOLDOWNS = {
    'upper': [CHEST_SH, LAT_KNEEL, TRI_OH, REAR_DELT, NECK],
    'lower': [HAM, QUAD, PIRI, CALF, BUTTERFLY],
    'full': [RUNNERS, GLUTE_SEAT, CHEST_SH, LAT_KNEEL, SPINE],
}

MOBILITY = [
    dict(day='Wed', title='Hips &\nLower Back',
         coach="Unlock the hips and ease the lower back after leg day. Slow breaths; never force a stretch.",
         moves=[KNEE_CIRCLES, FROG, BUTTERFLY, PIRI, GLUTE_SEAT, HAM, QUAD, IRON_CROSS, SIDE_LYING,
                LOW_BACK, SPINE, WGS]),
    dict(day='Fri', title='Upper Back\n& Shoulders',
         coach="Undo the desk and the pressing: open the chest, free the shoulders, loosen the neck.",
         moves=[WRISTS, NECK, NECK_PUSH, CHEST, CHEST_SH, BACK_PEC, LAT_KNEEL, LATERAL, REAR_DELT,
                TRI_OH, UPPER_BACK, UPDOG]),
    dict(day='Sun', title='Full-Body\nFlow',
         coach="Head to toe, moving more than holding. Ready the body for Monday.",
         moves=[INCH, WGS, SQUAT, REACH, WINDMILL, RUNNERS, WIDE_ANGLE, FROG, LAT_KNEEL, CHEST_SH,
                SPINE, UPDOG]),
]

WARMUPS = {
    'upper': [WALK, REACH, SCAP, UPDOG, INCH, CHEST, WGS],
    'lower': [WALK, WINDMILL, SQUAT, BRIDGE, LUNGE, WGS, JACKS],
    'full': [WALK, REACH, WINDMILL, INCH, SQUAT, WGS, JACKS],
}
DAY_WARMUP = {'Mon': 'upper', 'Tue': 'lower', 'Thu': 'upper', 'Sat': 'full'}

# ── body map ─────────────────────────────────────────────────────────────────────────────────
# Both datasets' muscle names → the regions drawn by the body map (assets/bodymap.json).
REGIONS = {
    'abs': ['abs'], 'abdominals': ['abs'], 'lower abs': ['abs'], 'core': ['abs', 'obliques'],
    'obliques': ['obliques'], 'serratus anterior': ['obliques'],
    'abductors': ['abductors'], 'adductors': ['adductor'], 'hip flexors': ['quadriceps'],
    'biceps': ['biceps'], 'triceps': ['triceps'], 'forearms': ['forearm'],
    'calves': ['calves'], 'ankles': ['calves'], 'feet': [],
    'delts': ['front-deltoids', 'back-deltoids'], 'deltoids': ['front-deltoids', 'back-deltoids'],
    'shoulders': ['front-deltoids', 'back-deltoids'],
    'glutes': ['gluteal'], 'hamstrings': ['hamstring'], 'quads': ['quadriceps'],
    'quadriceps': ['quadriceps'],
    'lats': ['upper-back'], 'latissimus dorsi': ['upper-back'], 'upper back': ['upper-back'],
    'middle back': ['upper-back'], 'rhomboids': ['upper-back'],
    'traps': ['trapezius'], 'trapezius': ['trapezius'],
    'lower back': ['lower-back'], 'spine': ['lower-back'],
    'pectorals': ['chest'], 'chest': ['chest'], 'upper chest': ['chest'],
    'neck': ['neck'], 'cardiovascular system': [],
    'levator scapulae': ['neck', 'trapezius'], 'sternocleidomastoid': ['neck'],
    'groin': ['adductor'], 'ankle stabilizers': ['calves'], 'wrists': ['forearm'], 'hands': [],
}


def regions(names):
    out = []
    for n in names:
        if n not in REGIONS:
            raise SystemExit(f"no body-map region for muscle '{n}' — add it to REGIONS")
        out += [r for r in REGIONS[n] if r not in out]
    return out


def body(target_names, secondary_names):
    primary = regions(target_names)
    secondary = [r for r in regions(secondary_names) if r not in primary]
    # Conditioning moves target "cardiovascular system": show the muscles doing the work.
    return (primary, secondary) if primary else (secondary, [])


CREDIT_GV = 'Instructions from exercises-dataset (MIT) · 3D demo © Gym visual'


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
    secondary = [m for m in (e.get('secondary_muscles') or []) if m][:5]
    primary_regions, secondary_regions = body([e.get('target', '')], secondary)
    out = dict(
        id=e['id'], name=e['name'], target=e.get('target', ''),
        secondary=secondary,
        equipment=e.get('equipment', ''), category=e.get('category', ''),
        steps=steps,
        # The 180×180 GIF, upscaled to a 720×720 animated WebP by tools/upscale_media.py.
        gif='assets/' + media_name(gif).replace('.gif', '.webp'), thumb='assets/' + media_name(img),
        bodyPrimary=primary_regions, bodySecondary=secondary_regions,
        credit=CREDIT_GV,
        sets=slot['sets'], weight=slot['weight'], rest=slot['rest'], type=slot['type'],
    )
    if slot['type'] == 'time':
        out['time'] = slot['time']
        out['block'] = 'hiit'
    else:
        out['reps'] = slot['reps']
        out['last'] = None
    return out


def build_recovery(moves, block, switch, handoff, secs=None):
    """Timed, hands-free moves: warm-up, cool-down or a mobility flow. secs overrides every length."""
    out = []
    for n, m in enumerate(moves):
        e = build(dict(id=m['src'], sets=1, time=m['time'], rest=0, weight=0, type='time'))
        last = n == len(moves) - 1
        e.update(sets=1, time=secs or m['time'], rest=handoff if last else switch,
                 weight=0, type='time', block=block, cue=m['cue'])
        if m['name']:
            e['name'] = m['name']
        if m['equipment']:
            e['equipment'] = m['equipment']
        if m['steps']:
            e['steps'] = m['steps']
            e['credit'] = '3D demo © Gym visual'
        out.append(e)
    return out


def build_warmup(moves):
    return build_recovery(moves, 'warmup', WARMUP_SWITCH, WARMUP_HANDOFF)


weeks_out, media = [], set()
for w in WEEKS:
    days = []
    for d in w['days']:
        warmup = build_warmup(WARMUPS[DAY_WARMUP[d['day']]])
        # 'mins' above is the designed estimate of the main session at the designed rests; adjust
        # it for the rest the cap removes and the warm-up that now leads the session.
        saved = sum(s['sets'] * (s['design_rest'] - s['rest']) for s in d['strength'])
        warm = sum(e['time'] + e['rest'] for e in warmup)
        cooldown = build_recovery(COOLDOWNS[DAY_WARMUP[d['day']]], 'cooldown', RECOVERY_SWITCH, 0)
        cool = sum(e['time'] + e['rest'] for e in cooldown)
        mins = d['mins'] - round(saved / 60) + round(warm / 60) + round(cool / 60)
        day = dict(num=d['num'], day=d['day'], title=d['title'], mins=mins, coach=d['coach'],
                   warmup=warmup,
                   strength=[build(s) for s in d['strength']],
                   hiit=[build(s) for s in d['hiit']],
                   cooldown=cooldown)
        for e in day['warmup'] + day['strength'] + day['hiit'] + day['cooldown']:
            media.add(e['gif'].split('/')[-1].replace('.webp', '.gif'))
            media.add(e['thumb'].split('/')[-1])
        days.append(day)
    weeks_out.append(dict(label=w['label'], focus=w['focus'], days=days))

mobility_out = []
for n, f in enumerate(MOBILITY):
    flow = build_recovery(f['moves'], 'mobility', RECOVERY_SWITCH, 0, secs=60)
    mins = round(sum(e['time'] + e['rest'] for e in flow) / 60)
    mobility_out.append(dict(num=n + 1, day=f['day'], title=f['title'], mins=mins, coach=f['coach'],
                             kind='mobility', flow=flow))
    for e in flow:
        media.add(e['gif'].split('/')[-1].replace('.webp', '.gif'))
        media.add(e['thumb'].split('/')[-1])

# Carry week A's seeded "last time" values so the very first session still reads true.
seed = {'0426': '12 kg × 8', '0293': '16 kg × 10', '1760': '18 kg × 12',
        '1459': '20 kg × 10', '0292': '18 kg × 10'}
for e in weeks_out[0]['days'][0]['strength'] + weeks_out[0]['days'][1]['strength'] + weeks_out[0]['days'][2]['strength']:
    if e['id'] in seed:
        e['last'] = seed[e['id']]

out_path = sys.argv[1]
with open(out_path, 'w') as f:
    json.dump(dict(weeks=weeks_out, mobility=mobility_out), f, indent=1, ensure_ascii=False)

uniq_ex = {e['id'] for w in weeks_out for d in w['days'] for e in d['strength'] + d['hiit']}
print(f"weeks={len(weeks_out)} unique exercises={len(uniq_ex)} media files={len(media)}")
print(f"wrote {out_path} ({os.path.getsize(out_path)//1024} KB)")
with open(os.path.join(HERE, 'media_list.txt'), 'w') as f:
    f.write('\n'.join(sorted(media)))
