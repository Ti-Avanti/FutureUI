# 示例索引

`assets/templates/starter/` 是一个配置增量包，不是完整插件数据目录，也不是可直接发给客户端的资源包。它复用目标 FutureUI 默认主题、公共语言和消息机制。先根据能力清单核对目标版本，再按需改造。

| 文件 | 页面与实际功能 |
|---|---|
| menus/demo_home.yml | 固定左侧导航、金币底板、生命条、函数反馈；导航项在右侧切换内容 |
| menus/demo_shop.yml | 左侧分类多选、逐项绿色选中态、再次点击取消、清空筛选、双列商品、页码底板、真实 Vault 余额 |
| menus/demo_detail.yml | 左图右侧份数、预设数量、可购上限、价格、购买确认 |
| menus/demo_quantity.yml | 带商品信息的键盘数字输入、范围校验、返回携带结果 |
| menus/demo_settings.yml | 固定导航、两组仅滑块按钮可点击的即时开关、昵称和音量底板 |
| menus/demo_settings_edit.yml | 从具体编辑按钮进入的昵称、音量滑块、复选框和排序表单，保存后带值返回 |
| menus/demo_receipt.yml | 真实购买结果、份数/总数/金额底板、继续选购或主页 |
| menus/demo_services.yml | 固定导航下的业务入口和真实余额 |
| menus/demo_cart.yml / demo_cart_receipt.yml | 购物车列表、移除/清空、真实整单结算、订单行数与物品数区别 |
| menus/demo_recycle.yml | 使用 sell-products 与 sell 按报价回收一份物品 |
| menus/demo_logic.yml | 命名条件、all/if/require、状态展示和真实原子兑换 |
| menus/demo_input.yml | 五种 input 模式、成功保存偏好、取消/超时反馈 |
| menus/demo_variables.yml | 本地变量组成 PAPI、raw/expand 对照及有界递归解析 |

配套文件包含 demo_workspace/demo_navigation/demo_switch 等模板、独立主题 assets/themes/demo.yml、shops/demo.yml、命名规则、反馈/兑换函数，以及两套语言和图片注册增量。业务数值、门槛和反馈读取同一组 demo_workspace.defaults 变量。

## 使用与改造

1. 读取目标 FutureUI 已有配置及资源，核对 ID 和主题。示例依赖正常启动的 FutureUI、packetevents、Vault 和实际经济实现；图形资源按服务器配置通过 CraftEngine 或 external 下载地址提供。CraftEngine、PAPI/FotiaTranslator 不是示例交易的强制依赖。
2. 将需要的独立菜单及其模板/商店/语言依赖放入交付目录。改名时同步更新所有静态引用；不要只复制一个引用了其他文件的页面。
3. `assets/images.yml` 按 images 键合并，保留原有图片；示例使用原版图片引用，无需复制 Mojang 原图。复制独立 assets/themes/demo.yml，保留全局 assets/theme.yml；先确认 demo 主题名没有与目标冲突。新菜单通过 layout.theme 选择主题。
4. 执行离线检查。获得部署授权后按实际环境合并文件，重载 FutureUI 并等待资源包构建/加载完成。
5. 入口 `/fui open demo_home`，同时提供 `fui-demo` 菜单命令。详情、数量和结果页通过购买流程打开，直接打开时缺少商品或交易上下文。

示例商店、购物车、回收和兑换都执行真实业务，价格是示例值；正式部署前按需求调整。修改货币时同时修改商品 currency、余额绑定、规则和相关文案。PAPI 示例额外需要 PlaceholderAPI；输入模式依赖实际协议和 PacketEvents 实现。

检查保存的偏好时退出菜单再重新进入。数量输入的返回使用 back.values；不要用再次打开列表替代携带份数返回。返回购买结果页不能重放 purchase 动作。

## 按需复制与能力范围

这些页面构成一个可按需裁剪的配置增量，不会自动安装进服务器。独立拷贝业务页时同时处理公共导航中的静态链接：保留对应页面，或删去不提供的入口；离线工具会检查遗漏的菜单、函数与规则引用。

demo_switch 是控件模板，按 demo_settings 中的单项静态列表方式复用，避免重复内部 ID。demo_navigation 是共享导航模板，页面通过 bindings.demo.section 指定选中栏目，demo.page 标记当前页；从子功能点击栏目会返回该栏目主页。表单、商品详情与订单结果保留各自的业务返回路径。

多选、循环、计划任务和其他提供器的契约见动作/变量/商店参考。示例覆盖常见组合，不代表所有插件能力已经实际运行验证，也不因某能力未在示例中出现就判定插件不支持。

## 插件随附的 BasicTool 接管布局

