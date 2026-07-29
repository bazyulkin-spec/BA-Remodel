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

## Шаг 10 — поверхности: стены и потолок — ВЫПОЛНЕН

Ядро: `MaterialCalc` — рулоны обоев (полосы, раппорт, стандарт 10.05 × 0.53 м), литры краски
(8 м²/л, число слоёв), клей для плитки по размеру (3.5–7 кг/м²). Проверено отдельным прогоном:
обои 4.0 × 2.7 → 3 рулона, краска 10.8 м² в 2 слоя → 2.7 л, клей на 8.84 м² плитки 600 → 48.6 кг.

ViewModel: `model` (RoomModel из контура и высоты стен), `finishes` по поверхностям,
`openings` (проёмы в координатах развёртки стены), `activeSurface`, методы `finishOf`,
`setFinish`, `addOpening`, `deleteOpening`, `surfaceAreaM2` (за вычетом проёмов),
`surfaceLayout` (раскладка для стены или потолка тем же движком).

Интерфейс: секция «Поверхности» — выбор пола, каждой стены и потолка, отделка
(плитка / обои / краска / без отделки), чистая площадь, материалы по типу отделки,
добавление окна 1.4 × 1.4 и двери 0.9 × 2.05, список проёмов с удалением, итог по комнате.

3D: стены окрашиваются по типу отделки, на стенах с плиткой рисуется настоящая раскладка
(до 700 плиток на стену, расчёт кэшируется через remember и не пересчитывается каждый кадр),
проёмы показаны тёмными прямоугольниками с подсветкой контура.

## Шаг 10 — исходные требования

Переключатель поверхности (Пол · Стены 1–N · Потолок), развёртка стены как отдельный
холст с проёмами (окно/дверь как Cutout), выбор отделки: плитка / обои / краска;
для обоев — расчёт рулонов (ширина, раппорт), для краски — литры по слоям.
Итог по всей комнате: сводка материалов по каждой поверхности.

## Шаг 11 — мебель и техника — ВЫПОЛНЕН (базовый уровень)

Сделано: `furniture: List<Furniture>` во ViewModel, `Selection.Furn`, перетаскивание и
растягивание объекта на плане (жесты между вырезами и панорамированием), пресеты кухни,
ванны, унитаза, стиральной машины, тумбы и холодильника, поворот объекта на 90°, флаг
«плитка под мебелью», удаление. На холсте объекты рисуются пунктиром с подписью, при снятом
флаге — перечёркнуты. Производный `coverage` (CoverageAnalyzer) показывает процент
перекрытия декора, число плиток под мебелью и экономию. Осталось на будущее: высота объекта
в 3D, вращение на произвольный угол, каталог с изображениями.

## Шаг В1 — честный вид сверху с мебелью — ВЫПОЛНЕН

Мебель на плане больше не закрашивает плитку: заливка полупрозрачная (0x66070E1A), под ней
читается раскладка, добавлена тень со смещением для объёма. Часть декора, попадающая под
объект, подсвечивается янтарной заливкой через `clipRect` по габариту мебели — сразу видно,
что именно будет скрыто. У выбранного объекта на холсте подписаны размеры. В шапке третий
переключатель слоёв (иконка мебели) — `vm.showFurniture` / `toggleFurniture()`, позволяет
сравнить пол с мебелью и без неё.

Следующий уровень визуализации — шаг 13 (3D): комната строится из RoomModel (пол, стены,
потолок), мебель — коробки по высоте `heightM`, камера вращается пальцем.

## Шаг Т — пробный период, обрезки, донат — ВЫПОЛНЕН

- `data/TrialManager.kt`: 30 дней полного доступа с первого запуска. Дата дублируется в
  SharedPreferences (`ba_trial`) и в файле `trial.dat`, берётся самая ранняя из известных.
  Оба источника плюс папка `projects/` включены в `res/xml/backup_rules.xml` и
  `res/xml/data_extraction_rules.xml`, манифест на них ссылается — при включённой облачной
  копии Google переустановка не сбрасывает отсчёт и не теряет проекты. Защита слабая
  (копию можно отключить, данные очистить) и является временной: настоящий триал
  переносится в подписку Google Play, где он привязан к аккаунту, а не к установке.
  `Entitlements.init(context)` вызывается в MainActivity; `active = isPro || trialDaysLeft > 0`,
  `showAds` инвертирован от доступа. Экран Pro показывает остаток дней или «период закончился».
- Секция «Обрезки»: годные остатки (обе стороны ≥ 10 см) и отход раздельно, площадь обрезков
  в м², список размеров с цветовой отметкой пригодности.
- Секция «Мебель» (см. шаг 11) и чип «Повернуть плитку» на 90° в секции «Плитка».
- Карточка доната на экране Pro: PayPal по адресу автора и кнопка «Написать автору» (mailto).

## Шаг 11 — исходные требования

Каталог пресетов (ванна, унитаз, тумба, душ, кухня, шкаф) + произвольный прямоугольник,
перетаскивание и поворот на плане, высота объекта, флаг «класть покрытие под объектом».
Показ перекрытия декора (`CoverageAnalyzer`) и экономии плитки под ванной/кухней.

## Шаг Р — релиз в Google Play — ПОДГОТОВЛЕН

Сделано: в app/build.gradle.kts релизная подпись из `upload-keystore.jks` (v1+v2+v3),
`versionCode`/`versionName` из переменных окружения; workflow `keystore.yml` (одноразовое
создание ключа, пароли из секретов, ключ отдаётся артефактом на сутки) и `release.yml`
(восстановление ключа из KEYSTORE_BASE64, сборка `:app:bundleRelease` и `:app:assembleRelease`,
артефакты AAB и APK, ключ удаляется после сборки). Папка `play/`: иконка 512×512,
баннер 1024×500, тексты страницы RU и EN, политика конфиденциальности (её копия в `docs/`
для GitHub Pages), CHECKLIST.md — пошаговая инструкция по консоли и закрытому тестированию.
Секреты, которые заводит пользователь: KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS,
затем KEYSTORE_BASE64. Ключ подписи и пароли модель не видит и не хранит.

Важно для планирования: личный аккаунт разработчика, созданный после 13.11.2023, обязан
провести закрытое тестирование (12 участников, 14 дней непрерывно) до публикации;
аккаунт на юрлицо освобождён. Поэтому тест запускается параллельно с разработкой шагов 10–13.

## Шаг 12 — смета в деньгах и логотип мастера — ВЫПОЛНЕН

Данные: @Serializable `Prices` в ProjectDto.kt (tileM2, adhesiveKg, roll, paintL,
workTileM2, workWallM2, workPaintM2, currency, по умолчанию «₪»); ProjectDto дополнен
furniture / finishes / openings / wallHeightM / prices — проект теперь сохраняется целиком,
старые JSON читаются за счёт значений по умолчанию.

VM: `prices` + `updatePrices { it.copy(...) }` (имя без set- из-за clash-правила),
`surfaceCosts(): List<SurfaceCost>` — материалы и работа по каждой отделанной поверхности:
плитка = штуки с запасом × площадь плитки × цена + клей(кг) × цена; обои = рулоны × цена;
краска = литры(2 слоя) × цена; работа = площадь × тариф по типу отделки. Логотип:
`masterLogo` (ImageBitmap), загрузка из галереи с копией в filesDir/logo.png
(восстанавливается в init, добавлен в backup-правила), `clearMasterLogo()`.

UI: секция «Смета» — валюта чипами ₪/$/€/₽, семь полей цен в прокручиваемых рядах,
строки стоимости по поверхностям, итоги «Материалы / Работа / Итого»; нулевые цены в смету
не входят (подсказка prices_hint). На вкладке отчёта карточка «Ваш логотип»: предпросмотр,
загрузка, удаление; помощники money/surfaceTitle/finishTitle в Panels.kt сделаны публичными
и используются обеими вкладками.

PDF: новые параметры logo / estimate / estimateTotal. Логотип вписывается в слот
430,34–555,74 по аспекту при Entitlements.brandedPdf (триал считается), иначе — прежняя
рамка-заглушка; блок «Смета» перед футером: значения по правому краю (Paint.Align.RIGHT),
итог жирным, защита от переполнения строк (y > 742 — стоп); после карты подрезки курсор
синхронизирован (`y = rowY`). Проверки tools/check.sh пройдены.

## Шаг 12 — исходные требования (смета в деньгах и брендирование)

Цены: плитка за м²/шт, клей, крестики, работа за м². Итоговая стоимость по поверхностям.
PDF v2: логотип и контакты мастера, разделы по поверхностям, карта подрезки, смета,
подпись «Inspired by Alexander Baziulkin» переключателем в настройках.

## Шаг 13 — 3D — ВЫПОЛНЕН (собственный рендер, без зависимостей)

`ui/editor/View3DScreen.kt` — программный рендер на Compose Canvas:
орбитальная камера (палец — поворот, два пальца — приближение), перспективная проекция,
сортировка граней по глубине (алгоритм художника). Комната строится из тех же данных, что и
план: основание пола, каждая плитка отдельной гранью с разнотоном и подсветкой подрезки,
декор-плитки светлее, стены по рёбрам контура высотой `vm.wallHeightM`, мебель — коробки по
`heightM` с затенением граней. Ближние стены отсекаются по внешней нормали относительно
камеры, поэтому комната видна изнутри. Ограничение MAX_TILES_3D = 1400 граней пола, дальше
показывается предупреждение. Вкладка «3D» добавлена в нижнюю навигацию (4 пункта),
высота стен переключается чипами 2.4 / 2.7 / 3.0 м.

Улучшения после первого просмотра на устройстве: камера сама подбирает дистанцию под размер
комнаты (`fitSpan`), фон — вертикальный градиент, между плитками рисуется шов, под мебелью
мягкая тень, фото плитки и декора натягивается на грани через `drawBitmapMesh` (до 420 плиток,
дальше только цвет). Ограничение граней пола MAX_TILES_3D = 1400.

Осталось из ТЗ по этой теме: модуль `:ar` на ARCore (отдельная сессия, новые зависимости),
текстуры фото плитки в 3D (сейчас цвет + разнотон), тени и освещение.

## Шаг 13 — исходные требования: AR (Future из ТЗ)

Простой 3D-просмотр комнаты по RoomModel (пол+стены+потолок с текстурами раскладки),
затем модуль `:ar` на ARCore. Требует новых зависимостей — выносится отдельной сессией.

## Совместимость установки (решено 22.07.2026)

Первая сборка ставилась не везде: APK был подписан только схемой v2 и требовал Android 8.0.
MainActivity зафиксирована в портрете (`android:screenOrientation="portrait"`): вертикальная
колонка «план + шторка» в альбомной ориентации телефона сжимала план до полоски. На больших
экранах (600dp+) Android 16+ игнорирует блокировку сам, но там высоты хватает и в альбомной.

Исправлено в app/build.gradle.kts: `minSdk = 24` и `signingConfigs.debug` с
`enableV1Signing/V2/V3 = true`. Добавлены растровые иконки mipmap-mdpi…xxxhdpi
(для API < 26, где адаптивная иконка не работает). Менять эти настройки обратно нельзя.

## Шаг Д — доводка оформления и инструментов — ВЫПОЛНЕН

- **Шрифт Inter** лежит в `res/font/` (regular, semibold, bold, ~1.3 МБ) и подключён в Typography —
  тот же, что в макете; интерфейс перестал выглядеть «системным».
- **Отмена и повтор**: во ViewModel история из 40 снимков (комната, плитка, узор, декор,
  мебель, отделка, проёмы). Точки фиксации — начало изменяющего жеста и все скачкообразные
  действия. Кнопки поверх холста справа сверху, неактивные приглушены анимацией.
- **Движение**: чипы меняют цвет плавно (animateColorAsState), секции панели переключаются
  через Crossfade, подсказки и баннеры — через Fade.
- **Объём**: у плиток появилась фаска (светлая грань сверху-слева, тень снизу-справа,
  включается при ≤700 плитках), шапка с мягким градиентом, у нижней панели тонкий кант.

## Шаг ВЗ — водяной знак бесплатной версии — ВЫПОЛНЕН

Идея: знак не мешает работе, а занимает место, куда мастер захочет поставить свой логотип —
это мотивирует оплату лучше запретов. `Entitlements.watermark = !isPro`: знак виден и в
пробный период, убирается только оплатой. На плане — мелкая подпись BA-Remodel в углу
(прозрачность ~21%) с синей полоской-акцентом; в 3D — такая же подпись слева внизу.
В PDF: бледная диагональ «BA-Remodel» по листу (alpha 16, шаг 170, поворот −28°),
рамка-заглушка «Здесь будет ваш логотип — в Pro» в правом верхнем углу (430,34–555,74),
в футере приписка «Бесплатная версия». `PdfReport.share` получил параметр `watermark`,
ReportTab передаёт `Entitlements.watermark` и показывает плашку-подсказку над отчётом.
В списке Pro добавлен пункт pro_f6 «Без водяного знака, ваш логотип в отчёте».
Проверки tools/check.sh пройдены.

## Шаг Ф — финальный дизайн-проход — ВЫПОЛНЕН

- Нижняя навигация переписана с Material3 NavigationBar на собственную тонкую панель:
  иконка в анимированной пилюле (animateColorAsState для фона/иконки/подписи), высота меньше,
  выглядит как в эталонном макете. NavigationBar-импорты удалены.
- Сцена редактора: поверх фона мягкое радиальное свечение акцентного цвета сверху
  (alpha 0.075) — план перестал быть плоской заливкой.
- Статистика: цифры «Целых / Подрезка / К покупке» перекатываются через animateIntAsState;
  карточка «К покупке» получила градиент Acc 0.30→0.10 вместо ровной заливки.
- Глубина: тень у FAB (12dp, цвет AccDeep) и у кнопок отмены/повтора (6dp).
- versionName по умолчанию поднят до 0.9.0 (CI по-прежнему может переопределить VERSION_NAME).
- tools/check.sh — ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ; движок: ALL CHECKS PASSED + ALL V2 CHECKS PASSED.

## Шаг П — примерка по фото (перспективное наложение) — ВЫПОЛНЕН

