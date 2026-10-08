# 商店和集成

## 商品模型

```yaml
id: custom_store
title: '@menus.custom.shop.title'
categories:
  - id: blocks
    name: '@menus.custom.shop.blocks'
    icon: BRICKS
products:
  - id: stone
    category: blocks
    name: '@menus.custom.shop.stone'
    item:
      material: STONE
      icon: custom_stone
    amount: 16
    price: 8
    currency: vault
    quantity:
      min: 1
      max: 128
      initial: 1
      step: 1
      presets: [1, 4, 16, 64, 128]
```

需要对应语言键与图片注册；完整样例见 starter。amount 为每份实际物品数，quantity 为购买份数，price 为每份单价。65 份、每份 16 个即 1040 个；不把单个 ItemStack 的 64 上限等同于订单总量限制。仍受最大订单物品数、背包可容纳空间、余额、库存、数量规则及购买额度限制。

商品 quantity 是静态整数规则：min 默认 1，max 缺省时读取旧 maximum（再缺省为 64），initial 默认 min，step 默认 1。max 不小于 min，initial 与每个 preset 都要落在范围内并满足 `(value - min) % step == 0`。presets 去重后最多 16 项；旧 maximum 与新 quantity.max 同时存在且不同会拒绝加载。不要把可动态定价推断成 quantity 也接受 PAPI。

修改兑换成本时使用共享变量或函数参数使说明、门槛与扣除保持一致。示例的 `demo_workspace` defaults 提供兑换成本和物品数，命名规则与事务动作读取同一组变量；成功反馈只接在事务成功之后。

折扣使用 discounts 的 condition/multiplier，价格和额度参数以目标 ShopPricing/ShopDefinition 为准。常见项包含 sell-price、daily-limit、daily-sell-limit、reset-timezone、rounding；库存或动态价格不要凭名称编字段。

## 通用布局和上下文

- 分类列表：source: shop-categories，shop: 实际商店 ID，paginate: false，cache: false。
- 商品列表：source: shop-products，shop: 同一个 ID，cache: false，page-size: 4，layout 为两列 grid。
- 商品卡片数据使用 item.name/icon/amount/price/quantity/total/total-items/currency 等；quantity-key 指向商品自己的份数变量。
- 打开详情时携带 shop、product、input.quantity；详情使用 product.name/icon/amount/price/quantity/total/total-items/balance/minimum/maximum/step/presets/available/available-maximum/status 等。
- 分类变化或排序变化后将 page absolute 设为 0；是否同时清空 shop.search 由页面交互决定，多选筛选通常保留关键词。排序使用 shop.sort；多个分页列表共享菜单 page，要评估分页交互。

### 分类多选与清空

当前源码基线支持 `category` 为一个分类 ID 或分类 ID 列表。列表中的分类按并集筛选，商品不会重复；关键词 `shop.search` 再限制商品名称或 ID，`shop.sort: price` 对整个结果按价格升序。空列表 `[]` 或空字符串表示不限制分类，分类按钮均未选中；未提供 category 或提供完全无效的非空选择时，兼容旧配置回退到首个分类。有效与无效 ID 混合时只保留有效 ID，重复项去重。此契约由 `ShopCategorySelection` 实现，旧版本应先核对源码或升级，不能只修改样式伪装支持。

`shop-categories` 每行输出 `item.id/name/icon/selected/selected-categories`。`item.selected` 是当前分类是否选中；`item.selected-categories` 是去重后的完整已选 ID 列表，空列表仍为空。后者可将从旧单选导航继承的字符串安全转换为多选列表；不要把 `'{category}'` 字符串包在 YAML 列表中冒充类型转换。

