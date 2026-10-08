# 布局与风格

本参考对应 `capabilities.json` 的源码基线。字段白名单仅表示核心校验器接受该名字，不表示每种组件都实现了该字段。

## 页面骨架

```yaml
id: custom_home
renderer: canvas
title: '@menus.custom.home.title'
permission: futureui.use
layout:
  type: column
  width: 540
  padding: 0
  gap: 9
  theme: demo
components:
  - id: workspace
    type: row
    gap: 9
    children:
      - id: navigation
        type: column
        width: 126
        gap: 9
        children: []
      - id: content
        type: column
        skin: surface
        padding: 9
        gap: 9
        children: []
```

这段是结构片段，实际页面应补齐内容、语言键和 `assets/themes/demo.yml`。完整示例在 `assets/templates/starter`。

共享导航默认保持相同的宽度、按钮位置与栏目顺序；切换时右侧替换为目标内容，当前导航项高亮。使用菜单 `bindings` 分别记录当前栏目和当前页面，避免继承上一页的选中状态。在栏目主页重复点击只刷新；购物车等子功能页高亮所属栏目，但点击该导航仍应回到栏目主页。商店的分类导航可单独设计。不要让“设置”导航直接打开整页表单；先显示设置内容，点击昵称或编辑按钮后再进入输入表单，保存通过 `back.values` 回传。

`row` 先扣内边距、子项固定宽度和间距，剩余宽度平均分给未指定 width 的子项；`grid` 等分列宽。不要让固定宽度之和超过可用宽度。`align` 为子项水平对齐，`text-align` 为文字对齐，`vertical-align` 为纵向对齐，合法值均为 `start/center/end`。

## 渲染器与控件

| 使用场景 | 组件 | 配置重点 |
|---|---|---|
| Canvas 自由布局 | row、column、grid、spacer | children、width、height、padding、gap、columns |
| 文本与数字 | text | text、skin、padding、text-align、height |
| 大图 | image | image 为注册 ID，fallback-image、size；默认 size 36/72/144 |
| 普通按钮 | button | text、actions、requirements、enabled、states、tooltip；1.1.3 支持 icon、icon-size、icon-gap、text-align |
| Canvas 多选按钮组 | list + button | 列表保存选项 ID；条件分支决定增删，states 逐项显示选中皮肤 |
| 开关 | toggle | key、persist、text；由渲染器实现切换，Canvas 可使用 skin |
| 进度 | progress | value 使用 0..1 的比例；fill-skin、track-skin 按现有主题 |
| 数据图表 | chart | chart-type: bars/ring/stacked；series、max、row-height、value-skin、empty-text |
| 场景预览 | 容器 + background-image | background-size 为图片显示高度，内容叠在图片上；文字保持独立可翻译 |
| 动态内容 | list | source、item、layout、page-size、paginate、cache |
| 连续横向展示带 | viewport | region、height、visible-items、position 或 motion、direction、gap、children |
| Dialog 文本 | text | 可用有背景的文本板；长文案交给文本换行 |
| 原生文本输入 | text-input | id 对应 input.id；label、initial、max-length、validation；可 multiline |
| 数字输入 | number-input | min、max、step、initial、integer，提交校验 |
| 滑块 | slider | min、max、step、initial；来自原生 Dialog |
| 勾选 | checkbox | label、initial |
| 单选与多选 | select、multi-select | options: value/label；多选有 min-selected/max-selected |
| 表单展示分区 | canvas（嵌入 Dialog） | width、layout、children；只能放展示组件 |

`item/slot/shop` 等虽出现在核心组件列表，也不能直接写进 Canvas。Canvas 展示商品使用 `list + shop-products + image`。库存输入槽属于不同渲染路径，不能以其存在推断自由画布支持拖放物品。此 Skill 默认生成 Canvas/Dialog 菜单，不生成箱子菜单。

