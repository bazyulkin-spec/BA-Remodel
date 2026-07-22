# BA-Remodel · Android — план поэтапной реализации

Этот файл — единственный источник правды для продолжения работы в новых сессиях.
Перед работой прочитай §2 (процесс), §3 (статус), §7 (контракты) и раздел своего шага в §8.
Остальное — по необходимости. Не читай подряд весь проект: это трата контекста.

## 1. Что делаем

Нативное Android-приложение **BA-Remodel** — планировщик раскладки плитки по техспецификации
BazForge v1.0 (проект переименован в BA-Remodel). Kotlin + Jetpack Compose.
Модули: `:core` (чистый Kotlin, движок раскладки — ГОТОВ) и `:app` (UI).
Модули `:ar` и `:assistant` из спеки — Фаза 1, после MVP (§11).

Обязательно: строка «BA-Remodel · Inspired by Alexander Baziulkin» (строковый ключ `credit`)
внизу секции «Проект» и в футере PDF-отчёта.
Локализация: EN (`res/values`) + RU (`res/values-ru`), выбирается системной локалью.
Эталон логики и UI — веб-версия `BA-Remodel.jsx` (есть у пользователя; для работы не обязательна).

## 2. Процесс между сессиями (ВАЖНО)

Контейнер сбрасывается между чатами. Каждая сессия делает РОВНО ОДИН шаг:

1. Пользователь прикладывает актуальный `BA-Remodel-Android-src.zip`.
2. `unzip -q /mnt/user-data/uploads/BA-Remodel-Android-src.zip -d /home/claude`
3. Выполнить один шаг из §8 (файлы создавать строго по путям из плана).
4. Прогнать проверку шага (описана в самом шаге).
5. Отметить шаг в §3 «Статус» этого файла (galочка + одна строка результата).
6. Собрать и отдать архив:
   `cd /home/claude && zip -qr /mnt/user-data/outputs/BA-Remodel-Android-src.zip BA-Remodel`
   и вызвать present_files с этим файлом.

Правила: готовые файлы НЕ переписывать (исключения указаны в шагах явно);
не добавлять зависимостей и не менять версии (§4); читать только файлы, нужные шагу;
ответы пользователю — коротко, по-русски.

## 3. Статус

- [x] Шаг 1 — каркас Gradle, манифест, ресурсы, строки EN/RU, иконка, ProjectDto
- [x] Шаг 2 — `:core`: движок + LayoutSuggester + JUnit + автономная проверка.
      Результат: kotlinc 2.1.0 компилирует, харнесс — 21/21 PASS, «ALL CHECKS PASSED»
- [x] Шаг 3 — Theme.kt, MainActivity.kt, EditorViewModel.kt, EditorCanvas.kt.
      Результат: весь API §7.2 реализован (проектные методы — заглушки для шага 5);
      grep-проверка ссылок `vm.*` из холста — 18/18 найдено; ключи строк существуют;
      зависимости не менялись. Отличия от плана: `applyRect` отбрасывает вырезы,
      не влезающие в новый прямоугольник; в MainActivity временный Box — шаг 4 заменит на MainScreen()
- [x] Шаг 4 — MainScreen.kt + Panels.kt. Отличие от плана: заглушка ReportTab не создавалась —
      шаг 5 выполнялся в той же сессии, MainScreen сразу вызывает настоящий ReportTab
- [x] Шаг 5 — ProjectRepository.kt, PdfReport.kt, ReportTab.kt, проектные методы VM реализованы
      (viewModelScope + IO, Toast). Проверка: 53 ссылки `vm.*` из UI найдены в VM,
      все ключи строк есть в EN и RU, зависимости не менялись
- [!] Сборка #3 в GitHub Actions: `:core:test` — зелёный, `:app` упал на одной ошибке
      (`fun setRoomMode` конфликтовал с сеттером свойства `roomMode`). Метод переименован
      в `switchRoomMode`; больше JVM-коллизий свойств и функций в модуле нет (проверено скриптом)
- [x] Шаг 7 — `:core` v2: ISurface/IPlacer из ТЗ, декор с областью рисунка, точки отсчёта
      раскладки, отчёт подрезки по стенам, перекрытие декора мебелью. Проверено kotlinc:
      старые 21 + новые 19 проверок, «ALL V2 CHECKS PASSED»; JUnit-зеркало CoreV2Test
- [x] Шаг 6 — README.md, wrapper на месте, движок перепроверен (ALL CHECKS PASSED), финальный zip.
      Осталось: сборка и ручной чек-лист §10 на устройстве пользователя

## 4. Технологии (зафиксировано, не менять)