```yaml
# 菜单根节点
defaults:
  category: []
  shop.search: ''
  shop.sort: ''
# 以下为 components 中的一个分类列表
components:
  - id: category_choices
    type: list
    source: shop-categories
    shop: system
    paginate: false
    cache: false
    layout:
      type: grid
      columns: 2
      gap: 9
    item:
      id: category_choice
      type: button
      text: '{item.name}'
      height: 27
      states:
        - visible:
            type: equals
            input: '{item.selected}'
            output: 'true'
          skin: selected
        - visible: true
          skin: button
      actions:
        - type: if
          when:
            type: equals
            input: '{item.selected}'
            output: 'true'
          then:
            - type: list
              key: category
              value: '{item.selected-categories}'
              operation: remove
              item: '{item.id}'
          else:
            - type: list
              key: category
              value: '{item.selected-categories}'
              operation: append
              item: '{item.id}'
        - type: page
          absolute: 0
```

清空按钮复用以下动作，选中样式随数据自然更新。`shop.sort: ''` 表示没有显式排序选择，仍使用商品自然顺序；点击“默认顺序”才设为 `default` 并高亮该项。不要在清空后设回 default 或首个分类，造成按钮仍然选中。当前导航和主操作按钮的强调色不属于筛选选中态。

```yaml
actions:
  - type: set-variable
    key: shop.search
    value: ''
  - type: set-variable
    key: shop.sort
    value: ''
  - type: set-variable
    key: category
    value: []
  - type: page
    absolute: 0
```

完整示例为 `menus/demo_shop.yml` 和 `templates/demo_category.yml`。筛选页进入商品页时沿用 `open-menu` 的变量传递，不覆盖为单个分类；返回页面时也保留这些变量。单选分类导航可以继续 `set-variable category: '{item.id}'`，会明确替换整个选择，不能称为多选交互。

确认购买动作：

```yaml
- type: purchase
  shop: '{shop}'
  product: '{product}'
  quantity: '{input.quantity}'
  quoted-price: '{product.price}'
- type: open-menu
  menu: custom_receipt
```

quoted-price 是每份报价，不是订单总价。只有 purchase 成功后才继续进入结果页；保留默认失败中止，结果页读取 purchase.quantity/items/total。不要给交易失败加 recover 后继续展示成功。

购物车：cart-add 使用 shop/product/quantity，cart-remove 使用 shop/product，cart-clear 清空，cart-checkout 结算。列表数据源为 cart；回收列表为 sell-products（使用当前上下文 shop），回收动作为 sell，可传 quoted-price。购物车结果中的 purchase.quantity 表示订单行数，不应标为单商品份数。

对应完整配置为 demo_cart、demo_cart_receipt、demo_recycle。示例回收读取 shops/demo.yml 的 sell-price；不是仅显示售价的装饰按钮。购物车由插件保存玩家当前条目，可能包含其他商店的商品，示例不会创建一份隔离的假购物车。

## 依赖与提供器

当前基线的硬依赖只有 packetevents；CraftEngine 是软依赖。未安装 CraftEngine 时，FutureUI 仍能启动并独立生成完整 ZIP，使用 external 下载地址分发；选择 craftengine 后端或使用其自定义物品时才需要启用 CraftEngine。资源分发配置见资源参考。Vault 是经济桥接，还需要实际经济插件；PlayerPoints 为独立积分提供器；experience 使用经验货币；物品货币在 config.yml 的 currencies 中定义。

自定义物品提供器 ID 包括 craftengine、itemsadder、nexo、oraxen、mmoitems，原版为 vanilla。安装了提供插件不代表目标物品 ID 存在；读取其现有配置或目录。不要将 provider ID 与 Bukkit 插件名混用。MMOItems 还涉及 MythicLib 和物品类型信息。

item-catalog、item-providers 用于实际提供器目录；online-players 用于在线玩家；currencies、metrics、extensions、condition-checks 等按内置数据源契约使用。metrics/extensions 还受管理员权限限制。

数据源注册、物品集成或自定义动作的存在，应通过实际插件 API/运行时诊断确认。声明一个带命名空间的 type 能通过部分静态校验，不代表运行时已经注册该能力。

