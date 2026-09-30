#!/usr/bin/env python3
"""Build Forge's multi-week plan.json from the exercises-dataset and free-exercise-db.

Usage:
    curl -L -o tools/exercises.json \
      https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json
    git clone --depth 1 https://github.com/yuhonas/free-exercise-db tools/free-exercise-db
    pip install pillow
    python3 tools/genplan.py app/src/main/assets/plan.json

Writes media_list.txt next to this script; fetch each gif/jpg name from the exercises-dataset's
videos/ (gif) or images/ (jpg) directory into app/src/main/assets/media/. The free-exercise-db
photo frames (fe_*.jpg) are copied and recompressed into that directory directly.
"""
import json, os, sys, re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = json.load(open(os.path.join(HERE, 'exercises.json')))
BY_ID = {e['id']: e for e in SRC}

# free-exercise-db (Unlicense): real-person start/end photos, played as a two-frame loop.
FE_DIR = os.environ.get('FREE_EXERCISE_DB', os.path.join(HERE, 'free-exercise-db'))
FE = {e['id']: e for e in json.load(open(os.path.join(FE_DIR, 'dist', 'exercises.json')))}

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
# joints the day loads. ~7 minutes of work plus short switch-overs. 'fe:' ids are
# free-exercise-db entries; plain ids are exercises-dataset entries (which bring the 3D GIF).

WARMUP_SWITCH = 10  # seconds to get into the next move
WARMUP_HANDOFF = 20  # after the last move: set up the bells for the first set


def W(src, secs, cue, name=None, equipment=None, steps=None):
    """steps replaces the dataset's instructions where they don't describe the move as done here."""
    return dict(src=src, time=secs, cue=cue, name=name, equipment=equipment, steps=steps)


WALK = W('fe:Walking_Treadmill', 120,
         "Brisk walk on the walkpad. Build to a pace you could still hold a conversation at.",
         name='walkpad brisk walk', equipment='walkpad',
         steps=["Start the walkpad at an easy pace and stand tall, arms swinging naturally.",
                "Every 30 seconds nudge the speed up a notch.",
                "Finish at a brisk pace: breathing harder, but still able to talk."])
ARMS = W('fe:Arm_Circles', 40, "Small circles that grow bigger. Reverse direction halfway.",
         name='arm circles')
SCAP = W('3021', 40, "Arms stay straight. Let the chest sink between the shoulder blades, then push "
                     "the floor away.",
         steps=["High plank: hands under the shoulders, body in one straight line.",
                "Keeping the arms locked, let the chest sink so the shoulder blades squeeze together.",
                "Push the floor away until the shoulder blades spread apart and the upper back "
                "rounds slightly.",
                "Small, controlled range — only the shoulder blades move."])
CAT = W('fe:Cat_Stretch', 40, "Round the spine up, then let it sag. Slow, with your breath.",
        name='cat-cow',
        steps=["On hands and knees: hands under the shoulders, knees under the hips.",
               "Breathe out, pull the belly in and round the whole spine up, letting the head drop.",
               "Breathe in, let the belly sink and lift the chest and gaze.",
               "Flow slowly between the two for the whole interval."])
INCH = W('1471', 40, "Walk the hands out to a plank and back in. Legs as straight as you can.")
CHEST = W('1167', 40, "Swing the arms wide open, then hug across the chest. Loose and rhythmic.",
          name='dynamic chest stretch')
WGS = W('1604', 60, "Lunge, elbow to instep, then rotate that arm to the ceiling. Switch sides at "
                    "30 s.", name="world's greatest stretch")
HIPS = W('fe:Standing_Hip_Circles', 40, "Big slow circles with the knee. Switch legs halfway.",
         name='standing hip circles')
SQUAT = W('fe:Bodyweight_Squat', 40, "Easy tempo, full depth, chest up. No load yet.",
          name='bodyweight squat')
BRIDGE = W('3013', 40, "Squeeze the glutes at the top and lower slowly.", name='glute bridge')
LUNGE = W('3470', 40, "Alternate legs. Upright torso, back knee hovering above the floor.",
          name='forward lunge')
JACKS = W('3224', 30, "Light and springy — heart rate up before the first set.",
          name='jumping jacks')