多选组中每个已选项均使用绿色 `selected` 皮肤，未选项使用普通按钮皮肤；再次点击只取消当前项。选中态由实际列表成员决定，不能用“最后点击的 ID”代替多选列表，也不能以字符串 contains 判断 ID，避免 `food` 误匹配 `seafood`。互斥排序使用一个标量值，最多一项选中。悬停、当前导航和主操作强调色是不同语义；清空筛选只清除筛选项的选中状态。商店完整配置见商店参考和 `demo_category`。

## 尺寸与布局

- Canvas 的 height、gap、padding、min-height：0..2043 且是 9 的倍数。实际可读文本/按钮需要正高度。
- 组件 width：1..1024；columns：1..9；布局嵌套不超过 20 层。
- 单行 Canvas 按钮和数值底板推荐 27 高；文本一行基础高 9，9 像素上下内边距可形成对称留白。绘制纵坐标落在 9 像素网格，18 或 36 高的单行区域无法严格对称居中，不能只写 vertical-align: center 就忽略网格。多行文字的高度按实际行数 × 9 加上下留白计算；例如双行 18 加 9+9 留白为 36 高。卡片可把多行放在共同面板内，各自使用 9 高文本行及明确间距，避免为每行叠不对称底板。
- 子容器写死 height 时必须装得下 children；顶部和底部 padding 都计入。
- 启动校验会给每个 list 展开一个 item 样本及 after-items，保留列表的 layout、尺寸和间距；此时没有玩家上下文，visible 互斥的兄弟节点仍会同时计入高度。动态内容的父容器优先设置 min-height，不要把 height 写成某一个可见分支的高度。例如 scope_area 的按钮和说明各高 27，静态合计为 54，父容器应使用 min-height: 27；实际只显示一项时仍保持 27 高。离线检查器采用相同单项样本规则，不能由静态通过推断任意数据条数都能放下。
- 图片显示高度要在主题 `canvas.image-sizes` 中生成；默认最大 144。注册 height 不能代替显示 size。
- 同一组菜单优先共享模板宽度。示例导航页使用 540 宽（左栏 126、间距 9、右侧 405）；商店/订单页复用 666 宽模板。实际编辑表单按内容选择尺寸，不将所有表单强制拉成同一高度。这些是示例设计值，不是插件强制值。
- 大画布、小窗口、GUI 缩放会导致滚动；滚动也可能影响基于着色器的悬停呈现。必须在目标客户端检查，不能承诺 CSS 式自适应。
- 文本长度受字体、语言和 PAPI 结果影响。数值预留较长金额的空间；中文和英文分别检查，不能只按字符数证明不溢出。

## Dialog 表单

```yaml
renderer: dialog
layout:
  columns: 2
  button-width: 314
  panel-width: 698
  panel-height: 369
  exit-width: 200
  exit-button: true
  resize-for-errors: false
  validation-feedback:
    width: 630
    skin: readout
    padding: 9
    min-height: 36
    show-label: false
    text: '@menus.custom.form.hint'
```

原生按钮可指定 width，不能指定 height、skin 或 disabled-skin；原生 row/column/grid 不能用 skin、height、padding、gap 做自由布局。使用嵌入 `type: canvas` 的展示区，然后在外部放实际输入和提交按钮。返回/取消按钮通常设 `validate: false`，使无效输入不阻止返回。

number-input 的 initial 必须满足范围与步长；step 大于 0，整数模式下 min/max/step/initial 均为整数。select 的 initial 必须匹配 option.value。输入 max-length 的基线范围是 1..32767；multiline 仅适用于 text-input。

## 主题规则

优先使用已存在的 skin：`surface` 内容板、`header` 标题栏、`well` 图片槽、`readout` 只读数值、`quantity` 可点击数量、`navigation` 导航、`button` 普通操作、`purchase` 主操作、`selected` 选中、`disabled` 禁用。

数字底板与背景应有清晰明度差；所有按钮保留一致的边框厚度，不加不对称黑边。长 tooltip 会遮住操作时优先减少说明并使用原有悬停高亮，避免把标题重复显示成浮层。

