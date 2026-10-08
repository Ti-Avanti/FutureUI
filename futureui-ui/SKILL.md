---
name: futureui-ui
description: 为 Minecraft FutureUI 创建、修改和检查自定义菜单。将自然语言需求转换为可加载的 Canvas/Dialog YAML、交互动作、多语言和像素资源配置；适用于服务器主页、商店、详情页、设置和表单。遵循目标插件版本的真实能力，不生成网页编辑器或虚构控件。
---

# FutureUI 自定义界面

以配置、语言文件和资源源文件交付界面。默认采用基岩版像素风格；用户明确指定的风格优先。普通菜单制作不需要修改 Java 源码。

## 开始前

1. 确定用户的目标：新建界面、修改现有界面、排查配置，还是仅要设计方案。仅设计时不写文件。
2. 读取目标 FutureUI 版本、已有菜单及其模板、`assets/theme.yml`、菜单指定的 `assets/themes/<主题>.yml`、`assets/images.yml`、语言设置和相关依赖。只读取与需求有关的文件；没有本地服务器时使用本 Skill 的参考基线。
3. 先读 [能力清单](references/capabilities.json) 的 `baseline`、`scope` 和渲染器限制。该清单对应一份具体源码快照，不能当作所有新版本的保证。版本不同或源码指纹不同，先核对目标实现；有 CodeGraph 时优先使用，无需为了使用本 Skill 安装它。
4. 明确页面结构、菜单之间传递的变量、业务操作和交付文件。价格、奖励、实际传送命令等关键业务信息缺失时询问；可逆的布局选择可按现有主题决定。沿用当前环境要求的提问工具。

## 按任务加载参考

| 任务 | 读取 |
|---|---|
| 所有菜单的结构、控件和布局 | [布局与风格](references/layout-and-style.md) |
| 权限、复杂操作、复用函数、交易 | [动作与条件](references/actions-and-conditions.md) |
| 商店、购物车、物品提供器、BasicTool / FotiaCosmetic 可选菜单接管 | [商店与集成](references/commerce-and-integrations.md) |
| FotiaChat 聊天颜色菜单接管 | [FotiaChat 颜色菜单](references/fotiachat.md) |
| FotiaCrates 奖池、动画、结果和历史接管 | [FotiaCrates 抽奖菜单](references/fotiacrates.md) |
| FotiaTags 称号、创建及动态效果菜单接管 | [FotiaTags 玩家菜单](references/fotiatags.md) |
| 变量、PAPI、多语言、输入与持久化 | [变量与语言](references/variables-and-language.md) |
| 图片、主题、字体、资源包兼容 | [资源与校验](references/assets-and-validation.md) |
| 从可用示例改造 | [示例索引](references/examples.md)，只打开对应页面和依赖 |

## 配置工作流

1. **先布局再业务**：选择 Canvas 页面或 Dialog 表单；确定左右分区、内容宽度、同级按钮规格与内容密度。默认导航采用左栏固定、右侧切换内容，商店可保留独立分类布局；只有具体的编辑操作打开输入表单。绘制内容用通用 row/column/grid/list 组合，导航模板与当前栏目变量保持一致。
2. **复用或隔离主题**：第二套外观优先使用独立 `assets/themes/<主题>.yml` 和 `layout.theme`。余额、价格、份数、页码和统计值使用 `readout` 等数值底板；可点击数字使用 `quantity`。滑块开关将状态文字、轨道和按钮分开，只有按钮响应点击与悬停。正常说明和校验错误预留同一块反馈区域。
   对简洁的基岩版界面，优先使用灰阶面板、单一选中色与少量原版像素图标；不要把复杂插画缩小后当作像素素材。图标统一留白，当前 9 像素绘制网格中，严格垂直居中要求容器高度与图标 size 的差为 18 的倍数，例如 54 高容器放 size 36。纯文字导航可避免拥挤的小图标与文字错位。
   可点击的选项入口使用可识别的按钮面、高光边和悬停态，例如“跟随客户端 >”；不要只套深色 readout 底板，让玩家误以为不可操作。只读数据、真正禁用的控件和可操作按钮应有清晰区别，恢复入口出现时也不应挤动原有开关。
3. **建立完整交互**：定义进入、切换、提交、失败、返回和关闭的行为。先区分单选、多选与普通操作：分类筛选允许多选时，每个已选按钮保持绿色，再次点击取消该项；排序这类互斥选项保持单选。清空同时重置实际筛选值、选中态和分页，不能只换皮肤或回填第一项。动态数据来自实际绑定或已安装的提供器。商店调用 `purchase` 等内置操作，不以多条扣款/发货命令模拟交易。
4. **生成文件**：使用独立菜单、模板、商店、函数、语言键前缀，避免覆盖已有 ID。按需生成 `menus/`、`templates/`、`shops/`、`functions/`、`rules/`、`hud/`、`languages/` 和资源增量；不要把示例包整体覆盖服务器目录。
5. **检查**：按用户允许的验证范围运行离线工具；运行中的服务器可使用插件诊断。记录检查范围，静态通过不等于已在客户端验证。实际部署、重载、构建资源包或购买测试按用户授权范围执行。