Пятая вкладка «Примерка» (иконка камеры): пользователь загружает фото пола, совмещает четыре
угловые ручки с углами пола, и текущая раскладка натягивается на снимок с перспективой.
Реализация: `PhotoFitScreen.kt`, гомография «square→quad» (Хекберт) с аффинной веткой для
параллелограммов — проверена автономно kotlinc (углы сходятся до 1e-5, прямые остаются
прямыми, «ГОМОГРАФИЯ КОРРЕКТНА»). Мир нормируется по габариту комнаты в единичный квадрат,
плитки рисуются через ту же карту: фото-текстуры drawBitmapMesh (≤420 плиток), иначе цвет
с разнотоном; декор — своей текстурой; подрезка обводится янтарным при включённом слое;
контур пола акцентом; швы полупрозрачные. Прозрачность наложения 50/80/100 %, углы
перетаскиваются (захват в радиусе 42 dp), сброс углов, фото живёт в рамках сеанса
(fitPhoto/fitQuad/fitAlpha во ViewModel; сеттер назван updateFitAlpha из-за clash-правила).

Настоящий ARCore сознательно отложен: у пользователя нет устройства с ARCore (BlueStacks
не даёт ни камеры, ни датчиков), проверить сборку и поведение невозможно. Когда появится
телефон — отдельная сессия: модуль `:ar`, зависимости Google, якорение к полу.

## Шаг AR — живой ARCore — РЕАЛИЗОВАН, ждёт проверки на реальном устройстве

Зависимость `com.google.ar:core:1.45.0` (если CI не найдёт версию — поднять до ближайшей
существующей 1.46+). Манифест: разрешение CAMERA (первое в приложении), features
camera/camera.ar с required=false, meta `com.google.ar.core = optional` — приложение
по-прежнему ставится на любые устройства, AR просто недоступен там, где нет ARCore.

`ar/ArScene.kt`: ArBridge (bitmap пола + габариты в метрах) и `renderFloorBitmap` —
офскрин-отрисовка текущей раскладки в прозрачный Bitmap ≤2048 px (клип по контуру комнаты,
фото-текстуры drawBitmapMesh ≤420 плиток, иначе цвет с разнотоном, швы, декор, подрезка).

`ar/ArActivity.kt`: классическая Activity + GLSurfaceView (ES 2.0), собственные шейдеры.
Фон камеры через samplerExternalOES + `frame.transformCoordinates2d(NDC→TEXTURE)`;
плоскости HORIZONTAL, тап → `frame.hitTest`, якорь `hit.createAnchor()` (повторный тап
переносит, «Сброс» очищает); пол — textured quad `widthM × heightM` в якоре, т.е. в
РЕАЛЬНОМ масштабе 1:1; blend premultiplied (ONE / ONE_MINUS_SRC_ALPHA).
Диагностика для удалённого теста: верхняя строка показывает состояние
(проверка / установка сервисов AR / не поддерживается / нет камеры / ищу поверхность /
коснитесь пола / готово), любое исключение выводится как «Ошибка AR: Класс: сообщение» —
это заменяет logcat при медленной обратной связи. Кнопки Сброс и Закрыть.

VM: `openAr(context)` — рендер картинки в Default-диспетчере, заполнение ArBridge, запуск
активности. Кнопка «AR через камеру» на вкладке «Примерка» + обязательный по условиям Google
текст об использовании Google Play Services for AR (строка ar_disclosure) там же.
Политика конфиденциальности дополнена разделом про камеру и ARCore (play/ + docs/).
При публикации в Play обновить форму Data safety (камера).

## Шаг О — доводка по отзыву с реального телефона — ВЫПОЛНЕН

Пользователь тестировал APK на телефоне (не только BlueStacks). Исправлено:
- **Касания.** Пороги захвата ручек (вершины, «+» на стене, углы вырезов и мебели) были в
  «сырых» пикселях (18–22f) — на плотных экранах это 6–8 dp, пальцем не попасть. Теперь
  во ViewModel есть `uiScale` (плотность из EditorCanvas через `updateUiScale`), пороги
  22–26 dp. Чипы: паддинги 14×11 (были 10×7), переключатели слоёв 42dp (были 37),
  отмена/повтор 44dp, сегмент режима выше.
- **Шторка сворачивается.** Тап по «ручке» прячет статистику и панель настроек — план на
  почти весь экран; в свёрнутом виде ручка подсвечена акцентом. Состояние в rememberSaveable.
- **Действия над выделенным из любого места.** Поверх плана (TopCenter) плавающая панель:
  для мебели — «90°» и удалить, для вершины/выреза — удалить. Не нужно искать нужную секцию.
- **Мебель обрела формы.** В core `Furniture.kind` (box/bath/wc/washer/fridge/kitchen/cabinet),
  пресеты задают kind и реальную высоту (ванна 0.6, холодильник 1.85 и т.д. — 3D сразу
  использует). На плане силуэты: ванна — скруглённая чаша, унитаз — овал с бачком,
  стиралка — люк-круг, кухня — мойка и конфорки, шкаф/холодильник — линии дверей.
- **3D.** Основание пола светлее (0xFF465065 — плитки не «плавают» на чёрном), тень мебели
  мягче (alpha 0x2E), добавлена подсказка hint_3d: «стены и мебель меняются в Редакторе —
  здесь только просмотр» (ответ на вопрос пользователя: 3D — вьюер, редактирование в Editor).
- **Дубль ярлыка.** EN sec_estimate был «Estimate» как и sec_calc → теперь «Costs».
- **Водяной знак** добавлен на экран «Примерка» (нижний левый угол).
- **Донат-модель 50 ₪.** Экран Pro упрощён: тарифы/«Скоро»/«Восстановить» убраны. DonateCard:
  оффер «50 ₪ → знаки исчезнут», благодарность, PayPal · 50 ₪, «Написать автору»,
  «У меня есть код» → диалог (почта + код). `Entitlements`: isPro теперь mutableStateOf
  (интерфейс реагирует сразу), персист в SharedPreferences `ba_pro`, `expectedCode(email)` —
  djb-подобный хеш (mod 1e6+3, затем mod 36^4, base36, 4 символа, префикс BA-),
  `activate(context,email,code)`. Генератор для автора: `play/code-generator.html`
  (офлайн, тот же алгоритм; паритет Kotlin/JS проверен прогоном — «АЛГОРИТМ КОДОВ ИДЕНТИЧЕН»).
  Код привязан к почте донатера — чужой код с другой почтой не работает.

Осталось из отзыва (следующий заход): вычистить вкладку «Отчёт» от лишнего (короче, увереннее),
двухпанельная раскладка для планшетов.

## Шаг Отчёт-v2 — вкладка «Отчёт» переработана — ВЫПОЛНЕН

По отзыву «уверенный, без лишнего»: главная цифра теперь сверху и крупно — hero-карточка
с именем проекта, датой и блоком «К покупке: N шт ≈ M м²» (25sp, градиент акцента, запас
мелкой строкой). Кнопка «Поделиться PDF» поднята сразу под hero. Три карточки параметров
слиты в одну «Кратко» (помещение, плитка, раскладка, целых/подрезка). Смета показывается
только при заданных ценах (Материалы/Работа/Итого, итог акцентом). Карточка логотипа без
изменений. Карта подрезки сжата: топ-4 размера + строка more_sizes «и ещё %1$d размеров —
в PDF» (строка добавлена EN+RU вместе с summary «Кратко»). Дисклеймер одной мелкой строкой.
PDF не менялся — полный список подрезки остаётся там. Вызов PdfReport.share и вычисления
estRows/estTotal сохранены без изменений.

## Грабля: дубль импорта = ошибка компиляции

Сборка 23.07 упала с «Conflicting import: imported name 'CornerRadius' is ambiguous» —
CornerRadius был импортирован в EditorCanvas.kt дважды (старый импорт уже существовал,
патч добавил второй). Дубли и одноимённые импорты из разных пакетов теперь ловит
tools/check.sh (проверка 5c) до отправки в CI.

## Шаг Ф2 — второй отзыв с телефона — ВЫПОЛНЕН

- **«Пятно» в 3D — найден и исправлен настоящий баг.** Основание пола сортировалось по
  средней глубине вместе со всеми гранями, поэтому дальняя половина плиток закрашивалась
  основанием (то самое тёмное пятно на скриншоте). Теперь у addFace есть forceBack:
  основание рисуется первым всегда (depth = Float.MAX_VALUE).
- **Мебель в 3D — формы, а не ящики.** Хелперы box() и seams(): ванна — борт с утопленной
  чашей, унитаз — чаша + бачок из двух объёмов, стиральная машина — круглый люк на боках,
  холодильник — горизонтальный шов дверей, тумба/шкаф — вертикальные швы, кухня — столешница
  светлее с тёмной мойкой, стул — сиденье + спинка, стол — столешница на четырёх ножках.
- **Новые пресеты**: Шкаф 1.2×0.6 (h 2.1), Стол 1.2×0.8 (h 0.75), Стул 0.45×0.45 (h 0.85) —
  с силуэтами на плане (двери шкафа, столешница с ножками, сиденье со спинкой).
- **Цена за штуку**: Prices.tilePc, поле «Плитка, шт» в смете; если задана — расчёт идёт по
  ней (подсказка price_unit_note). Валюта, как и раньше, чипами ₪/$/€/₽.
- **Обрезки в деньгах**: в смете строки «Обрезки, м²» и «Обрезки, деньги» (площадь отхода ×
  цена за м², выведенная из цены за шт при необходимости); тот же расчёт продублирован в
  «Кратко» отчёта. В hero отчёта под «К покупке» появилась строка бюджета «≈ N ₪».
- **Интерактивность**: чипы, кнопки навигации и отмены/повтора пружинят при нажатии
  (graphicsLayer scale 0.92–0.93 через interactionSource) и дают лёгкий виброотклик
  (LocalHapticFeedback, TextHandleMove). Ряби нет — indication = null.

## Шаг Б — бюджет на главном экране — ВЫПОЛНЕН

Карточка «К покупке» в статистике редактора показывает деньги: если задана цена плитки
(за штуку `tilePc`, иначе за м² × площадь плитки), под количеством выводится
«≈ сумма · +запас%» в выбранной валюте. Считается от точного `vm.buyCount`.
Полная смета, цена за штуку, обрезь в м² и деньгах — секция «Смета» (Costs); формы мебели
в 3D, пресеты стол/стул/шкаф, анимации нажатий — уже были в предыдущем архиве.
«Пятно» в 3D (подложка пола перекрывала дальние плитки из-за сортировки по средней глубине)
закрыто параметром `forceBack` у addFace — подложка рисуется первым слоем.

## Шаг Н — язык и масштаб интерфейса, бейдж BETA — ВЫПОЛНЕН

`data/UiPrefs.kt`: язык ("system"/"ru"/"en") и масштаб (0.9/1.0/1.12) в SharedPreferences
`ba_ui`, Compose-состояние. Язык применяется через `attachBaseContext(UiPrefs.wrap(...))`
в MainActivity и ArActivity + recreate() при смене (VM переживает, проекты целы).
Масштаб — глобально через CompositionLocalProvider(LocalDensity × scale) в setContent:
меняет ВСЁ (кнопки, текст, отступы, холст) мгновенно, без перезапуска. UI: карточка на Pro
под триалом — «Язык: Как в системе / Русский / English» и «Размер интерфейса:
Компактный / Обычный / Крупный». Сеттеры названы updateLang/updateScale (clash-правило).
Бейдж BETA в шапке ломался на две строки на узких экранах — заголовку weight(1f, fill=false)
+ ellipsis, бейджу maxLines=1 + softWrap=false. Проверка 5c поймала дубль импорта
rememberScrollState в ProScreen до CI — вычищен.

## Шаг Ш — шторка и 3D-панель — ВЫПОЛНЕН

Редактор: шторка стала трёхпозиционной — 0 скрыта · 1 половина · 2 полностью
(`sheetState` в rememberSaveable). Тап по ручке открывает/прячет, вертикальное
перетаскивание ручки (detectVerticalDragGestures, порог ±30px) переключает уровни.
В «половине» панель ограничена 172dp (полная — 330dp, animateDpAsState) — видно
одновременно план и настройки, это отвечает на «либо настройка, либо просмотр».
3D: нижняя панель получила такую же ручку (panelOpen, тап прячет — сцена почти на весь
экран), камера по умолчанию и по «Сбросить вид» ближе: dist 1.35 → 1.05.

Следующий крупный шаг по отзыву — режим «Квартира» (несколько комнат точками и стенами
на одном холсте, у каждой своя плитка/раскладка, общий 3D и сводная смета). Это изменение
модели данных (список комнат в проекте) — делается отдельным заходом.

## Шаг К — квартира: несколько комнат — ВЫПОЛНЕН (v1)

Модель: @Serializable `RoomDto` в ProjectDto.kt (name, spec: RoomSpec, tile, pattern,
colorArgb (−1 = по умолчанию), variation, decor, anchor, finishes, openings); в ProjectDto
поля `rooms: List<RoomDto>` и `activeRoom`. Комнаты живут в ОБЩЕЙ системе координат —
позиция комнаты равна координатам её точек, отдельного offset нет.

VM: `rooms`/`activeRoom` (mutableStateOf), рабочие поля класса остаются состоянием АКТИВНОЙ
комнаты — все производные (layout, decorIdx, coverage, cutInfo, model, surfaceCosts),
жесты, панели, отчёт, примерка и AR работают без изменений. `snapshotRoom()`/`applyRoom()`,
`syncActiveRoom()`, `switchRoom(i)` (pushUndo → sync → apply), `addRoom()` (новая 3.0×2.4
справа от габарита квартиры, наследует плитку/узор, свои decor/finishes/openings; fit()),
`deleteActiveRoom()` (≥1 остаётся), `apartmentPieces()` — суммарные штуки с запасом и деньги
по всем комнатам (активная считается из живого layout, остальные через TilingEngine.build).
Snap/undo расширен полями rooms+activeRoom. fit() берёт bbox всей квартиры. В gestureDown:
тап вне активной комнаты, но внутри другой — switchRoom (в обоих режимах). init-посев:
rooms всегда содержит активную. Сохранение: syncActiveRoom() перед dto; загрузка: dto.rooms
не пуст → восстановление списка, иначе legacy-путь (старые проекты открываются).