## BasicTool 可选菜单接管

适用于包含 FutureUI 适配器的 BasicTool Paper，以及提供 `FutureUIService.canRender`、`isOpen` 的 FutureUI 版本。BasicTool 对 FutureUI 为软依赖。管理员在 **BasicTool 原菜单文件**中逐页选择，未配置时保持 `inventory`；不是玩家个人设置，也不会改变菜单的业务数据来源。

```yaml
# plugins/BasicTool/menus/settings/overview.yml 中的 Options
Options:
  ui-engine: futureui # inventory / futureui
  futureui:
    menu: basictool/settings/overview
    page-size: 6 # 1–54；增大时需同时调整右侧布局，避免超出屏幕
    navigation-menu: overview # 同一组内的原菜单 ID，提供左侧导航
    content-icons: [] # 将原 Icons 中指定符号放入内容区，其余可操作静态项放入底栏
  permission: basictool.settings
```

`menu` 留空按 `basictool/<组>/<原菜单 ID>` 推导。缺少 FutureUI、接口或指定菜单，或者客户端不支持当前渲染策略时，使用原库存菜单；FutureUI 拒绝权限、条件或取消打开事件时不绕过检查。菜单直接打开，不因缺少资源包回执进入等待状态。各页可以选择不同引擎；动作跳转仍读取目标页配置。

内置适配布局在 `menus/basictool/` 与 `templates/integrations/basictool/`，新增安装会自动释放；已有文件不覆盖。布局保留独立 `adventure` 主题 ID，外观采用基岩版灰阶面板、绿色选中态和原版像素图标。个人设置、个人战绩、我的票券共用顶部功能页签、左侧导航、按钮规格及数值底板。颜色、间距和按钮规格修改 FutureUI 模板；原 `Icons/condition/actions`、数据配置、刷新频率、隐私与玩家设置仍由 BasicTool 负责。

`module-tabs.yml` 的三项页签通过 `player-command` 调用原 `basictool settings/stats/stock` 命令，分别检查 `basictool.settings/stats/vouchers` 权限，目标菜单仍读取管理员选择的引擎。顶部不重复显示本人名称；查询其他玩家战绩时仍显示查询对象。单页不显示无意义的 `1 / 1`，多页保留页码。

| 组与原入口 | 适配菜单 ID 后缀 |
|---|---|
| settings：`/settings` | overview、category、values、scopes、reset |
| statistics：`/stats` | overview、fotiabattleroyale、snowbyte、frostmodern、modes、details |
| vouchers：`/bt stock` | overview |

统计和券余额只显示 BasicTool 实际提供的数据；没有启用来源、没有记录、正在加载或无权限时保留真实状态。不要伪造记录使页面看起来充实。业务文字使用 BasicTool 的语言文件；通用 UI 文案在 `languages/<语言>/integrations/basictool.yml`，键前缀 `integrations.basictool`。

### 扩展契约

首次通过某组的 FutureUI 页面进入时，BasicTool 注册同名数据源与动作 `basictool:settings`、`basictool:statistics`、`basictool:vouchers`。这些不是始终存在的内置源。使用带冒号的扩展命名空间，才能携带其专有字段 `view`。数据源节点设置 `cache: false`，`view` 可为 `page/navigation/entries/controls/choices/metrics/detail`。

- `page`：单行页面信息，含 `title/subject/self/scope/has-scope/page/pages/count/empty/has-navigation/navigation-paged/has-controls/home/menu/group/source`。`self` 按目标 UUID 与查看者 UUID 比较，不依赖名称或昵称。
- 其他视图：含 `control/label/plain-label/detail/value/has-value/selected/enabled/choice/left/primary-click/right/static-action/inherited/kind`。`label/detail/value` 是文本组件，`plain-label/plain-detail` 是纯文本；`control` 是不应由配置伪造的原按钮标识。
- `left` 表示存在主操作，实际点击类型取 `primary-click`；`right` 仅在左右动作确实不同且都有动作时为 true。`enabled` 包含原编辑状态、忙碌状态和翻页边界。
- 动作 `operation`：`opened/closed/home/navigate/click/choose/select`。组件动作带 `control: '{item.control}'`，主动作带 `click: '{item.primary-click}'`；副动作 `click: right`。页面 `on-open/on-close` 必须保留对应 `opened/closed`，用于接管会话与原关闭事件的生命周期。