AGP 8.7.3 · Kotlin 2.1.0 · Compose BOM 2024.12.01 · Gradle 8.11.1 (wrapper) ·
compileSdk 35 · minSdk 26 · targetSdk 35 · JDK 17 · kotlinx-serialization 1.7.3 · JUnit 4.13.2.
Все зависимости — только через `gradle/libs.versions.toml`. Новые библиотеки НЕ добавлять
(в т.ч. material-icons: иконки — текстовые глифы ⤢ ✕ 📐 ✂ 📷 💾 ✨ ⬇).

## 5. Структура проекта

```
BA-Remodel/
├─ PLAN.md                          ← этот файл
├─ settings.gradle.kts  build.gradle.kts  gradle.properties  .gitignore
├─ gradle/libs.versions.toml   gradle/wrapper/…   gradlew  gradlew.bat
├─ tools/verify/Verify.kt           ← автономная проверка движка (§9)
├─ core/                            ← ГОТОВ, не менять
│  ├─ build.gradle.kts
│  ├─ src/main/kotlin/com/baremodel/core/
│  │    Models.kt  Geometry.kt  TilingEngine.kt  LayoutSuggester.kt
│  └─ src/test/kotlin/com/baremodel/core/TilingEngineTest.kt
└─ app/
   ├─ build.gradle.kts  proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml        ← готов (activity + FileProvider)
      ├─ res/…                      ← готово (strings EN/RU, тема, иконка, file_paths)
      └─ java/com/baremodel/app/
         ├─ data/ProjectDto.kt      ← готов (ProjectMeta + ProjectDto)
         ├─ data/ProjectRepository.kt        (шаг 5)
         ├─ report/PdfReport.kt              (шаг 5)
         ├─ MainActivity.kt                  (шаг 3)
         └─ ui/
            ├─ theme/Theme.kt                (шаг 3)
            └─ editor/
               EditorViewModel.kt            (шаг 3)
               EditorCanvas.kt               (шаг 3)
               MainScreen.kt  Panels.kt      (шаг 4)
               ReportTab.kt                  (шаг 5)
```

## 6. Математика движка (реализовано в :core; менять не нужно — справка)

Внутри всё в метрах: `tw=widthMm/1000`, `th=heightMm/1000`, `g=groutMm/1000`;
шаги решётки `W=tw+g`, `H=th+g`. Поворот узора θ и сдвиг (ox,oy):
`fwd(x,y)=(x·cosθ−y·sinθ+ox, x·sinθ+y·cosθ+oy)`; `inv` — обратное преобразование
(им переводится bbox комнаты в систему узора → диапазоны pminx…pmaxy).

Сетка / вразбежку (k=1|2|3): ряд r → `off=((r mod k)+k mod k)·W/k`; плитка `(c·W+off, r·H, tw, th)`.

Ёлочка 90° (решётка требует tw≤th; при tw>th стороны меняются местами — узор эквивалентен):
пара для (m,n)∈ℤ²: база `b=(m·(W+H)+n·H, m·(H−W)+n·H)`;
горизонтальная плитка `(bx, by, tw, th)`, вертикальная `(bx+W, by+H−W, th, tw)`.
Диапазоны: `m∈[⌊(pminx−pmaxy)/(2W)⌋−2, ⌈(pmaxx−pminy)/(2W)⌉+2]`;
`n∈[⌊min((pminy−m(H−W))/H,(pminx−m(W+H))/H)⌋−2, ⌈max(тех же)⌉+2]`.

Классификация: 9 сэмплов (u,v∈{0,½,1}) через inRoom (в полигоне и не внутри выреза).
9/9 → FULL, если внутри quad нет ни одной вершины комнаты/выреза, иначе CUT; 1–8 → CUT;
0 → CUT, если в quad есть вершина, иначе плитка пропускается. Плюс bbox-отсечение.

Карта подрезки: пересечение полигона комнаты с quad плитки (Sutherland–Hodgman, quad → CCW),
bbox куска в локальных координатах плитки, габарит a×b (a≥b) с шагом 0.5 см; куски <1 см отброшены.
Защита: > 16000 плиток в bbox → `overLimit=true`, раскладка пустая.

Инварианты (закрыты тестами): при g=0 узор разбивает плоскость — покрытие комнаты точное,
без дыр и перекрытий при любых θ, сдвигах и пропорциях (включая ёлочку на невыпуклой комнате).

## 7. Контракты (ОБЯЗАТЕЛЬНЫ для шагов 3–5)

### 7.1 Цвета (шаг 3 создаёт их в Theme.kt как top-level val типа Color)