UI: EditorCanvas — кэш `inactiveLayouts` (remember по rooms/activeRoom), неактивные комнаты
рисуются тускло (плитки alpha 0.26 их цветом, контур, подпись именем по центру; >900 плиток —
только заливка контура); секция «Комната» — чипы комнат + «+ Комната» + «Удалить»;
смета — строка «Итого по квартире · N: X шт ≈ деньги» при rooms>1. 3D: кэш `otherRooms`,
границы/камера по всей квартире, у остальных комнат подложка (forceBack), плитка (≤700),
стены с отсечением по камере; мебель глобальная — рисуется как раньше.

Ограничения v1 (кандидаты на следующий заход): PDF и вкладка «Отчёт» — по активной комнате
(в смете есть сводный итог); высота стен общая; переименование комнат нет (авто «Комната N»).

## Шаг ОР — область рисунка и список мебели — ВЫПОЛНЕН

Область рисунка: в секции «Плитка» чип «Область рисунка» (vm.showArt/toggleArt). На плане
поверх каждой плитки билинейной интерполяцией углов рисуется рамка области `decor.art`
(если рисунок не обведён — условная 0.2/0.2/0.6/0.6, «образ неважно какой»). Целые плитки —
синяя рамка, подрезанные — янтарная толще; всё в клипе контура комнаты, поэтому у стены
рамка обрезается ровно как обрежется рисунок. ≤700 плиток. Путь настройки области как была:
«Плитка → Фото плитки» и «Декор → обвести рисунок рамкой» (ArtAreaEditor).

Мебель: горизонтальный ряд пресетов заменён выпадающим списком — кнопка «Добавить мебель»
открывает DropdownMenu со всеми десятью пресетами вертикально (стол/стул/шкаф теперь видны,
место не занято).

## Грабля: init в середине класса = чёрный экран при старте

Сборка с квартирой падала при запуске (чёрный экран): init-посев `rooms = listOf(snapshotRoom(""))`
стоял в середине EditorViewModel, а `snapshotRoom` читает `finishes`/`openings`, объявленные
НИЖЕ по файлу. Kotlin инициализирует свойства строго сверху вниз, поэтому в момент init их
делегаты ещё null → NPE в конструкторе ViewModel → приложение умирает до первого кадра.
Компилятор это не ловит. Исправление: в классе ровно ОДИН init-блок и он всегда в самом
конце класса (туда же перенесён запуск загрузки логотипа). Проверка 5d в tools/check.sh
теперь ругается, если `init {` стоит выше последнего `by mutableStateOf` в файле.

## Шаг ПМ — перенос комнат, редактор рисунка, пол-картинка в 3D — ВЫПОЛНЕН

- **Перенос комнаты целиком**: Drag.ROOM_MOVE — в режиме «Комната» захват изнутри контура
  (после проверок вершин/«+»/вырезов/мебели, вместо панорамирования) тащит всю комнату:
  точки, вырезы, узор (offsetX/Y едет вместе, декор не съезжает) и мебель, чей центр был
  внутри (movedFurn). На gestureEnd координаты округляются до сантиметров.
- **Редактор рисунка стал заметным**: в секции «Плитка» крупная кнопка «Рисунок на плитке…» —
  диалог с ArtAreaEditor (рамка за углы), кнопкой загрузки фото (переиспользован picker
  секции) и подсказкой; при открытии автоматически включается показ областей на плане.
- **3D: пол переведён с граней-плиток на картинку** через renderFloorBitmap +
  drawBitmapMesh по перспективной сетке 10×8 (activeFloor/otherFloors в remember).
  Клип по контуру внутри картинки ⇒ ПОДРЕЗАННЫЕ ПЛИТКИ БОЛЬШЕ НЕ ВЫЛЕЗАЮТ ЗА СТЕНЫ
  (главная жалоба по скринам). Активная комната — с фото/декором, остальные — цветом,
  alpha 165. Грани плиток пола, основание-forceBack и лимит MAX_TILES_3D с плашкой
  too_many удалены; стены прочих комнат — тем же цветом 0x8E99AB, что и активной.
- **Шапка статистики**: над карточками имя активной комнаты и подсказка
  «активная комната · вся квартира — в «Смете»» (при rooms>1) — ответ на «как считается
  при нескольких комнатах»: карточки считают активную, свод — в Смете.

## Шаг КП — комнаты без наложений, переключение тапом — ВЫПОЛНЕН

Причина «не могу перетаскивать»: переключение на другую комнату срабатывало по НАЖАТИЮ
(gestureDown), любой промах мимо активной срывал жест. Теперь переключение — только по
короткому тапу: pendingSwitch запоминается на down, применяется в gestureEnd при
drag==PAN && !panMoved (panMoved ставится в PAN при сдвиге >3px). Движение из любой точки —
обычные жесты.

Причина «залезаю на комнаты» и «фигни» в 3D: контуры могли пересекаться, стены наложенных
комнат резали чужие полы. Введён запрет: файловые `segsCross` (строгое пересечение отрезков)
и `polygonsOverlap` (рёбра + вершины с миллиметровым сдвигом к центру — касание ВПЛОТНУЮ
и угол-к-углу разрешены, проверено автономным прогоном: «ГЕОМЕТРИЯ ПЕРЕСЕЧЕНИЙ КОРРЕКТНА»).
`overlapsOthers(cand)` в VM. ROOM_MOVE переписан через `shift(ddx,ddy)`: при упоре пробует
оси раздельно — комната СКОЛЬЗИТ вдоль чужой стены и останавливается вплотную (идеально для
примыкания стена-к-стене). Drag.VERTEX тоже не даёт увести вершину на чужую комнату.
Существующие проекты с уже наложенными комнатами: наложение остаётся, пока пользователь
не растащит (движение «наружу» разрешено всегда, т.к. проверяется кандидат).

## Шаг ОБ — большой отзыв с объекта — ВЫПОЛНЕН (первая половина)

Сделано в этом заходе:
- **Избранные размеры плитки**: чип «★ В избранное» в ряду пресетов (повторное нажатие
  убирает), избранные — отдельным рядом над пресетами, до 8 штук, персист в prefs `ba_fav`
  (строка w:h:g через «;», загрузка в init). VM: favTiles/toggleFavTile/applyFavTile.
- **Фото декора**: после загрузки виден чип «✓ Фото принято»; новая загрузка и «Очистить»
  сбрасывают старую рамку области (decor.art → FULL) — прежняя картинка/область не «залипает».
- **Номера подрезанных плиток** на плане (слой «Подрезка», ≤400 плиток, ширина >22dp):
  янтарные цифры по порядку — мастер подписывает нарезанное и сверяет с планом; заодно
  это ответ на «насчитал меньше»: теперь спор проверяется поштучно (просьба прислать скрин
  с номерами, если расхождение останется).
- **Карточки статистики — кнопки**: «С подрезкой» открывает секцию «Обрезки»,
  «К покупке» — «Смету». panelSection поднят во ViewModel (updatePanelSection),
  PanelHost использует его вместо локального rememberSaveable.
- **Точные длины сторон**: у выбранной вершины в плавающей панели чип «Длины сторон…» —
  диалог с двумя полями (до предыдущего/следующего угла, м; запятая допускается);
  applyEdgeLengths двигает соседние углы вдоль текущих направлений, с запретом наложения.
- **Уровень**: подпись длины стены становится ЗЕЛЁНОЙ, когда стена строго
  горизонтальна/вертикальна (допуск 5 мм) — видно, ровно ли поставил.
- **Примерка**: над наложением подписаны габариты комнаты «Ш × Д м» — понятен масштаб.
- **3D**: чип «Низкие стены» (32% высоты, плитка на стенах и проёмы скрываются) —
  внутренние стены не заслоняют обзор квартиры, но видно, где они.

Отложено в план (следующие заходы): пресет «Ниша/дверной проём»; дуги и круглые комнаты;
режим «точки → программа сама замыкает контур» и толщина стен; распознавание рукописного
чертежа с фото (пока: чертёж строится вручную, новый ввод длин это сильно ускоряет).

## Шаг Т2 — черчение по точкам, скругление, ниша — ВЫПОЛНЕН

- **Режим «Нарисовать по точкам»** (кнопка в секции «Комната»): vm.drawMode/drawPts,
  startDraw/cancelDraw/undoDrawPoint; тап ставит точку (addDrawPoint: прилипание к
  горизонтали/вертикали от предыдущей при отклонении <12 см, round2), тап у первой точки
  (<26dp·uiScale) или чип «Замкнуть» вызывает finishDraw: контур становится активной
  комнатой (вырезы очищаются), ≥3 точки, overlap-guard, reanchor+fit. Полилиния на канвасе
  (блок 7c): линия Acc (Warn при пересечении чужой комнаты — drawOverlaps), пунктир к
  первой, длины сегментов, первая точка выделена зелёным. Плавающая панель: Замкнуть /
  Точку назад / Отмена + подсказка. В drawMode прочие жесты отключены (drag = NONE).
- **«Скруглить…»** у выбранной вершины: диалог радиуса (по умолчанию 0.50 м, запятая ок),
  roundSelectedCorner — классический fillet: t=r/tan(θ/2) с клампом по 0.9·мин(рёбер),
  центр по биссектрисе, дуга 6 сегментов, overlap-guard. Круглые формы = многоугольник,
  движок режет подрезку честно.
- **«Ниша»** у выбранной вершины: addNicheAfterSelected — прямоугольная ниша 0.8×0.3 м
  НАРУЖУ по центру ребра после вершины (нормаль наружу через pointInPolygon),
  вставка 4 точек, overlap-guard. Дверной проём с наличником = та же ниша глубиной поменьше.
- Панель выделения обёрнута в horizontalScroll (чипов стало больше).

## Шаг АС — автосохранение черновика — ВЫПОЛНЕН

Репозиторий: `saveAutosave/loadAutosave` → filesDir/projects/__autosave.json (исключён из
списка проектов). VM: `currentDto()` (вынесен из saveProject; включает syncActiveRoom) и
`applyDto()` (вынесен из loadProject) переиспользуются. В конце init: восстановление
черновика при старте, затем фоновая корутина: snapshotFlow по всем полям проекта →
collectLatest → delay(1300) → запись черновика в IO. Чтобы автосейв не зацикливался,
syncActiveRoom больше не перезаписывает rooms, если снимок активной комнаты не изменился
(структурное сравнение RoomDto). В секции «Проект» подсказка autosave_note: черновик сам,
именованные сохранения — для нескольких объектов. Именованные проекты работают как раньше.

## Шаг ПП — план в PDF — ВЫПОЛНЕН

В правой колонке первой страницы (310..555, top 100, где было пусто) — рамка «План»:
раскладка активной комнаты через renderFloorBitmap (швы, подрезка янтарным, фото плитки
и декор, разнотон), подпись габаритов «Ш × Д м». PdfReport.share расширен параметрами
colorArgb/variation/decorIdx/tileBmp/decorBmp (с дефолтами), ReportTab передаёт значения
из VM. Проверена компоновка: бокс до y=294, карта подрезки начинается ниже (~307) —
третья колонка списка не пересекается с планом.

Заметки по скрину пользователя (косметика, в следующий заход):
- дата в шапке PDF на системе с ивритом рендерится вперемешку («24 2026 ביולי») —
  форматировать датой языка приложения (UiPrefs), а не системной локали;
- в «Costs» при единственной строке дублируются Materials и Total — прятать промежуточный
  итог, когда строка одна.
- Проверка арифметики его отчёта вручную: 72×1.1=79.2→80 шт; 80×0.81=64.8 м²; сумма
  площадей кусков из cut list + целые ≈ площадь комнаты (39.9 ≈ 39.36) — отчёт корректен.
  Полоски «90.00 × 0.50 cm» — сигнал неудачной привязки узора, а не ошибка.

AR: подтверждён рабочим на втором телефоне (живая камера + раскладка 1:1). Идея в бэклог:
переключение вариантов плитки прямо в AR-экране (A/B для клиента).

## Правка: подписи и кружки «+» больше не накладываются

По скринам с телефона: кружок «+» (радиус 8dp на середине стены) перекрывал начало подписи
длины («2.31» → «.31»). Подпись отодвинута с 17dp до 30dp по нормали — зазор 12.5dp.
Пороги скрытия для коротких стен были в «сырых» пикселях (46f/56f) — умножены на плотность:
на телефоне мелкие стены больше не порождают кашу из плюсов и цифр; при зуме появляются.
(Та же грабля «пиксели вместо dp», что и с порогами касаний, — при добавлении экранных
констант в канвас всегда умножать на d.)

## Шаг НП — «Новый проект» — ВЫПОЛНЕН

Секция «Проект»: кнопка «Новый проект» с подтверждением («черновик будет заменён —
сохраните с именем, если нужен»). vm.newProject(): pushUndo, сброс к дефолтам (комната
4×3, плитка 600×600/3, узор/декор/мебель/поверхности/проёмы/высота 2.7), при этом
РАСЦЕНКИ (prices) и ЛОГОТИП мастера сохраняются — они принадлежат мастеру, не объекту.
Параллельная работа над объектами: Сохранить с именем → Новый проект → второй объект;
переключение через список в «Проекте». Автосейв (шаг АС) хранит текущий черновик.

## Шаг ВП — выбор плитки, ручной декор, иврит — ВЫПОЛНЕН

- **Выбор плитки тапом** (режим «Узор»): Selection.Tile; на down запоминается tappedTile
  (pointInPolygon по corners), в gestureEnd при PATTERN && !patternMoved — выбор/снятие
  (тап по пустому месту снимает). Подсветка на канвасе (блок 4s). В плавающей панели:
  «600×600 · Подрезка №K» (номер совпадает с нумерацией на плане) + чип «Декор».
- **Ручной декор**: decorOverrides Map<String,Boolean> с ключом «см:см» начала плитки
  (rect.x/y × 100, стабилен к правкам контура; при сдвиге узора метки остаются на месте
  комнаты). decorIdx = авто-подбор + overrides (true — добавить, false — снять авто).
  toggleTileDecor() с pushUndo. Хранится ПО КОМНАТЕ: RoomDto.decorOverrides (+ snapshot/
  apply/Snap-undo/newProject). Ставится и на подрезанные — пользователь сам решает,
  резать декор или прятать. Сценарии: «цветочки по краям» — тапнуть нужные; панно 2×2 —
  отметить 4 соседние.
