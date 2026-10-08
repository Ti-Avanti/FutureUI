# 资源、版本和校验

## 源素材与生成产物

长期维护的源文件位于 FutureUI 数据目录的 assets/：images.yml 注册图片，images/*.yml 为图片增量，theme.yml 定义全局外观，themes/<名称>.yml 定义独立主题，pack.yml 提供内容包元数据，textures/ 保存 PNG。图片增量按文件名排序合并，最后由 images.yml 覆盖同名配置。读取 versions.yml 获取当前覆盖层配置，保持既有分发方案。

generated/resourcepack 是完整资源包目录，generated/futureui-resource-pack.zip 是独立可分发 ZIP；它们始终由 FutureUI 生成。选择 CraftEngine 后端时再发布合包副本，external 后端不发布到 CraftEngine。不要直接修改生成产物来交付长期菜单定制。对 images.yml/theme.yml/config.yml 等共享文件提供键级合并增量；新增普通菜单文件可单独安装。

字形分配保留 generated/glyph-map.json 中的历史编号，优先使用 U+E000–U+F8FF，用满后转入 U+F0000–U+FFFFD 补充私用区。新增主题不会再受所有资源共用 6,400 个编号的限制；输出为完整 Unicode 码点，不能按单个 UTF-16 char 截取。相同美术风格优先复用已有主题，避免重复生成皮肤；资源更新后仍需分发新资源包，不能仅复制 glyph-map.json。

本次能力快照对应版本标记 `0.0.3beta`；独立主题能力由此前 1.1.3 引入。当前默认菜单分目录保存于 menus/standard 与 menus/compact，显式 id 仍决定菜单身份，不能仅因文件搬目录就给 open-menu 目标添加目录前缀。DefaultMenuMigration 会处理清单内的旧默认菜单文件并保留修改；自定义示例使用 demo_ 前缀，避免进入默认文件名迁移范围。

自 `0.0.2beta` 修订包起，启动及完整重载前会修复已发布的 BasicTool 设置模板：仅匹配 `templates/integrations/basictool/settings-panel.yml` 中已知的 scope_area 固定高度片段，将 height: 27 改为 min-height: 27；同目录 settings-panel-row.yml 的 panel_setting_row 将 height: 36 改为 min-height: 36。其余内容和换行保持不变，原文件备份到 `generated/migrations/template-layout/<唯一编号>/<原文件名>`。重复加载不会反复修改或备份；已改变该片段结构或已配置 min-height 的自定义模板不自动覆盖，应按布局参考核对动态分支的尺寸。

## 可选分发后端

CraftEngine 是软依赖；packetevents 仍是硬依赖。config.yml 的 `resources.backend` 支持：

| 值 | 行为 |
|---|---|
| auto | 默认值；已启用 CraftEngine 时使用其合包分发，否则使用 external |
| craftengine | 明确使用 CraftEngine；未启用时给出配置错误，不静默切换 |
| external | FutureUI 生成 ZIP，通过配置的 HTTP(S) 直链发送；不调用 CE 构建或发送命令 |

```yaml
# config.yml 合并增量；替换为实际托管地址。
resources:
  backend: external
  external:
    url: 'https://cdn.example.com/futureui-resource-pack.zip?v={sha1}'
    verify-pack-path: ''
    send-on-join: true
    required: false
    prompt: '@messages.pack-prompt'
```

`/fui pack` 生成独立 ZIP，并在消息中显示路径及当前后端；`/fui status` 可查看后端、客户端协议、覆盖层和资源包加载状态。external 不自动上传，也不启动 HTTP 服务。将 ZIP 上传到实际托管服务，直链必须提供与本地校验文件相同的内容；支持 `{sha1}` 和 `{uuid}` 地址占位符，客户端 UUID 按文件 SHA-1 推导。

`verify-pack-path` 留空时检查独立 ZIP；手动与其它资源合包时指定最终 ZIP 的本地路径，相对路径以 FutureUI 数据目录为基准。合包须保留覆盖层、字体、着色器及构建收据。`send-on-join` 控制主动发送；关闭后打开菜单也不会触发补发，由管理员安排资源包分发。`required: true` 会让拒绝资源包的客户端断开连接，按服务器需求选择。

下载地址留空不会阻止插件启动、ZIP 生成或菜单打开，但不能发送资源包。菜单不再检查玩家是否返回本插件预期的资源包加载回执，不弹出缺包提示，不排队等待，也不因缺少回执切换为原生界面。旧 `resources.require-pack`、`compatibility.pack-fallback` 和菜单 `fallbacks.pack` 已停用，旧配置保留这些键也不会恢复拦截；新配置不再生成这些选项。回执仅用于资源发送去重、诊断和成功加载后的界面刷新，不能据此推断玩家本地是否手动安装了材质包。客户端协议、版本覆盖层策略、权限、条件与打开事件检查仍然保留；管理员仍需提供对应字体和着色器，跳过回执检查不会替玩家安装资源包。

`resources.build-command/resend-command/expected-pack-uuid` 及 versions.yml 的 craftengine 段仅用于 CraftEngine 后端。重新载入配置时后端会重新选择；外部资源内容变化后须更新托管文件。菜单打开不依赖本次资源包的成功加载回执。

## PNG 与命名

- 图片应为真实 PNG，透明素材保留 Alpha。推荐 16×16、32×32、64×64 像素图；缩放使用最近邻。
- 图片 ID 只允许 `[a-z0-9_/-]+`。file 相对 assets/textures，不能使用绝对路径或 `..` 越界。建议文件名也使用小写英文、数字和下划线。
- `images.<id>.height` 是字体绘制高度，基线范围 1..256，ascent 不能超过 height；原图尺寸和菜单的 `size` 是另两个值。
- Canvas 与按钮图标的显示规格应在全局或独立主题的 canvas.image-sizes 中，范围 9..144，且是 9 的倍数。当前基线编译的是所有主题规格的并集，每个普通 PNG 都必须满足该并集的分行条件。
- 每个登记图片的有效高度 H 必须对**每一个生成规格 S**满足 `H % (S / 9) == 0`。默认 `[36,72,144]` 要求高度为 16 的倍数。普通 PNG 可设 `canvas-height: 64`，以最近邻生成符合分行要求的副本，源文件保持不变。此值默认为 0（使用原图），非零范围为 16..240，缩放后的宽度也不超过 240，以留出客户端字体图集空间；vanilla 引用不使用此字段。
- Canvas 按真实宽高比居中：`size` 为显示高度，显示宽度是 `size × 宽高比`。例如 3:1 场景以 size 72 显示，需要至少 216×72 的容器。超出分配区域会拒绝绘制；不要依赖裁切。按钮图标宜继续使用正方形素材。
- 图片中的大面积透明边会产生视觉偏移；像素图的边缘和留白应与同组资源一致。
- `CanvasImages` 与按钮图标的垂直位置会对齐 9 像素行。需要上下等距留白时，`(容器 height - size) % 18 == 0`；例如 54×54 容器内显示 36×36 图标。27 高按钮中的 size 18 无法做到严格上下等距，宜使用纯文字按钮，或将图标放到独立且对齐的容器。
- 使用原版像素材质时保留其清晰边缘与 Alpha；不要用高细节场景、渐变光照、写实纹理或缩小插画代替低分辨率像素图标。悬停覆盖应足够轻，避免盖住按钮文字。
- 不把语言、价格、余额、页码、数量画死在图片内。

```yaml
# assets/images.yml 的合并增量
images:
  custom_coin:
    file: custom/coin.png
    height: 32
    ascent: 28
```

原版素材使用 `vanilla: minecraft:item/diamond.png` 等实际路径。row-widths 用于每行有效像素宽度，16 行原版图片应准确提供 16 项；不要用全 16 冒充具有透明边的精确指标。原版图片引用不要求把 Mojang 的原图打包到 Skill。

## 资源包元数据与多版本

最终资源包 ZIP 根目录为 pack.mcmeta 和 assets/，版本覆盖层位于其声明的目录。assets/pack.yml 是源内容元数据，仍需保留；它供构建器使用，并在选择 CraftEngine 后端时发布为其内容元数据，不是 pack.mcmeta。FutureUI 生成字体的 namespace 必须保持 futureui。

普通菜单创建复用已配置的字体、着色器和分发流程。Skill 不为新增图标另建一套 core shader，也不自行扩大发送版本范围。存在 versions.yml 时，源文件中的 profiles、protocols、min/max-format、includes/shaders 等需保持一致；检查到源码存在多版本配置，不等于资源包已在所有客户端可用。

公共 overrides 路径为 `assets/overrides/assets/<namespace>/...`。支持版本覆盖层的版本还允许 `assets/overrides/<已配置覆盖层>/assets/<namespace>/...`；旧版本不一定支持，先查目标实现。不能用 overrides 伪造内部构建回执。

字体纹理中可能含着色器读取的标记色与透明度。禁止对所有生成 PNG 做调色、统一不透明化、裁边或有损优化；更换美术图标只修改自己登记的普通图像。合包出现同路径着色器冲突时需要实际合并逻辑，不能依赖最后覆盖者自动兼容。

## 透明画布与原生焦点线

`canvas.background.enabled: false` 只关闭画布整块底板；`canvas.interaction.cover-focus-outline: false` 关闭旧的边缘遮盖。它们不是客户端“禁止焦点边框”的 API。不要为了去白框擅自打开整块背景，也不要把只读区域挂上刷新动作来清除焦点。

当前源码配套资源通过各覆盖层的 `assets/minecraft/shaders/core/gui.vsh`、`gui.fsh`，配合公共 `assets/futureui/shaders/include/canvas_focus.glsl` 过滤大画布边线。对应源文件在 FutureUI 的 `assets/overrides/`。普通界面制作复用目标已有资源，不随示例包额外覆盖全局 GUI 着色器。

过滤器按颜色、线条厚度、居中位置和画布几何特征匹配，不识别插件菜单 ID。`FUI_CANVAS_BODY_WIDTHS` 中的宽度为 `layout.width + 32`；当前匹配 482、572、590、608、626、662、698。新增宽度需要核对该表；超小窗口、滚动、原版布局变化或其他包的 GUI 着色器不能据此保证兼容。不要描述为任意尺寸自动适配，也不要把截图或其他客户端的结果代替目标版本能力依据。

示例不携带焦点过滤器源码，防止覆盖目标服务器已有合并逻辑；它使用已有匹配范围中的 540、666 宽画布。独立主题和图片增量可复用，过滤器是否可用仍取决于目标资源包。

元数据规则的外部依据：[Mojang 1.21.9 Pack Metadata](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9)。跨旧格式和新格式时必须按实际客户端与构建器规则处理 pack_format、supported_formats、min_format/max_format；不在通用 Skill 内硬编码一个格式号适配所有版本。

## 离线工具

工具为 `scripts/check_config.py`，依赖 Python 3.10+、PyYAML。脚本只读输入目录，不调用网络、服务器或资源构建。没有 --base 时会把参考基线的内置图片、主题和语言键视为可用依赖；部署目标删改过默认资源时必须提供 --base。

检查范围：

1. UTF-8 无 BOM、重复 YAML 键、根结构、递归别名、重复文件 ID。
2. 对象深合并/列表替换的模板展开、模板循环、组件类型与 ID；逐个校验状态合并后的组件。
3. 核心字段名单、Canvas/Dialog 限制、已知数字范围与步长、商品 quantity/旧 maximum 冲突和预设份数。
4. 静态菜单、商店、商品、函数、规则、HUD、语言键、图片和 skin 引用。
5. 独立主题名称、局部皮肤/别名/全局回退、主题规格并集、按钮图标与悬停高度。
6. 语言占位符一致性；PNG 签名、块结构与 CRC、尺寸和分行整除。

不会验证完整 Java 语义、每个自由映射字段、全部表达式、RE2J、PAPI、第三方物品 ID、动态引用、真实字体宽度、资源包 ZIP、GPU 着色器或交易。PNG 检查不等于完整解码及像素美术验收。JSON 结果明确列出未验证范围。

`--base` 合并模拟只用于读取引用，不会写回服务器；菜单/模板同文件替换，共享语言/资源配置（含独立主题文件）对象深合并。人工部署必须采用相同合并方式。组件模板的皮肤跟随使用方菜单检查，不将未指定主题的模板误判为只能用全局皮肤。

`--source` 比较能力清单记录的源码 SHA-256；差异作为警告提示复核，不自动改能力清单。升级基线时核对 ConfigFields、渲染器、MenuTheme/资产加载、QuantityRule 和状态选择逻辑，再更新版本与指纹；只替换版本号不足以确认新能力。新增主题相关实现文件也应纳入指纹范围。

退出码：0 代表所列静态检查未发现错误（可能仍有警告）；1 为配置错误；2 为参数或依赖问题。不能把 0 描述成游戏内验证通过。

## 插件与客户端验证

- `/fui validate`：检查磁盘配置，不等于已重载到当前会话。
- `/fui inspect <menu>`：以玩家上下文检查当前已加载菜单。
- `/fui trace <text>`：查看占位符解析过程。
- `/fui dryrun <menu> [component]`：展示要求结果和动作名称，不模拟所有分支，不执行交易。
- `/fui reload`、`/fui pack` 会改变运行状态/资源构建，只在用户要求部署或相关操作时执行；单菜单重载不能代替新增语言、模板、资源的完整更新。

上述插件命令与离线脚本的结论有不同范围。配置可加载不代表字体排版、GPU 着色器、PAPI 依赖或真实经济业务已经得到验证；交付时按实际检查范围说明。
