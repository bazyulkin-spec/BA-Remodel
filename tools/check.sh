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

echo "== 5b. иконки / цвета / типы ядра без импорта =="
A=app/src/main/java/com/baremodel/app
for i in $(grep -rhoE "BaIcons\.[A-Za-z]+" $A | sed 's/BaIcons\.//' | sort -u); do
  grep -q "val $i:" $A/ui/theme/Icons.kt || { echo "  MISS BaIcons.$i"; fail=1; }
done
for f in $(find $A -name "*.kt" ! -path "*/ui/theme/*"); do
  for c in $(grep -oE "\b(Bg|Panel|Panel2|Panel3|LineC|Line2|Txt|Sub|Dim|Acc|Acc2|AccDeep|AccSoft|WarmSoft|Warn|Good|Bad|CanvasBg|GroutC)\b" "$f" | sort -u); do
    grep -qE "^import com\.baremodel\.app\.ui\.theme\.$c$" "$f" || { echo "  MISS $c в $(basename $f)"; fail=1; }
  done
  for t in $(grep -oE "\b(Furniture|CoverageAnalyzer|CoverageReport|CutPiece|CutAnalyzer|CutReport|DecorSpec|DecorMode|DecorPlanner|ArtRect|AnchorMode|Aligner|TileSpec|RoomSpec|PatternSpec|PatternType|Pt|LayoutResult|TileClass|LocalRect|TilingEngine|LayoutSuggester)\b" "$f" | sort -u); do
    grep -qE "^import com\.baremodel\.core\.$t$" "$f" || { echo "  MISS $t в $(basename $f)"; fail=1; }
  done
done

echo "== 5b2. типы отрисовки без импорта =="
for f in $(find app/src/main/java -name "*.kt"); do
  for t in Stroke Path PathEffect StrokeJoin CornerRadius; do
    if grep -q "[^A-Za-z]$t(" "$f" && ! grep -q "^import .*\.$t$" "$f"; then
      echo "  MISS $t в $(basename $f)"; fail=1
    fi
  done
done

echo "== 5c. дубли импортов =="
for f in $(find app/src/main/java core/src/main/kotlin -name "*.kt" 2>/dev/null); do
  d=$(grep "^import " "$f" | sort | uniq -d)
  [ -n "$d" ] && { echo "  ДУБЛЬ в $(basename $f): $d"; fail=1; }
  a=$(grep "^import " "$f" | sed 's/^import //; s/^\(.*\)\.\([A-Za-z0-9_]*\)$/\2/' | sort | uniq -d)
  for name in $a; do
    n=$(grep -c "^import .*\.$name$" "$f")
    [ "$n" -gt 1 ] && { echo "  КОНФЛИКТ ИМЕНИ $name в $(basename $f)"; fail=1; }
  done
done

echo "== 5c2. импорт без пакета =="
bad=$(grep -rn "^import [A-Z]" app/src/main/java core/src/main/kotlin --include="*.kt" 2>/dev/null)
[ -n "$bad" ] && { echo "  импорт без пакета:"; echo "$bad"; fail=1; }

echo "== 5d. init-блоки только после всех свойств =="
for f in $(grep -rl "^    init {" app/src/main/java --include="*.kt" 2>/dev/null); do
  li=$(grep -n "^    init {" "$f" | head -1 | cut -d: -f1)
  lp=$(grep -n "by mutableStateOf" "$f" | tail -1 | cut -d: -f1)
  if [ -n "$li" ] && [ -n "$lp" ] && [ "$li" -lt "$lp" ]; then
    echo "  init ВЫШЕ свойств в $(basename $f): init@$li, последнее свойство@$lp"; fail=1
  fi
done

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

echo "== 5b3. compose-расширения без импорта (toArgb / asAndroidBitmap / nativeCanvas) =="
for sym in toArgb asAndroidBitmap; do
  for f in $(grep -rl "\.$sym(" app/src/main/java --include="*.kt"); do
    grep -q "import .*\.$sym$" "$f" || { echo "  $f: .$sym( без импорта"; fail=1; }
  done
done

echo "== 7. @Composable внутри не-inline лямбд (joinToString/run по ссылке) =="
# joinToString НЕ inline: stringResource/painterResource внутри её лямбды не компилируется.
# Эвристика: composable-вызов в пределах 8 строк после joinToString( с ОТКРЫТОЙ лямбдой.
# Лямбда, закрытая на той же строке, безопасна — иначе ловились соседние строки выражения.
BAD7=$(find app/src -name "*.kt" -exec awk '
  FNR==1 { w = 0 }
  /joinToString[^{]*[{]/ { w = ($0 ~ /}/) ? 0 : 8 }
  w > 0 && /stringResource\(|painterResource\(/ {
    print "  " FILENAME ":" FNR " composable внутри joinToString?"
  }
  { if (w > 0) w-- }
' {} + | tee /dev/stderr | wc -l)
if [ "$BAD7" -gt 0 ]; then fail=1; fi

echo "== 8. все варианты Selection покрыты в when (иначе Kotlin 2.x не соберёт) =="
python3 - <<'PY8' || fail=1
import re, sys
E = 'app/src/main/java/com/baremodel/app/ui/editor/'
vm = open(E + 'EditorViewModel.kt', encoding='utf-8').read()
subs = set(re.findall(r'data class (\w+)\([^)]*\)\s*:\s*Selection', vm))
bad = 0
src = open(E + 'MainScreen.kt', encoding='utf-8').read()
for m in re.finditer(r'when \((?:sel|selection)\)\s*\{', src):
    # тело when по балансу скобок, иначе else -> из чужого when всё маскирует
    k = src.index("{", m.start())
    depth = 0
    while k < len(src):
        if src[k] == "{":
            depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0:
                break
        k += 1
    body = src[m.start():k]
    if "else ->" in body:
        continue
    for name in sorted(subs):
        if ("Selection." + name) not in body:
            print("  НЕ ПОКРЫТ Selection." + name + " в when (sel)"); bad += 1
print("  вариантов Selection:", len(subs), "| не покрыто:", bad)
sys.exit(1 if bad else 0)
PY8

echo "== 9. константы enum ядра существуют (ловит Unresolved reference) =="
python3 - <<'PY9' || fail=1
import re, glob, sys
vals = {}
for f in glob.glob('core/src/main/kotlin/com/baremodel/core/*.kt'):
    for m in re.finditer(r'enum class (\w+)\s*\{([^}]*)\}', open(f, encoding='utf-8').read()):
        name = m.group(1)
        items = [x.strip().split('(')[0].strip() for x in m.group(2).split(',') if x.strip()]
        vals[name] = set(i for i in items if re.fullmatch(r"[A-Z_0-9]+", i))
bad = 0
for f in glob.glob('app/src/main/java/**/*.kt', recursive=True):
    src = open(f, encoding='utf-8').read()
    for enum, allowed in vals.items():
        if not allowed:
            continue
        for m in re.finditer(r'(?<![A-Za-z0-9_])' + enum + r'\.([A-Z_][A-Z_0-9]*)', src):
            if m.group(1) not in allowed:
                print("  НЕТ КОНСТАНТЫ " + enum + "." + m.group(1) + " в " + f.split("/")[-1])
                bad += 1
print("  enum ядра:", len(vals), "| неизвестных констант:", bad)
sys.exit(1 if bad else 0)
PY9

[ $fail -eq 0 ] && echo "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" || echo "ЕСТЬ ЗАМЕЧАНИЯ"
exit $fail