```
Bg=0xFF0B1322  Panel=0xFF101A2C  Panel2=0xFF0C1526  LineC=0xFF1D2A42
Txt=0xFFE9EEF6  Sub=0xFF8CA0BC  Acc=0xFF3D8BFF  Acc2=0xFF7DB4FF
Warn=0xFFFFB454  CanvasBg=0xFF070E1A  GroutC=0xFF4A5462  Good=0xFF4ADE80
```
`darkColorScheme(primary=Acc, background=Bg, surface=Panel, onSurface=Txt,
surfaceVariant=Panel2, onSurfaceVariant=Sub, outline=LineC, secondary=Acc2, error=Warn)`.

### 7.2 EditorViewModel — публичный API (шаг 3 реализует ВЕСЬ список; шаг 4 использует как есть)

`class EditorViewModel(app: Application) : AndroidViewModel(app)` + top-level в том же файле:
`data class ViewTransform(val scale: Float = 110f, val offset: Offset = Offset(40f, 60f))`
`sealed interface Selection { data class Vertex(val i: Int): Selection; data class Cut(val i: Int): Selection }`

Состояние (`var … by mutableStateOf`, сеттеры private кроме projectName):
```
room = RoomSpec([(0,0),(4,0),(4,3),(0,3)])   tile = TileSpec(600,600,3)   pattern = PatternSpec()
tileColor = Color(0xFFC7CCD6)   variation = true   tileImage: ImageBitmap? = null
reservePct = 10   roomMode = false   showDims = true   showCuts = true
selection: Selection? = null   view = ViewTransform()   hintVisible = true
projectName = ""   projects: List<ProjectMeta> = emptyList()
suggestions: List<LayoutSuggester.Suggestion>? = null
canvasSize: Size = Size.Zero        // обычное поле, не state
```
Производные: `val layout: LayoutResult by derivedStateOf { TilingEngine.build(room, tile, pattern) }`;
`buyCount = ceil(layout.totalCount*(1+reservePct/100.0)).toInt()`;
`buyM2 = buyCount*tile.widthMm*tile.heightMm/1e6`.

Методы:
```
toWorld(Offset): Pt   toScreen(Pt): Offset   fit()   maybeInitialFit()
gestureDown(pos: Offset)   gestureMove(pos: Offset, prev: Offset)   gestureEnd()   cancelGesture()
pinch(base: ViewTransform, d0: Float, mid0: Offset, d: Float, mid: Offset)
   // scale = (base.scale*d/d0).coerceIn(12f, 2400f); мировая точка под mid0 остаётся под mid
setTileWidth(mm) setTileHeight(mm) setGrout(mm) setPatternType(t) setRotation(deg) resetShift()
setColor(c: Color)/*сброс tileImage*/ toggleVariation() clearImage()
loadTileImage(context, uri)   // viewModelScope+IO; SDK>=28 ImageDecoder, иначе MediaStore.Images
applyRect(wM, hM)   applyLShape()  // [(0,0),(4,0),(4,1.8),(2.2,1.8),(2.2,3),(0,3)]
addCutout()          // 0.8×0.8 в центроиде комнаты, включает roomMode, selection=Cut
deleteSelectedVertex()  // только если points.size>3
deleteSelectedCutout()  setSelectedCutW(m)  setSelectedCutH(m)
setReserve(p)  switchRoomMode(b)  toggleDims()  toggleCuts()
runSuggest()   // viewModelScope+Default → suggestions
applySuggestion(s)  // pattern = pattern.copy(type=s.type, rotationDeg=s.rotationDeg); suggestions=null
refreshProjects()  saveProject()  loadProject(name)  deleteProject(name)
   // в шаге 3 — ПУСТЫЕ тела с комментарием «реализуется в шаге 5»
```
Снап вершин при перетаскивании: округление до 0.01 м + прилипание к x/y двух соседних
вершин при |Δ| < 10/scale. Импорт ProjectMeta: `com.baremodel.app.data.ProjectMeta`.

### 7.3 Жесты (EditorCanvas → методы VM)

Один палец, режим «Узор»: точка внутри полигона комнаты → тянем узор
(`pattern.offsetX += dx/scale`, аналогично y); вне полигона → панорамирование (`view.offset += d`).
Один палец, режим «Комната» (порядок хит-тестов): вершина (экранное расстояние <22px) →
перетаскивание со снапом; «+» на середине ребра (рисуется, если экранная длина ребра ≥56px;
хит <18px) → вставить вершину в середину и сразу тащить её; ручка выреза (угол x+w,y+h; <22px) →
resize (w,h = мир−угол, минимум 0.1 м, округление 0.01); тело выреза → перенос (с учётом точки
захвата); иначе → панорамирование. Первый жест скрывает hintVisible.
Два пальца всегда pinch: при появлении второго пальца запомнить (view, d0, mid0) и звать
`vm.pinch(...)`; когда пальцев снова один — `cancelGesture()`, палец бездействует до нового касания.
Реализация: `Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown → vm.gestureDown;
цикл awaitPointerEvent: обновлять map активных указателей, consume() всем changes;
1 палец → vm.gestureMove(pos, previousPosition); выход, когда прижатых нет → vm.gestureEnd } }`.
`Modifier.onSizeChanged { vm.canvasSize = Size(...); vm.maybeInitialFit() }`.
`fit()`: bbox комнаты, `s = clamp((w−76)/bw, (h−96)/bh, min 12)`, центрирование.

