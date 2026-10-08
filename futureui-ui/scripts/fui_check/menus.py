"""组件结构、数值与渲染器约束检查。动态布局需由插件验证。"""
import re

from .io import dynamic, merge, walk
from .logic import actions, condition, fields, link, number
from .layout import measure
from .viewports import registry as viewport_registry, check as check_viewports


def geometry(work, node, path):
    for key in ("height", "gap", "padding", "min-height"):
        if key in node:
            number(work, node[key], path + "." + key, 0, 2043, True, 9)
    if "width" in node:
        number(work, node["width"], path + ".width", 1, 1024, True)
    if "columns" in node:
        number(work, node["columns"], path + ".columns", 1, 9, True)
    for key in ("align", "text-align", "vertical-align"):
        if key in node and node[key] not in ("start", "center", "end"):
            work.report.error(path, f"{key} 只允许 start/center/end")


def numeric_input(work, node, path):
    minimum = node.get("min", 0)
    vals = [number(work, node.get(k, default), path + "." + k) for k, default in (("min", minimum), ("max", 100), ("step", 1), ("initial", minimum))]
    if any(v is None for v in vals):
        return
    low, high, step, initial = vals
    if low > high or step <= 0 or not low <= initial <= high or (initial - low) % step:
        work.report.error(path, "范围、步长或 initial 无效")
    if node.get("integer", node.get("type") == "number-input") and any(v != v.to_integral_value() for v in vals):
        work.report.error(path, "整数输入的范围、步长和初值必须均为整数")


def chart(work, node, path):
    style = node.get("chart-type", "bars")
    if style not in ("bars", "ring", "stacked"):
        work.report.error(path, "chart-type 只允许 bars/ring/stacked")
    stride = number(work, node.get("row-height", 27), path + ".row-height", 27, 144, True, 9)
    number(work, node.get("height", 108), path + ".height", 72 if style == "ring" else 45 if style == "stacked" else int(stride or 27), 2043, True, 9)
    if "width" in node:
        number(work, node["width"], path + ".width", 72 if style == "ring" else 90, 1024, True)
    series = node.get("series")
    if not isinstance(series, (list, str)) or isinstance(series, list) and len(series) > 32:
        work.report.error(path, "series 必须为最多 32 项的列表或变量引用")
    if "max" in node:
        maximum = number(work, node["max"], path + ".max")
        if maximum is not None and maximum <= 0:
            work.report.error(path, "图表 max 必须为正数")


