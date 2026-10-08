"""按插件的单项列表样本检查静态布局；不代表实际数据或客户端字体测量。"""
from .io import dynamic, merge


def _int(node, key, default=0):
    value = node.get(key, default)
    if dynamic(value):
        raise LookupError("动态尺寸")
    return int(value)


def _sample(nodes, depth=0):
    if depth > 20:
        raise ValueError("布局嵌套超过 20 层")
    result = []
    for node in nodes:
        if node.get("type") == "list":
            item = node.get("item", {})
            if "template" in item:
                continue
            children = _sample([item, *node.get("after-items", [])], depth + 1)
            if any(key in node for key in ("layout", "width", "height", "min-height", "padding", "skin")):
                layout = node.get("layout", {})
                group = merge(node, layout)
                for key in ("layout", "item", "after-items", "states"):
                    group.pop(key, None)
                group.update(type=layout.get("type", "column"),
                             id=str(node.get("id", "list")) + "_layout", children=children)
                result.append(group)
            else:
                result.extend(children)
        elif "children" in node:
            result.append({**node, "children": _sample(node["children"], depth + 1)})
        else:
            result.append(node)
    return result


def measure(work, nodes, settings, width, path, depth=0):
    if depth > 20 or not isinstance(nodes, list):
        return None
    try:
        if depth == 0:
            # 与 MenuValidator.sample 一致：条件尚无玩家上下文，不跳过互斥分支。
            nodes = _sample(nodes)
        padding, gap = _int(settings, "padding"), _int(settings, "gap", 9)
        inner = width - padding * 2
        mode = settings.get("type", "column")
        if mode not in ("row", "column", "grid"):
            work.report.error(path, f"无效容器类型: {mode}")
            return None
        if inner < 1:
            work.report.error(path, "内边距挤占全部可用宽度")
            return None
        columns = max(1, len(nodes)) if mode == "row" else _int(settings, "columns", 2) if mode == "grid" else 1
        if columns < 1:
            return None
        cell = (inner - gap * (columns - 1)) // columns
        if mode == "row":
            fixed = sum(_int(n, "width") for n in nodes if "width" in n)
            remaining = inner - gap * max(0, len(nodes) - 1) - fixed
            auto = sum("width" not in n for n in nodes)
            if remaining < 0:
                work.report.error(path, "固定子项宽度与间距超过父容器")
                return None
            widths = [_int(n, "width") if "width" in n else remaining // max(1, auto) for n in nodes]
        else:
            widths = [min(cell if mode == "grid" else inner, _int(n, "width", cell if mode == "grid" else inner)) for n in nodes]
        rows, current, unknown = [], 0, False
        for i, (node, available) in enumerate(zip(nodes, widths)):
            if not isinstance(node, dict):
                return None
            at = path + "." + str(node.get("id", i))
            if available < 1:
                work.report.error(at, "组件没有可用宽度")
                return None
            if i and i % columns == 0:
                rows.append(current)
                current = 0
            kind = node.get("type", "text")
            if kind == "viewport":
                intrinsic = _int(node, "height", 99)
            elif "children" in node:
                intrinsic = measure(work, node["children"], node, available, at, depth + 1)
                if intrinsic is None:
                    unknown = True
                    continue
            else:
                intrinsic = 27 if kind in ("button", "toggle") else _int(node, "size", 72) if kind == "image" else 108 if kind in ("chart", "raster") else 9 + 2 * _int(node, "padding") if kind == "text" else 9
            height = max(_int(node, "min-height"), _int(node, "height", intrinsic))
            if "children" in node and height < intrinsic:
                work.report.error(at, f"容器高度 {height} 小于静态内容高度 {intrinsic}")
            if kind == "text" and (available - 2 * _int(node, "padding") < 1 or height - 2 * _int(node, "padding") < 9):
                work.report.error(at, "文字底板的内边距未给文字留出空间")
            if kind == "image" and available < _int(node, "size", 72):
                work.report.error(at, "图片显示规格宽于其分配区域")
            current = max(current, height)
        if unknown:
            return None
        if nodes:
            rows.append(current)
        intrinsic = max(_int(settings, "min-height"), sum(rows) + max(0, len(rows) - 1) * gap + 2 * padding)
        if "height" in settings and _int(settings, "height") < intrinsic:
            work.report.error(path, f"布局高度不足，需要至少 {intrinsic}")
        return max(intrinsic, _int(settings, "height", intrinsic))
    except (LookupError, TypeError, ValueError):
        return None
