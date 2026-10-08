# FutureUI

**用 YAML 构建基岩版风格的 Java 版服务器菜单。**

FutureUI 将像素画布、原生表单、条件与动作系统组合在一起，让商店、玩家设置、时装衣柜和抽奖界面共用一套可配置的菜单引擎。页面布局、语言、按钮状态与业务数据分别维护，日常调整菜单无需修改 Java 代码。

[下载 0.0.3beta](https://github.com/Ti-Avanti/FutureUI/releases/tag/v0.0.3beta) · [默认菜单](src/main/resources/menus) · [配置文件](src/main/resources/config.yml) · [独立菜单 Skill](https://github.com/Ti-Avanti/futureui-skill)

## 能做什么

| 能力 | 展示与交互 |
| --- | --- |
| 像素界面 | 左右分栏、分类导航、商品网格、卡片、数值底板、图片、悬停与选中状态 |
| 表单与设置 | 文本和数字输入、开关、复选框、单选、多选、滑块、范围与步长校验；Canvas 展示与原生输入页配合使用 |
| 数据与动效 | 进度条、横条／环形／堆叠图表、动态像素图、水平滚动视口和边缘裁切；通过提供插件呈现角色预览、渐变色与抽奖动画 |
| 商店交易 | 分类多选、搜索、排序、分页、份数选择、购买、回收、购物车、折扣、库存与限购 |
| 条件与动作 | 权限、余额、物品、玩家状态、变量和组合条件；分支、循环、函数、延迟、定时任务及受限可逆事务 |
| 变量与语言 | 本地变量与 PlaceholderAPI 多轮解析；跟随客户端语言，可优先采用 FotiaTranslator 的玩家语言选择 |
| 插件扩展 | 注册数据源、动作、条件、货币及物品提供器，让原插件保留业务逻辑，由 FutureUI 负责显示 |

基岩版风格指界面设计；本项目面向 **Minecraft Java 版客户端**。Canvas 效果需要加载相应资源包，无需额外安装菜单客户端 Mod。

## 安装与首次打开

### 运行环境

| 项目 | 要求 |
| --- | --- |
| 服务端 | 使用 Paper API 的服务端；项目编译基线为 `1.21.8` |
| Java | 插件字节码目标为 Java 21；实际运行环境仍须满足所选服务端的 Java 要求 |
| 必需插件 | `packetevents`，当前构建依赖版本为 `2.13.0` |
| 资源包 | Canvas 需要 FutureUI 生成的字体、图片及对应客户端版本的着色器 |

1. 将 Release 中的 `FutureUI-0.0.3beta.jar` 和 PacketEvents 放入服务器 `plugins/`。
2. 启动服务器，等待 FutureUI 完成初始化，自动生成配置及资源包。
3. 按下文选择 CraftEngine 合包或独立资源包分发，让玩家加载资源包。
4. 在游戏中执行 `/fui`，或用 `/fui open compact/main` 打开第二套默认界面。

默认入口是 `main`。希望使用第二套菜单时，在 `plugins/FutureUI/config.yml` 中修改已有键：

```yaml
default-menu: compact/main
```

执行 `/fui reload` 后生效。默认菜单分为 [standard](src/main/resources/menus/standard) 与 [compact](src/main/resources/menus/compact) 两套；前者的主菜单 ID 是 `main`，后者是 `compact/main`，菜单 ID 不一定等于文件路径。

新版本会补充缺失的默认文件、主配置和语言键；已有菜单与模板应按差异合并，避免覆盖自己的布局。

### 按需安装的依赖

| 插件 | 用途 |
| --- | --- |
| CraftEngine | 自动合包与分发、CraftEngine 自定义物品；属于软依赖 |
| Vault + 经济插件 | 金币余额与交易；Vault 本身不提供账户余额 |
| PlayerPoints | 积分货币 |
| PlaceholderAPI | 解析已安装扩展提供的 PAPI 变量 |
| FotiaTranslator | 使用玩家自行选择的语言 |
| ItemsAdder / Nexo / Oraxen / MMOItems | 对应自定义物品提供器；MMOItems 还涉及 MythicLib |
| ViaVersion | 多版本连接时的协议转换；资源包着色器仍需对应版本适配 |

可选依赖的完整声明见 [plugin.yml](src/main/resources/plugin.yml)。默认系统商店使用 `vault`，使用前需要实际可用的经济插件；也可以改用积分、经验、物品货币或 API 注册的货币。

## 资源包与多版本

资源的编辑入口统一位于 `plugins/FutureUI/assets/`。图片映射、主题、覆盖层、纹理和着色器由 FutureUI 管理；`generated/` 是构建结果目录。

**使用 CraftEngine：** 默认 `resources.backend: auto` 会在 CraftEngine 已启用时使用其合包流程。`/fui pack` 生成 FutureUI 资源，并按配置触发 CraftEngine 构建与分发。明确指定 `craftengine` 后端时需要启用 CraftEngine。

**独立分发：** 未使用 CraftEngine 时，把生成的 ZIP 上传到自己的资源包托管服务，并在现有配置中合并：

```yaml
resources:
  backend: external
  external:
    url: "https://你的资源域名/futureui-resource-pack.zip?sha1={sha1}"
    verify-pack-path: ""
    send-on-join: true
    required: false
```

生成文件为 `plugins/FutureUI/generated/futureui-resource-pack.zip`。填写实际可下载的 HTTP(S) 直链，执行 `/fui reload`，让玩家重新连接并加载。FutureUI 不自动上传文件或启动下载服务器。手动合包时，`verify-pack-path` 指向最终 ZIP，下载地址也必须提供同一份文件。

一个 ZIP 可以包含公共资源和多个版本覆盖层，客户端按资源格式选择自己的着色器。当前 [versions.yml](src/main/resources/assets/versions.yml) 登记了 `1.21.8`、`1.21.9–1.21.10`、`1.21.11`、`26.1–26.1.2`、`26.2`、`26.3` 的客户端配置。这是当前配置范围，不等同于所有服务端、客户端及其他着色器组合都已验证。

未匹配的客户端由 `unsupported-client` 策略处理：支持 Dialog 的客户端可回退原生界面，也可配置为拒绝。新增版本需要同时核对协议、资源格式和着色器，不能只扩大版本区间。菜单打开不以资源包加载回执作为门槛；这不会替玩家安装资源包。

## 默认菜单与配置

默认提供玩家中心、系统商店、钱包、设置、搜索、数量选择、购买结果、购物车、回收、服务大厅等菜单，以及条件、变量和动作的配置示例。可以从已有菜单复制并修改布局、语言和交互。

```text
plugins/FutureUI/
├── config.yml      默认入口、语言、货币、变量解析与资源分发
├── menus/          菜单定义与组件
├── templates/      可复用的页面与组件模板
├── shops/          分类、商品、价格、数量与交易规则
├── languages/      界面文案与消息
├── functions/      可复用动作函数
├── rules/          可复用条件
├── hud/            HUD 配置
├── assets/         图片、主题、纹理、着色器与版本覆盖层
└── generated/      自动生成的资源包与构建数据
```

配置使用 **UTF-8 无 BOM**。显示文本支持 MiniMessage、`&`／`§` 旧颜色码及十六进制颜色；MiniMessage 文本可用 `<!i>` 关闭斜体。语言文件使用 `@路径.键` 引用，新增文案应同时补充相应语言。

### 商品份数

[系统商店](src/main/resources/shops/system.yml) 的 `amount` 是每份物品数量，`quantity` 定义购买份数范围、步长和快捷选项：

```yaml
# 商品配置节选；合并到 shops/system.yml 的对应商品中。
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

按这组基础价格，购买 65 份代表 1,040 个物品、总价 520。发放会按物品堆叠上限拆分，仍会检查余额、真实背包空间、库存、限购及交易总量限制；“超过 64 个”不代表可以绕过这些条件。

### 变量与语言

默认优先使用已启用、已就绪的 FotiaTranslator 玩家语言，否则跟随客户端语言，并按配置回退。PAPI 支持递归展开，默认配置为：

```yaml
placeholders:
  mode: recursive
  max-rounds: 5
  max-length: 32768
  order: local-first
  expand-input: false
```

解析稳定、检测到循环或达到上限时停止。玩家输入默认按字面文本处理；需要展开可信输入时使用明确的配置入口。可用 `/fui trace <文本>` 查看每轮解析结果。

## 已接入的插件菜单

FutureUI 提供显示模板，原插件负责权限、拥有状态、存储和交易。**需要对应插件中带 FutureUI 适配器的版本**，仅安装 FutureUI 不会自动接管其他插件的菜单。管理员在对应插件配置中选择 UI 引擎，玩家沿用原入口。

| 插件 | 已适配范围 | 配置参考 |
| --- | --- | --- |
| BasicTool | 玩家设置、战绩与票券的已接入页面 | [接入契约](https://github.com/Ti-Avanti/futureui-skill/blob/main/futureui-ui/references/commerce-and-integrations.md) |
| FotiaCosmetic | 统一衣柜、武器与传奇皮肤分类、穿戴／脱下、当前装扮预览 | [接入与预览配置](https://github.com/Ti-Avanti/futureui-skill/blob/main/futureui-ui/references/commerce-and-integrations.md) |
| FotiaChat | 聊天颜色选择、筛选、渐变预览与恢复默认 | [配置参考](https://github.com/Ti-Avanti/futureui-skill/blob/main/futureui-ui/references/fotiachat.md) |
| FotiaCrates | 奖池预览、横向滚动抽奖、单抽／连抽结果与历史 | [配置参考](https://github.com/Ti-Avanti/futureui-skill/blob/main/futureui-ui/references/fotiacrates.md) |
| FotiaTags | 称号选择、自定义称号及图标、详情、删除确认、效果仓库／商店／购买确认，共八个玩家页面 | [配置参考](https://github.com/Ti-Avanti/futureui-skill/blob/main/futureui-ui/references/fotiatags.md) |

这些集成需要从原插件入口建立会话；直接执行 `/fui open fotiachat/colors` 等命令不能替代业务入口。未接管的管理页面继续使用原界面。

标准与紧凑布局供玩家按 GUI 缩放选择，当前不自动读取客户端窗口尺寸。自定义物品可以用于业务发放；Canvas 缩略图需要注册图片或由提供器生成，CE／IA 的物品 ID、`CustomModelData` 与 `item-model` 不会自动变成模型缩略图。

## 常用命令

`/futureui` 的别名是 `/fui`。

| 命令 | 用途 |
| --- | --- |
| `/fui` | 打开配置的默认菜单 |
| `/fui open <菜单ID> [键=值...]` | 打开普通菜单并传递参数 |
| `/fui close` | 关闭当前菜单 |
| `/fui cancel` | 取消当前输入 |
| `/fui reload [菜单ID]` | 重载全部配置，或指定菜单 |
| `/fui pack` | 构建资源包 |
| `/fui status` | 查看菜单、后端、语言及客户端配置状态 |
| `/fui validate` | 校验当前配置 |
| `/fui inspect <菜单ID>` | 查看编译后的组件与打开条件 |
| `/fui trace <文本>` | 查看变量解析过程 |
| `/fui dryrun <菜单ID> [组件ID]` | 查看动作与条件，不执行业务动作 |
| `/fui openfor <玩家> <菜单ID>` | 为在线玩家打开菜单 |
| `/fui transactions` | 查看需要人工复核的交易记录 |

普通菜单默认权限为 `futureui.use`，各菜单可单独设定权限与条件；管理和诊断命令需要 `futureui.admin`，默认授予 OP。

## 扩展与构建

其他插件通过 Bukkit `ServicesManager` 获取 [FutureUIService](src/main/java/gg/fotia/futureui/api/FutureUIService.java)，可打开／刷新／关闭菜单，并注册动作、条件、货币、物品与数据源。界面操作在服务端主线程调用；`canRender` 只检查菜单与渲染能力，实际打开仍须执行 `open` 的权限和条件检查。

源码使用 Maven，仓库根目录包含 `pom.xml` 与 `src/`：

```bash
mvn -DskipTests package
```

构建需要 [pom.xml](pom.xml) 中列出的 API 依赖。`gg.fotia:translator-api:1.1.0` 需由本地 Maven 仓库或可访问的私有仓库提供；仓库不附带依赖 JAR。

产物位于 `target/`：

- `FutureUI-0.0.3beta.jar`：服务端插件。
- `FutureUI-0.0.3beta-bundle.zip`：同一插件，以及从 `src/main/resources/` 收集的 FutureUI 接入菜单、模板、语言与资源配置。

本地 `distribution/` 目录不提交到 Git，也不进入发布包。第三方插件自身的菜单开关与配置由对应插件维护；FutureUI 内置的集成模板仍正常提供。

## 菜单编写 Skill

[futureui-skill](https://github.com/Ti-Avanti/futureui-skill) 是独立仓库，提供配置契约、示例和离线检查工具，供能够读取 `SKILL.md` 的 AI 助手使用。插件运行不依赖 Skill。可从 [独立 Release](https://github.com/Ti-Avanti/futureui-skill/releases) 下载。