### 7.4 Порядок отрисовки (DrawScope; масштаб толщин — умножать на density)

1. Заливка всего холста CanvasBg.
2. Режим «Комната» и 0.5·scale > 16: точечная сетка с шагом 0.5 м (круг r≈1.2·density,
   белый alpha 0.055).
3. Path комнаты с fillType=EvenOdd (полигон + addRect всех вырезов) — залить GroutC.
4. `clipPath(roomPath)`: все плитки из layout.tiles:
   без фото — Path из 4 углов, цвет = tileColor с разнотоном (см. 7.5);
   с фото — withTransform: H-плитка → translate(q[0]) + rotate(rotationDeg, pivot=Zero),
   drawImage(dstSize=(rc.w·s, rc.h·s)); V-плитка → translate(q[1]) + rotate(rotationDeg+90),
   dstSize=(rc.h·s, rc.w·s). Затем при showCuts: для CUT-плиток контур Warn alpha .9
   (stroke 1.4·density) + диагональ q[0]→q[2].
5. Контур комнаты Acc, stroke 2.5·density, StrokeJoin.Round.
6. Вырезы: прямоугольник Warn, пунктир dashPathEffect([6,5]·density), stroke 2·density.
7. showDims: подписи рёбер через drawIntoCanvas/nativeCanvas — скрывать, если экранное ребро
   <46px; позиция: середина ребра + нормаль наружу (если mid+normal·0.08 внутри полигона —
   инвертировать) на 17·density; фон-«пилюля» drawRoundRect argb(224,9,15,26) высотой ~19·density;
   текст bold ~11·density, формат String.format(Locale.getDefault(), "%.2f")+" "+stringResource(unit_m)
   (строку юнита передать в canvas-функцию параметром).
8. Режим «Комната»: круги «+» на серединах длинных рёбер (r 8·density, фон Panel2 alpha .9,
   обводка Acc2, крестик); вершины — круг r 7·density (выбранная Warn, иначе Acc, белая обводка 2);
   ручка выреза — квадрат 12×12·density в углу (выбранный вырез — Warn).

### 7.5 Прочее

Разнотон: `hash = fract(sin(rc.x*127.1 + rc.y*311.7) * 43758.5453)`; каждый канал RGB
сдвинуть на `((hash−0.5)*20).toInt()` с clamp 0..255.
`layout.overLimit` → баннер с текстом too_many поверх холста, статистика при этом нули.
Чипы запаса: 5 / 10 / 15 %. Строковые ресурсы — ТОЛЬКО существующие ключи;
перед шагами 3–4 открой `app/src/main/res/values/strings.xml` и используй ключи оттуда.

## 8. Шаги

### Шаг 3 — Theme, MainActivity, EditorViewModel, EditorCanvas

Создать 4 файла (пути — §5): `ui/theme/Theme.kt` (цвета §7.1 + `@Composable fun BARemodelTheme(content)`),
`MainActivity.kt` (ComponentActivity, enableEdgeToEdge, setContent { BARemodelTheme { ВРЕМЕННО:
Box(fillMaxSize().background(Bg)) { EditorCanvas(viewModel(), Modifier.fillMaxSize()) } } } —
шаг 4 заменит содержимое на MainScreen()), `ui/editor/EditorViewModel.kt` (весь API §7.2, жесты §7.3),
`ui/editor/EditorCanvas.kt` (§7.3–7.4; сигнатура `@Composable fun EditorCanvas(vm: EditorViewModel,
modifier: Modifier = Modifier)`).
Разрешённые импорты: androidx.compose.foundation.\*, foundation.gestures.\*, foundation.layout.\*,
runtime.\*, ui.\*, ui.geometry.\*, ui.graphics.\*, ui.graphics.drawscope.\*, ui.input.pointer.\*,
ui.layout.onSizeChanged, ui.platform.{LocalDensity, LocalContext}, ui.unit.\*, androidx.lifecycle.\*,
androidx.activity.\*, android.graphics.\* (для nativeCanvas), com.baremodel.core.\*,
com.baremodel.app.data.ProjectMeta.
Проверка: `grep -oh "vm\.[a-zA-Z]*" app/src/main/java/com/baremodel/app/ui/editor/EditorCanvas.kt | sort -u`
— каждое имя должно существовать в EditorViewModel.kt; ни одного нового ключа строк и зависимостей.
Компиляция Compose в контейнере невозможна (нет Android SDK) — финальная сборка в шаге 6.