适配器要求由原入口创建的服务端 `basictool.session`，不能手写令牌或用 `/futureui open basictool/...` 替代原入口。动作会重新检查当前玩家、页面、目标隐私、权限、原条件和按钮快照，再调用原业务动作。列表只是展示，不用 console 命令替代设置或发券接口。

### API 打开状态

在服务端主线程调用 `canRender(player, menu)` 仅查询菜单存在和客户端渲染支持，不代表已获得权限、满足条件或加载材质包。仍须使用 `open` 执行权限、条件及打开事件检查；没有资源包回执不会阻止打开。`isOpen(player, menu, arguments)` 只匹配活动会话及所给参数子集；不再存在等待资源包的会话，不能把 `open` 返回 false 解释为正在加载资源。完成后关闭自己持有的会话，避免关闭别的插件后来打开的菜单。

### BasicTool 设置交互与可视化数据

- `entries` 设置 `inline-choices: true` 后，旧界面 1–3 项枚举在行内展示；新工作区使用提供器的 `ui_inline_max`（BasicTool `settings-ui.yml` 的 `cycle-max-options`，范围 2–4）。对应选择页必须也启用 futureui、具有访问权限，并且父按钮是直接的 `preference:` 操作；选择页由父菜单 `Data.choices-menu` 指定，省略时沿用 `values`。纯布尔设置优先显示滑块。
- `choices` 带 `control: '{item.control}'` 获取指定设置的允许选项；动作 `choose` 带 `control/choice-control`，仍执行原 values 页动作并再次校验。不能用伪造字符串跳过选项权限。
- 布尔条目提供 `switch/on/toggle-enabled`；仅持久化、无独立工作流动作、选项恰好为 true/false 且当前值明确时有效。滑块按钮使用 `operation: click, click: toggle`；状态文字和轨道不挂动作。混合原设置状态不转换成“关闭”。
- `right` 对设置条目表示当前确实可恢复：通用设置显示“恢复默认”，游戏独立覆盖显示“用通用设置”。未改动条目隐藏恢复入口。恢复动作使用 `reset-click`：旧界面为 right，新工作区为 shift_right（普通右键用于反向轮换）。`action-kind` 区分翻页、返回、适用位置、筛选、恢复分类与恢复单项，避免统一命名为“执行”。
- 设置行保留固定恢复按钮列，入口隐藏时也保留空间，避免切换状态后滑块左右跳动。分类页把适用位置与恢复分类放在一行，前后翻页放在另一行，返回总览使用左侧已有入口。
- `page` 额外提供 category-id、scope-id、category-label、scope-label、global、busy、source-id、status、status-label、season-label、mode-label、selected-key、setting.*；has-outcomes 为真时才有 stat.games/stat.wins/stat.other/stat.win-rate/stat.win-rate-fraction，最后一项为 0–1。changed-count 只统计当前页可恢复条目，不能用于宣称全分类修改数量。
- `metrics` 配置 metrics 列表（key/label/icon），只读取当前记录中存在且有效的数值。label 使用 BasicTool `{lang:statistics.fields.games}` 等语言引用。`page.comparison` 是当前页记录的数值比较，field 默认为 games，不能用它暗示全历史趋势。
- `entries/navigation` 的 icons 映射按设置 key、券种 ID 或原图标符号选择 FutureUI 注册图像；分类目录始终优先用 category，不受当前设置 key 影响；icon 为回退图像。此映射属于显示配置，不改变实际 Minecraft 物品。
- 票券 `select` 仅选择展示条目，`detail` 返回所选券的实际余额和原 lore 说明。默认选中当前页第一项；翻页后选中项不存在时重新选第一项。不会发放、扣除或核销票券。
- 适配页内置每页数量：设置目录/值/范围 6，设置条目 3，统计模式 3、详情 4、赛季 2，票券 2。调整原 Options.futureui.page-size 时同步核对布局，原箱子菜单容量不受影响。
- 设置页不展示 TAB、聊天或音量波形示意图。`setting-preview.yml` 为兼容已有引用保留文件名，仅提供真实的通知音效试听按钮；按钮只在音效分类出现，并在通知提示音开启时启用。
- 管理员统一设置由 BasicTool `settings.yml` 的条目字段 `server-controlled: true` 控制（省略默认 false）：有效值始终取该条目的 `default`，优先于旧玩家值、游戏范围覆盖与原生导入值；玩家菜单隐藏该项且拒绝玩家修改。仅适用于可持久化且没有工作流 action 的设置。本套默认将 `display.sidebar` 统一为 false，并关闭 BasicTool `scoreboards.yml` 的 `enabled`，个人设置和界面示意均不提供计分板开关。不要只隐藏 UI 而保留可开启的实际设置。

