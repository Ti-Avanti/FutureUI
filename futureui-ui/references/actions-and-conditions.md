# 动作与条件

完整的类型名单在 `capabilities.json`。下面按真实执行入口描述参数；表中列的是主要参数，不应把核心字段白名单当作任意动作的通用参数表。扩展动作/条件需要实际注册的提供插件和它自己的文档。

## 三种判断不能混用

- `visible`：决定是否展示。
- `enabled` 和组件 `requirements`：决定能否点击；业务执行时仍需检查当前状态。
- 普通动作 `condition`：不满足时跳过该动作并继续后面的动作。强制门槛使用 `require`，失败会终止后续操作，除非显式 recover/continue-on-failure。

## 动作通用规则

通用字段包括 condition、chance（0..1）、timeout-seconds、on-success、on-failure、finally、recover、continue-on-failure、parse。默认失败中止链；on-failure 仅反馈，不会自动把失败变为成功。finally 用于收尾，仍受会话、步骤预算等约束，不能作为持久事务恢复的保证。

每个动作列表最多 128 项，配置嵌套最多 24 层；运行时默认共享 1024 步，受 config.yml `actions.max-steps` 控制。导航使用 open-menu/back；更新本页变量时让正常刷新流程更新内容，不反复 close/open 菜单。

| 动作 | 主要参数与行为 |
|---|---|
| open-menu / back | menu、arguments；back 的 values 将结果带回上一页。跳转复制当前变量并覆盖 arguments |
| close / refresh | 关闭或刷新当前页 |
| set-variable | key、value；会话变量 |
| adjust-number | key、delta、initial、min、max、step；按范围和步长钳制 |
| toggle / save-preference | key、persist；或 key、value；后者保存玩家偏好 |
| page | offset 相对翻页；absolute 为从 0 开始的页号 |
| message / actionbar | text（支持语言键） |
| title | title、subtitle、fade-in-ticks、stay-ticks、fade-out-ticks |
| sound | sound、volume、pitch；还受玩家音量偏好影响 |
| player-command / console-command | command；执行身份不同，必须核实目标插件的真实命令 |
| open-url / copy-text | url 或 value、label；发送可点击的聊天组件，不是自动打开浏览器 |
| language | language；需要已就绪的 FotiaTranslator |
| hud / hide-hud | hud 引用 hud 文件的 ID，或停止显示 |
| connect | server；BungeeCord 通道交给配置正确的代理处理 |
| require | requirements 条件树、失败消息键 message |
| if | when、then、else |
| call | function、arguments、output、exports；函数放 functions/ |
| return / break / stop | 返回 value、退出循环、终止流程 |
| repeat | times（0..256）、actions |
| for-each | values 列表（最多 256）、as、actions；默认值变量 loop.value，还有 loop.index/first/last |
| for-players | selector: viewer/all/名字/UUID，filter、actions；仅对授权范围内玩家执行 |
| random | choices，每项 weight（非负）、actions；总权重大于 0 |
| delay | ticks；执行器将其限制在 1..12000 |
| schedule / cancel-task | key、times（1..1200）、interval-ticks、delay-ticks、actions；任务跟随当前会话 |
| retry | attempts（1..3）、actions；直接子动作限 transaction/require/delay/fail，不重试待人工复核的交易 |
| fail | message 为消息键 |
| purchase / sell | shop、product、quantity；购买可用 quoted-price，详见商店参考 |
| cart-add/remove/clear/checkout | 购物车操作，详见商店参考 |
| give-item | item 定义、amount；支持拆分发放，受背包空间及总数上限限制 |
| take-item / count-item | match、selection、amount；count-item 用 key 输出计数 |
| edit-item / repair-item / enchant-item | match、selection；edit 对应 edit 映射，enchant 对应 enchantments；修复使用 damage=0 |
| take-currency / give-currency | currency、amount（非负）；单独操作不自动组成交易 |
| data-get/set/add/remove/toggle | key、scope: player/global/session；get 用 default/output，set 用 value，add 用 amount |
| quota | key、amount、limit；与 data 动作一样可设 ttl-seconds 或 reset: daily + timezone |
| cooldown | key、seconds；0 为移除。与名为 cooldown 的条件配合 |
| calculate | key、expression；内置算术表达式，不是 JavaScript |
| list | key、value、operation: set/append/remove/unique/count/clear/join/split/get；按操作使用 item/separator/index/default；结果写会话变量 |
| date | key、timestamp（毫秒）、timezone、offset-seconds、format |
| transaction | actions 为限定的可逆步骤；反馈放在事务外层 |
| input | key、mode、prompt、initial、validation 等；见输入参考 |

## 条件目录

条件一般使用对象，可用 true/false 布尔简写；`type: 'true'`、`type: 'false'` 要加引号。多数内置 type 可加 `!` 取反；组合条件使用 not 更清晰。named 使用显式 not 包裹，不假设 `!named` 是完整的复用入口。