当前基线的独立主题位于 `assets/themes/<名称>.yml`，名称只允许 `[a-z0-9_-]+`。通过菜单 `layout.theme: <名称>` 选择；不指定时使用全局 `assets/theme.yml`。`layout.theme` 是静态名称，不使用占位符。嵌入 Dialog 的 Canvas 可通过自己的 `layout.theme` 覆盖，否则沿用菜单主题。

独立主题的 `canvas.styles` 定义本主题皮肤；`skin-aliases` 将通用名字映射到本主题已有皮肤。本地没有对应皮肤时才回退到全局同名皮肤。别名不能指向不存在的皮肤，也不能指望别名无限转发。独立主题隔离 Canvas 外观及 Dialog 底板；原生按钮贴图仍由全局 `native-widgets` 控制。

按钮图标的 `icon` 引用注册图片 ID，`icon-size` 默认 18，`icon-gap` 默认 6；图标规格必须已生成。所有主题的 `canvas.image-sizes` 与全局规格取并集生成。`canvas.interaction.hover-heights` 可按实际按钮高度声明 9..252 的 9 倍数；这是主题资源设置，不是按钮字段。保持同级按钮 27 或 36 高，文字基线以 9 为单位对齐。

## 只点击按钮的开关

普通 `type: toggle` 会把整块控件作为按钮。需要基岩版滑块样式时，使用 [demo_switch.yml](../assets/templates/starter/templates/demo_switch.yml)：状态文字、左右半槽分别为组件。开启时左槽显示绿色 I，右侧为浅灰按钮；关闭时左侧为浅灰按钮，右槽显示 O。只给浅灰块覆盖 `type: button` 和 `toggle` 动作，状态槽保持 `type: text`，不挂空动作伪装只读。

示例通过单项 `source: static` 列表传入 `{item.key}` 和 `{item.on}`，列表自动为模板内部 ID 添加前缀，允许在同页复用。控件标题独立使用语言键，不能把 `@语言键` 当普通变量再期望自动查语言文件。示例设置页同时展示两次复用及持久化： [demo_settings.yml](../assets/templates/starter/menus/demo_settings.yml)。

## 模板、列表和状态

模板由 `templates/` 相对路径（去 .yml）命名，模板文件中的 id 不改变模板名。对象递归合并、列表替换；不要假设继承会自动拼接 children。

列表项使用 `{item.xxx}`；page 是菜单共享分页状态。分类导航设置 `paginate: false`，可变内容设 `cache: false`；按实际需求设置刷新频率，避免高频 PAPI 刷新。

`states` 按 priority 从小到大选第一个 visible 成立的状态，同优先级保留原顺序；没有状态匹配时组件隐藏。状态与基础组件深合并，列表整段替换，可改变 `type`、`text`、`skin` 和动作。保留一个 `visible: true` 回退状态可避免无匹配时消失。

每个合并后的状态仍须符合渲染器限制：Dialog 展示 Canvas 不能借状态切成按钮，原生按钮也不能借状态添加 height/skin。普通组件 ID 在一个菜单中唯一，仅包含字母、数字、下划线；列表运行时会展开项 ID，不要手写任意未经证实的动态组件 ID。离线工具逐个检查有效状态，不会把互斥分支当成同时可见内容。

## 真实数据图表与预览

`chart` 是只读展示组件，不执行数据库查询。`series` 可为列表，或完整变量引用（例如 `'{item.comparison}'`）返回的列表；每项含 `label/value`，可选 `display/skin`。数字与标签仍来自业务提供器或配置，示例数值必须明确标注。缺失、负数、无效或非有限数值会跳过，总和为 0 时显示 `empty-text`，默认 `@messages.chart-empty`。

```yaml
- id: mode_chart
  type: chart
  chart-type: bars
  height: 135
  row-height: 45
  series: '{item.comparison}'
  fill-skin: chart-main
  track-skin: readout
  value-skin: readout
```