### Шаг 4 — MainScreen + Panels

`ui/editor/MainScreen.kt`: `@Composable fun MainScreen(vm: EditorViewModel = viewModel())` —
Column(systemBarsPadding): TopBar (лого-бокс 34dp с градиентом Acc→0xFF2A62C8 и текстом «BA»,
заголовок app_name + «β» цветом Acc2, подзаголовок tagline цветом Sub; справа чипы вкладок
tab_editor/tab_report, состояние `var tab by rememberSaveable`); tab==0 → EditorTab, иначе
ReportTab(vm) (файл появится в шаге 5 — на этом шаге вставить заглушку
`@Composable fun ReportTabPlaceholder()` с Text(tab_report) и TODO-комментарием, шаг 5 заменит вызов).
EditorTab: Box(weight 1f) { EditorCanvas(vm, fillMaxSize()); оверлеи: слева-сверху чипы
mode_pattern/mode_room → vm.setRoomMode; справа-сверху чипы 📐 dims (vm.toggleDims) и ✂ cuts_layer
(vm.toggleCuts); справа-снизу кнопка 44dp «⤢» → vm.fit(); снизу-центр пилюля-подсказка
(hint_pattern/hint_room, видна при vm.hintVisible && !layout.overLimit); сверху-центр баннер too_many
при overLimit } + StatsRow (горизонтальный скролл: area → "%.2f "+unit_m2; full_tiles;
cut_tiles цветом Warn; buy: buyCount+pcs и «+reservePct%» цветом Acc2).
`ui/editor/Panels.kt`: `@Composable fun Chip(text, selected=false, warn=false, onClick)`
(RoundedCornerShape 9dp, border 1dp LineC/Acc/Warn, фон прозрачный либо Acc alpha .16 / Warn alpha .14,
текст 12.5sp SemiBold цветом Sub/Acc2/Warn); `@Composable fun NumField(label, value: Double, suffix,
min, max, width=84.dp, onValue)` — OutlinedTextField, локальный текст `remember(value)`,
KeyboardType.Decimal, замена ',' на '.', onValue только при валидном парсе в [min,max];
`@Composable fun PanelHost(vm)` — ряд чипов секций (sec_tile..sec_project, rememberSaveable Int)
+ контент высотой max 270dp c verticalScroll:
• PatternSection: 4 чипа pat_*, Slider поворота 0..90 (+ чипы 0/45/90), чип reset_shift.
• TileSection: пресеты 60×60 / 30×60 / 80×80 / 20×120 / 10×20 (см = ×10 мм) → setTileWidth/Height;
  NumField width/length/grout (unit_mm, 30..2000 / шов 0..30); палитра 10 цветов
  0xFFC7CCD6 0xFF98A1AC 0xFF6C7683 0xFF3A4658 0xFF22304A 0xFFBFA284 0xFF8A6D52 0xFFE7E2D6
  0xFFB7C6BD 0xFF7A8E9C (квадраты 26dp, выбранный с обводкой Acc); чип variation;
  чип «📷 photo/photo_on» → rememberLauncherForActivityResult(PickVisualMedia) → vm.loadTileImage;
  чип clear при tileImage!=null.
• RoomSection: два NumField Ш/Д (unit_m, 1..30, локальный rememberSaveable, старт 4/3) + чип
  rect+apply → vm.applyRect; чип lshape → applyLShape; чип add_cutout; при Selection.Vertex и
  points>3 — чип warn «✕ del_point»; при Selection.Cut — NumField W/H выреза (0.1..10) +
  чип warn «✕ del_cutout».
• CalcSection: строки area/perimeter (polygonPerimeter из core)/full/cut/total; чипы запаса 5/10/15;
  карточка buy (фон Acc alpha .12, рамка Acc): «buyCount pcs ≈ buyM2 m²»; мелкий текст disclaimer (Sub).
• TipsSection: если suggestions==null — текст suggest_note и чип «✨ suggest»; иначе строка
  current (имя текущего узора + total/cuts), карточки топ-3: имя (pat_* [+ « 45°»]), total
  (+дельта к текущему цветом Good/Warn), cuts, чип use → vm.applySuggestion; чип recalc.
• ProjectSection: OutlinedTextField (projectName, placeholder default_name), чип «💾 save» →
  vm.saveProject; список vm.projects (имя + дата DateFormat.getDateInstance) с чипами open /
  warn «✕»; внизу мелко credit (Sub).