WARMUPS = {
    'upper': [WALK, ARMS, SCAP, CAT, INCH, CHEST, WGS],
    'lower': [WALK, HIPS, SQUAT, BRIDGE, LUNGE, WGS, JACKS],
    'full': [WALK, ARMS, HIPS, INCH, SQUAT, WGS, JACKS],
}
DAY_WARMUP = {'Mon': 'upper', 'Tue': 'lower', 'Thu': 'upper', 'Sat': 'full'}

# ── real-person photos ───────────────────────────────────────────────────────────────────────
# exercises-dataset id → free-exercise-db id, each checked by eye against the 3D demo. Only
# faithful matches are listed; the rest keep the 3D animation alone.
PHOTOS = {
    '0259': 'Push-Ups_-_Close_Triceps_Position', '0274': 'Crunches',
    '0283': 'Push-Ups_-_Close_Triceps_Position', '0285': 'Dumbbell_Alternate_Bicep_Curl',
    '0286': 'Dumbbell_One-Arm_Shoulder_Press', '0292': 'One-Arm_Dumbbell_Row',
    '0293': 'Bent_Over_Two-Dumbbell_Row', '0295': 'Dumbbell_Clean',
    '0296': 'Close-Grip_Dumbbell_Press', '0299': 'Cuban_Press', '0310': 'Front_Dumbbell_Raise',
    '0313': 'Hammer_Curls', '0334': 'Side_Lateral_Raise', '0336': 'Dumbbell_Lunges',
    '0375': 'Bent-Arm_Dumbbell_Pullover', '0378': 'Seated_Bent-Over_Rear_Delt_Raise',
    '0381': 'Dumbbell_Rear_Lunge', '0406': 'Dumbbell_Shrug', '0413': 'Dumbbell_Squat',
    '0416': 'Dumbbell_Bicep_Curl', '0417': 'Standing_Dumbbell_Calf_Raise',
    '0426': 'Standing_Dumbbell_Press', '0430': 'Standing_Dumbbell_Triceps_Extension',
    '0431': 'Dumbbell_Step_Ups', '0432': 'Stiff-Legged_Dumbbell_Deadlift',
    '0433': 'Straight-Arm_Dumbbell_Pullover', '0437': 'Standing_Dumbbell_Upright_Row',
    '0514': 'Freehand_Jump_Squat', '0630': 'Mountain_Climbers',
    '0660': 'Close-Grip_Push-Up_off_of_a_Dumbbell', '0662': 'Pushups', '0672': 'Bench_Dips',
    '0687': 'Russian_Twist', '1459': 'Stiff-Legged_Dumbbell_Deadlift',
    '1757': 'Kettlebell_One-Legged_Deadlift', '1760': 'Goblet_Squat',
    '2137': 'Arnold_Dumbbell_Press', '2292': 'Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench',
    '2368': 'Split_Squats', '3013': 'Butt_Lift_Bridge', '3220': 'Star_Jump',
    '3470': 'Bodyweight_Walking_Lunge', '3582': 'Split_Jump',
    # warm-up moves
    '1471': 'Inchworm', '1167': 'Dynamic_Chest_Stretch', '1604': 'Worlds_Greatest_Stretch',
}

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


# Dataset vocabulary for free-exercise-db-only moves, so chips read the same across the app.
FE_TARGET = {'quadriceps': 'quads', 'shoulders': 'delts', 'abdominals': 'abs', 'chest': 'pectorals',
             'middle back': 'upper back'}

photo_files = set()


def photo_pair(fe_id):
    e = FE.get(fe_id)
    if e is None or len(e.get('images') or []) < 2:
        raise SystemExit(f"free-exercise-db has no photo pair for {fe_id}")
    names = [f'fe_{fe_id}_{i}.jpg' for i in (0, 1)]
    photo_files.update((fe_id, i, n) for i, n in enumerate(names))
    return ['assets/' + n for n in names]