上例的 `comparison` 由实际数据源提供，皮肤也须在使用的主题中注册。图表最多 32 项；bars 保留每行 27..144 高的配置兼容（9 的倍数），常规布局推荐 45 或 54。数字底板高为 `min(27, row-height - 9)`，标签与数值垂直对齐，条形高 9 像素，行高至少 45 时在条形后保留 9 像素行间距；超过高度容量的行不显示。max 留空使用当前序列最大值。

ring 使用连续的 72×72 像素环，中心数字底板为 54×27；默认显示首项占总量的百分比，四舍五入到一位小数并去掉无意义的末尾 0，可用 `text` 指定动态值。标题或说明必须明确中心数字的含义。右侧图例区域至少 99 像素时同时显示标签与数值，因此完整数值图例建议图表宽度至少 180；较窄时仅显示可容纳的标签。

ring/stacked 图例优先使用 36 像素行距；高度不足时压缩为 27 或 18。stacked 至少 45 高，完整图例推荐 `27 + 36 × 项数`，兼容的紧凑下限仍为 `27 + 18 × 项数`。图例数值底板为 18 或 27 高，不用 9 像素条承载数字。图表默认高 108；bars/stacked 最小宽 90，ring 最小宽 72。直接放在原生 Dialog 中会降为文本摘要；要保留图形应使用展示 Canvas。

`background-image` 可写在 Canvas 容器上，`background-size` 默认为 72，与普通 image 一样需要已生成规格和足够的宽高。图片先绘制，子组件后绘制，图片自身不绑定点击。例如预览图上叠放按设置可见的名字和记分板，标明“效果示意”，不要声称这是即时世界截图。

`layout.exit-button: false` 隐藏画布外原生关闭按钮，使用无按钮的 DialogList 外壳；仍可按 Esc 关闭，也可配置画布内 `close` 动作。不要用空 MultiAction 代替，Paper 会拒绝空操作列表。

### 动态像素快照

`raster` 用于 Canvas 中的像素预览，也可放入 Dialog 内嵌 Canvas 作为只读展示。`value` 必须是扩展数据源提供的 `gg.fotia.futureui.api.RasterImage` 对象，不能填写 PNG 路径、URL、Base64 或模型 ID 充当对象。完整变量保留其类型，例如 `value: '{item.preview}'`。明确指定 `width/height`，高度沿用 9 像素网格，默认高 108。

快照宽高均为 1–192，使用不可变 ARGB 像素副本；透明度低于 128 的像素不绘制。等比居中、最近邻缩放，放大时取整数倍且不超过 4，缩小时按可用尺寸采样。绘制将每行不重叠的同色片段分组，每种颜色只发送一次样式，共用独立的 `futureui:raster` 字体，不占用普通图片字形编号；快照更新不触发资源包重建或重新下载。首次使用该能力需要更新并加载包含 raster 字体的资源包。

普通 Canvas 内可为 `raster` 配置 `actions`，使整个缩略图区域可点击；按钮底板、悬停与禁用状态使用 `skin/disabled-skin/enabled/requirements`，沿用会话和权限校验。Dialog 内嵌 Canvas 禁止携带动作。名称和真实穿戴状态建议放在图片下方，不以额外文字按钮代替缩略图点击。交互高度需要主题生成对应的 `canvas.interaction.hover-heights`，例如缩略图高 72。

缺失或无效类型的 value 不绘制图片，可用 `empty-text` 显示占位文字。用明确的加载、未配置模型、默认皮肤等状态处理空值，不把空预览伪装成实际外观。文件读取、皮肤获取和模型计算应由扩展在后台完成，渲染阶段只接收已生成的快照。它是服务端生成的静态投影，旋转由重新生成快照实现，不是嵌入客户端实体渲染器，也不保证任意动画、着色器模型或自定义物品自动适配。