Обновить MainActivity: содержимое setContent → `BARemodelTheme { MainScreen() }`.
Проверка: (а) `grep -rhoE "R\.string\.[a-z_0-9]+" app/src/main/java | sort -u` — каждый ключ
есть в res/values/strings.xml; (б) grep vm.\* по MainScreen.kt и Panels.kt — все имена есть в VM;
(в) нет новых зависимостей/иконок.

### Шаг 5 — данные, PDF, вкладка «Отчёт»

`data/ProjectRepository.kt`: `class ProjectRepository(context: Context)`; каталог
`File(context.filesDir, "projects")` (mkdirs); `Json { prettyPrint = true; ignoreUnknownKeys = true }`;
имя файла: `name.replace(Regex("[^\\w\\u0400-\\u04FF -]"), "_") + ".json"`;
`fun list(): List<ProjectMeta>` (десериализация каждого файла, сортировка по savedAt desc),
`fun save(dto: ProjectDto)`, `fun load(name): ProjectDto?`, `fun delete(name)`; всё в runCatching.
`report/PdfReport.kt`: `object PdfReport { fun share(context, name, room: RoomSpec, tile: TileSpec,
pattern: PatternSpec, layout: LayoutResult, reservePct: Int, buyCount: Int, buyM2: Double,
patternLabel: String) }` — PdfDocument, страница 595×842; шапка: маленький тег credit, крупно имя
проекта, дата (DateFormat); секции строками через Paint: params (room_label: площадь "%.2f m²",
периметр; tile_label: WxH unit_mm + grout; layout_label: patternLabel), results (full/cut/total,
жирно buy: buyCount pcs ≈ buyM2 m²), cut_map (строки «a × b cm · N pcs», максимум 28, дальше «…»,
либо no_cuts); футер: disclaimer + credit мелко. Файл → `File(context.cacheDir, "reports")` (mkdirs),
`FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)`,
ACTION_SEND type application/pdf + FLAG_GRANT_READ_URI_PERMISSION, createChooser.
`ui/editor/ReportTab.kt`: `@Composable fun ReportTab(vm)` — вертикальный скролл карточек
(фон Panel, рамка LineC, скругление 13dp): заголовок (BA-Remodel, имя проекта или default_name,
дата), карточка params, карточка results (внутри синяя мини-карточка buy), карточка cut_map
(чипы кусков или no_cuts), кнопка «⬇ share_pdf» (фон Acc) → PdfReport.share(...,
patternLabel = stringResource нужного pat_* (+ «45°» при повороте)).
В EditorViewModel реализовать тела refreshProjects/saveProject/loadProject/deleteProject
(viewModelScope + Dispatchers.IO; loadProject восстанавливает room/tile/pattern/tileColor/
variation/reservePct/projectName; после save/delete — refreshProjects; Toast R.string.saved/
loaded/deleted в главном потоке). В MainScreen заменить заглушку на ReportTab(vm).
Проверка: поля ProjectDto ↔ присваивания в VM симметричны; никаких новых зависимостей;
manifest и res/xml/file_paths.xml уже готовы — не трогать.

### Шаг 6 — финализация

Если в корне нет gradlew/gradle-wrapper.jar — скачать:
`curl -fsSL -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar`
(аналогично `/v8.11.1/gradlew` и `/v8.11.1/gradlew.bat` в корень, затем `chmod +x gradlew`;
если домен недоступен — пропустить: Android Studio создаст wrapper сама).
Написать README.md (по-русски): как открыть (Android Studio Ladybug+, JDK 17), Sync,
`./gradlew :core:test`, `./gradlew :app:assembleDebug`, установка APK; карта модулей vs спека;
что дальше (§11). Прогнать §9 ещё раз. Финальный zip → пользователю + инструкция и чек-лист §10.

## 9. Автономная проверка движка (в контейнере, повторяема в любой сессии)

```
command -v java || (apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless)
[ -d /opt/kotlinc ] || (curl -fsSL -o /tmp/kotlinc.zip \
  https://github.com/JetBrains/kotlin/releases/download/v2.1.0/kotlin-compiler-2.1.0.zip \
  && unzip -q /tmp/kotlinc.zip -d /opt)
V=/tmp/verify; rm -rf $V; mkdir -p $V
cp /home/claude/BA-Remodel/core/src/main/kotlin/com/baremodel/core/*.kt $V/
cp /home/claude/BA-Remodel/tools/verify/Verify.kt $V/
sed -i '/kotlinx\.serialization/d; s/@Serializable//g' $V/*.kt   # в контейнере нет maven central
cd $V && /opt/kotlinc/bin/kotlinc *.kt -include-runtime -d verify.jar
java -jar verify.jar || java -cp verify.jar VerifyKt
```
Ожидаемо: 21 строка `PASS …` и в конце `ALL CHECKS PASSED` (код возврата 0).