CREDIT_GV = 'Instructions from exercises-dataset (MIT) · 3D demo © Gym visual'
CREDIT_FE = 'Photos from free-exercise-db (public domain)'


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
    photos = photo_pair(PHOTOS[e['id']]) if e['id'] in PHOTOS else []
    out = dict(
        id=e['id'], name=e['name'], target=e.get('target', ''),
        secondary=secondary,
        equipment=e.get('equipment', ''), category=e.get('category', ''),
        steps=steps,
        gif='assets/' + media_name(gif), thumb='assets/' + media_name(img),
        photos=photos,
        bodyPrimary=primary_regions, bodySecondary=secondary_regions,
        credit=CREDIT_GV + (' · ' + CREDIT_FE if photos else ''),
        sets=slot['sets'], weight=slot['weight'], rest=slot['rest'], type=slot['type'],
    )
    if slot['type'] == 'time':
        out['time'] = slot['time']
        out['block'] = 'hiit'
    else:
        out['reps'] = slot['reps']
        out['last'] = None
    return out


def build_fe(fe_id):
    """A warm-up move that exists only in free-exercise-db: photos, no 3D demo."""
    e = FE[fe_id]
    photos = photo_pair(fe_id)
    target = e['primaryMuscles'][0]
    primary_regions, secondary_regions = body(e['primaryMuscles'], e['secondaryMuscles'])
    return dict(
        id='fe-' + fe_id, name=e['name'].lower(), target=FE_TARGET.get(target, target),
        secondary=[FE_TARGET.get(m, m) for m in e['secondaryMuscles']][:5],
        equipment=e.get('equipment') or 'body weight', category=e.get('category') or '',
        steps=e.get('instructions') or [],
        gif='', thumb=photos[0], photos=photos,
        bodyPrimary=primary_regions, bodySecondary=secondary_regions,
        credit='Instructions and ' + CREDIT_FE[0].lower() + CREDIT_FE[1:],
    )


def build_warmup(moves):
    out = []
    for n, m in enumerate(moves):
        if m['src'].startswith('fe:'):
            e = build_fe(m['src'][3:])
        else:
            e = build(dict(id=m['src'], sets=1, time=m['time'], rest=0, weight=0, type='time'))
        last = n == len(moves) - 1
        e.update(sets=1, time=m['time'], rest=WARMUP_HANDOFF if last else WARMUP_SWITCH,
                 weight=0, type='time', block='warmup', cue=m['cue'])
        if m['name']:
            e['name'] = m['name']
        if m['equipment']:
            e['equipment'] = m['equipment']
        if m['steps']:
            e['steps'] = m['steps']
            e['credit'] = ' · '.join(
                (['3D demo © Gym visual'] if e['gif'] else []) + ([CREDIT_FE] if e['photos'] else []))
        out.append(e)
    return out


weeks_out, media = [], set()
for w in WEEKS:
    days = []
    for d in w['days']:
        warmup = build_warmup(WARMUPS[DAY_WARMUP[d['day']]])
        # 'mins' above is the designed estimate of the main session at the designed rests; adjust
        # it for the rest the cap removes and the warm-up that now leads the session.
        saved = sum(s['sets'] * (s['design_rest'] - s['rest']) for s in d['strength'])
        warm = sum(e['time'] + e['rest'] for e in warmup)
        mins = d['mins'] - round(saved / 60) + round(warm / 60)
        day = dict(num=d['num'], day=d['day'], title=d['title'], mins=mins, coach=d['coach'],
                   warmup=warmup,
                   strength=[build(s) for s in d['strength']],
                   hiit=[build(s) for s in d['hiit']])
        for e in day['warmup'] + day['strength'] + day['hiit']:
            for ref in [e['gif'], e['thumb']]:
                if ref and not ref.split('/')[-1].startswith('fe_'):
                    media.add(ref.split('/')[-1])
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
with open(os.path.join(HERE, 'media_list.txt'), 'w') as f:
    f.write('\n'.join(sorted(media)))

# Photo frames: the dataset ships 850×567 JPEGs; recompress them into the APK's media folder.
from PIL import Image

media_dir = os.path.join(os.path.dirname(os.path.abspath(out_path)), 'media')
for fe_id, i, name in sorted(photo_files):
    frame = Image.open(os.path.join(FE_DIR, 'exercises', FE[fe_id]['images'][i])).convert('RGB')
    frame.thumbnail((850, 850))
    frame.save(os.path.join(media_dir, name), 'JPEG', quality=80, optimize=True)
print(f"photo frames={len(photo_files)}")
