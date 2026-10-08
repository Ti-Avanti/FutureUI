# FotiaTags 玩家菜单

需要 FotiaTags `1.1.2` 的 FutureUI 适配修订包及 FutureUI `0.0.3beta` 配套菜单。保留原 `/tags menu`、`/tags custom`、`/tags effects`、`/tags effects shop` 入口。

## 开关与范围

在 FotiaTags 的每个原菜单文件中使用 `ui-engine: inventory / futureui`。默认 `inventory`；可混合选择，导航进入未接管页面时调用原菜单。管理员控制显示引擎，玩家只选择界面尺寸。

接管八个玩家页面：

| 配置文件（位于 FotiaTags/menus） | 页面与实际行为 |
|---|---|
| tag-select.yml | 有效且有权限的普通称号、自定义称号，佩戴、卸下、筛选、分页 |
| custom-tag.yml | 原草稿、前后缀编辑、预览、购买创建 |
| custom-tag-icons.yml | 原图标目录，选中后返回草稿 |
| custom-tag-detail.yml | 查看本人自定义称号、佩戴／卸下、删除入口 |
| custom-tag-delete.yml | 明确确认删除、按原配置退款 |
| gradient-storage.yml | 已拥有且有效的效果，选择、清除、分页 |
| gradient-shop.yml | 可出售效果、价格和拥有状态，遵守原 hide-owned 配置 |
| gradient-purchase-confirm.yml | 原购买事务、扣款、解锁及失败处理 |

六个管理员菜单仍使用原界面。原 `custom-tags.enabled`、`dynamic-gradients.enabled`、权限、价格、限额和内容限制继续生效；切换显示引擎不会自动开启这些业务模块。

```yaml
# plugins/FotiaTags/menus/tag-select.yml
ui-engine: futureui
```

FotiaTags 首次启动生成 `futureui.yml`：

```yaml
default-layout: standard
layouts:
  standard:
    menu: fotiatags/standard
    page-size: 4
  compact:
    menu: fotiatags/compact
    page-size: 2
input-menu: fotiatags/input
icons:
  fallback: paper
  entries:
    icon/nametag: name_tag
    icon/diamond: diamond
    effect/ocean: heart_of_the_sea
```

每页数量范围 1–36，变更须同步调整模板。标准画布为 540×315，左侧导航和当前佩戴、右侧内容切换；紧凑画布为 252×216，顶部一行导航，每页两条。尺寸存入玩家 PDC `fotiatags:futureui-layout`，没有自动感知窗口大小。Canvas 默认每 4 tick 刷新，可在菜单的 `refresh-ticks` 调整；效果预览使用原动态效果渲染器。

布局与文字分别位于 FutureUI 的 `templates/integrations/fotiatags/` 和 `languages/zh_cn/integrations/fotiatags.yml`、`en_us` 同路径。界面文案遵守 FutureUI 客户端语言／FotiaTranslator 链；原插件业务消息仍遵守 FotiaTags 的语言设置。称号名称、前后缀与效果名称保留管理员配置。

## 数据和动作契约

数据源与动作 ID 均为 `fotiatags:menu`。必须从原入口建立 `fotiatags.session` 令牌；直接 `/fui open` 不会建立可操作会话。数据源使用 `cache: false`、`paginate: false`，分页由适配器处理。

`page` 包含 view、filter、busy、compact、current/current-empty、count/empty/page/pages/previous/next、can-tags/can-create/can-effects/can-clear、list-view/detail-view、草稿 preview/prefix/suffix/image/icon-name、prefix-editable/suffix-editable/draft-locked/draft-complete，以及目标预览、退款、价格、currency、owned、purchase-state、can-confirm、duration-unit、duration-value。字段精确拼写以 `TagMenuData.page` 和默认模板为准。

`entries` 包含 id、kind（tag/custom/icon/effect）、name、preview、image、selected、owned、shop、permanent、days、hours、price、currency、enabled。过期时间按剩余小时向上取整；普通称号和效果先通过原拥有／到期检查。name 和 preview 为保留颜色且按宽度裁切的组件。购买确认的 duration-unit 为 permanent/days/hours/minutes/seconds；duration-value 为按整除单位表示的准确数量，永久时为 0。

动作 operation：opened、closed、close、navigate、previous、next、layout、filter、select、clear、input、submit、cancel-input、confirm、equip、delete、back、footer。navigate.id 使用上表文件名去掉 `.yml`；filter.id 为 all/normal/custom/cycle；select.id 为当前条目的 `{item.id}`；input.id 为 prefix/suffix。footer 在称号／效果仓库清除选择，其余页面返回。

输入使用原生 Dialog `text-input`，字段 ID 必须为 `value`，提交读取 `input.value`。它只编辑草稿，不扣费；最终确认才进入原购买流程。颜色格式、可见长度、MiniMessage 安全规则、屏蔽词、图标、限额仍由原 `CustomTagManager` 验证。关闭和切换后的异步回调不重新打开旧页面。

## 自定义称号的包裹符号

`custom-tags.display-wrapper.enabled/left/right` 仍由 FotiaTags 原配置控制。左右装饰与正文分别解析后组合：正文的旧颜色码、MiniMessage、加粗或动态渐变不改变包裹符号。两侧装饰保留各自配置的样式；关闭包装或正文为空时沿用原规则。草稿、称号详情、仓库、实际佩戴与 PAPI 输出共用这一边界，动态效果先渲染自定义称号正文，再添加装饰。FutureUI 的当前佩戴区域显示玩家当前启用的动态效果。该规则针对自动包裹的自定义称号，不重新解释管理员手写普通称号中的所有方括号。

## 图片与边界

图片在 FutureUI 注册，FotiaTags 只做 `tag/<称号ID>`、`icon/<图标ID>`、`effect/<效果ID>` 到图片 ID 的映射。新增图片不用另配 FotiaTags 资源包。默认复用原版像素图；item-model 和 CustomModelData 不会自动变成 Canvas 图片。原文字解析器不能识别的 `<image:...>`／`%image_...%` 预览回退到称号名，避免把实现标记显示给玩家；需要图片外观时配置图片映射。

普通称号和效果选择使用原异步数据服务；自定义创建／删除、退款、效果购买使用原业务链和操作锁。动作重新核对玩家、页面、拥有状态、权限及业务开关，不能通过修改按钮参数操作他人的资产。缺少 FutureUI、菜单或客户端渲染能力时保留原界面；打开事件／权限拒绝不通过回退绕过。

默认主题为 adventure，透明外层、面板内容底板、明确按钮和悬停态。单行文本底板使用 27 像素高度与 9 像素内边距；卡片内容在共同面板内排版，避免 18 像素独立文字底板在 9 像素行网格上偏顶。标准各详情页的最终操作栏保持同一基线；紧凑布局图标与双行文字区域保持相同高度。图片只用已生成且能整行切分原纹理的显示规格，不能为了凑行高将原版 16×16 图标设置为未生成的 27 像素。显示条件应放在外层容器，文字／皮肤 states 放在内部，避免 states 覆盖 visible 导致筛选栏与说明同时显示。发布包附同源菜单配置，测试服称号和玩家数据不进入发布包。