新的 `/bt settings` 工作区由支持 `PreferencePanelNavigation` 的 BasicTool 提供：

- BasicTool `settings-ui.yml` 定义最多六个分类、常用快捷项、默认分类/范围、单项外观覆盖、在线会话内位置记忆和重置确认时限。玩家设置仍保存到原来的偏好键；分类显示不改变存储语义。
- 新默认菜单为 `menus/settings/panel.yml`、`picker.yml`、`navigation.yml`；原有自定义旧菜单保留。新文件首次生成时分别沿用旧 category、values、overview 的显示引擎选择。FutureUI 对应 `menus/basictool/settings/panel.yml` 与 `picker.yml`。
- panel 为同页导航，箱子每页十二项、FutureUI 每页六项；picker 为第二层，默认每页十二项，保存成功后返回原分类/页码。范围选择用当前页面的 `panel_scopes` 状态，`Data.scope-page-size: 12` 控制 FutureUI 范围条目容量。
- page 附带 `panel_scopes/panel_modified/panel_reset/panel_has_scopes/panel_feedback/panel_reset_label/panel_reset_hint`。布尔值可能以字符串传输，使用 equals 判断；不能把字符串 false 当作真值。条目 `state_label` 表示实际状态或不可用原因；未保存成功时不展示新值。
- 筛选只显示可恢复的已修改项；批量恢复需要在确认时限内第二次点击，FutureUI 提供独立取消按钮。恢复只作用于当前分类与范围；非全局范围删除独立覆盖后使用通用值。
- 新模板 `settings-panel.yml/settings-panel-row.yml/settings-panel-control.yml` 使用独立 `assets/themes/basictool-settings.yml`：固定分类、灰阶面板与青绿色选中态、语义绿色开关。画布宽 540，9 像素网格，六个 36 高设置行。当前宽度对应已登记的 572 像素正文焦点过滤范围；页面尺寸仍需按客户端 GUI 缩放确认，不能宣称自动适配窗口。

新增资源使用 `assets/images/basictool.yml` 与 `assets/themes/adventure.yml`，不把图片注册配置写进 CraftEngine。管理员图表演示为 `chart_showcase`，明确使用样本值，与 BasicTool 玩家战绩分离：顶部显示总局数、胜局和负局，左侧切换模式对局数与占比，右侧显示胜负分布与胜率。相同数据采用互斥切换，避免把三种图形重复堆在一页。

原箱子 Data.empty 图标属于空状态，不算一张真实条目。page.empty 反映过滤后的内容；has-empty-label/empty-label/empty-description 保留原空状态文案供自定义布局使用。

