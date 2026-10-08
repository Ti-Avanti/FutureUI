# FotiaCrates 玩家抽奖界面

需要包含可选菜单适配器的 FotiaCrates `1.1.21` 修订包和 FutureUI `0.0.3beta` 配套菜单。原命令 `/crate preview <crate>`、`/crate open <crate> [amount]`、`/crate history` 及实体抽奖入口继续使用原权限和业务服务。

## 配置与范围

FotiaCrates 首次启动自动生成 `plugins/FotiaCrates/futureui.yml`，默认 `ui-engine: inventory`；设为 `futureui` 后接管玩家奖池、GUI 抽奖动画、单抽／连抽结果和历史。管理员编辑器、物品拖入及奖励物品管理仍使用原界面。发布包 `menu-configs/FotiaCrates/futureui.yml` 提供已启用的安装示例，不覆盖原抽奖箱和钥匙配置。

```yaml
ui-engine: futureui
default-layout: standard
layouts:
  standard:
    menu: fotiacrates/standard
    page-size: 6
    reel-slots: 7
  compact:
    menu: fotiacrates/compact
    page-size: 2
    reel-slots: 5
history-limit: 100
animation:
  enabled: true
  direction: left
  frame-ticks: 1
  travel-slots: 12
  slowdown-power: 3.0
  landing-spread: 0.35
  avoid-adjacent-duplicates: true
  hold-ticks: 20
  max-seconds: 15
icons:
  fallback: paper
  crates: {}
  rewards: {}
  materials:
    diamond_sword: diamond_sword
    iron_chestplate: iron_chestplate
```

default-layout 为 standard / compact，玩家已保存的布局优先；没有自动窗口尺寸检测。每页条数为 1–36，修改条数须同步调整模板；历史条数为 1–500。标准画布 540×279，顶部展示钥匙、状态和保底，奖励区占满宽度、每页六列一行，抽奖次数和快捷操作位于底部；紧凑画布 252×180，每页两列一行，省略单抽及最大次数快捷按钮，保留加减次数和抽奖主按钮。奖池、历史和结果页签在同一页面切换，标准和紧凑布局的单个结果均使用全宽左图右文展示；紧凑结果卡高 72，图标 size 36，标题、名称、稀有度各占 18。连抽结果沿用分页网格，序号显示本批第几次抽取，不显示奖池概率。

animation.enabled 关闭时使用原 GUI 动画；启用时非 INSTANT 的 GUI 动画统一为横向轮盘。原实体模型、粒子及独立 physical-animation 仍由 FotiaCrates 处理。时长读取抽奖箱 animation.duration，受 1–60 秒的 max-seconds 上限约束；frame-ticks 为固定刷新间隔 1–20 tick，默认 1；direction 为 left/right，控制滚动方向；travel-slots 为全程经过的奖励格数 4–240，默认 12；slowdown-power 为 1.0–5.0，默认 3.0，以缓动曲线先快后慢，1.0 为匀速；hold-ticks 为最终落点停留时间 0–100 tick。avoid-adjacent-duplicates 默认 true，候选允许时避免展示带相邻重复；只改变演出排序，不用于中奖概率或保底计算。每档 reel-slots 可选 3、5、7、9，默认标准 7、紧凑 5；展示带预排落点，中奖物品自然进入中央，不在结束时突然替换。模板使用 viewport 和小数项位移，将图标、文字和底板一起连续横移，边缘按像素裁切。中央指针固定，停止后实际指向的中奖卡片变绿。`animation.landing-spread` 控制每次演出的随机落点偏移，范围 0–0.4，默认 0.35，单位为单格步长比例；0 恢复居中。落点在演出开始时采样一次，纳入同一条减速曲线，不在结束时突然对齐中心。默认两档布局将指针保持在中奖卡片内部；自定义增大卡片间隙时应减小偏移。该随机数只控制显示，不改变奖励或概率。标准和紧凑默认菜单均不显示轮盘下方的动画进度条，沿用内容区最小高度，使抽奖和结果切换时底部操作位置稳定；顶部保底进度独立保留。区域几何在 FutureUI/assets/viewports.yml，标准和紧凑区域分别为 crates_standard、crates_compact；不通过给整页加背景来裁切。默认标准图标 36、紧凑图标 18，增大格数时须检查可用宽度与文字。连抽轮盘展示首个显示奖励，结果页按实际奖励展示整批。连抽演出仍遵守抽奖箱 multi-open.animation.enabled，发奖不依赖动画开启。

调整单个抽奖箱的滚动时长，应修改 FotiaCrates `crates/<箱子ID>.yml` 的 `animation.duration`，然后执行 `/crate reload`；`futureui.yml` 的 `max-seconds` 只限制上限，不是实际时长。普通宝箱随包示例使用 `duration: 8`（8 秒滚动），默认 `hold-ticks: 20` 再停留 1 秒后进入结果页；已有服务器配置须自行调整，不会被新版默认文件覆盖。

先在 FutureUI `assets/images/*.yml` 注册图片，再填写图标 ID。icons.materials 使用小写 Material 名；icons.rewards 的键是 `抽奖箱ID/奖励ID`，优先于材质映射；未知材质使用 fallback。icons.crates 提供 page 的 crate-icon 字段供自定义模板使用，默认顶部以文字展示抽奖箱名。默认资源增量 `assets/images/fotiacrates.yml` 注册钻石剑和铁胸甲，其余复用已有图标。不要把物品插件 ID 或模型文件名直接当作图片 ID。