def components(work, nodes, renderer, path, ids, depth=0, embedded=False):
    if not isinstance(nodes, list):
        work.report.error(path, "组件必须为列表")
        return
    if depth > 20:
        work.report.error(path, "组件嵌套超过 20")
        return
    for index, node in enumerate(nodes):
        at = f"{path}[{index}]"
        if not isinstance(node, dict):
            work.report.error(at, "组件必须为映射")
            continue
        fields(work, node, work.catalog["core_fields"], at)
        ident, kind = node.get("id", ""), node.get("type", "text")
        if not isinstance(ident, str) or not re.fullmatch(r"[a-zA-Z0-9_]+", ident) or ident in ids:
            work.report.error(at, f"无效或重复组件 ID: {ident}")
        ids.add(str(ident))
        if kind not in work.catalog["components"]:
            work.report.error(at, f"未知组件: {kind}")
        canvas = renderer == "canvas" or embedded
        if kind == "raster" and not canvas:
            work.report.error(at, "raster 只用于 Canvas 或 Dialog 内嵌 Canvas")
        if canvas:
            geometry(work, node, at)
            if kind not in work.catalog["canvas_components"]:
                work.report.error(at, f"Canvas 不支持 {kind}")
            if embedded and (kind in ("button", "toggle") or kind == "raster" and node.get("actions")):
                work.report.error(at, "Dialog 展示 Canvas 不能含交互按钮")
        elif renderer == "dialog":
            forbidden = work.catalog["dialog_native_button_forbidden_fields"] if kind in ("button", "toggle") else work.catalog["dialog_native_container_forbidden_fields"] if kind in ("row", "column", "grid") else []
            for key in forbidden:
                if key in node:
                    work.report.error(at, f"原生 {kind} 不支持 {key}")
        for key in ("width", "control-width"):
            if key in node:
                number(work, node[key], at + "." + key, 1, 1024, True)
        if kind == "canvas" and renderer != "dialog":
            work.report.error(at, "嵌入 canvas 只适用于 dialog")
        for key in ("visible", "enabled", "requirements"):
            condition(work, node.get(key), at + "." + key)
        for key in ("actions", "common-actions", "on-place", "on-take", "on-change"):
            actions(work, node.get(key, []), at + "." + key)
        if kind in ("number-input", "slider"):
            numeric_input(work, node, at)
        if kind == "chart":
            chart(work, node, at)
        if kind in ("text-input", "number-input"):
            number(work, node.get("max-length", 32 if kind == "number-input" else 128), at + ".max-length", 1, 32767, True)
        if kind in ("select", "multi-select"):
            options = node.get("options", [])
            if not isinstance(options, list) or not options:
                work.report.error(at, "选择控件需要 options 列表")
            else:
                choices = [option.get("value") for option in options if isinstance(option, dict)]
                if kind == "select" and "initial" in node and not dynamic(node["initial"]) and not any(dynamic(v) for v in choices) and node["initial"] not in choices:
                    work.report.error(at, "initial 不在选项中")
                if kind == "multi-select":
                    low = number(work, node.get("min-selected", 0), at + ".min-selected", 0, integer=True)
                    number(work, node.get("max-selected", len(options)), at + ".max-selected", low if low is not None else 0, integer=True)
        validation = node.get("validation", {})
        if isinstance(validation, dict):
            condition(work, validation.get("condition"), at + ".validation.condition")
        states = node.get("states", [])
        if not isinstance(states, list):
            work.report.error(at + ".states", "states 必须为列表")
        else:
            for state_index, state in enumerate(states):
                state_path = f"{at}.states[{state_index}]"
                if not isinstance(state, dict):
                    work.report.error(state_path, "状态必须为映射")
                    continue
                fields(work, state, work.catalog["core_fields"], state_path)
                number(work, state.get("priority", 0), state_path + ".priority", integer=True)
                effective = merge({k: v for k, v in node.items() if k != "states"}, state)
                # 状态互斥，同一组件的 ID 不与自己的基础配置重复计数。
                components(work, [effective], renderer, state_path, ids - {str(ident)}, depth + 1, embedded)
        for click in node.get("click-types", {}).values() if isinstance(node.get("click-types", {}), dict) else []:
            if isinstance(click, dict):
                actions(work, click.get("actions", []), at + ".click-types")
        child_embedded = embedded or kind == "canvas"
        if "children" in node:
            components(work, node["children"], renderer, at + ".children", ids, depth + 1, child_embedded)
        if kind == "list":
            number(work, node.get("page-size", 9), at + ".page-size", 1, integer=True)
            source = node.get("source", "")
            if source not in work.catalog["data_sources"]:
                work.report.warn(at, f"数据源 {source!r} 需要核实运行时注册")
            if source in ("shop-categories", "shop-products"):
                link(work, "shops", node.get("shop", "system"), at)
            item = dict(node.get("item", {})) if isinstance(node.get("item"), dict) else {}
            item.setdefault("id", "entry")
            components(work, [item], renderer, at + ".item", set(), depth + 1, embedded)
            components(work, node.get("after-items", []), renderer, at + ".after-items", ids, depth + 1, embedded)
            geometry(work, node.get("layout", {}), at + ".layout")


def check_menus(work):
    regions = viewport_registry(work)
    if not work.files:
        work.report.error("root", "未发现可检查的 FutureUI 配置文件")
    for ident, raw in work.groups["menus"].items():
        path = "menus/" + ident
        try:
            menu = work.expand(raw)
        except (ValueError, RecursionError) as exc:
            work.report.error(path, str(exc))
            continue
        fields(work, menu, work.catalog["menu_fields"], path)
        renderer = menu.get("renderer", "dialog")
        if renderer not in ("canvas", "dialog"):
            work.report.warn(path, f"{renderer} 超出本工具完整组件检查范围")
            continue
        layout = menu.get("layout", {})
        if not isinstance(layout, dict):
            work.report.error(path, "layout 必须为映射")
            continue
        if renderer == "canvas":
            geometry(work, layout, path + ".layout")
        else:
            for key, high in (("panel-width", 2047), ("panel-height", 2047), ("button-width", 1024), ("exit-width", 1024)):
                if key in layout:
                    number(work, layout[key], path + ".layout." + key, 0 if key == "panel-height" else 1, high, True)
            geometry(work, layout.get("validation-feedback", {}), path + ".feedback")
        condition(work, menu.get("requirements"), path + ".requirements")
        for key in ("on-open", "on-close"):
            actions(work, menu.get(key, []), path + "." + key)
        components(work, menu.get("components", []), renderer, path + ".components", set())
        check_viewports(work, menu, path, regions)
        if renderer == "canvas":
            if not dynamic(layout.get("width", 576)):
                measure(work, menu.get("components", []), layout, int(layout.get("width", 576)), path)
        else:
            for at, node in walk(menu.get("components", []), path):
                if isinstance(node, dict) and node.get("type") == "canvas" and not dynamic(node.get("width", 420)):
                    measure(work, node.get("children", []), node.get("layout", {}), int(node.get("width", 420)), at)
        for command in menu.get("open-commands", []):
            if not isinstance(command, str) or not re.fullmatch(r"[a-z0-9_-]{1,40}", command):
                work.report.error(path, f"无效菜单命令: {command}")
    for ident, value in work.groups["functions"].items():
        actions(work, value.get("actions", []), "functions/" + ident)
    for ident, value in work.groups["rules"].items():
        condition(work, value, "rules/" + ident)