## 10. Ручной чек-лист на устройстве (после сборки в шаге 6)

1. Запуск: комната 4×3 м, плитка 600×600, счётчики площади/плиток > 0.
2. Один палец внутри комнаты — узор сдвигается, счётчики пересчитываются.
3. Два пальца — зум к точке между пальцами; кнопка ⤢ вписывает план.
4. Режим «Комната»: угол тянется со снапом, подписи размеров обновляются; «+» на стене
   добавляет угол; выбранная лишняя точка удаляется.
5. Вырез: добавить, перенести, растянуть за угловую ручку, задать точные размеры, удалить.
6. Ёлочка + поворот 45°: визуально без дыр и перекрытий, подрезка подсвечена.
7. Фото плитки из галереи ложится текстурой по узору; «Убрать» возвращает цвет.
8. Советы: 3 варианта с дельтами расхода/подрезки; «Выбрать» применяет узор.
9. Проект: сохранить → перезапустить приложение → открыть — всё восстановилось.
10. Отчёт: PDF открывается шарингом; внутри параметры, материалы, карта подрезки и строка credit.

## 11. После MVP (Фаза 1 — отдельные сессии, не в рамках шагов 1–6)

Модуль `:ar` (ARCore: обмер комнаты и проекция раскладки), импорт/экспорт DXF/SVG/PDF-план,
мультирум, вынос расчёта с main-потока (snapshotFlow + Dispatchers.Default + debounce),
шрифт Inter (Google Fonts), модуль `:assistant` (советы по подготовке основания и укладке).


---

# ФАЗА 2 — большое приложение (шаги 8–13)

Решение пользователя: всё должно быть в одном Android-приложении, а не в вебе.
Каждый шаг = одна сессия = новый APK через GitHub Actions (репозиторий пользователя,
загрузка zip через веб-интерфейс, workflow сам распаковывает и собирает).

Демонстрационные макеты механики (HTML, у пользователя на руках, эталон поведения):
BA-Remodel-Design.html (новый интерфейс), BA-Remodel-Decor.html (центровка декора,
подрезка по стенам, мебель), BA-Remodel-TileEditor.html (фото плитки + область рисунка).

## Готово в :core (шаг 7) — использовать, не переписывать

- `Surfaces.kt` — ISurface (FLOOR/WALL/CEILING), Finish (TILE/WALLPAPER/PAINT/NONE),
  `RoomModel.fromFloor(points, heightM)` → пол + стены по рёбрам + потолок;
  `surface.areaM2()`, `surface.buildLayout(tile, pattern)` — один движок для всех поверхностей.
- `Decor.kt` — `ArtRect` (область рисунка в долях плитки), `DecorSpec(mode, everyN,
  panelCols, panelRows, art)`, `DecorMode{NONE,SINGLE,PANEL,EVERY_N,ALL}`,
  `AnchorMode{ART_CENTER,TILE_CENTER,CORNER,FREE}`, `Aligner.applyAnchor(...)` →
  PatternSpec со смещением, `DecorPlanner.select(...)` → индексы декор-плиток
  (никогда не на подрезке), `DecorPlanner.artBounds(...)` → габарит рисунка в мире.
- `CutReport.kt` — `CutAnalyzer.analyze(room, tile, layout)` → `EdgeCut` по каждому ребру
  (min/max ширина полосы, число плиток), симметрия по осям, `LayoutWarning`
  (THIN_STRIP < 6 см, TAPERED_STRIP, ASYMMETRIC).
- `Furniture.kt` — IPlacer, `Furniture(x,y,w,h,heightM,coversFinish)`,
  `CoverageAnalyzer.analyze(...)` → % перекрытия декора, плиток под мебелью, экономия.

## Шаг 8 — новый визуальный слой — ВЫПОЛНЕН

Сделано: Theme.kt v2 (палитра макета, Shapes 9/11/16/22/26, Typography), Icons.kt —
16 собственных ImageVector штрихом 1.7 по сетке 24 (эмодзи убраны полностью, новых
зависимостей нет), MainScreen.kt переписан: шапка с лого-градиентом и BETA, иконки-
переключатели слоёв, сегментированный переключатель режима с градиентом, FAB «вписать»,
подсказка и баннер лимита через AnimatedVisibility, карточки статистики, нижняя панель
со скруглением и «ручкой», NavigationBar (Редактор | Отчёт | Pro), Crossfade между
вкладками. Panels.kt: карточки узоров с миниатюрами (рисуются в Canvas), IconChip.
ProScreen.kt: экран Pro + объект `Entitlements` (isPro/showAds/tileEditor/surfaces/
furniture/brandedPdf) — единая точка проверки доступа, к которой позже подключится биллинг.
Проверки: обращения vm.* существуют, ключи строк есть в EN и RU, зависимости не менялись,
структура 12 файлов валидна.