BasicTool 保留原菜单配置的轮询周期，但 FutureUI 只在页面或导航快照变化、或者当前点击回调接近到期时重绘；不为相同内容不断替换点击令牌。玩家操作完成仍由 FutureUI 刷新，权限与隐私检查持续执行。

### BasicTool 段位卡

需要包含 `FutureUiRank` 的 BasicTool 适配版本。`basictool:statistics` 的 `view: page` 节点可带 `rank` 配置，默认模板位于 `templates/integrations/basictool/statistics.yml`。这不是 FutureUI 内置的段位系统，不查询新数据库，也不根据胜率重新计算游戏段位。

```yaml
rank:
  icon: golden_helmet
  ranked-categories: [ranked]
  score-fields: [points, rating, elo] # 依序取首个有效数值；真实的 0 保留
  fields:
    name: rank
    id: rank_id
    base-id: base_rank_id
    category: category
    minimum: minimum_points
    next: next_points
    progress: progress
    position: position
  tiers:
  - prefix: gold # 匹配 gold 或 gold- 开头的段位 ID
    icon: gold_ingot
    name-token: '{lang:ranked.rank.gold}'
    translation: gold
```

`tiers` 按顺序取首个匹配项。只有原名称包含 `name-token` 时才输出 `rank.translation`，交给共享模板的语言状态显示；自定义名称保留原格式。添加新的 translation 值时同时添加 `rank-name.yml` 的状态及两种语言键，不能仅声明映射。未识别的外部语言令牌回退到实际段位 ID，不显示未解析的令牌。

页面字段使用 `{item.rank.*}`：`state/name/name-field/id/translation/division/icon/has-score/score/score-field/progress-state/fraction/percent`。`state` 为 `ready/rating/unrated/casual/select/empty/unavailable`；未明确选择记录时为 select，非排位类别为 casual，仅提供数值评分而没有段位名时为 rating，不虚构段位。来源状态仍保留在 `item.status`。

晋级状态 `progress-state` 为 `next/percent/capped/leaderboard/unknown`。存在有效的 minimum/next 门槛时，从实际积分计算 0–1 的 fraction，同时提供 remaining/next；只有有效百分比时显示 percent。达到积分上限显示 capped，不代表已取得最高排行榜荣誉。实际段位 ID 与基础段位 ID 不同且名次有效时显示 leaderboard，另有 position。缺失值使用占位符，不能伪装成零积分或零进度。

共享 `rank-card.yml` 高 99，段位名称与分页字段共用 `rank-name.yml`，避免原始语言令牌出现在下方字段；段位图标为已注册的原版像素图像；所有数值有底板。默认详情为紧凑标题、固定段位卡、胜率条、四项字段和同高底栏，翻页不隐藏段位。模式页与详情页均适配右栏宽 504、高 351；增加每页条目数时需同步调整布局。`rank_showcase` 只供管理员查看明确标注的样例，不是实际玩家数据。


## FotiaCosmetic 可选菜单接管

同一发布包也包含 FotiaChat 颜色菜单的安装配置，详见 [FotiaChat 接管配置与扩展契约](fotiachat.md)。下文的文件数量仅统计 FotiaCosmetic。

FutureUI `0.0.3beta` 的 `-bundle.zip` 同时提供插件 JAR 和 `menu-configs/`。其中 `FutureUI/` 保存 BasicTool、FotiaCosmetic、FotiaChat、FotiaCrates、FotiaTags 的接入菜单、模板、语言文件与所需主题，均与 JAR 内置资源一致；`FotiaCosmetic/` 保存三个菜单配置、九种语言及空模型映射的 `futureui-preview.yml`。后者是发布时保存的配置快照，示例的 `menus/main.yml` 已设 `ui-engine: futureui`，需要支持统一衣柜的 FotiaCosmetic `1.0.2` 或后续兼容版本。按目录合并到各插件的数据文件夹；已有自定义配置先比较再合并，JAR 不会自动覆盖已有菜单或替其他插件切换引擎。FutureUI 侧配置来源是 `src/main/resources/`，FotiaCosmetic 快照来源是 `distribution/fotiacosmetic/`，后续调整集成配置时应一并复核；正常 Maven `package` 会重新生成包含配置的发布包。