- **Разные плитки в одной комнате**: рабочий способ сегодня — разбить на комнаты-зоны
  вплотную (у каждой свой размер/цвет/узор, свод в «Итого по квартире»); полноценные
  зоны внутри одного контура — в бэклог.
- **Иврит**: values-iw + values-he (253/253 ключей), чип «עברית» на Pro, supportsRtl=true.
  Термины: אריח/פוגה/פריסה/חיתוך/שאריות/עלויות/המחשה; просить носителя поправки.
- Русского текста в EN-ресурсах скан не нашёл (кириллица в values/ отсутствует) —
  ждём скрин конкретного места от пользователя.

## Грабля: глобальный replace сломал импорт + пропущенный анкер

CI предыдущей сборки: (1) «Unresolved reference Pt» — массовая замена
`com.baremodel.core.Pt → Pt` зацепила и строку импорта, получилось `import Pt`;
правило: замены полных имён делать ТОЛЬКО в теле кода, импорты не трогать; проверка 5c2
теперь ловит `import <Заглавная>` без пакета. (2) «val cannot be reassigned» в Panels —
анкер замены `{ section = i }` не совпал по именованному параметру `selected =`, присвоение
осталось; правило: после anchored-replace с несколькими вхождениями grep-ать остатки
старого паттерна. Ошибка «failed to push some refs» в конце лога — гонка коммитов в его
workflow (пуш во время нового коммита), к коду отношения не имеет, лечится перезапуском job.

## Шаг СВ — связка обрезков, вырезы, перегородки, бренд, область без фото — ВЫПОЛНЕН

- **Вырезы не накладываются** («пустота на пустоту» создавала площадь из-за even-odd):
  cutsOverlap (AABB, касание разрешено), CUT_MOVE/CUT_RESIZE — упор со скольжением по осям,
  addCutout сдвигается вправо до свободного места (8 попыток).
- **Связка список⇄план**: cutPieceOf — размер куска каждой CUT-плитки сэмплингом 13×13
  точек по билинейной сетке углов (derivedStateOf, кэш). Тап по строке в «Обрезках»
  (OffcutRow получил selectedRow/onRowClick) подсвечивает на плане плитки, чей остаток-
  дополнение совпадает (блок 4h, допуск 1 см, обе ориентации); повторный тап снимает.
  Выбор плитки на плане показывает «· ≈55.5×10.0 см» — размер её куска.
- **«см»/«шт» в EN** — были зашиты в OffcutRow; заменены на unit_cm/pcs.
- **Область рисунка без фото**: ArtAreaEditor при отсутствии картинки рисует фон цветом
  плитки, рамка редактируется как обычно; плашка «Загрузите фото» убрана.
- **3D: перегородки полупрозрачные автоматически** — стена, за которой (по внешней нормали,
  +7 см) лежит другая комната, рисуется alpha 0.42 без плитки и проёмов; в обоих циклах
  (активная и остальные). «Низкие стены» остаются как ручной режим.
- **Бренд**: строка wm_brand «BA-Remodel · Baziulkin Alexander» во всех локалях; углы
  плана и примерки подписаны ею; диагональ PDF чередует BA-Remodel / Baziulkin Alexander.
  Код разблокировки уже снимает все навязчивые знаки (гейт — Entitlements.isPro; будущая
  реклама, если появится, вешается на тот же флаг); футер-кредит в PDF остаётся и в Pro.
  Файл логотипа от пользователя — скриншот с запечённой «шахматкой», в ассеты не годится;
  запрошен чистый PNG с прозрачностью, тогда картинка встанет в знаки и шапку PDF.

## Шаг ДВ — доводка: даты, смета, квартира в отчёте, планшет — ВЫПОЛНЕН

- **Дата в отчёте и PDF** бралась из системной локали → на телефоне с ивритом собиралась
  вперемешку. Добавлен `UiPrefs.locale(context)` (system → Locale.getDefault(), иначе
  Locale(код)); ReportTab и PdfReport форматируют дату им.
- **Дубль «Материалы = Итого»**: разбивка Материалы/Работа добавляется только когда обе
  части > 0 (иначе она дословно повторяла итог) — и в списке для PDF, и в секции отчёта.
- **Отчёт и PDF по всей квартире**: `RoomStat` (имя, площадь, к покупке, деньги, формат
  плитки) + `apartmentStats()` во ViewModel (активная комната считается из живого layout,
  остальные через TilingEngine.build); `apartmentPieces()` теперь производная от него.
  В отчёте — карточка «Итого по квартире» со строкой на комнату и общим итогом; в PDF —
  одноимённый раздел между «Материалы» и картой подрезки (только при rooms > 1, обрезается
  по y > 700, чтобы не наехать на футер). Параметры share: apartment/apartmentTotal.
- **Планшетный режим**: EditorTab обёрнут в BoxWithConstraints; при ширине ≥ 720dp —
  Row: план слева (weight 1), справа колонка 360dp со статистикой и PanelHost
  (у PanelHost появился параметр maxContentHeight, на планшете 2000dp — панель занимает
  всю высоту и скроллится). Узкие экраны — прежняя раскладка со шторкой. Содержимое
  холста вынесено в `BoxScope.EditorStage(vm)` и переиспользуется обеими ветками.

Осталось нереализуемым/большим (в план, не сделано):
зоны с разной плиткой внутри ОДНОГО контура (движковая переработка; сейчас — комнаты-зоны
вплотную), толщина стен в 3D, распознавание рукописного чертежа с фото (нужна ML-модель),
переключение вариантов плитки внутри AR (ArActivity не имеет доступа к ViewModel — нужен
предрендер нескольких вариантов), Play Billing и публикация (ждут решения по аккаунту).

## Шаг ПН — панно из нескольких плиток — ВЫПОЛНЕН

Ответ на «розочка из 5 плиток, хочу положить под стену и увидеть, как обрежется».

Модель: панно = одна картинка (фото декора), растянутая на cols×rows плиток.
Размер берётся из `decor.panelCols/panelRows` (те же поля, что у режима PANEL).
Якорь хранится в КООРДИНАТАХ РАСКЛАДКИ (`panelRX/panelRY` = rect.x/rect.y плитки), поэтому
панно едет вместе с узором при сдвиге пальцем и не «отклеивается». Состояние: panelOn/
panelRX/panelRY во ViewModel, в RoomDto (по комнате), в Snap/undo и newProject.
`panelCell(t)` возвращает (столбец, строка) или null: индекс считается по шагу
(ширина+шов)/1000 с допуском 0.3 шага — плитка должна стоять в узле решётки, поэтому
в раскладке «в разбежку» соседний ряд в панно не попадает (проверено автономным прогоном
kotlinc: «ПАННО: РАСКЛАДКА КЛЕТОК КОРРЕКТНА» — 6 плиток для 3×2, клетки уникальны,
полусдвиг и внешние плитки отсеиваются).

Отрисовка одинаковая везде: каждой плитке достаётся своя доля картинки —
в редакторе через drawImage(srcOffset/srcSize) внутри клипа контура (у стены кусок
режется ровно как в жизни), в 3D/AR/PDF через renderFloorBitmap (новый параметр
`panel: PanelInfo(cols, rows, cellOf)`; куски нарезаются Bitmap.createBitmap и кэшируются
на клетку), в Примерке — тем же кэшем нарезки поверх фото.

UI: выбрал плитку на плане → чип «Панно отсюда» (эта плитка становится левым верхним углом)
и «Убрать панно»; в секции «Декор» — размеры 2×2 / 3×2 / 3×3 / 4×3 / 5×1, подсказка и
пресет «Шахматы» (EVERY_N с шагом 2, добавлен setEveryN).

Зоны с РАЗНОЙ плиткой в одном контуре по-прежнему делаются комнатами-зонами вплотную
(упор и скольжение уже позволяют стыковать их идеально); настоящие зоны внутри одного
контура остаются в бэклоге как движковая переработка.

## Шаг ПЧ — план-подложка и толщина стен — ВЫПОЛНЕН

**Подложка (ответ на «сфоткать готовый чертёж, чтобы программа взяла за базу»).**
Полное автораспознавание требует ML-модели, поэтому сделан рабочий максимум без неё:
фото чертежа кладётся ПОД рабочую область и калибруется по одному известному размеру,
после чего контур обводится готовым режимом «Нарисовать по точкам» — и сразу в метрах.
- VM: planImage (в рамках сеанса), planOrigin (мировые коорд. левого верхнего угла),
  planMPerPx (метров на пиксель), planAlpha, planMove, calibMode/calibA/calibB/calibDialog.
  loadPlanImage ставит стартовый масштаб «ширина ≈ 8 м».
- Калибровка: два касания по концам известного размера → диалог «Реальное расстояние, м» →
  applyCalibration(realM): k = real/d0, planMPerPx *= k, подложка масштабируется ВОКРУГ
  первой точки (planOrigin = a + (planOrigin − a)·k), поэтому отмеченный угол не уезжает.
  Проверено автономным прогоном kotlinc: «КАЛИБРОВКА ПОДЛОЖКИ КОРРЕКТНА».
- Жесты: Drag.PLAN_MOVE (чип «Двигать подложку»), в calibMode тап ставит точки.
- Канвас: блок 2b рисует подложку с alpha ПЕРЕД основанием комнаты, блок 6d — точки и
  отрезок калибровки.
- UI в секции «Комната»: «Фото плана» / «Калибровать» / «Двигать подложку» / «Убрать»,
  прозрачность 25/45/70 %, подсказка (в режиме калибровки — своя).

**Толщина стен** (`wallThicknessM`, по умолчанию 0.10 м, в ProjectDto и Snap/undo):
на плане — полоса СНАРУЖИ контура (clipPath(Difference) + Stroke двойной ширины с
Miter-стыками, поэтому углы честные), в 3D — верхняя грань стены по нормали наружу
(стена перестала быть «бумажной»), у перегородок наследуется полупрозрачность.
UI: чипы 5/10/15/20/25 см в секции «Комната».

Осталось из бэклога: зоны с разной плиткой в одном контуре (движковая переработка),
автораспознавание чертежа (ML), переключение вариантов плитки внутри AR, Play Billing.

## Шаг ЗН — зоны, автообводка чертежа, варианты в AR — ВЫПОЛНЕН

**1. Зоны с разной плиткой в ОДНОМ контуре** (главная просьба).
- core: `clipPolygonByRect` (Сазерленд—Ходжман). Проверено автономно: зона внутри,
  зона больше комнаты, зона вне, зона в Г-образной комнате, зона за стеной —
  «ОТСЕЧЕНИЕ МНОГОУГОЛЬНИКА КОРРЕКТНО».
- Модель: `ZoneDto(x, y, w, h, tile, pattern, colorArgb, variation)` в RoomDto.zones.
- Ключевая идея: базовая раскладка строится по комнате, где прямоугольники зон добавлены
  как ВЫРЕЗЫ, а раскладка зоны — по контуру «комната ∩ прямоугольник». Поэтому стык
  режется честно с обеих сторон, без спецкода.
- VM: zones/activeZone, addZone/deleteActiveZone/updateActiveZone, zoneLayouts (derived),
  zoneLayers() для рендера, countsByFormat() — закупка по каждому формату с запасом.
  Пока зона выбрана, сеттеры плитки/узора пишут В ЗОНУ (uiTile/uiPattern/uiColor читает UI).
- Жесты: Drag.ZONE_MOVE (внутри) и ZONE_RESIZE (правый нижний угол), Selection.Zone.
- Канвас: блок 4z рисует плитку зон, 7z — рамки (выбранная акцентом + ручка).
- 3D/AR/PDF: renderFloorBitmap получил `extra: List<ExtraLayer>` (плитки зоны, цвет,
  разнотон) — зоны видны везде.
- UI: секция «Комната» → «Зоны»: чипы зон с форматом, «+ Зона», «Удалить зону», подсказка;
  «Расчёт» → «По форматам плитки» при нескольких форматах.

**2. Автообводка чертежа по фото** (`PlanTracer.kt`, без ML): яркость → порог Оцу →
заливка светлой области от касания (с поиском светлого пикселя, если попал в линию) →
обход границы по соседям Мура → упрощение Рамера—Дугласа—Пекера (eps ≈ 0.6 % кадра) →
отбрасывание коротких рёбер → выравнивание почти горизонтальных/вертикальных стен.
Автономный прогон на синтетических планах: прямоугольник — 4 угла, площадь в пределах 1.3 %,
все рёбра по осям; Г-образная — 6 углов; касание по стене и шум обработаны —
«АВТООБВОДКА ЧЕРТЕЖА КОРРЕКТНА». В приложении: чип «Обвести автоматически» в блоке
подложки, касание внутри помещения → контур в метрах (пересчёт через planOrigin/planMPerPx,
картинка ужимается до 700 px для скорости), overlap-guard, при неудаче — понятный тост.

**3. Варианты плитки в AR**: ArBridge.variants (подпись → картинка пола). openAr рендерит
текущий формат плюс до трёх ИЗБРАННЫХ форматов (в фоне, Dispatchers.Default); на AR-экране
первая кнопка циклически переключает вариант, текстура пола меняется в GL-потоке
(флаг variantPending). Клиенту показываешь «а вот так» не выходя из камеры.

Не делаем: Play Billing (по решению пользователя).

## Шаг OCR — чтение размеров с чертежа — ВЫПОЛНЕН

Зависимость `com.google.mlkit:text-recognition:16.0.1` (на устройстве, офлайн, +~4 МБ APK;
репозиторий google() уже подключён). `ui/editor/PlanOcr.kt`: TextRecognition по подложке,
из каждого элемента вытаскивается число регуляркой `\d{1,5}([.,]\d{1,2})?`, единицы
угадываются по величине (<30 → метры, <1000 → сантиметры, иначе миллиметры), диапазон
0.15…60 м, остальное отбрасывается. Разбор проверен автономно на числах с чертежа
пользователя (555/425/359/4,25/5550/80/R0.4/1:100 и мусор) — «РАЗБОР РАЗМЕРОВ С ЧЕРТЕЖА
КОРРЕКТЕН».