`layout.exit-button` 也参与悬停定位：有退出栏和无退出栏采用不同的原生正文尺寸，随附着色器以已登记正文宽度、原生一像素边线与水平位置识别焦点框，不依赖固定纵向位置；GUI 尺寸向上取整，窄窗口考虑原生容器左侧夹紧，不需要开启背景来遮挡。更新此能力必须同时更新插件和共享 shader 资源。进入原生滚动区时当前实现会隐藏高亮以避免错位，因此常规 Canvas 应按实际 GUI 尺寸控制高度；无退出栏建议满足 `画布高度 + 56 <= GUI 高度`，有退出栏为 `画布高度 + 74 <= GUI 高度`。

原生单画布容器除 `layout.width` 外额外需要 52 像素水平空间。以不裁切容器为目标时，应满足 `画布宽度 + 52 <= GUI 宽度`；不要用物理窗口像素直接代替 GUI 逻辑像素。配置中改变画布宽度时，按资源参考核对 `canvas_focus.glsl` 的正文宽度列表（画布宽度 + 32）；此检测属于资源包，不是主题背景。

## 连续横向滚动

`viewport` 用于独立 Canvas 的只读展示区域，支持图片、文字、底板、进度、图表、raster、布局和动态列表；移动区域内不放按钮、输入框或嵌套 viewport，操作控件放在外部固定区域。它不是鼠标滚轮列表，也不是 Dialog 的原生滚动条。

先在 `assets/viewports.yml` 注册裁切几何（最多 64 项）：

```yaml
demo_scroll:
  canvas-width: 540
  left: 0
  width: 540
```

canvas-width 是整个菜单画布宽度，left 是该区域相对画布的实际左边缘，width 是实际分配宽度；均为静态整数，区域必须落在画布内。相同几何可以跨菜单复用，右侧分区可配置对应的非零 left。插件会在绘制时核对布局与注册几何，错配会报错，不会静默显示错位内容。修改区域后需要 `/fui reload` 并加载新资源包。源着色器保持可编辑，生成器在共享 `fui_position`/`fui_fragment` 接口接入裁切；自定义共享着色器须保留这些接口。不得清除生成的低 alpha 锚点像素。

```yaml
id: gallery
type: viewport
region: demo_scroll
height: 81
visible-items: 3
direction: left
gap: 9
motion:
  from: 0
  to: 5
  duration-ticks: 120
  interval-ticks: 1
  easing: linear
  loop: true
children: []  # 填入卡片或不带 layout 的 list；完整配置见 demo_scroll.yml
```

- height 为固定区域高度，必须是正的 9 的倍数；每个实际子项必须装得下。父布局按此高度计量，横向内容不撑大整页。
- visible-items 为 1–32 的整数，决定同屏容量，gap 仍为 9 的倍数；按区域宽度均分卡片宽度。只读条目最多 256 项，画面外的完整卡片跳过绘制，边缘的图标、文字和底板逐像素裁切。
- direction 为 left（向左移动，列表从左到右排列）或 right（向右移动，列表从右到左排列）。
- `position` 为小数项位移，1 表示移动一个卡片宽度加间距，允许变量，范围 -65536..65536。由提供器更新位置时使用它，刷新频率由提供器或菜单配置控制。
- `position` 与 `motion` 二选一。motion 以本次菜单会话打开时间为起点；from/to 支持变量，duration-ticks 为 1..12000、delay-ticks 为 0..12000；easing 支持 linear/ease-in/ease-out/ease-in-out，loop 默认 false。interval-ticks 必须为静态整数 1..20，默认 1，自动参与菜单刷新。重新打开菜单才重置计时，普通刷新不重置。
- 循环到终点会回到起点。要无缝循环，条目末尾需接上首屏相同的内容；不要把任意不重复列表写成“自动无缝”。starter/demo_scroll 已展示这种编排。
- 位移按像素更新；服务端默认最高每 tick 一次（正常 20 TPS），不承诺客户端帧率插值或 60 FPS 动画。保持整个菜单宽度、固定控件位置和资源包版本覆盖层一致。