## CE / IA 自定义奖励图标

当前 Canvas 绘制注册的位图，不从 CE / IA 物品 ID、ItemStack、CustomModelData 或 item-model 自动生成模型缩略图。没有奖励专属映射时仅按基础 Material 查找，多个自定义物品使用同一种基础材质会显示相同图标。

将对应的 PNG 缩略图放到 `plugins/FutureUI/assets/textures/crates/custom_sword.png`，在 FutureUI 注册后，再让 FotiaCrates 引用其图片 ID。以下示例需要实际 PNG，并按真实箱子 ID、奖励 ID 合并：

```yaml
# plugins/FutureUI/assets/images/crate_custom.yml
images:
  custom_sword:
    file: crates/custom_sword.png
    height: 32
    ascent: 28
```

```yaml
# plugins/FotiaCrates/futureui.yml 的合并增量
icons:
  rewards:
    common/custom_sword: custom_sword
```

素材与字体由 FutureUI 构建，沿用已配置的 CraftEngine 合包或 external 分发；FotiaCrates 不需要另建材质包目录或下载配置。物品在手中或背包中的模型仍由原物品资源包提供，菜单缩略图只是它的独立展示图片。3D 模型需要准备对应视角的 PNG，不要直接把模型展开贴图当作完整物品图标。

## 扩展契约

数据源与动作均为 `fotiacrates:menu`，要求原入口建立的 `fotiacrates.session` 令牌；直接 `/fui open` 不能建立可抽奖会话。数据源使用 cache: false、paginate: false，分页由适配器负责。

| view | 字段 |
|---|---|
| page | name、crate-icon、view、feedback、busy、idle、keys、amount、maximum、can-draw、can-less、can-more、can-layout、can-preview、can-history、can-results、page、pages、count、previous、next、empty、loading、progress、reel-slots、reel-position、reel-direction、history-other、history-name、has-pity、pity、pity-target、pity-progress、pity-rarity |
| entries | image、name、rarity、rare、detail、mode、history；奖池／结果另有 obtained；draw-index 在结果中为本批 1 起始序号，其余视图为 0 |
| frames | image、name、rarity、rare、selected（停止后的中奖位置） |

name、rarity、detail 等文本是组件；image 为注册图片 ID。page.view 为 preview / history / spin / results；feedback 为 ready / preparing / spinning / braking / revealed / settling / complete / failed。entries.mode 为 percentage / weight / hidden / results / history。数字仍需数值底板。frames 在演出中返回 reel-slots + 2 项，首尾各预留一个过渡项；reel-position 为当前窗口内 1..2 的小数项位移，跨整格时窗口推进，最终保留随机的小数落点，不强制回到 1。frames.selected 以实际中奖项在当前窗口中的索引计算，不能把列表中间项直接当作中奖项。不能再把 frames 当作仅三格或固定等宽 row 渲染，否则会恢复成跳格。

operation 支持 opened、closed、tab、previous、next、less、more、maximum、layout、draw、close。tab.id 为 preview / history / results；layout.id 为 standard / compact；draw.id 为 single 或 selected。次数受钥匙总量、该箱连抽开关及上限约束，最终执行再由原 OpenCommand、CrateOpenService、MultiOpenService 校验。

## 行为约束

- 原服务负责权限、扣钥匙、概率、替代奖励、不重复抽奖、分档保底、持久化日志及发奖。动画只展示已提交结果，展示帧不参与中奖计算。结果使用实际奖励，可能由原系统补发，不能统一写成“已放入背包”。
- 沿用原奖池顺序与百分比／权重／隐藏设置。百分比是原预览的基础权重口径，不宣称包含个人保底、权限替代等条件后的即时概率；已收集奖励独立标记。
- 保底显示距触发最近的一档，读取该档独立计数及稀有度，不用旧共享计数器代替。
- 玩家 API 与状态更新在主线程；历史查询异步完成，迟到结果仅更新有效历史会话。查看他人记录重新校验 fotiacrates.history.others。
- 默认关闭按钮使用单一内置 close 动作，on-close 交由适配器结束展示会话；同一 Canvas 会话重绘不会使这类无条件关闭按钮失效。自定义扩展 close 动作仍走正常帧校验。
- 正在提交、演出或结算时禁止重复抽奖和切换布局。关闭或断线不重新抽取、不重复发奖；结果在原会话锁释放后恢复操作。
- 依赖缺失或客户端／菜单渲染能力不可用时保留原界面；权限和打开事件拒绝不通过回退绕过。无资源包回执门禁。
- 通用文案位于 languages/zh_cn/integrations/fotiacrates.yml 和 en_us 同路径，使用 FutureUI 客户端语言及 FotiaTranslator 链；箱子及奖励名称读取原配置。
- visible 与 states 同时存在时，状态深合并可能覆盖 visible；需要独立外层条件的空态使用容器包裹，状态放在内层文本。

资源在 menus/fotiacrates/ 与 templates/integrations/fotiacrates/，JAR 自动安装缺失文件，发布包另附同源配置；已有自定义文件按差异合并。