VM: `runPlanOcr()` (картинка ужимается до 1600 px, координаты чисел переводятся в мировые
через planOrigin/planMPerPx), `ocrNumbers`, `ocrBusy`, `ocrNear(p)` — ближайшие числа.
UI: чип «Прочитать размеры» → «Найдено размеров: N»; числа рисуются зелёным поверх
подложки (блок 6o); в диалоге калибровки и в диалоге «Длины сторон…» — ряд чипов
«С чертежа:», тап подставляет значение в поле. Ничего не применяется молча: пользователь
видит, что прочитано, и выбирает сам.

Ограничение честно: ML Kit читает ПЕЧАТНЫЙ текст. На рукописном эскизе (как IMG_7083)
распознавание ненадёжно — для таких чертежей рабочий путь прежний: автообводка контура
по фото + ввод длин цифрами. Для печатных планов (PDF-распечатка, CAD) — читает хорошо.

## Шаг ИС — исправления по отзыву (две панели, обрезки, панно, кисть) — ВЫПОЛНЕН

- **«Два окна» в эмуляторе** — это планшетный режим (ширина ≥720dp), он верный, но план
  оставался мелким в углу: fit() отрабатывал до смены раскладки. Теперь при изменении
  холста больше чем на 25 % (lastFitSize) план вписывается заново.
- **Подсветка обрезков работала через раз** — БАГ: сравнивался «остаток», а список
  показывает ПОЛЕЗНЫЙ кусок. `cutPieceOf` переписан точно как в движке:
  clipPolygonByQuad(комната, плитка) → bbox в осях плитки → округление до полусантиметра;
  подсветка сравнивает кусок с строкой напрямую (допуск 0.26 см). Теперь совпадает всегда,
  и подпись у выбранной плитки («≈30,0×25,5 см») точная.
- **Панно не видно без фото** — теперь плитки панно закрашиваются акцентом и обводятся,
  поэтому смена размера 2×2 / 3×2 / 5×1 видна сразу, даже до загрузки картинки.
- **Своя окраска плиток**: `tileColors` (ключ «см:см» в координатах раскладки, поэтому
  крашеные плитки ЕДУТ ВМЕСТЕ С УЗОРОМ), режим кисти (Drag.PAINT — тап и протяжка красят,
  повторный тап снимает), палитра из 5 цветов + «Стереть», хранение по комнате и в undo.
- **«Плитка» и «Декор» объединены**: DecorSection вызывается в конце TileSection, вкладка
  «Декор» убрана (секций 10 вместо 11), ссылки карточек статистики пересчитаны (6 и 7).
- **Направляющие-уровень**: у выбранной вершины рисуются пунктирные оси, они становятся
  зелёными, когда координата совпала с другой вершиной (±1 см) — видно, ровно ли стоит.
- Плавающая панель выделения ограничена 96 % ширины холста, чтобы не уезжать под панель
  настроек в планшетном режиме (скругление угла — чип «Скруглить…» именно там).

## Правка: общий сброс и цветная палитра кисти

- `resetPlacements()` — «Сбросить всё наставленное»: крашеные плитки, ручной декор, панно,
  зоны, мебель, режим декора → NONE; контур, плитка, цены, подложка и проект НЕ трогаются.
  С pushUndo (можно отменить) и подтверждением. Кнопка в секции «Проект» рядом с
  «Новый проект» (полный сброс — это по-прежнему «Новый проект»).
- Палитра кисти рисовалась чипами с символом «■», из-за чего все квадратики выглядели
  одинаково (цвет чипа, а не краски). Заменена на настоящие цветные квадраты 30dp
  с рамкой-подсветкой выбранного, как в палитре плитки; добавлен терракотовый цвет.

## Правка: панель активных режимов

Внизу слева над планом (BottomStart, отступ справа 74dp — не перекрывает кнопку «вписать»)
появляется полоска чипов со всем, что сейчас включено: кисть, крашеные плитки, панно,
область рисунка, режим декора, выбранная зона, автообводка, калибровка, перенос подложки,
сама подложка, прочитанные размеры, подсветка обрезков. Тап по чипу «✕» выключает именно
это, без захода в секцию. Полоска скрыта, когда ничего не активно; список строится
buildList из состояния VM, добавлены clearOcr() и clearHighlightCut().

## Правка: крашеные плитки в 3D, AR, PDF и примерке

Кисть красила только план: renderFloorBitmap ничего не знал про tileColors, поэтому в 3D,
AR и PDF пол выглядел «не тем». Добавлен параметр `colorOf: ((PlacedTile) -> Int?)?`,
он имеет приоритет над фото/декором/панно; вызовы переданы из openAr, View3DScreen
(ключ remember дополнен vm.tileColors), ReportTab→PdfReport и PhotoFitScreen.
Столешница стола сделана светлее, ножки толще — на тёмном фоне их не было видно.

## Правка: отмена жестов и упрощение контура

- **БАГ: перетаскивания не попадали в отмену.** pushUndo вызывался только у кнопочных
  действий; тащить вершину, вырез, мебель, зону, комнату или узор можно было без снимка,
  и «Отменить» откатывал не то. Добавлен `pushOnce()` (один снимок на жест, флаг
  editSnapPushed сбрасывается в gestureDown) и вызван во всех ветках захвата.
- **«Упростить контур»** (чип в секции «Комната» при >4 точках): сливает точки ближе 4 см
  и убирает лежащие почти на прямой (отклонение <2 см). Проверено автономно: замусоренный
  прямоугольник из 12 точек → 4 точки, площадь 12.00 без изменений; Г-образная комната
  из 8 точек → 6, площадь 9.00 — настоящие углы сохранены. (Первая версия оставляла мусор
  из-за guard `out.size < 3` — убран, теперь откат к слитым точкам только если контур
  выродился.)

## Шаг КР — кисть размера — ВЫПОЛНЕН

Запрос: «как кисть цветом, только чтобы плитка была другого размера». Сделано мостом к
зонам: `formatBrush` + `brushTile`; в режиме кисти протяжка по полу (Drag.FORMAT)
накапливает габарит задетых плиток (brushTouch по corners), а gestureEnd (brushFinish)
создаёт ZoneDto с этим прямоугольником и выбранным форматом, наследуя тип узора комнаты,
и делает зону активной. Дальше зона тянется/меняется как обычно.
UI: в секции «Плитка» под палитрой кисти — чип «Кисть размера» и ряд форматов
(избранные пользователя + 30×30, 30×60, 20×120, 10×10), подсказка; режим виден и
выключается в панели активных режимов.

Про «другую форму»: непрямоугольная плитка (шестигранник, восьмигранник со вставкой) —
это новый тип раскладки в движке, отдельный крупный шаг; в текущей модели плитка всегда
прямоугольная, а «форма» задаётся узором (шов, разбежка, ёлка) и зонами.

## Правка: зоны переехали к кисти размера (убрано дублирование)

Было два места про одно и то же: блок «Зоны» в секции «Комната» (+ Зона / выбор / удалить)
и «Кисть размера» в «Плитке», которая тоже создаёт зоны. Оставлено одно место:
- «Плитка»: палитра кисти → «Кисть размера» + форматы → список зон (выбор, удалить)
  и подсказка, которая теперь описывает реальный порядок: провёл кистью → зона создана,
  выбрал её → настройки плитки сверху применяются к ней.
- «Комната»: остались только геометрия и подложка (контур, черчение по точкам, упрощение,
  толщина стен, прямоугольник/L-форма, вырезы, комнаты квартиры).
Кнопка «+ Зона» и строка add_zone удалены из всех локалей; метод addZone() оставлен в VM
как резервный API.

## Правка: перетаскивание комнат квартиры

Жалоба «Комната 2 не перетягивается, сразу ставится точка» — причина найдена в порядке
разбора касания: сначала проверялись ручки АКТИВНОЙ комнаты («+» на середине ребра,
радиус 22dp), и палец, опущенный над соседней комнатой, попадал в них → в контур
добавлялась точка (отсюда «лесенка» на скрине).

Теперь:
- касание внутри ДРУГОЙ комнаты сразу делает её активной и начинает перенос —
  одним жестом, без предварительного тапа (в режиме «Узор» — панорамирование);
- перед этим проверяется близость к вершине активной комнаты (26dp), чтобы вершину на
  общей стене не «отдать» соседке;
- страховка: если палец над чужой комнатой, ветка «+» пропускается совсем.

## Правка: прозрачные стены в 3D — ручной режим

Автоматическая полупрозрачность срабатывает только для ПЕРЕГОРОДОК (за стеной по внешней
нормали лежит другая комната). У пользователя комнаты стоят порознь, общих стен нет —
поэтому ничего не «гасилось». Добавлен чип «Прозрачные стены» рядом с «Низкие стены»:
все стены (и их верх) рисуются с alpha 0.40, плитка стен и проёмы скрываются, чтобы
не рябило. Альфа работает корректно, потому что грани сортируются от дальних к ближним.

## БАГ: отрицательная площадь из-за выреза — ИСПРАВЛЕН

На скрине пользователя «ПЛОЩАДЬ −5,32 м²»: TilingEngine вычитал ПОЛНУЮ площадь выреза
(`polygonArea(pts) - cutouts.sumOf { it.w * it.h }`), даже когда вырез торчал за стену.
Теперь вычитается только пересечение выреза с контуром:
`polygonArea(clipPolygonByRect(pts, ...))`. Проверено автономно: вырез внутри, наполовину
снаружи, целиком снаружи и огромный (как у пользователя) — «ПЛОЩАДЬ С ВЫРЕЗАМИ КОРРЕКТНА»,
минуса больше нет.
Дополнительно: при перетаскивании вырез не отпускается за пределы комнаты (центр обязан
оставаться внутри контура), иначе он «улетал» и висел снаружи.

## Правка: магнит комнат и номера подрезки в 3D

- **Чёрные щели между комнатами**: комнаты казались состыкованными, но стояли с зазором
  в пару сантиметров, и в 3D между полами оставалась тёмная полоса, а перегородки не
  считались внутренними. Добавлен магнит: `snapOffset()` сравнивает габариты активной
  комнаты с соседними (правый край к левому, левый к правому, плюс выравнивание кромок)
  и при расстоянии меньше 12 см доводит комнату вплотную — прямо в ROOM_MOVE, после
  упора/скольжения. Теперь стык получается сам, щели нет, перегородка автоматически
  становится полупрозрачной.
- **Номера подрезки в 3D/AR/PDF**: renderFloorBitmap получил флаг `cutNumbers` (включён,
  когда включён слой «Подрезка», ≤400 плиток) и рисует те же янтарные номера, что и план.
  Теперь видно, какая плитка режется, не только в редакторе.

## Шаг РМ — материалы, проценты подрезки, выбор плитки в 3D — ВЫПОЛНЕН

- **Клей, затирка, плинтус** в секции «Расчёт». В core добавлены `groutKg` (классическая
  формула (A+B)/(A×B)×шов×толщина×1.6 с практическим коэффициентом 1.1) и
  `plinth(периметр, двери, хлыст 2.5 м)` → метры и число хлыстов; двери вычитаются
  (проём считается дверью, если стоит на полу, y < 5 см). Проверено автономно:
  клей 60×60 → 5.5 кг/м², затирка 60×60/3 мм → 0.158 кг/м² (справочные 0.15–0.25),
  у 30×30 расход вдвое больше, плинтус 14 м − 0.9 м двери → 13.76 м = 6 хлыстов —
  «МАТЕРИАЛЫ СЧИТАЮТСЯ КОРРЕКТНО».
- **Проценты подрезки**: в «Расчёте» строка «С подрезкой N (X% из них с подрезкой)»;
  в «Обрезках» вверху пояснение «размеры — полезный кусок, в см», доля резаных плиток
  и «В отход: X м² (Y%)»; в каждой строке списка — доля куска от целой плитки в скобках.
- **Выбор плитки в 3D**: при отрисовке пола запоминаются экранные четырёхугольники плиток
  (≤900), короткий тап (без вращения) ищет плитку под пальцем (pointInQuad по чётности
  пересечений), подсвечивает её на полу и показывает плашку сверху: формат, номер
  подрезки и размер полезного куска — те же данные, что в редакторе.

## Правка: стены комнат упираются, а не налезают

Замечание пользователя: у двух комнат с толстыми стенами полосы стен накладывались друг
на друга, вместо того чтобы упереться. Причина: полоса рисуется НАРУЖУ от контура, а
магнит сводил контуры вплотную (зазор 0), поэтому каждая стена ложилась на соседский пол.
- Магнит теперь целится в зазор, равный толщине стены (`bx0 − t − ax1` и т.д.): полосы
  обеих комнат ложатся точно в этот промежуток и читаются как ОДНА общая стена.
- Порог срабатывания стал `0.10 + толщина` — иначе при толстой стене «подвёл вплотную»
  не дотягивался до нужной поправки. Проверено автономно: зазор 0.20 → 0.15; вплотную
  0.02 → раздвигается до 0.15; на 0.9 м магнит молчит — «МАГНИТ С ТОЛЩИНОЙ СТЕНЫ КОРРЕКТЕН».
- На плане полоса стены вырезается из контуров соседних комнат (clipPath Difference до
  трёх соседей), поэтому даже при ручной стыковке вплотную стена не закрывает чужой пол.

## Грабля: Stroke без импорта в View3DScreen

CI: «Unresolved reference 'Stroke'» — подсветка выбранной плитки в 3D использовала
`Stroke(2.4f)`, а в этом файле раньше обводки не рисовались, импорта не было.
Импорт добавлен. Проверка 5b2 в tools/check.sh теперь ловит использование типов
отрисовки (Stroke, Path, PathEffect, StrokeJoin, CornerRadius) без соответствующего
импорта — тот же класс ошибок больше не доедет до CI.

## Шаг АР2 — польза в AR: сводка и рулетка — ВЫПОЛНЕН

Что теперь показывает AR помимо самой раскладки 1:1:
- **Номера подрезанных плиток** прямо на полу (через cutNumbers в renderFloorBitmap,
  включены вместе со слоем «Подрезка») + янтарная обводка резаных;
- **Крашеные плитки, зоны и панно** — рендерятся тем же кодом, что план и 3D;
- **Сводка в камере**: ArBridge.info (площадь · к покупке · подрезка) заполняется в openAr
  и выводится второй строкой под статусом;
