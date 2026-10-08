"""资源引用、PNG 文件结构和画布分行规格；不编译着色器。"""
import re
import struct
import zlib

from .io import dynamic
from .logic import number
from .themes import ThemeCatalog


def png_size(path):
    with path.open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            raise ValueError("文件内容不是 PNG")
        dimensions, image_data, ended = None, False, False
        total = 0
        while not ended:
            header = stream.read(8)
            if len(header) != 8:
                raise ValueError("PNG 缺少完整块或 IEND")
            size, kind = struct.unpack(">I4s", header)
            total += size
            if size > 32 * 1024 * 1024 or total > 64 * 1024 * 1024:
                raise ValueError("图片超过离线检查工具 64 MiB 上限")
            data, checksum = stream.read(size), stream.read(4)
            if len(data) != size or len(checksum) != 4 or zlib.crc32(kind + data) & 0xffffffff != struct.unpack(">I", checksum)[0]:
                raise ValueError("PNG 块不完整或 CRC 校验失败")
            if dimensions is None:
                if kind != b"IHDR" or size != 13:
                    raise ValueError("PNG 首块必须为 IHDR")
                dimensions = struct.unpack(">II", data[:8])
                if min(dimensions) < 1:
                    raise ValueError("PNG 宽高必须大于 0")
            image_data |= kind == b"IDAT"
            ended = kind == b"IEND"
        if not image_data:
            raise ValueError("PNG 缺少图像数据")
        return dimensions


def check_assets(work):
    assets = work.assets
    themes = ThemeCatalog(work)
    sizes = themes.sizes
    images = assets.get("images", {})
    if not isinstance(images, dict):
        work.report.error("assets/images.yml", "images 必须为映射")
        images = {}
    known = set(images) | (set(work.catalog["defaults"]["builtin_images"]) if not work.base else set())
    for ident, spec in images.items():
        at = f"assets/images.yml.images.{ident}"
        if not re.fullmatch(r"[a-z0-9_/-]+", ident):
            work.report.error(at, "图片 ID 只能包含小写字母、数字、下划线、短横线或 /")
        if not isinstance(spec, dict):
            work.report.error(at, "图片配置必须为映射")
            continue
        if "vanilla" in spec:
            if not isinstance(spec["vanilla"], str) or not re.fullmatch(r"minecraft:[a-z0-9_/-]+\.png", spec["vanilla"]):
                work.report.error(at, "无效原版材质路径")
            height, width = 16, 16
        else:
            try:
                path = work.texture_path(spec.get("file", ident + ".png"))
                if path.suffix.lower() != ".png":
                    raise ValueError("图片应使用 .png 扩展名")
                width, height = png_size(path)
                target = number(work, spec.get("canvas-height", 0), at + ".canvas-height", 0, 240, True)
                if target:
                    if target < 16:
                        work.report.error(at, "canvas-height 为 0 或 16..240")
                    width, height = max(1, int(width * int(target) / height + .5)), int(target)
                    if width > 240:
                        work.report.error(at, "归一化图片宽度不能超过 240，请减小 canvas-height")
                if width != height:
                    work.report.warn(at, "非正方形图片：分配宽度至少为 size × 原图宽高比，绘制器按实际宽高居中")
            except (OSError, ValueError, TypeError) as exc:
                work.report.error(at, str(exc))
                continue
        metric = number(work, spec.get("height", 16 if "vanilla" in spec else 24), at + ".height", 1, 256, True)
        ascent = number(work, spec.get("ascent", 14 if "vanilla" in spec else int(metric or 24) - 2), at + ".ascent", integer=True)
        if metric is not None and ascent is not None and ascent > metric:
            work.report.error(at, "ascent 不能大于 height")
        for size in sizes:
            if height % (size // 9):
                work.report.error(at, f"原图高度 {height} 不能按显示 size={size} 分成整行")
        if "row-widths" in spec:
            rows = spec["row-widths"]
            if not isinstance(rows, list) or len(rows) != 16:
                work.report.warn(at, "原版 row-widths 应含 16 项，否则编译器使用回退值")
            elif any(number(work, v, at + ".row-widths", 0, width, True) is None for v in rows):
                pass
    metadata = work.files.get("assets/pack.yml", {})
    if metadata and metadata.get("namespace") != "futureui":
        work.report.error("assets/pack.yml", "namespace 必须保持 futureui")
    for group in ("menus", "templates"):
        for ident, raw in work.groups[group].items():
            try:
                expanded = work.expand(raw)
            except (ValueError, RecursionError):
                continue
            for path, node, theme in themes.nodes(expanded, group + "/" + ident, None if group == "templates" else ""):
                image_keys = ("image", "fallback-image", "background-image", "icon") if node.get("type") in ("button", "toggle") else ("image", "fallback-image", "background-image")
                for key in image_keys:
                    value = node.get(key)
                    if isinstance(value, str) and value and not dynamic(value) and value not in known:
                        work.report.error(path, f"未注册图片: {value}")
                for key in ("skin", "disabled-skin", "fill-skin", "track-skin", "value-skin"):
                    value = node.get(key)
                    themes.check_skin(theme, value, path + "." + key)
                if node.get("type") == "image" and not dynamic(node.get("size", 72)) and node.get("size", 72) not in sizes:
                    work.report.error(path, f"图片显示规格未生成: {node.get('size', 72)}")
                if node.get("type") == "image":
                    size, height = node.get("size", 72), node.get("height", node.get("size", 72))
                    if isinstance(size, int) and isinstance(height, int) and height >= size and (height - size) % 18:
                        work.report.warn(path, "图标上下留白不能在 9 像素网格中等距；建议 height-size 为 18 的倍数")
                if node.get("background-image") and not dynamic(node.get("background-size", 72)) and node.get("background-size", 72) not in sizes:
                    work.report.error(path, "background-size 必须使用已生成的图片规格")
                if node.get("type") in ("button", "toggle") and node.get("icon"):
                    size = number(work, node.get("icon-size", 18), path + ".icon-size", 9, 144, True, 9)
                    if size is not None and int(size) not in sizes:
                        work.report.error(path, f"按钮图标显示规格未生成: {size}")
                    height = node.get("height", 27)
                    if size is not None and isinstance(height, int) and height >= size and (height - size) % 18:
                        work.report.warn(path, "按钮中的图标无法严格垂直居中；建议独立对齐图标位或调整按钮高度")
                    if "icon-gap" in node:
                        number(work, node["icon-gap"], path + ".icon-gap", 0, 1024, True)
    if "assets/versions.yml" in work.changed:
        work.report.warn("assets/versions.yml", "版本覆盖层需要插件构建和对应客户端验证；本工具不认证版本范围/着色器")