| 条件 | 参数 |
|---|---|
| all / any / at-least / requirements | conditions 列表；minimum、optional、stop-at-success |
| not | condition |
| named | rule、arguments，命名规则放 rules/，参数为 arg.xxx |
| permission / has-permission | permission |
| permissions | permissions 列表、minimum |
| currency / has-money | currency、amount |
| item / has-item | 物品匹配字段、amount |
| experience | amount、levels；默认按等级 |
| world / gamemode | worlds 或 modes 列表 |
| health / food / level / world-time | min、max |
| weather | weather: clear/rain/thunder |
| sneaking / flying / op | 当前玩家状态 |
| empty-slots | amount |
| cooldown | key；未处于冷却时通过 |
| equals / string-equals / equals-ignore-case / contains / starts-with / ends-with | input、output |
| length | input、min、max |
| regex | input、pattern；RE2J 全串匹配，不是 Python/JavaScript 正则 |
| in | input、values 列表 |
| exists / number / integer / uuid | input |
| compare / == / != / > / >= / < / <= | input、output；compare 可用 operator |
| expression | expression；内置条件表达式，不执行脚本语言 |
| data | scope、key，可选 value；存在性与值检查 |
| plugin | plugin 为 Bukkit 的实际插件名 |
| list-contains | list、value |
| online-player | input 为在线名字 |
| pdc | key、value、data-type；详细类型核对目标实现 |
| scoreboard-tag / biome | tag 或 biomes 列表 |
| distance | x、y、z、可选 world、min/max |
| day-of-week | days 英文星期列表、timezone |

`requirements` 中非 optional 条件必须通过，此外通过总数须达到 minimum；不能用 minimum=1 绕过必选条件。单个条件的 on-success/on-failure 由 require 等明确执行反馈的入口处理，纯 visible 判断不能作为发奖励的入口。

## Canvas 多选按钮

使用 `defaults: {chosen: []}` 初始化列表，选中态用 `list-contains` 精确判断。点击用一个 `if` 决定 `list remove` 或 `list append`；不要顺序写两个反向 condition，否则前一个动作更新列表后，后一个可能再次命中。`list` 结果保存到会话变量，不自动持久化；当前没有 `operation: toggle`。

```yaml
states:
  - visible:
      type: list-contains
      list: '{chosen}'
      value: '{item.id}'
    skin: selected
  - visible: true
    skin: button
actions:
  - type: if
    when:
      type: list-contains
      list: '{chosen}'
      value: '{item.id}'
    then:
      - type: list
        key: chosen
        operation: remove
        item: '{item.id}'
    else:
      - type: list
        key: chosen
        operation: append
        item: '{item.id}'
```

清空使用 `list` 的 `operation: clear` 或 `set-variable value: []`，随后重置相关分页。多个分类是“任一已选分类”的并集，关键词再与该结果求交集；购物车勾选等其他业务的空列表含义必须单独定义，不能一律推断为空列表选中全部。商店专用数据源还提供兼容旧单分类值的规范化列表，见商店参考。

## 原子业务示例

收费修复示例（片段；价格应来自用户业务配置，20 仅为示例值）：

```yaml
actions:
  - type: require
    requirements:
      type: permission
      permission: myserver.repair
  - type: transaction
    actions:
      - type: take-currency
        currency: vault
        amount: 20
      - type: repair-item
        selection:
          slot: mainhand
    on-success:
      - type: message
        text: '@menus.custom.repair.success'
    on-failure:
      - type: message
        text: '@menus.custom.repair.failed'
```

transaction 只接受能力清单 `transaction_actions` 中的步骤，不接受任意控制台命令、延迟、子事务或导航；步骤内不可放 on-success/on-failure/finally，不能写 scope: session。外部经济结果不确定时可能进入待复核状态，不能宣称所有第三方经济异常都能自动回滚。

## 复用函数示例

```yaml
# functions/custom_feedback.yml
parameters:
  pitch: 1
actions:
  - type: message
    text: '@menus.custom.saved'
  - type: sound
    sound: minecraft:ui.button.click
    pitch: '{arg.pitch}'
```

```yaml
- type: call
  function: custom_feedback
  arguments:
    pitch: 1.2
```

message.text 直接写语言键，参数用于提示音。不要把占位符递归解析误认为语言键也无限递归查表；按变量输出一个 `@...` 字符串不会自动等同于文本入口的语言键。

## 动画中的关闭按钮

Canvas 中仅包含一个 `type: close` 动作、且没有 `enabled` 或 `requirements` 的普通按钮，允许同一菜单会话的旧画面回调完成关闭，避免快速重绘吞掉关闭点击。菜单切换后旧会话仍失效；交易、扩展动作、带条件的关闭按钮继续校验当前帧。使用 on-close 处理所属插件的会话清理。