- **Рулетка**: кнопка «Измерить» — два касания по полу ставят якоря, приложение считает
  расстояние между ними по позам ARCore и пишет «Расстояние: X.XX м»; следующая пара
  меряется от последней точки (удобно обходить комнату по периметру). В режиме измерения
  раскладка не переставляется, а уже поставленный пол продолжает рисоваться
  (drawFloorIfPlaced получает камеру текущего кадра — второй session.update() за кадр
  вызывать нельзя);
- **Варианты плитки** — кнопка переключения избранных форматов (шаг ЗН).

## Правка: подрезка заметнее и стыковка комнат кнопками

- **Видно, что режется**: на плане подрезанные плитки получили лёгкую янтарную ЗАЛИВКУ
  (alpha 0.16) поверх обводки; в 3D/AR — такая же заливка (alpha 46/255) при включённом
  слое «Подрезка», обводка стала толще и ярче.
- **Процент в скобках** добавлен в подпись выбранной плитки И в редакторе, И в 3D:
  «600×600 · Подрезка №1 · ≈30.0×29.0 см (24%)» — доля полезного куска от целой плитки.
  (В списке «Обрезки» проценты были добавлены шагом раньше.)
- **Стыковка комнат кнопками** (жалоба «тяжело перетягивать и клеить»): в секции «Комната»
  при нескольких комнатах — «Прижать к соседней: ◀ влево / вправо ▶ / ▲ вверх / ▼ вниз».
  `dockActiveRoom(side)` находит БЛИЖАЙШУЮ комнату по центрам, ставит активную вплотную
  с зазором в толщину стены и выравнивает по кромке; вместе с комнатой едут вырезы, узор,
  мебель внутри и зоны; overlap-guard, undo, fit.

## Шаг СТ — толщина по стенам и 3D по всем комнатам — ВЫПОЛНЕН

- **Разная толщина у разных стен**: `wallThickness: Map<String, Double>` в RoomDto и VM
  (по комнате, в undo и новом проекте), `wallThicknessOf(id)` = своя или общая.
  Функция названа `updateWallThicknessOf` — проверка 1 сразу поймала бы clash с
  одноимённым свойством. UI: в «Поверхностях» при выбранной стене ряд «Толщина этой
  стены»: «Как у всех» + 5/10/15/20/25/30/40 см. Общая толщина в «Комнате» тоже до 40 см
  (кламп в модели 2…60 см).
- **План**: полоса рисуется теперь ПО КАЖДОМУ ребру своей толщиной (квад наружу по внешней
  нормали, концы удлинены на толщину соседних стен, чтобы углы сходились), с вырезанием
  контуров соседних комнат.
- **3D**: верх стены берёт толщину этой стены.
- **3D по всем комнатам**: соседние комнаты рендерятся с номерами подрезки (cutNumbers),
  их плитки попадают в tileQuads, поэтому тапом выбирается плитка ЛЮБОЙ комнаты —
  плашка показывает формат этой комнаты и номер её подрезки (точный размер куска
  считается пока для активной).

## Шаг ПД — проёмы на плане и охват статистики — ВЫПОЛНЕН

- **Двери и окна теперь видны на плане** (блок 6e): проём вырезается светлым разрывом в
  полосе стены своей толщины, дверь (проём стоит на полу, y < 5 см) дополнительно получает
  пунктирную дугу открывания и полотно — как на архитектурном чертеже; окно — просто
  подсвеченный разрыв. Проёмы по-прежнему ставятся в «Поверхностях» («+ окно» / «+ дверь»)
  и видны в 3D.
- **Переключатель «Комната / Квартира»** в шапке статистики (при нескольких комнатах):
  `statsApartment` + `apartmentTotals()` (площадь, целые, подрезка, к покупке по всем
  комнатам). Карточки показывают выбранный охват, заголовок пишет «Квартира» или имя
  комнаты. Полная разбивка по комнатам остаётся в «Смете» и в отчёте.
- Перегородка внутри помещения делается двумя комнатами, состыкованными кнопками
  «Прижать к соседней»: стена между ними и есть перегородка, её толщина настраивается
  отдельно в «Поверхностях», а дверь в ней — проёмом на этой стене.

## Правка: быстрые действия в шапке плана

«Неудобно за полным сбросом заходить в Проект». Рядом с отменой/повтором добавлена кнопка
«⋯» (BaIcons.More) с выпадающим меню: «Вписать», «Упростить контур» (при >4 точках),
«Сбросить всё наставленное» (с подтверждением, отменяемо) и «Новый проект»
(с подтверждением). Те же действия остались и в секции «Проект» — теперь они просто
доступны в один тап прямо над планом.

## Правка: авторский знак на каждом экране

Просьба: знак должен быть виден везде, бледный, вперемешку с названием программы.
Добавлен `WatermarkOverlay()` в MainScreen поверх содержимого вкладок (кроме вкладки Pro):
диагональ −28°, плитка текста с шагом 250×104 dp, строки ЧЕРЕДУЮТ «BA-Remodel» и
«Baziulkin Alexander», цвет argb(20, 233, 238, 246) — заметно, но не мешает работе.
Canvas без обработчиков касаний, поэтому жесты проходят насквозь. Гейт прежний:
`Entitlements.watermark = !isPro`, значит донат-код снимает знак разом на всех экранах,
в PDF и в AR. Текст в карточке доната переписан под это обещание (EN/RU/HE).
Точечные знаки в углах плана, примерки и PDF остаются как были.

## ОБЯЗАТЕЛЬНО перед сдачей любого шага

`bash tools/check.sh` — шесть проверок: коллизии свойство↔функция, модификаторы без импорта,
обращения `vm.*`, ключи строк EN/RU, вызовы AnimatedVisibility, баланс скобок.
Должно вывести «ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ». Плюс автономный прогон движка (§9 и VerifyV2Kt).

## Известные грабли компиляции (проверять перед сдачей шага)

- `AnimatedVisibility` внутри `Box`, вложенного в `Column`: подхватывается перегрузка
  `ColumnScope.AnimatedVisibility` и падает с «cannot be called with an implicit receiver».
  Решение: вызывать через обёртку `Fade(visible, modifier) { ... }` в MainScreen.kt —
  она объявлена вне scope, поэтому берётся обычная перегрузка.
- Каждый модификатор нужно импортировать явно: `fillMaxSize`, `fillMaxWidth`, `heightIn`,
  `widthIn`, `horizontalScroll`, `verticalScroll`, `systemBarsPadding`, `onSizeChanged`,
  `pointerInput`. Перед сдачей шага прогонять grep-проверку «модификатор без импорта».
- Функция вида `fun setXxx(...)` конфликтует со свойством `xxx` (одна JVM-сигнатура) —
  именно поэтому методы называются `switchRoomMode` и `switchAnchor`. Ловится пунктом 1
  в tools/check.sh; ошибка вылезала дважды, всегда прогоняй скрипт.

## Порядок и правила

Один шаг = одна сессия. Перед шагом: распаковать zip, прочитать §7 контрактов и раздел шага.
После шага: прогнать §9 и проверки шага 7 (`java -cp v2.jar VerifyV2Kt`), обновить статус,
собрать zip, отдать пользователю. Пользователь загружает zip в GitHub → Actions → APK.

## Правка: единая нумерация подрезок, честный чип реза, развязка верхних кнопок — ВЫПОЛНЕНО

Три жалобы беты закрыты одним шагом.

**Ядро.** Новый `core/CutNumbering.kt`: `CutPieceInfo(index, number, wMm, hMm, aCm, bCm,
areaPct, cutOffMm, cx, cy)` и `CutNumbering.compute(room, layout)`. Номера получают только
реально видимые куски (габарит ≥ 1 см, `MIN_PIECE_CM`); плитки-фантомы на границе (клип пуст)
номеров не получают и нумерацию не сдвигают — раньше именно из-за них «№1» было не найти.
Порядок — по рядам узора (`rect.y`, затем `rect.x`), предсказуемо ряд за рядом. `areaPct` —
честная площадь куска (клип по комнате минус вырезы), а не произведение габаритов; для плитки
с вырезом-колонной внутри теперь ~56 %, а не 100 %. `cutOffMm` — сколько срезано при прямом
резе (одна сторона осталась целой), иначе null. `cx, cy` — центр видимого куска: номер больше
не рисуется в центре целой плитки за стеной.

**Потребители.** `EditorCanvas` рисует номера из `vm.cutInfo` в центрах кусков; порог показа
один на все номера (мин. сторона плитки > 20 dp на экране) — без «дыр» в последовательности.
`renderFloorBitmap` получил параметр `cutInfo` (фолбэк на старую нумерацию, если не передан);
прокинут из View3DScreen (актив + неактивные комнаты через `otherCutInfo`), `PdfReport`
(считает сам из room+layout) и `openAr`. Чипы выбранной плитки в 2D и 3D используют общий
`cutChipSuffix(ci)` из MainScreen.kt: « №N · остаётся W×H мм (P%) · срезано C мм»; старые
дублированные циклы `cutNo` удалены. Строки `cut_remain`/`cut_off` добавлены в EN/RU/HE/IW.

**Перекрытие кнопок.** Строка чипов выбора и панель рисования опущены под верхний ряд
(`top = 68.dp`): отмена/повтор и переключатель «узор/комната» больше не перекрываются.
Баннер лимита плиток опущен на 118 dp, чтобы не лечь под чипы.

**Проверки.** `tools/check.sh` — все пройдены; VerifyV2Kt — ALL PASSED; новый
`core/src/test/kotlin/.../CutNumberingTest.kt` (фантомы без номера, номера подряд, срез
250/295 мм, центры внутри комнаты, вырез 55,6 %) — прогонится в CI шагом `:core:test`.
Автономно прогнано kotlinc 2.0.21: движок + оба verify-сценария зелёные.

## Шаг ПТ — типы проёмов: окно, дверь, балкон, вход, проход — ВЫПОЛНЕН

Закрывает жалобу «не понятно, где дверь в квартиру, где окно, где балкон».

**Модель.** Константы `OPENING_WINDOW/DOOR/BALCONY/ENTRY/PASSAGE` (уровень файла
EditorViewModel.kt). Состояние `openingKinds: Map<String, List<Int>>` параллельно
`openings`; `openingKindsOf(id)` выравнивает по списку и для старых сохранений выводит
тип по высоте (на полу — дверь, поднят — окно) — совместимость полная. Поле добавлено
в RoomDto и ProjectDto с default emptyMap(), в Snap/undo, snapshotRoom/applyRoom,
addRoom/newProject, toDto/applyDto и в ключи автосохранения.

**Функции.** `addOpening(..., kind)` — пять пресетов; `updateOpening(id, i, x/w/h/sill)` —
отступ от начала стены, ширина, высота, подоконник (с зажимами по длине стены и высоте);
`setOpeningKind` — смена типа: не-окна опускаются на пол, окно поднимается на подоконник;
`deleteOpening` синхронно удаляет тип.

**План (6e).** Знаки по типам: окно — двойная линия остекления в полосе стены; дверь —
дуга открывания с полотном (как было); балконная дверь — дуга + остекление; входная —
дуга с закрашенным сектором и толстым полотном, цвет Good — вход виден с одного взгляда;
проход — пунктир по середине полосы. Цвет контура проёма тоже по типу.

**Панель «Поверхности».** Пять кнопок добавления (скролл-строка). Каждый проём — строка
«Тип · Ш×В м · ⇤ отступ», по тапу раскрывается редактор: чипы смены типа + NumField
отступ/ширина/высота (+ «от пола» только у окна). Удаление как раньше.

**3D.** Контур проёма окрашен по типу (окно Acc, вход Good, проход Sub, двери Acc2),
у остеклённых — голубоватая заливка «стекла».

Строки: height, edit, hide, opening_balcony/entry/passage, kind_*, opening_offset,
opening_sill — EN/RU/HE/IW. tools/check.sh — все проверки пройдены. Перетаскивание
проёма пальцем по плану — в шаге «Стены» (там же ортопривязка и ввод длины на ребре).

## Шаг СД — стены: длина по тапу, стретч вместо луча, орто-рисование, честный клип — ВЫПОЛНЕН

Закрывает «неудобно указывать длину стены и оно неправильно работает», «стенки неудобно
рисовать» и «под стенами нереально считается отрезка».

**Стретч-алгоритм.** `setEdgeLength(edge, newLen, moveEnd)`: двигается выбранный конец
(A = вершина edge, B = вершина edge+1) вдоль направления стены; сдвиг тянет цепочку вершин
до первой стены, ПАРАЛЛЕЛЬНОЙ редактируемой — она поглощает изменение своей длиной. Комната
растягивается, прямые углы сохраняются. Прежний код двигал соседнюю вершину по лучу из угла
и ломал смежные стены — в этом и было «работает неправильно». Без параллельного поглотителя
(косой контур) двигается только сам конец. Проверено автономно kotlinc: прямоугольник (обе
стороны), Г-образная (полка едет целиком, поглотитель −0.7 м), треугольник (фолбэк).
`applyEdgeLengths` из диалога угла теперь делегирует сюда: выбранный угол — неподвижный конец.

**Редактор по тапу.** Метка размера стены на плане стала кнопкой: тап (радиус 22 dp,
`edgeLabelAt` повторяет математику отрисовки) открывает диалог «Стена N — длина» с полем и
выбором конца A/B; на плане стена подсвечена, концы помечены кружками A и B (блок 7c).
Состояние `edgeEditIndex`/`closeEdgeEdit` во ViewModel, диалог в MainScreen перед edgeDialog.

**Рисование.** `addDrawPoint`: привязка направления к 0/45/90° (допуск ~8.6°) от последней
точки + прилипание к уровням X/Y всех уже поставленных точек, включая первую — контур
замыкается ровно. Длины сегментов уже подписывались.

**Честный клип.** Подсветка выбранной плитки (4s) и совпавших обрезков (4h) обрезаются по
контуру комнаты — раньше полный квад плитки вылезал под полосу стены, и казалось, что под
стеной «лежит» плитка. Штриховка подрезки и так внутри clipPath(191).

Строки edge_len_title/len_m/move_which/edge_len_hint — EN/RU/HE/IW. tools/check.sh пройден.
Отложено в следующий подшаг: перетаскивание проёма пальцем по плану (жест OPENING_MOVE) и
разбор «толщина работает неправильно» — нужен конкретный симптом от пользователя.

## Правка: перетаскивание проёмов пальцем и wall-N без «переездов» — ВЫПОЛНЕНО