这组文件随 FutureUI 安装释放，位于 `menus/basictool/settings/`（5 页）、`menus/basictool/statistics/`（6 页）和 `menus/basictool/vouchers/`（1 页），共享 `templates/integrations/basictool/` 中的对应模板与独立 `adventure` 主题。它们不属于 starter 增量包。

先在 BasicTool 原菜单的 `Options.ui-engine` 选择 `futureui`，再从 `/settings`、`/stats`、`/bt stock` 进入。页面依赖 BasicTool 适配器及原菜单会话，不能直接使用 `/fui open` 演示；配置字段、生命周期和数据源契约见[商店与集成](commerce-and-integrations.md#basictool-可选菜单接管)。保留原菜单引擎的默认值，只有管理员指定的页面切换显示方式。

- `chart_showcase`：横条、像素环、堆叠比例的明确样本数据展示；需要 adventure 主题。
- `rank_showcase`：管理员段位展示，明确标注为样例；左栏可切换常规段位、积分上限、排行榜段位、仅评分、暂无段位、非排位、未启用、无记录和尚未选择模式。与真实页面共用 `integrations/basictool/rank-card`，不写入玩家数据；可用 `/fui open rank_showcase` 打开，需要 `futureui.admin`。
- `menus/basictool/`：个人设置、个人战绩和我的票券，采用统一的基岩版灰阶界面、绿色选中态和原版像素图标；设置页仅显示实际选项与操作，由 BasicTool 原命令创建会话，详见商店与集成参考。

## 插件随附的 FotiaCosmetic 接管布局

`menus/fotiacosmetic/main.yml`、`menus/fotiacosmetic/weapons.yml` 共用 `templates/integrations/fotiacosmetic/` 的 page、preview、entry 模板和 adventure 主题。两页均由原 `/fc` 入口及页签进入，管理员在 FotiaCosmetic 原菜单内设置 `ui-engine: futureui`。左侧显示玩家当前穿戴和真实皮肤（缺失时明确标注默认人形），并提供独立脱下按钮；右侧为可直接点击的两列两行缩略图，每页四项已拥有时装。分类固定为一行，每页五项，左右箭头切换七个外观分类或九个武器分类，不堆叠多行。点击缩略图直接穿戴并刷新左侧，无待确认预览步骤。另有 `main_compact/weapons_compact` 与对应 `_compact` 模板，每页两项、单行两个分类；通过原菜单或 `/fc layout compact|standard` 切换并记忆，默认紧凑布局。

这些页面需要有效的 FotiaCosmetic 服务端会话，不可通过 `/fui open` 冒充。模型使用 FotiaCosmetic 的外置 `futureui-preview.yml` 与 `previews/`，插件不内置任何用户提供的测试模型。具体路径、字段、状态与限制见[商店与集成](commerce-and-integrations.md#fotiacosmetic-可选菜单接管)。

## 插件随附的 FotiaChat 颜色菜单

`menus/fotiachat/colors.yml` 和 `colors_compact.yml` 共用 `templates/integrations/fotiachat/color.yml` 色条组件及 adventure 主题；标准三列九项，紧凑两列四项。从 `/chatcolor` 进入并使用真实颜色权限和保存逻辑，不是独立演示数据。管理员在原 color-menu.yml 选择显示引擎。配置、字段、操作和关闭生命周期见 [FotiaChat 颜色菜单](fotiachat.md)。

## 插件随附的 FotiaCrates 抽奖菜单

`menus/fotiacrates/standard.yml` 和 `compact.yml` 使用 adventure 主题，从 `/crate preview <crate>` 等原入口进入。顶部信息、全宽奖品区和底部操作栏内切换奖池、历史和结果；提供真实钥匙、分档保底、单抽／连抽、轮盘及分页结果。管理员在 FotiaCrates/futureui.yml 选择引擎。配置、图片和会话契约见 [FotiaCrates 抽奖菜单](fotiacrates.md)。

## 连续滚动示例

`starter/menus/demo_scroll.yml` 是三格连续物品展示，配合 `assets/viewports.yml`、demo 主题、demo 图片和对应语言键。motion 以 120 tick 滚过五项，末尾重复首屏内容实现循环衔接；重新播放和关闭按钮保持固定。无需 FotiaCrates 适配器；生产菜单可改用数据源和 position 绑定。

## FotiaTags 玩家菜单

八个玩家页面的开关、称号草稿、效果购买和标准／紧凑布局见 [FotiaTags 接管](fotiatags.md)。默认配置来自 `menus/fotiatags/` 与 `templates/integrations/fotiatags/`；数据和动作需要 FotiaTags 原入口建立会话。
