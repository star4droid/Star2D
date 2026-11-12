#!/usr/bin/env python3
"""Tests of the Najma compiler, no phone needed. For every src/test/cases/*.njm:
  - it is compiled to Java by the Najma compiler;
  - "# error: text" lines: the compiler must report an error containing that text (and nothing is compiled further);
  - otherwise the generated Java must compile with javac against libGDX + the engine runtime + najma.N;
  - "# run: text": the static task «اختبار» of the class is run and must return that text;
  - "# line: N text": line N of the generated Java must contain that text (line numbers are kept).
usage: python3 najma/tools/test_najma.py <dir with gdx.jar gdx-box2d.jar box2dlights.jar game.jar najma-n.jar> [case name]
"""
import glob, os, re, shutil, subprocess, sys, tempfile
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JARS = sys.argv[1]
ONLY = sys.argv[2] if len(sys.argv) > 2 else None
CP = ':'.join(sorted(glob.glob(JARS + '/*.jar')))
B = os.path.join(ROOT, 'build'); CL = B + '/classes'
ENV = dict(os.environ, JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8')
def sh(*a, **k): return subprocess.run(a, capture_output=True, text=True, env=ENV, **k)
shutil.rmtree(B, ignore_errors=True); os.makedirs(CL)
r = sh('javac', '-nowarn', '-Xlint:-options', '--release', '8', '-encoding', 'UTF-8', '-d', CL, *glob.glob(ROOT + '/src/main/java/com/star4droid/najma/*.java'), *glob.glob(ROOT + '/src/test/java/com/star4droid/najma/*.java'))
if r.returncode: print(r.stderr); sys.exit(1)
shutil.copytree(ROOT + '/src/main/resources/najma', CL + '/najma')
# the scene's generated Refs classes (the editor writes them from the scene)
T = tempfile.mkdtemp()
os.makedirs(T + '/refs/com/star4droid/Game/Refs')
EL = 'player enemy coin scoreText bar stick ground bullet door button1 title'.split()
for kind, base in (('Item', 'ItemScript'), ('Scene', 'SceneScript')):
    open(T + '/refs/com/star4droid/Game/Refs/main%sRefs.java' % kind, 'w').write('package com.star4droid.Game.Refs; public abstract class main%sRefs extends com.star4droid.template.Utils.%s { %s }' % (kind, base, ' '.join('public com.star4droid.template.Utils.PlayerItem %s;' % e for e in EL)))
os.makedirs(T + '/rc')
r = sh('javac', '-nowarn', '-Xlint:-options', '--release', '8', '-cp', CP, '-d', T + '/rc', *glob.glob(T + '/refs/com/star4droid/Game/Refs/*.java'))
if r.returncode: print(r.stderr); sys.exit(1)
open(T + '/Run.java', 'w').write('public class Run { public static void main(String[] a) throws Exception { System.out.print(Class.forName(a[0]).getMethod("اختبار").invoke(null)); } }')
sh('javac', '-d', T + '/rc', T + '/Run.java')
# every code block of the in-app guide is a test case too (kinds: عنصر item, مشهد scene, صنف plain, جزء statements inside an event)
GUIDE = os.path.join(os.path.dirname(ROOT), 'app/src/main/assets/najma/guide.md')
os.makedirs(T + '/guide')
if os.path.isfile(GUIDE):
    g = open(GUIDE, encoding='utf-8').read()
    for i, m in enumerate(re.finditer(r'^```(\S*)\n(.*?)^```$', g, re.M | re.S)):
        kind, code = m.group(1), m.group(2)
        if kind == 'ناتج': continue
        k = {'عنصر': 'item', 'مشهد': 'scene', 'صنف': 'plain', 'جزء': 'item'}.get(kind)
        if k is None: print('FAIL guide block %d: unknown kind %r' % (i, kind)); sys.exit(1)
        if kind == 'جزء': code = 'عند البدء {\n' + code + '}\n'
        line = g[:m.start()].count('\n') + 1
        open(T + '/guide/guide_%03d_line%d.njm' % (i, line), 'w', encoding='utf-8').write('# kind: %s\n# elements: known\n%s' % (k, code))
fails = 0; n = 0
for case in sorted(glob.glob(ROOT + '/src/test/cases/*.njm')) + sorted(glob.glob(T + '/guide/*.njm')):
    name = os.path.basename(case)[:-4]
    if ONLY and ONLY != name: continue
    n += 1
    src = open(case, encoding='utf-8').read()
    kind = (re.search(r'^# kind: (\w+)', src, re.M) or [0, 'plain'])[1]
    errors = re.findall(r'^# error: (.+)$', src, re.M)
    runs = re.findall(r'^# run: (.*)$', src, re.M)
    lines = re.findall(r'^# line: (\d+) (.+)$', src, re.M)
    rel = {'item': 'com/star4droid/Game/Scripts/main/%sScript.njm', 'scene': 'com/star4droid/Game/SceneScript/%sScript.njm', 'plain': 'tests/%s.njm'}[kind] % ('main' if kind == 'scene' else name)
    out = T + '/gen/' + rel[:-4] + '.java'
    os.makedirs(os.path.dirname(out), exist_ok=True)
    els = ','.join(e + '=com.star4droid.template.Utils.PlayerItem' for e in EL) if 'elements: known' in src else ''
    r = sh('java', '-cp', CL, 'com.star4droid.najma.Cli', case, rel, out, els)
    got = [l[4:] for l in r.stdout.split('\n') if l.startswith('ERR ')]
    def bad(msg):
        global fails; fails += 1; print('FAIL %-22s %s' % (name, msg))
    if 'internal' in r.stdout or 'Exception' in r.stderr or 'داخلي' in r.stdout: bad('compiler crashed: ' + r.stdout + r.stderr); continue
    if errors:
        miss = [e for e in errors if not any(e in g for g in got)]
        if miss: bad('expected error not reported: %s\n     got: %s' % (miss, got))
        else: print('ok   %-22s (%d expected errors)' % (name, len(errors)))
        continue
    if got: bad('unexpected errors: %s' % got); continue
    gen = open(out, encoding='utf-8').read().split('\n')
    lm = [(int(a), b) for a, b in lines if b not in gen[int(a) - 1]]
    if lm: bad('line mapping: %s\n     line is: %s' % (lm, gen[lm[0][0] - 1])); continue
    r = sh('javac', '-nowarn', '-Xlint:-options', '--release', '8', '-encoding', 'UTF-8', '-cp', CP + ':' + T + '/rc', '-d', T + '/out_' + name, out)
    if r.returncode: bad('generated Java does not compile:\n' + '\n'.join(l for l in r.stderr.split('\n') if 'Picked up' not in l)[:3000]); continue
    if runs:
        r = sh('java', '-cp', CP + ':' + T + '/rc:' + T + '/out_' + name, 'Run', rel[:-4].replace('/', '.'))
        if r.stdout != runs[0]: bad('run: expected %r got %r %s' % (runs[0], r.stdout, r.stderr[-600:])); continue
    if not name.startswith('guide_'): print('ok   %-22s' % name)
print('%d cases, %d failed' % (n, fails))
sys.exit(1 if fails else 0)