## Шаг 8 — исходные требования (эталон: BA-Remodel-Design.html)

Theme v2 (Material 3 dark, палитра из макета: bg #07090D, surface #0E1117/#141922,
accent #5B92FF, warm #FFB454, ok #48D597, радиусы 13/18/26), шрифт Inter через
`androidx.compose.ui:ui-text-google-fonts` (добавить в каталог версий),
векторные иконки вместо эмодзи (`material-icons-core` + собственные ImageVector для узоров),
`NavigationBar` (Редактор | Отчёт | позже Проекты/AR), `SingleChoiceSegmentedButtonRow`
для режима, нижняя панель как sheet со скруглением и «ручкой», карточки статистики
с крупными цифрами, FAB «вписать», анимации `animateFloatAsState`/`AnimatedVisibility`,
haptics при перетаскивании углов. Проверка: сборка + визуальное сравнение с макетом.

## Шаг 9 — редактор плитки и декор — ВЫПОЛНЕН

Сделано: в EditorViewModel добавлены `decor: DecorSpec`, `anchor: AnchorMode`, `decorImage`,
производные `decorIdx` и `cutReport`, метод `reanchor()` (вызывается при смене плитки, узора,
формы комнаты, области рисунка и точки отсчёта); ручной сдвиг узора пальцем переводит привязку
в FREE. EditorCanvas рисует декор-плитки отдельной текстурой/цветом и оси привязки пунктиром.
Panels: новая секция «Декор» — редактор области рисунка поверх фото (перетаскивание рамки,
угловая ручка, перекрестие центра), режимы декора (нет/один/панно/каждая 3-я/вся комната),
точка отсчёта (по рисунку/по плитке/от угла/свободно); в разделе «Расчёт» — подрезка по каждой
стене, симметрия и предупреждения из CutAnalyzer. ProjectDto хранит decor и anchor.
Проверки: типы ядра на месте, обращения vm.* существуют, ключи строк в EN и RU, скобки сходятся,
движок пере-проверен (ALL CHECKS PASSED + ALL V2 CHECKS PASSED).

## Шаг 9 — исходные требования (эталон: BA-Remodel-TileEditor.html)

Экран плитки: фото из галереи, рамка области рисунка (перетаскивание + угловая ручка),
размеры в мм, тип плитки (фон/декор/бордюр). Панель декора: режим (один/панно/каждая N/вся),
точка отсчёта (по рисунку/по плитке/от угла). Canvas рисует декор-плитки фото-текстурой,
центрирует по `Aligner`, показывает предупреждения из `CutAnalyzer`.
Хранение: расширить ProjectDto полями decor/art/anchor/tileImage (файл в filesDir).

## Шаг 10 — поверхности: стены и потолок

Переключатель поверхности (Пол · Стены 1–N · Потолок), развёртка стены как отдельный
холст с проёмами (окно/дверь как Cutout), выбор отделки: плитка / обои / краска;
для обоев — расчёт рулонов (ширина, раппорт), для краски — литры по слоям.
Итог по всей комнате: сводка материалов по каждой поверхности.

## Шаг 11 — мебель и техника

Каталог пресетов (ванна, унитаз, тумба, душ, кухня, шкаф) + произвольный прямоугольник,
перетаскивание и поворот на плане, высота объекта, флаг «класть покрытие под объектом».
Показ перекрытия декора (`CoverageAnalyzer`) и экономии плитки под ванной/кухней.

## Шаг 12 — смета в деньгах и брендирование

Цены: плитка за м²/шт, клей, крестики, работа за м². Итоговая стоимость по поверхностям.
PDF v2: логотип и контакты мастера, разделы по поверхностям, карта подрезки, смета,
подпись «Inspired by Alexander Baziulkin» переключателем в настройках.

## Шаг 13 — 3D и AR (Future из ТЗ)

Простой 3D-просмотр комнаты по RoomModel (пол+стены+потолок с текстурами раскладки),
затем модуль `:ar` на ARCore. Требует новых зависимостей — выносится отдельной сессией.

## Порядок и правила

Один шаг = одна сессия. Перед шагом: распаковать zip, прочитать §7 контрактов и раздел шага.
После шага: прогнать §9 и проверки шага 7 (`java -cp v2.jar VerifyV2Kt`), обновить статус,
собрать zip, отдать пользователю. Пользователь загружает zip в GitHub → Actions → APK.