需要支持统一菜单的 FotiaCosmetic 和支持 raster 的 FutureUI。显示引擎统一在 FotiaCosmetic 的 menus/main.yml 设置；menus/weapons.yml 仅提供普通武器分类和图标状态，menus/legendary.yml 提供独立于物品插件的传奇分类。

    ui-engine: futureui
    futureui:
      menu: fotiacosmetic/wardrobe
      page-size: 4
      category-page-size: 5
      default-layout: compact
      compact:
        menu: fotiacosmetic/wardrobe_compact
        page-size: 2
        category-page-size: 2

从 /fc 打开同一菜单；/fc legendary [物品ID] 直接定位传奇分类，兼容适配后的 /fci skins。第一行 main/weapons/legendary 三个一级分类，第二行具体分类横向分页；分类页和皮肤页独立。标准宽 666，左侧预览 234，右侧选择 414，间距 18，主体最小高 333；紧凑宽 252，左侧 81，右侧 162，间距 9。顶部两行各高 27，间距 9；布局切换和返回上级在底部，标准每页四项、紧凑两项。纯原版客户端不支持自动检测窗口尺寸。

普通时装与普通武器分别读取 main.yml、weapons.yml 的 show-unowned（默认 false）；关闭时未拥有项不占格，不显示屏障。进入传奇一级分类或 /fc legendary 时先显示全部启用的传奇武器，点击后显示该武器的全部皮肤。旧 show-locked 选项已移除；传奇皮肤保留图标，用 owned 和状态文案区分拥有情况，未拥有的皮肤禁止选择，持久化服务仍再次校验。传奇分类和皮肤元数据不依赖本服安装 FotiaCustomItems，名称、排序及原版兜底图标来自本地配置；安装后可补充真实武器图标和比赛限制。恢复默认只清除当前武器的预选。传奇皮肤影响之后新生成的武器，既有发放武器的外观锁定不改变。跨服共享需要一致皮肤 ID 和共享数据库。

箱子菜单的默认布局为六行九列：顶部三类各占连续三格，中间三行共 27 个皮肤位置；第六行槽位 46 返回上级、49 卸下或恢复默认，其余未绑定位置保持空白。相同按钮字符可以在 layout 重复出现。一级分类 main-menu/weapon-menu/legendary-menu 使用 selected/unselected；普通二级分类保留同名两套配置，传奇 weapons.<id>.selected/unselected 以 icon 为公共配置。状态可分别设置 material（VANILLA 下的 id 别名）、item-model、custom-model-data、name、lore、glow，显式 glow: false 会生效。物品模型仅用于箱子菜单；Canvas 仍通过模板的 skin/图像配置表现状态。内容分页 previous/next 与分类分页 category-previous/category-next 各自读取 controls.<按钮>.hide-when-disabled（默认 true），false 时保留禁用按钮；Canvas 默认模板隐藏按钮内容、保留 spacer 占位，避免菜单跳动。卸下只清除主动选择，已配置的默认时装由原显示解析器接续。传奇皮肤页的返回先回到武器列表；其他页面的返回行为由 controls.back.action 配置：CLOSE 关闭，PLAYER_COMMAND 执行玩家命令，CONSOLE_COMMAND 执行控制台命令；命令支持 {player}，先关闭当前菜单再执行。FutureUI 的 operation: back 复用同一配置，不能自动推断其他插件的父菜单。

缺少 FutureUI、指定菜单或客户端渲染能力时回退两层分类的箱子菜单。FutureUI 权限、条件或打开事件拒绝时不回退绕过检查。左侧显示真实选择结果；图片缺失使用明确状态，传奇允许保存预选，不能把默认物品图标宣称为模型预览。

