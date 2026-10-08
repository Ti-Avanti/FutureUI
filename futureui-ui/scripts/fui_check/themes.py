"""独立主题的作用域、别名与生成规格；按 1.1.3 的查找顺序检查。"""
import re

from .io import dynamic
from .logic import number


class ThemeCatalog:
    def __init__(self, work):
        self.work = work
        self.styles, self.aliases, self.sizes = {}, {}, set()
        self._read("", work.assets, "assets/theme.yml")
        if not work.base:
            self.styles[""].update(work.catalog["defaults"]["skins"])
        for name, theme in work.themes.items():
            path = f"assets/themes/{name}.yml"
            if not re.fullmatch(r"[a-z0-9_-]+", name):
                work.report.error(path, "主题文件名只允许小写字母、数字、下划线和短横线")
            self._read(name, theme, path)

    def _mapping(self, value, path):
        if isinstance(value, dict):
            return value
        self.work.report.error(path, "必须为映射")
        return {}

    def _read(self, name, theme, path):
        canvas = self._mapping(theme.get("canvas", {}), path + ".canvas")
        self.styles[name] = set(self._mapping(canvas.get("styles", {}), path + ".canvas.styles"))
        aliases = self._mapping(theme.get("skin-aliases", {}), path + ".skin-aliases")
        self.aliases[name] = aliases
        for alias, target in aliases.items():
            if not isinstance(target, str) or target not in self.styles[name]:
                self.work.report.error(path + ".skin-aliases." + alias, f"别名必须指向本主题已有皮肤: {target}")
        sizes = canvas.get("image-sizes", [] if name else self.work.catalog["defaults"]["image_sizes"])
        if not isinstance(sizes, list):
            self.work.report.error(path, "canvas.image-sizes 必须为列表")
            sizes = []
        if not name and not sizes:
            sizes = self.work.catalog["defaults"]["image_sizes"]
        for i, value in enumerate(sizes):
            parsed = number(self.work, value, f"{path}.canvas.image-sizes[{i}]", 9, 144, True, 9)
            if parsed is not None:
                self.sizes.add(int(parsed))
        interaction = self._mapping(canvas.get("interaction", {}), path + ".canvas.interaction")
        heights = interaction.get("hover-heights", [])
        if not isinstance(heights, list):
            self.work.report.error(path, "hover-heights 必须为列表")
        else:
            for value in heights:
                number(self.work, value, path + ".canvas.interaction.hover-heights", 9, 252, True, 9)

    def scope(self, node, inherited, path):
        layout = node.get("layout", {})
        if not isinstance(layout, dict) or "theme" not in layout:
            return inherited
        name = layout["theme"]
        if not isinstance(name, str) or name not in self.styles:
            self.work.report.error(path + ".layout.theme", f"不存在的静态菜单主题: {name}")
            return ""
        return name

    def check_skin(self, theme, value, path):
        if not isinstance(value, str) or not value or dynamic(value) or theme is None:
            return
        local = self.aliases.get(theme, {}).get(value, value)
        if local not in self.styles.get(theme, set()) and value not in self.styles[""]:
            self.work.report.error(path, f"主题 {theme or '默认'} 中不存在 skin: {value}")

    def nodes(self, value, path, inherited=""):
        """模板中的局部皮肤由引用它的菜单确定，展开后再按作用域检查。"""
        if isinstance(value, list):
            for i, child in enumerate(value):
                yield from self.nodes(child, f"{path}[{i}]", inherited)
        elif isinstance(value, dict):
            theme = self.scope(value, inherited, path)
            yield path, value, theme
            for key, child in value.items():
                if isinstance(child, (dict, list)):
                    yield from self.nodes(child, path + "." + key, theme)
