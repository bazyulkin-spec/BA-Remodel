#!/usr/bin/env bash
# Обязательные проверки перед сдачей шага. Запуск: bash tools/check.sh
cd "$(dirname "$0")/.." || exit 1
fail=0
E=app/src/main/java/com/baremodel/app/ui/editor

echo "== 1. коллизии свойство ↔ функция (Platform declaration clash) =="
python3 - <<'PY' || fail=1
import re, glob, sys
bad=0
for p in sorted(glob.glob('**/*.kt', recursive=True)):
    s=open(p,encoding='utf-8').read()
    props=set(re.findall(r'^\s*(?:var|val)\s+([a-zA-Z][A-Za-z0-9_]*)\s', s, re.M))
    funs=set(re.findall(r'\bfun\s+([a-zA-Z][A-Za-z0-9_]*)\s*\(', s))
    for pr in props:
        cap=pr[0].upper()+pr[1:]
        for pref in ('set','get'):
            if pref+cap in funs:
                print(f"  КОНФЛИКТ {p}: {pr} <-> {pref}{cap}"); bad+=1
print("  конфликтов:", bad)
sys.exit(1 if bad else 0)
PY

echo "== 2. модификаторы без импорта =="
for f in $E/*.kt app/src/main/java/com/baremodel/app/*.kt; do
  for m in fillMaxSize fillMaxWidth heightIn widthIn horizontalScroll verticalScroll \
           systemBarsPadding onSizeChanged pointerInput aspectRatio weight offset; do
    if grep -q "\.$m(" "$f" && ! grep -qE "^import .*\.$m$" "$f" && [ "$m" != "weight" ]; then
      echo "  MISS $m в $(basename $f)"; fail=1
    fi
  done
done

echo "== 3. обращения vm.* существуют во ViewModel =="
for n in $(grep -oh "vm\.[a-zA-Z0-9_]*" $E/*.kt | sed 's/vm\.//' | sort -u); do
  grep -qE "(var|val|fun) $n\b" $E/EditorViewModel.kt || { echo "  MISS vm.$n"; fail=1; }
done

echo "== 4. строковые ключи есть в EN и RU =="
for k in $(grep -rhoE "R\.string\.[a-z_0-9]+" app/src/main/java | sed 's/R\.string\.//' | sort -u); do
  grep -q "name=\"$k\"" app/src/main/res/values/strings.xml && \
  grep -q "name=\"$k\"" app/src/main/res/values-ru/strings.xml || { echo "  MISS $k"; fail=1; }
done

echo "== 5. AnimatedVisibility только через обёртку Fade =="
if grep -n "AnimatedVisibility(" $E/*.kt | grep -v "private fun Fade" | grep -v "    AnimatedVisibility(" >/dev/null; then
  echo "  проверьте вызовы AnimatedVisibility вне Fade"
fi

echo "== 6. баланс скобок =="
python3 - <<'PY' || fail=1
import glob, sys
bad=0
for p in sorted(glob.glob('app/src/main/java/**/*.kt', recursive=True)):
    s=open(p,encoding='utf-8').read(); d={'{':0,'(':0}; i=0; instr=incom=inline=False; pr={'}':'{',')':'('}
    while i<len(s):
        c=s[i]
        if inline:
            if c=='\n': inline=False
        elif incom:
            if s[i:i+2]=='*/': incom=False; i+=1
        elif instr:
            if c=='\\': i+=1
            elif c=='"': instr=False
        else:
            if s[i:i+2]=='//': inline=True; i+=1
            elif s[i:i+2]=='/*': incom=True; i+=1
            elif c=='"': instr=True
            elif c in '{(': d[c]+=1
            elif c in '})': d[pr[c]]-=1
        i+=1
    if d['{'] or d['(']: print("  BAD", p, d); bad+=1
print("  файлов с дисбалансом:", bad)
sys.exit(1 if bad else 0)
PY

[ $fail -eq 0 ] && echo "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" || echo "ЕСТЬ ЗАМЕЧАНИЯ"
exit $fail
