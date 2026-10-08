# 变量、输入和多语言

## 变量来源

| 形式 | 含义 |
|---|---|
| `{player.name}`、`{player.health}`、`{server.online}` | 内置实时信息；完整可用项见能力清单与目标实现 |
| `{input.nickname}` | id: nickname 的表单输入；默认不继续展开玩家文本 |
| `{item.xxx}` | list 当前项 |
| `{product.xxx}` | 上下文 shop/product 指向有效商品时提供的报价与数量信息 |
| `{purchase.quantity/items/total}` | 成功购买后的结果，不使用展示报价替代实际结果 |
| `{page}`、`{pages}` | 列表编译时提供的分页值 |
| `{arg.xxx}` | 命名函数或规则的参数 |
| `{data.xxx}`、`{global.xxx}` | 持久数据；按目标实现的访问规则 |
| `%identifier_params%` | 已安装 PlaceholderAPI 扩展的变量 |

不要凭变量名字推断它存在。未解析的占位符应检查上下文和依赖；不要将其替换为虚假的余额或成功状态。

菜单 `defaults` 在变量缺失时设置值；`bindings` 在绘制时计算只读值。支持 value、data、currency、item-count、calculate、condition、date。`initial-only: true` 适合输入初始值，防止每次刷新覆盖用户已输入内容。

```yaml
bindings:
  account.balance:
    type: currency
    currency: vault
  input.nickname:
    type: data
    key: custom.nickname
    default: ''
    initial-only: true
```

set-variable 是会话状态；data-set 是持久存储（scope: session 除外）；save-preference 使用玩家偏好存储。它们用途不同，保存后重新打开应从相同存储入口读取。复杂列表保存用 data-set，不将列表压成未经约定的字符串。

## PAPI 多轮解析

config.yml 已有真实配置：

```yaml
placeholders:
  mode: recursive
  max-rounds: 5
  max-length: 32768
  order: local-first
  expand-input: false
```

mode 为 raw/once/recursive；max-rounds 为 1..16，max-length 为 256..1048576；解析稳定、遇到循环或达到轮数限制时停止。order 可使用 local-first 或 papi-first。动作 text 等支持的入口可通过 parse 覆盖策略，不能假设每个组件字段都有独立 parse 支持。

`{raw:变量}` 保持结果字面文本，`{expand:变量}` 为明确选择的变量允许展开。输入、参数、存储文本等默认保护，尤其不能将玩家输入直接拼成控制台命令。需要执行受控命令时，验证并映射到事先配置的固定命令/参数。

文本入口的语言键查找与变量展开是不同步骤。`text: '@menus.demo.saved'` 会查语言文件；将字符串 `@menus.demo.saved` 放到变量再用 `text: '{some.value}'`，不能假设会再查一次语言文件。

诊断用 `/fui trace <原始文本>` 查看每轮输出及停止原因；保留 trace 命令的原始 PAPI 文本，避免在执行命令前先解析掉。

完整混合解析示例见 `menus/demo_variables.yml`：`demo.chain → demo.token → %futureui_balance_{demo.currency}% → 当前余额`。`{raw:demo.token}` 用于字面展示，`{expand:demo.chain}` 只显式展开可信配置链。依赖 PlaceholderAPI 的显示/按钮绑定到实际 plugin 条件；不安装时显示依赖说明，不填假余额。不要向全局 config.yml 写入 `expand-input: true`。

## 语言文件

`languages/zh_cn/menus/demo.yml` 中：

```yaml
home:
  title: '<!i><white>服务器大厅'
  balance: "<!i><gray>余额\n<green>{account.balance}"
```

对应 `@menus.demo.home.title` 和 `@menus.demo.home.balance`。文件路径形成键前缀，不再在文件内重复 `menus.demo`。示例中的换行实际写入时用双引号 `\n` 或 YAML 块文本；单引号中的反斜杠不转义。

至少为所需语言提供同名键、相同占位符。文本加 `<!i>`，可用旧 `&/§` 颜色码、十六进制颜色码和 MiniMessage；不要把原始 `§` 文本直接传入外部 MiniMessage 解析器，FutureUI 自身会先转换。

语言选择：启用并就绪的 FotiaTranslator 提供玩家偏好，否则取客户端语言。本地逐键回退涉及 locale alias、语言基码、配置 default 和 en_us；不要为单个菜单另写固定语言选择。language 动作需要 FotiaTranslator，可将按钮的 enabled 绑定到真实可用性。

## 输入与表单

number-input/slider 的 min/max/step/initial 可使用现有变量；数值必须满足范围、步长和整数规则。checkbox 使用布尔值；select 使用 value/label；multi-select 的 initial 应保持列表类型。

输入错误使用 validation-feedback 保持外框稳定。取消和返回按钮使用 `validate: false`；提交按钮使用默认校验。恢复输入时检查初始值是否仍在新的范围内。

独立 `input` 动作支持 dialog/chat/sign/anvil/book，受 PacketEvents、客户端协议和真实运行环境限制；不要把箱子式库存菜单当作这些模式的 UI 替代。Dialog 自由展示区域也不能直接嵌原生输入。

`menus/demo_input.yml` 展示五种输入入口：成功后保存到同一个玩家偏好 key 并回到内容页；取消/超时只反馈并返回，不覆盖原值。普通导航始终进入带左栏的内容页，输入动作由右侧具体按钮触发。

```yaml
- type: input
  mode: dialog
  key: input.search
  prompt: '@menus.custom.search.prompt'
  max-length: 64
  timeout-seconds: 60
  on-success:
    - type: set-variable
      key: shop.search
      value: '{input.search}'
    - type: page
      absolute: 0
```

这是需要补充语言键的片段。具体输入字段、取消行为以目标版本为准，不在未知版本中猜测原生输入协议。
