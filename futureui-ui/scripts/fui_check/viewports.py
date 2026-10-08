"""水平展示带的静态配置契约；不代替客户端裁切检查。"""
import re
from .io import dynamic
from .logic import number


def registry(work):
    regions = work.files.get("assets/viewports.yml", {})
    if len(regions) > 64:
        work.report.error("assets/viewports.yml", "最多支持 64 个区域")
    for name, value in regions.items():
        path = "assets/viewports.yml." + name
        if not re.fullmatch(r"[a-z0-9_-]+", name) or not isinstance(value, dict):
            work.report.error(path, "区域 ID 或结构无效")
            continue
        for field in value:
            if field not in {"canvas-width", "left", "width"}:
                work.report.error(path, f"未知字段 {field}")
        bounds = [number(work, value.get(k, default), path + "." + k, low, 1024, True)
                  for k, default, low in (("canvas-width", 540, 1), ("left", 0, 0), ("width", value.get("canvas-width", 540), 1))]
        if all(v is not None for v in bounds) and bounds[1] + bounds[2] > bounds[0]:
            work.report.error(path, "裁切区域超出画布宽度")
        if any(dynamic(v) for v in value.values()):
            work.report.error(path, "资源区域尺寸必须是静态整数")
    return regions


def check(work, menu, path, regions):
    display = {"text", "image", "raster", "progress", "chart", "spacer", "row", "column", "grid", "list"}

    def inspect(nodes, inside=False):
        for node in nodes:
            if not isinstance(node, dict):
                continue
            kind, at = node.get("type", "text"), path + "." + str(node.get("id", ""))
            if inside and (kind not in display or node.get("actions")):
                work.report.error(at, "viewport 内只允许展示组件；按钮放在外部")
            viewport = kind == "viewport"
            if viewport:
                if menu.get("renderer", "dialog") != "canvas":
                    work.report.error(at, "viewport 只用于独立 Canvas 菜单")
                region = regions.get(node.get("region"), {})
                if not region:
                    work.report.error(at, "region 未在 assets/viewports.yml 注册")
                elif region.get("canvas-width", 540) != menu.get("layout", {}).get("width", 576):
                    work.report.error(at, "区域 canvas-width 与菜单宽度不一致")
                number(work, node.get("height", 0), at + ".height", 9, 2043, True, 9)
                number(work, node.get("visible-items", 7), at + ".visible-items", 1, 32, True)
                number(work, node.get("position", 0), at + ".position", -65536, 65536)
                if not dynamic(node.get("direction")) and node.get("direction", "left") not in ("left", "right"):
                    work.report.error(at, "direction 只允许 left/right")
                motion = node.get("motion", {})
                if motion and "position" in node:
                    work.report.error(at, "position 与 motion 二选一")
                if isinstance(motion, dict):
                    for field in motion:
                        if field not in {"from", "to", "duration-ticks", "delay-ticks", "easing", "loop", "interval-ticks"}:
                            work.report.error(at, f"未知 motion 字段 {field}")
                    for key, default, low, high, integer in (("from", 0, -65536, 65536, False), ("to", 1, -65536, 65536, False), ("duration-ticks", 80, 1, 12000, True), ("delay-ticks", 0, 0, 12000, True), ("interval-ticks", 1, 1, 20, True)):
                        number(work, motion.get(key, default), at + ".motion." + key, low, high, integer)
                    if dynamic(motion.get("interval-ticks")):
                        work.report.error(at, "motion.interval-ticks 必须是静态整数")
                    if not dynamic(motion.get("easing")) and motion.get("easing", "linear") not in ("linear", "ease-in", "ease-out", "ease-in-out"):
                        work.report.error(at, "无效 motion.easing")
            inspect(node.get("children", []), inside or viewport)
            inspect(node.get("after-items", []), inside or viewport)
            if kind == "list":
                inspect([node.get("item", {})], inside)
    inspect(menu.get("components", []))
