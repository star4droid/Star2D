#!/usr/bin/env python3
"""Builds najma/src/main/resources/najma/index.txt.gz : the class index the Najma compiler uses to know the
type of every libGDX / engine / Java member (variable type inference + Arabic member names).
usage: python3 najma/tools/gen_index.py <dir with gdx.jar gdx-box2d.jar box2dlights.jar game.jar>
Format (one line each):  C fqn<T,..> kind super ifaces | F name type [s] | M name ret (params) [s]
"""
import gzip, os, re, subprocess, sys, zipfile
JARS = sys.argv[1]
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src/main/resources/najma/index.txt.gz')
JDK = """java.lang.Object java.lang.String java.lang.Math java.lang.Integer java.lang.Long java.lang.Float java.lang.Double
java.lang.Boolean java.lang.Character java.lang.Short java.lang.Byte java.lang.Number java.lang.StringBuilder java.lang.System
java.lang.Runnable java.lang.Thread java.lang.Exception java.lang.RuntimeException java.lang.Throwable java.lang.Iterable
java.lang.CharSequence java.lang.Comparable java.lang.Enum java.lang.Class java.lang.Error
java.util.List java.util.ArrayList java.util.LinkedList java.util.Map java.util.HashMap java.util.TreeMap java.util.LinkedHashMap
java.util.Set java.util.HashSet java.util.TreeSet java.util.LinkedHashSet java.util.Collection java.util.Collections java.util.Arrays
java.util.Iterator java.util.Queue java.util.Deque java.util.ArrayDeque java.util.Random java.util.Comparator java.util.Map$Entry
java.util.Date java.util.Calendar java.util.Locale java.util.Objects java.util.Stack java.util.PriorityQueue
java.util.SequencedCollection java.util.SequencedSet java.util.SequencedMap java.util.AbstractList java.util.AbstractCollection java.util.AbstractMap java.util.AbstractSet java.util.AbstractSequentialList java.util.AbstractQueue java.util.SortedMap java.util.NavigableMap java.util.SortedSet java.util.NavigableSet
java.io.File java.io.IOException java.io.InputStream java.io.OutputStream java.io.PrintStream""".split()
SKIP = ('com/badlogic/gdx/physics/bullet/', 'net/mgsx/', 'com/badlogic/gdx/utils/compression/', 'com/star4droid/star2d/Helpers/rtl/')
names, cp = [], []
# najma.N (the runtime helper) is compiled here so the index knows it
import tempfile, shutil
_tmp = tempfile.mkdtemp()
os.makedirs(_tmp + '/src/najma'); shutil.copy(os.path.join(os.path.dirname(OUT), 'N.java.txt'), _tmp + '/src/najma/N.java')
_cp = ':'.join(os.path.join(JARS, j) for j in os.listdir(JARS) if j.endswith('.jar') and j != 'najma-n.jar')
subprocess.check_call(['javac', '-nowarn', '-Xlint:-options', '--release', '8', '-cp', _cp, '-d', _tmp + '/out', _tmp + '/src/najma/N.java'])
subprocess.check_call(['jar', 'cf', os.path.join(JARS, 'najma-n.jar'), '-C', _tmp + '/out', '.'])
for j in sorted(os.listdir(JARS)):
    if not j.endswith('.jar'): continue
    cp.append(os.path.join(JARS, j))
    for n in zipfile.ZipFile(cp[-1]).namelist():
        if not n.endswith('.class') or n.startswith(SKIP) or n.startswith('META-INF'): continue
        base = n[:-6]
        if re.search(r'\$\d', base) or 'package-info' in base or 'module-info' in base: continue
        names.append(base.replace('/', '.'))
names = sorted(set(names)) + JDK
out = []
hdr = re.compile(r'^(?:public |protected |abstract |final |static |strictfp |sealed |non-sealed )*(class|interface|enum|@interface) (\S+?)(<.*?>)?(?: extends (.+?))?(?: implements (.+?))?(?: permits .+?)? \{$')
def split_top(s):
    r, d, cur = [], 0, ''
    for ch in s:
        if ch == '<': d += 1
        if ch == '>': d -= 1
        if ch == ',' and d == 0: r.append(cur.strip()); cur = ''
        else: cur += ch
    if cur.strip(): r.append(cur.strip())
    return r
def clean(t):
    t = re.sub(r'\? (extends|super) ', '', t).replace('...', '[]')
    return t.replace(' ', '')
for i in range(0, len(names), 400):
    txt = subprocess.run(['javap', '-protected', '-cp', ':'.join(cp)] + names[i:i+400], capture_output=True, text=True).stdout
    for line in txt.split('\n'):
        s = line.strip()
        if not s or s.startswith('Compiled from') or s == '}': continue
        m = hdr.match(s)
        if m and not line.startswith(' '):
            kind, fqn, tp, sup, ifs = m.groups()
            # the header's own type params may contain "extends": cut bounds
            tp = '<' + ','.join(re.sub(r' extends .*', '', p) for p in split_top(tp[1:-1])) + '>' if tp else ''
            if kind == 'interface' and sup: ifs, sup = sup, None
            out.append('C %s%s %s %s %s' % (fqn, tp, kind[0] if kind != '@interface' else 'a', clean(sup) if sup else '-', ','.join(clean(x) for x in split_top(ifs)) if ifs else '-'))
            continue
        if not s.endswith(';'): continue
        s = s[:-1]
        s = re.sub(r' throws .*$', '', s)
        static = ' static ' in ' ' + s
        s = re.sub(r'^((public|protected|static|final|abstract|synchronized|native|transient|volatile|strictfp|default) )+', '', s)
        if s.startswith('<'):  # method type params
            d = 0
            for k, ch in enumerate(s):
                if ch == '<': d += 1
                if ch == '>':
                    d -= 1
                    if d == 0: s = s[k+1:].strip(); break
        if '(' in s:
            head, params = s[:s.index('(')], s[s.index('(')+1:s.rindex(')')]
            parts = head.rsplit(' ', 1)
            if len(parts) == 1: ret, name = '<init>', '<init>'
            else: ret, name = clean(parts[0]), parts[1]
            out.append('M %s %s (%s)%s' % (name, ret, ';'.join(clean(p) for p in split_top(params)), ' s' if static else ''))
        else:
            parts = s.rsplit(' ', 1)
            if len(parts) == 2: out.append('F %s %s%s' % (parts[1], clean(parts[0]), ' s' if static else ''))
# the index is made with a desktop JDK: drop what Android does not have (Java 21 sequenced collections)
NEW21 = {'reversed', 'getFirst', 'getLast', 'addFirst', 'addLast', 'removeFirst', 'removeLast'}
KEEP21 = ('java.util.LinkedList', 'java.util.Deque', 'java.util.ArrayDeque')
flt, cur = [], ''
for l in out:
    if l.startswith('C '): cur = l.split(' ')[1].split('<')[0]
    elif cur.startswith('java.util.Sequenced'): continue
    elif cur.startswith('java.util.') and cur not in KEEP21 and l.split(' ')[1] in NEW21: continue
    flt.append(l)
out = flt
with gzip.open(OUT, 'wt', encoding='utf-8', compresslevel=9) as f: f.write('\n'.join(out) + '\n')
print(len(names), 'classes,', len(out), 'lines,', os.path.getsize(OUT) // 1024, 'KB')
