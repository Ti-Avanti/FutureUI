# FotiaChat 聊天颜色菜单

此接管需要带颜色适配器的 FotiaChat `1.1.5` 和支持 raster 的 FutureUI `0.0.3beta` 修订包。入口保持 `/chatcolor`、`/chatcolor gui`；只接管聊天颜色选择，物品、背包和末影箱快照仍使用原界面。

发布包 `FutureUI-0.0.3beta-bundle.zip` 的 `menu-configs/FutureUI/` 含下述七个菜单、模板和语言文件；`menu-configs/FotiaChat/menus/color-menu.yml` 是已开启 FutureUI 的安装示例，来源为 `distribution/fotiachat/`。现有配置按键合并，不覆盖自定义颜色；`colors.yml` 仍由 FotiaChat 管理。FotiaChat 插件 JAR 需要单独更新，未启用集成时默认使用原箱子菜单。

## 管理员配置

在 `plugins/FotiaChat/menus/color-menu.yml` 根节点设置：

```yaml
ui-engine: futureui # 默认 inventory，管理员按需启用。
futureui:
  menu: fotiachat/colors
  page-size: 9
  default-layout: standard
  show-locked: true
  preview-text: lang:color.preview-text
  compact:
    menu: fotiachat/colors_compact
    page-size: 4
```

`ui-engine` 为 inventory / futureui；`default-layout` 为 standard / compact；两个 profile 的 page-size 为 1–36。玩家记忆的布局优先于 default-layout。增大每页条数时应同步调整 FutureUI 模板，不能声称固定布局会自动感知窗口。`show-locked: false` 隐藏无颜色权限的选项；true 显示变灰的色条、带 × 的名称并禁止点击。颜色和显示顺序读取 FotiaChat 的 `colors.yml`，颜色名称由原语言系统解析。原 Layout / Icons 仅用于箱子界面，不用于生成新颜色条，也不会将其任意菜单动作复制到 FutureUI。

`preview-text` 接受普通配置文本或 `lang:` 语言键；解析旧颜色码、MiniMessage 和 PAPI 后，只绘制预览，不向公共聊天发送消息。选中和恢复默认调用原 ColorManager，并复用原存储及 BasicTool 玩家设置桥接；没有另建颜色数据库。

## 页面与资源

标准模板宽 540、高 261，左侧发言预览、当前颜色与恢复按钮，右侧三列三行颜色，每页九项。紧凑模板宽 252、高 180，左侧预览，右侧两列两行，每页四项。两种布局均提供全部、纯色、渐变、可用筛选、分页、关闭和布局切换。渐变筛选同时包括 GRADIENT / RAINBOW。名称过长时缩短展示，颜色 ID 和业务操作不受影响。

- 入口：`menus/fotiachat/colors.yml`、`colors_compact.yml`。
- 模板：`templates/integrations/fotiachat/page.yml`、`page_compact.yml`、`color.yml`。
- 通用文案：`languages/zh_cn/integrations/fotiachat.yml` 和 en_us 同路径；沿用 FutureUI 的客户端语言与 FotiaTranslator 选择链。
- 主题复用 `adventure`，无整块底层遮罩，沿用已有焦点过滤与悬停资源。颜色条由实际颜色格式生成 72×9 的 RasterImage，灰色条表示不可用，不引入 AI 图片、外部纹理或每次选色重建资源包。

## 扩展契约

数据源和动作共用 `fotiachat:colors` 命名空间，需要 `/chatcolor` 创建的服务端 `fotiachat.session` 令牌；不能通过 `/fui open fotiachat/colors` 伪造会话。适配器按需从 ServicesManager 获取 FutureUI，未安装或指定菜单／客户端渲染能力不可用时回退原界面；权限、条件和打开事件拒绝时不回退绕过检查。

数据源使用 `cache: false`、`paginate: false`，分页由 FotiaChat 会话负责：

| view | 返回字段 |
|---|---|
| page | current-name、has-current、preview、page、pages、count、previous、next、empty、filter、feedback |
| entries | id、name、selected、available、swatch |

name/current-name/preview 为文本组件；swatch 为 RasterImage。feedback 为 preview / saved / reset。操作 operation 为 opened、closed、select、reset、filter、previous、next、layout、close；select 的 id 是当前页颜色 ID，filter 的 id 为 all / single / effects / available，layout 的 id 为 standard / compact。

每次点击重新检查会话、菜单身份、`fotiachat.color` 权限、颜色存在性、当前页范围及颜色自己的使用权限。关闭、退出和重载清理会话与扩展注册；布局切换在成功打开后才记忆。所有权限和玩家 API 在服务端主线程访问，预览不读数据库。两个菜单必须保留对应 opened / closed 生命周期动作。