## 必须保留的实现约束

- Canvas 支持文本、图片、按钮、toggle、进度、chart、viewport 和布局容器；chart 可绘制横条、像素环与堆叠比例。容器可用 background-image 叠放效果预览，图片按真实宽高比居中。按钮组可用列表变量、条件分支与 states 实现多选。原生文本框、数字输入、slider、checkbox、select、multi-select 放在 Dialog。Dialog 内嵌 Canvas 是展示区，按钮和输入控件放在展示区外。
- 连续横向展示使用 `viewport`，先注册 assets/viewports.yml 的静态裁切几何，再绑定小数 position 或 motion 插值；图标、文字和底板共同移动。仅用于独立 Canvas 的只读区域，按钮置于外部；字段、循环编排和刷新限制见布局参考。
- 文字底板与文字共用同一网格：单行推荐 `height: 27`、`padding: 9`、`vertical-align: center`。不要使用 18 高的独立单行底板并假定能严格居中；多行文本按实际行数 × 9 加上下留白计算，图文区域还须核对图片规格与高度差。
- Canvas 的 `height/gap/padding/min-height` 是 9 的倍数。宽度、列数、图片尺寸等具体约束见能力清单。不要写 CSS、任意坐标、原生按钮单独皮肤等不存在的属性。
- 包含动态列表或互斥可见分支的容器优先使用 `min-height`。启动校验按每个列表一个条目采样，尚无玩家上下文，互斥分支也会共同计高；不要用其中一个分支的高度写死父容器。离线工具采用相同采样规则，实际多条数据仍需按配置的条数和尺寸检查。
- 原生 Dialog 按钮没有独立的 `height/skin/disabled-skin`；统一外观由 `assets/theme.yml` 的 `native-widgets` 决定，这也会影响其他使用原版按钮的界面。
- 透明画布不通过整块底板遮盖焦点白框。复用目标插件已有的焦点过滤资源，并核对菜单宽度与覆盖层；当前方案有几何匹配范围，不是任意窗口、尺寸的通用关闭开关。细节见资源参考。
- 模板深合并对象，列表整段替换。覆盖 `components` 或 `children` 不会追加原列表。
- 显示条件与执行条件分别处理；需要强制阻断时使用组件 `requirements` 或动作 `require`。普通动作的 `condition` 不满足时是跳过该动作，不是中止整条链。
- `%PAPI%` 与 `{local.variable}` 使用有界递归解析；玩家输入默认保持字面文本。不能为了消除未解析变量开启全局 `expand-input` 或无限解析。
- 所有新增可见文案进入语言文件，保持占位符一致；沿用客户端语言和 FotiaTranslator 的选择链。默认提供 `zh_cn`、`en_us`，用户指定语言时以其需求为准。使用 UTF-8 无 BOM，格式文本加 `<!i>`，兼容 `&`、`§` 和 MiniMessage。
- 原图尺寸与 GUI 显示尺寸不同；PNG、分行整除、图片注册和资源命名遵守资源参考。保留字体/着色器的内部标记像素，不对生成资源做批量调色或压缩改色。
- 使用当前版本证实存在的扩展和客户端兼容方案。未知能力说明缺口；不要虚构配置、自动改插件源码或将计划中的覆盖层宣称为已经支持。
- 菜单打开不校验玩家的资源包加载回执，也不因缺少回执等待、重发或降级。不要生成已停用的 require-pack、pack-fallback 或 fallbacks.pack 配置；资源包分发、客户端版本支持及权限条件仍按各自配置执行。
- FotiaChat 颜色菜单由原 color-menu.yml 的 ui-engine 选择，默认 inventory。FutureUI 接管从 `/chatcolor` 进入，读取 colors.yml 并调用原 ColorManager；提供标准／紧凑布局、真实色条、权限状态和预览，具体配置及 `fotiachat:colors` 会话契约见专门参考。颜色菜单不接管物品快照，不复制原 Layout / Icons 的任意动作。
- FotiaCrates 由其 futureui.yml 的 ui-engine 选择，默认 inventory。`fotiacrates:menu` 接管玩家显示，原服务负责扣钥匙、保底和结算；动画展示已确定奖励，结果不能统一承诺已进入背包。历史异步读取，标准／紧凑布局手动切换；默认滚动区横向铺满、分别展示 7／5 格，信息置顶、操作置底。格数和缓动配置、字段、图标映射和生命周期见专门参考。
- FotiaTags 的八个玩家页面由各自 `menus/<页面>.yml` 的 `ui-engine` 选择，默认 `inventory`。`fotiatags:menu` 只适配显示和经过会话校验的动作，原模块开关、权限、扣款、退款、持久化及操作锁继续生效。标准／紧凑布局和前后缀表单共用原草稿；管理员菜单保留原界面，具体契约见 FotiaTags 参考。
- BasicTool 接管由管理员在原菜单 YAML 的 `Options.ui-engine` 选择，默认 `inventory`，可选 `futureui`。从 BasicTool 原命令进入并复用其权限、条件和业务动作；`basictool:*` 数据源和动作需要对应适配版本与有效会话，不能当作 FutureUI 内置数据源或直接打开的独立演示页。
- 面向玩家使用“恢复默认”“使用通用设置”等结果明确的文案；不要把“继承”“执行”等实现术语作为唯一按钮标签。设置仅在实际布尔值和允许值检查通过时显示滑块，轨道和说明只读；不能把缺失、未接入或不同来源的混合状态当作关闭。
- BasicTool 新设置工作区使用 `settings/panel`、`settings/picker` 与固定六分类导航，分类内容由 BasicTool 的 `settings-ui.yml` 定义。布尔直接切换，少量枚举行内选择，较多选项才进入 picker；范围选择和恢复确认在当前工作区完成。恢复按钮使用提供器返回的 `reset-click`，不要把新工作区的普通右键轮换误当成恢复操作。新主题为 `basictool-settings`，默认画布宽 540、设置每页 6 项；不声称原版客户端支持窗口尺寸自动检测。
- 图表须写清统计对象、单位与百分比含义，避免用多个图形重复同一组数据却命名为不同指标。数值底板留出垂直内边距，标签与条形分行；常规横条推荐每行 45 像素。不要在玩家设置页用无实际用途的 TAB、聊天或音量波形示意填空；保留真实选项和试听等实际操作。
- BasicTool 战绩详情的段位区固定在字段列表上方，读取当前选定记录；积分、评分与排行榜荣誉段位分开处理。来源未启用或没有记录时显示真实状态，不用演示段位代替。字段、图标和语言令牌映射见商店与集成参考。
- FotiaCosmetic 统一菜单由 main.yml 的 ui-engine 选择显示引擎。顶部第一行是时装、武器、传奇武器，第二行是具体分类独立分页。普通列表通过各自菜单的 show-unowned 控制未拥有项，默认隐藏；传奇先显示武器列表，点击后显示全部皮肤，保留图标并显示拥有状态，未拥有的皮肤不可选择。传奇分类由 FotiaCosmetic 本地配置提供，未安装 FotiaCustomItems 仍可预选；模型缺失使用明确标记的默认图标。标准/紧凑两档共用导航并记忆玩家尺寸偏好，首次默认紧凑；布局切换和返回上级位于底部；返回使用原插件 controls.back.action 的 CLOSE/PLAYER_COMMAND/CONSOLE_COMMAND 配置。箱子菜单支持角色字符重复，对应同一动作的多个点击格，默认 27 个皮肤位置。箱子菜单的一级及二级分类支持 selected/unselected 两套 material（或 id）、item-model 等物品配置；Canvas 使用 skin/图片表达状态，不把物品模型字段当作 Canvas 图片。四个分页按钮读取各自 controls.<按钮>.hide-when-disabled，默认 true，隐藏时保留空位。模型快照由外置预览配置提供，不能把模型文件名当作 image；不将手动尺寸切换描述为自动感知窗口。

## 离线检查

需要 Python 3.10+ 和 PyYAML。先检查依赖；缺少时给出安装命令，不擅自改全局 Python 环境。命令中的路径均替换为实际路径。

```text
python -B scripts/check_config.py --root /path/to/menu-output --base /path/to/plugins/FutureUI
python -B scripts/check_config.py --root assets/templates/starter
python -B scripts/check_config.py --root /path/to/menu-output --source /path/to/FutureUI-source --json
```

`--base` 只读合并已有配置以解析引用；`--source` 只读比较基线源码指纹；脚本不会安装、部署、重载或修改输入文件。检查范围与退出码见资源参考。

## 交付

给出生成文件的实际路径、菜单入口、依赖和配置合并位置。说明已完成的检查及尚需客户端确认的部分。已有服务器配置采用最小增量，资源由 FutureUI 独立生成，再按配置使用 CraftEngine 合包或 external 下载地址分发；CraftEngine 为软依赖，不把 `generated/` 当作编辑入口。

本目录可整体分发。其他大模型工具可以读取 `SKILL.md` 及按需引用文件；是否自动发现 Skill 由宿主工具决定。在 Codex 中，目录进入其技能发现位置后可使用 `$futureui-ui`；仅放在普通项目目录不会自动完成全局安装。