提供器契约：
- 数据源 fotiacosmetic:menu，view 为 page/categories/entries，须由原菜单生成的有效会话打开。
- page 新增 legendary-overview 和 section=main/weapons/legendary，保留 weapon（武器和传奇均为 true）、category、page/pages/count、empty、previous/next、category-previous/category-next，并新增 previous-visible/next-visible/category-previous-visible/category-next-visible、busy、hidden、selected、has-selection、can-unequip 及 preview/preview-loading/preview-available/preview-partial/preview-fallback。
- categories 返回 id/name/selected/weapon；统一使用 operation: category，id 是当前二级分类 ID。
- entries 返回 id/name/selected/equipped/owned/hidden/busy/icon/thumbnail/thumbnail-loading/thumbnail-available，以及 weapon-entry/selectable/state-label。武器入口 weapon-entry=true、selectable=true，owned 不表示武器所有权；皮肤的 owned 表示实际拥有状态。select 动作在武器列表进入皮肤页，在皮肤列表选择皮肤。未拥有皮肤 selectable=false，状态显示“未拥有”；缩略图不可用时显示默认图标与暂无预览，不能省略未解锁提示。
- 操作 opened/closed/view/layout/category/weapon/legendary/categories-previous/categories-next/previous/next/select/equip/unequip/back；view 的 id 为 main/weapons/legendary；layout 为 standard/compact。weapon 和 legendary 保留为分类操作兼容别名。
- 默认模板使用 page_unified、preview_unified、entry_unified 及各自 _compact 版本，旧 main/weapons 菜单 ID 保留兼容；旧默认拥有者配置升级为 wardrobe ID，用户自定义 ID 不覆盖。

### 外置预览资源

FotiaCosmetic 首次安装自动生成 `futureui-preview.yml`，默认模型映射为空。模型、纹理和可选默认皮肤放在其 `previews/` 中，不放到 FutureUI 的 image 字段或生成目录：

```yaml
fallback-skin: skins/default.png # 可留空；无可用玩家皮肤时显示灰色人形
models:
  your_hat:
    file: hats/your_hat.bbmodel
    exclude-groups: [Head] # 例如仅供建模参考的头部组
    translation: [0, 0, 0]
    rotation: [0, 0, 0]
    scale: [1, 1, 1]
  your_weapon:
    texture: weapons/your_weapon.png
```

file 和 texture 二选一，路径不能越出 previews。file 支持带 PNG 纹理的 Blockbench 立方体模型、分组和旋转，最多 2048 个立方体、32 层分组；读取的是静态姿态，不执行压缩包内 Java，不解析任意实体代码或动画。纹理可以来自模型内嵌 PNG 或模型目录内的相对文件。texture 显示平面物品纹理的双面预览，不能宣称为复杂三维武器。

左侧角色/武器快照以 96×108 渲染，右侧单件缩略图以 144×72 渲染。玩家皮肤从玩家资料中的 Mojang 纹理地址在后台获取并缓存，支持宽/窄手臂；无可用皮肤则回退并标注。角色视图展示当前实际可见的穿戴外观，武器视图展示当前类别已装备皮肤；分类翻页、条目分页和旋转都不改变装备。未提供模型的部分保留 partial 状态。后台队列上限 64，快照缓存最多 128 项，模型缓存 64 项，单会话缩略图缓存 24 项；重新加载 FotiaCosmetic 配置会关闭旧菜单并释放预览缓存。

预览映射不创建真实物品模型。实际穿戴的 `icon/visual-item/actual-item` 仍遵循 FotiaCosmetic 原物品配置，客户端资源包须包含其真实模型。用户提供的测试帽子、测试武器和验证用配置仅进入测试服务器目录，不进入任何插件 JAR、默认配置或发布资源。