Хвосты шага СД закрыты.

**Проём тащится пальцем.** Новый жест `Drag.OPENING_MOVE`: хват за полосу проёма на стене
(в любом режиме; допуски 8/6 dp, углы с ручками вершин не перехватываются), движение
проецируется на ось стены, позиция зажимается в [0, len−w]. Магниты как везде: края стены
и центр (8 dp). Наезд на соседний проём той же стены блокируется. Undo — штатный
gestureSnapped на первом движении. Поле `dragWall` рядом с `dragIndex`/`grabDx`.

**Диагноз «толщина работает неправильно».** Карты `wall-N` (толщина, проёмы, типы проёмов,
отделка) не пересчитывались при изменении числа вершин: после ниши, скругления угла или
удаления угла записи оставались на старых номерах и «переезжали» на чужие стены. Новый
`remapWallKeys(fromEdge, shift)`: вставка сдвигает последующие стены на +shift, удаление
дропает записи схлопнувшейся стены и сдвигает на −1; floor/ceiling нетронуты. Вызовы:
`roundSelectedCorner` (+arc.size−1 после угла), `addNicheAfterSelected` (+4 после ребра),
`deleteSelectedVertex` (−1). Проверено автономно kotlinc: три сценария зелёные.
Известное ограничение (задокументировано): `simplifyRoom` и полная замена контура
(applyRect/applyLShape/autoTrace/finishDraw) карты не ремапят — как и раньше.

tools/check.sh — все проверки пройдены.

## Шаг ПР — пороги: плитка в межкомнатных проёмах считается и видна — ВЫПОЛНЕН

Ответ на вопрос пользователя «под проёмами межкомнатных стен плитка нормально считается?» —
до этого шага НЕТ: комнаты — отдельные контуры, зазор толщины стены между ними никому не
принадлежал, и плитка порога не считалась вовсе. Теперь:

**Расчёт.** `ThresholdStrip` (уровень файла) + `thresholdStripsFor(spec, opens, kinds,
thick, selfIdx)`: дверные проёмы (не окна) на стенах, за которыми лежит другая комната
(проба-точка за стеной внутри чужого контура). `thresholdStrips` (derived, активная
комната), `thresholdAreaM2`, `thresholdPieces` (раскладка по длинной стороне плитки:
ceil(w / шагДлиннойСтороны)). Порог приписан комнате, на чьей стене создан проём — дверь
ставится на ОДНУ из двух смежных стен, иначе задвоится (задокументировано).

**Куда вошло.** buyCount активной комнаты; карточка «Подрезка» (fallback в StatsRow);
apartmentTotals (площадь/подрезка/покупка по всем комнатам); apartmentStats — смета по
комнатам (площадь, закупка, деньги). PDF получает готовый buyCount — включён автоматически.

**План (6t).** Полоса порога рисуется в проёме: заливка Acc2 0.14 + пунктирная рамка —
видно, что плитка проходит через дверь.

**3D.** Убрано условие `!isInternal` на отрисовку проёмов: дверь между комнатами больше не
исчезает — рисуется на полупрозрачной перегородке с цветом по типу. Ограничение (как было):
проёмы НЕактивных комнат в 3D видны только когда та комната активна.

tools/check.sh — все проверки пройдены. Шаг «Группы настроек» — следующая сессия.

## Шаг ПЛ — плинтус: сегменты, план распила, режим «из плитки» — ВЫПОЛНЕН

Запрос пользователя: плинтус так же жизненно важен, как плитка — нужно видеть, какие куски
есть и куда их ставить, с советами как по плитке.

**Ядро `SkirtingCalc.kt`.** `segments(points, openings)` — каждая стена режется стоящими
на полу проёмами (y < 0.05: двери, проходы, балконные и входные) на куски; окна плинтус не
рвут; `SkirtSegment` знает стену, отступ и номер куска (для подписи «ст.3·a/b»).
`plan(segments, barLenM)` — сегменты длиннее хлыста режутся на целые части + добор (счётчик
стыков), куски пакуются First-Fit-Decreasing: остаток одного хлыста уходит на короткий
сегмент другой стены — это и есть совет «какой кусок куда». Результат: хлысты с распилом и
остатками, итог метров, стыки.

**ViewModel.** `skirtMode` (0 хлысты / 1 из плитки, `switchSkirtMode` — грабля №1 поймана
check.sh), `skirtBarLenM` (0.5–6), `skirtHeightMm` (30–200); `skirtPlan` (derived; в режиме
плитки хлыст = полоса длиной в длинную сторону плитки); `skirtStripsPerTile`;
`skirtFromOffcuts` — полосы из обрезков раскладки: прямые резы дают кусок «срезано × целая
сторона», из него floor(срезано/высота) полос — счёт и метры для совета. Настройки в
ProjectDto (default'ы — совместимость), toDto/applyDto, ключи автосохранения.

**Панель «Расчёт».** Блок «Плинтус»: чипы режима, поле хлыста или высоты полосы; итог
(метры · сегменты), «Хлыстов купить N × L · стыков K» или «Полос N · плиток M (по k с
плитки)» + зелёный совет «Из обрезков ≈N полос · X м»; кнопка «План распила…» раскрывает
по-хлыстово: «№1: 2.50 → ст.4·a + … · ост. 0.45».

**Проверено.** Автономно kotlinc: дверь рвёт стену (5 сегментов), окно нет, 13.1 м,
6 хлыстов (FFD совмещает остатки), 3 стыка, все сегменты закрыты; режим плитки 600×300/80 —
23 полосы = 8 плиток. JUnit `SkirtingCalcTest` добавлен в CI. tools/check.sh пройден.
Старый `MaterialCalc.plinth` больше не используется панелью (оставлен для совместимости).

## Шаг ГР — группы настроек: две ступени вместо ленты из десяти чипов — ВЫПОЛНЕН

Закрывает «пересмотреть группы на логичность, подгруппы открывать по нужде, интерфейс
максимально френдли».

**Структура.** PanelHost: пять групп → подразделы. «Помещение» (Комната, Поверхности) ·
«Плитка и узор» (Плитка, Узор) · «Обстановка» (Мебель) · «Расчёты» (Расчёт, Обрезки,
Смета, Советы) · «Проект». Верхний ряд — группы; второй ряд подразделов появляется только
когда их больше одного. Номера секций прежние (0..9): сохранённый panelSection, Crossfade
и все внешние переходы работают без изменений.

**Память места.** `lastSub: mutableStateMapOf` — возврат в группу открывает последний
использованный подраздел (запись через LaunchedEffect(section), не в композиции — иначе
цикл рекомпозиции). Грабля с неуникальным якорем импорта (OutlinedTextField ⊂
OutlinedTextFieldDefaults) поймана и обойдена — правка переприменена с парным якорем.

Строки grp_room/grp_tile/grp_furnish/grp_calc/grp_project — EN/RU/HE/IW (названия групп
не дублируют подразделы: «Помещение»≠«Комната», «Плитка и узор»≠«Плитка»).
tools/check.sh — все проверки пройдены.

Дорожная карта фидбека: 2, 5, 6 (баги) ✓ · 1 (проёмы+типы) ✓ · 7 (стены+пороги) ✓ ·
3 (группы) ✓ · плинтус (внеплановый запрос) ✓. Остался шаг 4 — «умная резка»: магниты
раскладки к симметрии/полплитки, предупреждения о полосках на экран, парование резов
(две подрезки из одной плитки) — следующая сессия.

## Шаг УР — умная резка: магниты раскладки, полоски на экран, парование резов — ВЫПОЛНЕН

Последний пункт дорожной карты фидбека (№4).

**Магниты узора.** В Drag.PATTERN сдвиг прилипает к «хорошим» положениям, как уровни у
стен: шов по центру комнаты, плитка по центру, целая плитка у ближней/дальней стены —
по каждой оси независимо, сравнение по модулю шага (плитка+шов), допуск 7 dp, побеждает
БЛИЖАЙШИЙ кандидат (грабля «первый по списку» поймана автотестом и исправлена).
Состояние patternSnapX/Y (0 нет · 2 шов-центр · 3 плитка-центр · 4/5 у стены), сброс в
gestureEnd и cancelGesture. Канвас 7e рисует зелёную пунктирную направляющую по месту
прилипания — видно, к чему прилипло. Работает при rotationDeg == 0.

**Полоски — на экран.** Плашка над подсказкой (BottomCenter, bottom 56): «Полоска X.X см ·
стена N» из cutReport (минимальная THIN_STRIP); тап по тексту подсвечивает стену (warnEdge,
канвас 7d, линия Warn 4.5 dp); кнопка «Исправить» → fixThinEdge: сдвиг узора по внутренней
нормали стены на (полшага − полоска) — полоса становится полплитки одним нажатием
(rotation 0; pushUndo; anchor=FREE). Раньше предупреждения жили только в «Советах».

**Парование резов.** CutPairs (уровень файла) + derived cutPairs: прямые полосы из
cutInfo (cutOffMm != null, одна сторона целая) пакуются FFD в плитку с пропилом 4 мм,
два пула по оси реза (поперёк ширины / поперёк высоты), ёлочка исключена. pairCuts
(default true, в ProjectDto + автосейв) — тумблер в «Обрезках» с зелёной «Экономия: −N шт»
и раскрывашкой «Пары…»: «№12 + №47» — какие подрезки резать из одной плитки.
pairedCutTiles = cutCount − saved; buyCount = (full + pairedCutTiles + пороги) × запас.
Квартирные суммы/смета — консервативно без парования (задокументировано).

**Проверено.** Автономно kotlinc: магниты (центр-шов, центр-плитка, у стены, вне допуска,
ближайший при конфликте), парование (250+250→1 плитка, 350+300→2, три по 180→1,
90 подсаживается к 500) — всё зелёное. tools/check.sh пройден.

ДОРОЖНАЯ КАРТА ФИДБЕКА ЗАКРЫТА ПОЛНОСТЬЮ: 2,5,6 ✓ · 1 ✓ · 7+пороги ✓ · 3 ✓ · плинтус ✓ · 4 ✓.

## Аудит приёмки — «принял бы я такой софт сам?» — ВЫПОЛНЕН

Проход по всему новому функционалу глазами злого ревьюера. Найдено и исправлено:

**1. Дверь «висела» за стеной.** После setEdgeLength / удаления угла / скругления / ниши /
перетаскивания вершин проёмы оставались на старых координатах и могли оказаться за концом
укоротившейся стены. Новый `clampOpenings()`: ширина ≤ длины стены, отступ ∈ [0, len−w];
вызовы после всех мутаций контура + gestureEnd для VERTEX-драга. Тест: стена 2.0 — дверь
съезжает к 1.1; стена 0.6 — дверь ужимается.

**2. Двойной счёт материала: парование × плинтус-из-обрезков.** Совет «полос из обрезков»
считал остаток каждой подрезки отдельно, а парование этот же материал уже отдавало второй
подрезке. CutBin теперь несёт restMm и stripLenMm; при включённом паровании полосы
считаются из РЕАЛЬНЫХ остатков бинов (600: 250+250 → 1 полоса, а не задвоенные 8), при
выключенном — как раньше (350+300 → 6, эквивалент подтверждён тестом). Ёлочка — фолбэк на
старую формулу. UI пар перешёл на bin.nums (список — только бины ≥2).

**3. PDF не сходился сам с собой.** «Купить» включает пороги и парование, а строки
целые/подрезка/всего — нет. Добавлена строка-расшифровка «Пороги +N · Экономия −M»
(параметры thresholdPieces/pairSaved в PdfReport.share, ReportTab передаёт). Строка
thresholds_lbl ×4 локали.

Проверено: tools/check.sh — все; автономные тесты аудита зелёные; apartmentPieces уже
консистентен (делегирует в apartmentStats с порогами); buyM2 = buyCount×площадь ✓.

**Известные ограничения (осознанные, задокументированы):**
- fixThinEdge может создать полоску у противоположной стены — лечится повторным тапом или
  магнитом «по центру»; кандидат на симметричный автофикс.
- Проёмы неактивных комнат в 3D видны при активации той комнаты.
- simplifyRoom и полная замена контура (applyRect/applyLShape/autoTrace/finishDraw) не
  ремапят карты wall-N.
- Квартирные totals — консервативно без парования (расшифровка есть в PDF активной комнаты).
- Магниты/фикс работают при rotationDeg == 0.

## Фикс сборки CI (лог 82467000609) — ВЫПОЛНЕН

Первый прогон на GitHub Actions после большой серии: одна ошибка компиляции и один
отказ push.

**Компиляция.** Panels.kt:1534 — `stringResource` внутри лямбды `joinToString`:
в отличие от forEach/let, joinToString НЕ inline, и Compose запрещает @Composable-вызовы
в её лямбде. Строки skirt_wall_short/skirt_rest вынесены в val перед циклом плана распила.
Других таких мест нет (проверено). В tools/check.sh добавлено правило №7 — эвристика
«composable-вызов в 8 строках после joinToString(» — грабля больше не повторится.

**Push.** `[remote rejected] … refusing to allow a GitHub App to create or update workflow
.github/workflows/keystore.yml without workflows permission` — архив исходников тащил
.github/workflows, а у GITHUB_TOKEN нет права workflows. Решение: .github исключён из
поставляемого архива — workflow живёт в репозитории и архивом не перезаписывается.
Замечание: «BUILD SUCCESSFUL in 3s» в конце лога — фолбэк-шаг, не APK.

## Шаг ФЛ — раскрывающиеся блоки внутри секций: «открыл — настроил» — ВЫПОЛНЕН

Фидбек по скриншоту планшетной раскладки: «полотно настроек справа пугает… нужно
по группам, раскрывающееся». Группы верхнего уровня уже были (шаг ГР); теперь и ВНУТРИ
секций контент собран в фолды.

**Компонент Fold (Panels).** Заголовок: шеврон ▸/▾ + название + в свёрнутом виде краткое
текущее значение справа (например «600×600 · 3 мм»); тап — открылся (Fade). Состояние —
во ViewModel (`foldStates`/`foldOpen`/`toggleFold`): переживает смену секций и групп в
рамках сеанса; ключи вида "tile.size". `Fade` в MainScreen переведён private → internal.

**Разбивка.**
- Плитка: «Размер и шов» (открыт по умолчанию, сабтайтл размеры) · «Цвет, раскраска,
  зоны» · «Вид плитки и фото» (picker перенесён внутрь — использовался только здесь) ·
  «Декор и панно» (DecorSection целиком).
- Поверхности: выбор поверхности и толщина стены видимы всегда; «Отделка» (открыт) ·
  «Проёмы» (сабтайтл — количество; внутренний заголовок убран — им стал фолд) ·
  «Итого материалы».
- Расчёт: ключевые цифры видимы; «Плинтус» (сабтайтл — метры) · «Подрезка по стенам».
- Комната: комнаты/стыковка/толщина/рисование видимы; «Подложка-план» · «Шаблоны формы»
  (контекстные кнопки выделения — вне фолдов, они привязаны к канвасу).

Строки fold_size/color/look/decor/finish/templates ×4 локали. tools/check.sh — все
проверки, включая баланс скобок после ~20 структурных обёрток и правило №7.

## Шаг ПМ — проёмы: постановка тапом и метки-указатели; курс на «все работы» — ВЫПОЛНЕН

Фидбек: «не увидел, как удобно указать двери… на чертеже не понятно, где двери — стрелкой
указать или выделить, чтобы понимать квартирку сверху»; видение — программа со временем
закрывает весь дизайн и работы в квартире по группам материалов.

**Постановка в два касания.** В режиме «Комната» (когда ничего не выделено) сверху ряд
чипов «+ Дверь / + Окно / + Балкон / + Вход / + Проход»; выбранный тип взводится
(`placeOpeningKind`, подсказка «Тапни по стене — проём встанет в место касания»), тап по
плану ищет ближайшую стену (порог 20 dp, проекция на отрезок) и ставит проём ЦЕНТРОМ в
точку касания (`addOpeningAt`, размеры-пресеты как в панели). После постановки режим
разоружается; выход из «Комнаты» тоже разоружает. Дальше проём таскается пальцем (жест
OPENING_MOVE из шага СД) и правится в «Поверхностях».

**Метки-указатели (канвас 6m).** У каждого проёма снаружи стены — цветной кружок с первой
буквой типа (Д/О/Б/В/П — берётся из локализованных kind_*, работает на всех языках) и
линия-указатель к самому проёму. Цвета как у знаков: окно Acc, вход Good, проход Sub,
двери Acc2. Размер в dp — читается на любом зуме; «квартирка сверху» ориентируется с
одного взгляда.

**Курс «все работы в квартире».** Зафиксировано направление: материалы/работы как группы
(плитка → плинтус → краска стен → обои → потолок). Текущая архитектура уже несёт это:
Поверхности = отделка каждой стены/потолка (плитка/краска/обои) с площадями и итогами,
плинтус — свой модуль с распилом, смета собирает по комнатам. Следующий шаг курса —
экран «Работы»: сводный чек-лист по квартире (работа → объём → материалы → статус).

Строка place_opening_hint ×4. tools/check.sh — все проверки пройдены.

## Шаг РБ — экран «Работы»: чек-лист всей квартиры со статусами — ВЫПОЛНЕН

Первый экран курса «программа закрывает весь дизайн и работы в квартире».

**Модель.** `WorkRow(key, room, title, detail)` (уровень файла VM). `worksList()` собирает
по каждой комнате: пол-плитка (площадь с порогами · штук к покупке · формат), плинтус
(метры · хлысты или плитки по режиму), каждая стена с отделкой ≠ NONE (краска — литры в
2 слоя, обои — рулоны через MaterialCalc.wallpaper, плитка — штук с запасом; площадь
стены минус проёмы через `wallAreaOf`), потолок-краска. Активная комната — из живого
состояния, остальные — из снапшотов.

**Статусы.** `workStatus: Map<String, Int>` (0 план · 1 в работе · 2 готово), ключи
"r{i}.floor|skirt|wall-N|ceiling"; `cycleWorkStatus` по тапу. Хранится в ProjectDto
(default emptyMap — совместимость), toDto/applyDto, ключи автосохранения. Известное
ограничение: ключи привязаны к индексу комнаты — удаление комнаты сдвигает статусы
последующих (задокументировано).

**UI.** Секция 10 → WorksSection, первый подраздел группы «Расчёты» (sec_works). Сверху
прогресс «Готово X / N» (зелёный при полном); строки: заголовок «Комната · Работа» +
пилюля статуса (тап переключает, цвета Dim/Acc2/Good, готовое приглушается) + строка
объёма/материалов. Пустое состояние и подсказка. Строки ×4 локали (sec_works, work_*,
works_*, liters_short, rolls_short).

tools/check.sh — все проверки пройдены.

## Шаг ДД — доделки: все «известные ограничения» закрыты — ВЫПОЛНЕН

Мандат пользователя «что можешь доделать — доделай». Закрыты пять пунктов из списка
ограничений аудита:

**1. Умный «Исправить» полоску.** fixThinEdge переписан: три кандидата сдвига по оси
нормали — «полплитки у этой стены», «шов по центру», «плитка по центру»; каждый честно
прогоняется движком (TilingEngine + CutAnalyzer), применяется тот, где минимальная полоска
ПО ВСЕМ стенам максимальна (тай-брейк — меньший сдвиг). Пинг-понг «исправил тут — вылезло
напротив» устранён по построению.

**2. Магниты и фикс при повороте узора кратном 90°.** Направляющие/прилипание и кнопка
«Исправить» работают при 0/90/180/270 (при 90/270 шаги по осям свапаются — период решётки
в мире меняется местами; линии решётки проходят через начало координат, поэтому
конгруэнции сохраняются). Гейт в MainScreen обновлён.

**3. Проёмы соседних комнат в 3D.** Блок неактивных стен рисует их проёмы из
r.openings/r.openingKinds с цветом по типу и «стеклом» — двери и окна всей квартиры видны
без переключения активной комнаты.

**4. Статусы работ при удалении комнаты.** deleteActiveRoom сдвигает ключи r{i}.*:
удалённая комната выбрасывается, последующие переезжают на −1 — чек-лист не путается.

**5. simplifyRoom наследует свойства стен.** Функция переписана с трекингом исходных
индексов вершин: новая стена j наследует записи стены, начинавшейся в выжившей вершине;
записи схлопнувшихся стен отбрасываются; после — clampOpenings. Полная замена контура
(applyRect/applyLShape/autoTrace/finishDraw) по-прежнему без remap — это осознанно:
контур новый, старые стены не имеют соответствия.

Проверено: tools/check.sh — все; автономные тесты наследования стен и сдвига статусов —
зелёные. Ограничений в списке не осталось.

## Шаг СХ — схема для людей: слова вместо букв и «Поделиться планом (PNG)» — ВЫПОЛНЕН

Фидбек: буквы на метках проёмов непонятны постороннему; «если я человеку эту схему скинул —
чтобы он понял расположение сразу»; вопрос «как-то это есть в программе?».

**Слова на плане.** Метки проёмов (канвас 6m) вместо кружка с буквой — цветная табличка с
ПОЛНЫМ словом из локали: «Дверь», «Окно», «Балкон», «Вход», «Проход» (kind_* целиком, без
сокращений), с тем же указателем на проём. Размер в dp — читается на любом зуме и языке.

**Шаринг схемы.** Новый `PlanShare.share(context, vm)` (ui/editor): PNG «как чертёж» —
подложка renderFloorBitmap (плитка, номера подрезок, зоны, панно), поверх: полоса стен по
толщине, проёмы с разрывом, дугой у дверных и словом-табличкой снаружи, размеры сторон
внутрь комнаты, заголовок (имя проекта) и итоги внизу (площадь · целые · подрезка ·
купить · формат) + подпись бренда. Файл в cacheDir/reports (та же папка, что PDF — уже
разрешена FileProvider), ACTION_SEND image/png через chooser. Кнопка «Поделиться планом
(PNG)» — в «Отчёте» над PDF-кнопкой. PDF с чертежом как был, PNG — быстрый вариант «скинуть
в мессенджер». Строка share_plan ×4.

tools/check.sh — все проверки пройдены.

## Фикс сборки CI (лог 82518124866) — ВЫПОЛНЕН

Одна ошибка: EditorCanvas.kt:928 Unresolved reference 'toArgb'. Причина — моя же: условие
вставки импорта в скрипте правки было инвертировано (guard по drawscope-импорту сработал
наоборот) и вставка молча пропустилась. Импорт добавлен. В tools/check.sh — правило 5b3:
использование compose-расширений (.toArgb( / .asAndroidBitmap() без соответствующего
import в том же файле — фейл; прогон по всему app — чисто. Push-реджекта workflow в этом
логе уже нет — исключение .github из архива сработало.

## Шаг ПП — проёмы по-взрослому: размеры-пресеты, палитра, чертёж в PDF — ВЫПОЛНЕН

Жёсткий фидбек: фиксированные размеры проёмов — «бред», метки плохо читаются, дверь бывает
внешняя/межкомнатная, окно обычное/в пол, балкон дверь/во всю стену; 3D-чип непонятен и
«ломает отображение»; шаринг должен жить в Отчёте и охватывать всё.

**Размеры-пресеты.** При взведённом типе под чипами — второй ряд размеров:
дверь 0.7/0.8/0.9/1.0 · вход 0.9/1.0/1.2 · окно 0.9×1.2 / 1.4×1.4 / 1.8×1.4 / «В пол»
(подоконник 0) / «Вся стена» · балкон 0.8 / 1.8 / «Вся стена» (французское остекление) ·
проход 0.9/1.2/1.5. VM: placeOpeningW/H/Sill (+setPlaceOpeningSize), ширина −1 = «во всю
стену» (addOpeningAt берёт длину стены), defaultOpeningSize на уровне файла. Точные размеры
как раньше правятся в «Поверхностях».

**Палитра.** `openingTone(kind)` (EditorCanvas, internal) — единый цвет на плане/3D/PNG:
окно Acc-голубой · дверь ОРАНЖЕВЫЙ 0xFFFF9046 · балкон БИРЮЗОВЫЙ 0xFF3ED0C3 · вход Good ·
проход Sub — дверь и балкон больше не сливаются. Таблички: белый жирный текст, шрифт
10.5 dp (план) / 27 px (PNG), пилюли крупнее.

**3D.** Чип опущен под шапку (top 64 dp — не перекрывает заголовок, как на скрине);
выбранная плитка подсвечивается на полу полупрозрачным Acc-квадом с контуром — понятно,
о чём говорит чип. «Ломает отображение» — жду от пользователя конкретику (что именно).

**Отчёт — центр всего.** PlanShare разрезан: `renderBitmap(context, vm, withHeader)` +
`share()`. PDF-отчёт теперь получает ПОЛНЫЙ чертёж (`planBmp` в PdfReport.share, ReportTab
передаёт withHeader=false): стены по толщине, проёмы с дугами и словами, размеры — вместо
голого пола. Кнопка «Поделиться планом (PNG)» там же. Строки op_full_height/op_full_wall ×4.

tools/check.sh — все проверки. Грабля отступов (6e/6m) поймана assert'ом до записи.

## Шаг МП — мастер проёма: программа сама спрашивает «что здесь и какого размера» — ВЫПОЛНЕН

Запрос: «может как-то спрашивать — окно в пол или нет, или дверь — и указывать от этого,
и потом всё считать».

**Поток.** Голый тап по стене в режиме «Комната» → AlertDialog шаг 1 «Что здесь?» — пять
крупных цветных кнопок типов (тон = openingTone). Выбор → шаг 2 «Размер»: пресеты как в
жизни (дверь 0.7–1.0 · вход 0.9–1.2 · окно 0.9×1.2/1.4×1.4/1.8×1.4/«В пол»/«Вся стена» ·
балкон 0.8/1.8/«Вся стена» · проход 0.9–1.5) + поля Ширина/Высота (у окна — «От пола»)
для своих цифр; пресет заполняет поля («Вся стена» подставляет реальную длину стены).
«Поставить» → confirmOpeningWizard → addOpeningAt в точку тапа → все расчёты (пол, пороги,
плинтус, стены, покупка, Работы) пересчитываются сами. Чипы типов сверху остались как
быстрый вход: взведённый чип + тап по стене открывает мастер сразу на шаге размера.

**VM.** OpeningWizard(wall, sM, kind) + openingWizard/close/wizardPickKind/
confirmOpeningWizard; голый-тап ветка стоит ПОСЛЕ перехвата перетаскивания проёмов
(грабля порядка поймана до записи: иначе мастер воровал жест у существующих дверей) и
до переключения комнат; порог 14 dp к линии стены, вершины (26 dp) не перехватываются.
Верхний ряд пресетов из прошлого шага удалён — размер спрашивает мастер; подсказка
обновлена: «Тапни по стене — программа спросит размер». Строки wiz_what/wiz_size/
wiz_place ×4, place_opening_hint переписан ×4.

tools/check.sh — все проверки пройдены.

## Шаг ИН — «не фикс»: толщина, высота и стены — индивидуально цифрами — ВЫПОЛНЕН

Запрос: «и с толщиной стены так же, и межкомнатными, и везде где можно — не фикс, чтобы
индивидуально всё».

**Толщина.** Рядом с чипами-пресетами (5–40 см) — свободное поле «Толщина, см» (2–60):
общая толщина в «Комнате» и толщина КАЖДОЙ стены отдельно в «Поверхностях» (поле показывает
свою либо общую) — гипсокартон 7, кирпич 12, несущая 38 и любой нестандарт. Межкомнатная
перегородка настраивается персонально как любая стена.

**Стена в один тап.** Диалог по тапу на метку размера («Стена N — длина») получил второе
поле «Толщина, см»: длина и толщина конкретной стены правятся в одном месте; «Применить»
пишет обе.

**Высота.** В 3D рядом с чипами 2.4/2.7/3.0 — свободное поле «Высота стен, м» (1.8–4.0);
ряд стал скроллируемым. Правило №2 поймало отсутствующий импорт horizontalScroll в
View3DScreen — добавлен вместе с rememberScrollState.

Аудит «что ещё фикс»: плитка/шов/плинтус/запас/проёмы — уже свободные поля; мебель
тянется жестом. Осталось глобальной высота стен (одна на квартиру) — per-room высота
отмечена в бэклог. Строка thick_lbl ×4. tools/check.sh — все проверки.
