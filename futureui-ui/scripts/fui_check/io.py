"""YAML 读取、配置合并、模板展开和语言键检查。"""
import copy
import re
from pathlib import Path

import yaml


class Report:
    def __init__(self):
        self.errors, self.warnings = [], []

    def error(self, path, message):
        self.errors.append({"path": str(path), "message": message})

    def warn(self, path, message):
        entry = {"path": str(path), "message": message}
        if entry not in self.warnings:
            self.warnings.append(entry)


class UniqueLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        seen = set()
        for key, _ in node.value:
            if key.tag == "tag:yaml.org,2002:merge":
                continue
            value = self.construct_object(key, deep=False)
            if not isinstance(value, str):
                raise ValueError(f"{key.start_mark}: YAML 配置键应为字符串")
            if value in seen:
                raise ValueError(f"{key.start_mark}: 重复 YAML 键 {value}")
            seen.add(value)
        self.flatten_mapping(node)
        return super().construct_mapping(node, deep=deep)


def dynamic(value):
    return isinstance(value, str) and ("{" in value or "%" in value)


def merge(base, patch):
    result = copy.deepcopy(base)
    for key, value in patch.items():
        result[key] = merge(result[key], value) if isinstance(result.get(key), dict) and isinstance(value, dict) else copy.deepcopy(value)
    return result


def walk(value, path="", depth=0):
    if depth > 64:
        raise ValueError(f"{path}: 配置过深或存在递归 YAML 别名")
    yield path, value
    children = value.items() if isinstance(value, dict) else enumerate(value) if isinstance(value, list) else ()
    for key, child in children:
        yield from walk(child, f"{path}.{key}", depth + 1)


def read_yaml(path):
    raw = path.read_bytes()
    if raw.startswith(b"\xef\xbb\xbf"):
        raise ValueError(f"{path}: 请保存为 UTF-8 无 BOM")
    try:
        value = yaml.load(raw.decode("utf-8"), Loader=UniqueLoader)
    except (yaml.YAMLError, UnicodeError) as exc:
        raise ValueError(f"{path}: {exc}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"{path}: 文件根节点必须为映射")
    for _ in walk(value):
        pass
    return value


class Workspace:
    GROUPS = ("menus", "templates", "functions", "rules", "shops", "hud")

    def __init__(self, root, base, catalog, report):
        self.root = root.resolve()
        self.base = base.resolve() if base else None
        self.catalog, self.report = catalog, report
        self.files, self.changed = {}, set()
        self.groups = {group: {} for group in self.GROUPS}
        self.assets, self.settings, self.languages, self.themes = {}, {}, {}, {}
        self.origins = {}

    def load(self):
        for directory in (self.base, self.root):
            if directory is None:
                continue
            if not directory.is_dir():
                raise ValueError(f"配置目录不存在: {directory}")
            paths = []
            for group in (*self.GROUPS, "languages"):
                paths.extend((directory / group).rglob("*.yml"))
            paths.extend(directory / p for p in ("config.yml", "assets/images.yml", "assets/theme.yml", "assets/pack.yml", "assets/versions.yml", "assets/viewports.yml") if (directory / p).is_file())
            paths.extend((directory / "assets/themes").glob("*.yml"))
            paths.extend((directory / "assets/images").glob("*.yml"))
            for path in sorted(set(paths)):
                rel = path.relative_to(directory).as_posix()
                try:
                    value = read_yaml(path)
                except (OSError, ValueError) as exc:
                    self.report.error(rel, str(exc))
                    continue
                if directory == self.root:
                    self.changed.add(rel)
                shared = rel.startswith(("languages/", "assets/themes/", "assets/images/")) or rel in {"config.yml", "assets/images.yml", "assets/theme.yml", "assets/pack.yml", "assets/versions.yml", "assets/viewports.yml"}
                self.files[rel] = merge(self.files.get(rel, {}), value) if shared else value
                self.origins[rel] = path
        for rel, value in self.files.items():
            group = rel.split("/", 1)[0]
            if group in self.groups:
                ident = rel[len(group) + 1:-4] if group == "templates" else value.get("id", rel[len(group) + 1:-4])
                if not isinstance(ident, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_/-]*", ident):
                    self.report.error(rel, "无效文件 ID")
                    continue
                if ident in self.groups[group]:
                    self.report.error(rel, f"重复 {group} ID: {ident}")
                self.groups[group][ident] = value
            if rel.startswith("languages/"):
                self._language(rel, value)
            if rel.startswith("assets/themes/"):
                self.themes[Path(rel).stem] = value
        self.settings = self.files.get("config.yml", {})
        images = {}
        for rel in sorted(self.files):
            if rel.startswith("assets/images/"):
                images = merge(images, self.files[rel])
        images = merge(images, self.files.get("assets/images.yml", {}))
        self.assets = merge(images, self.files.get("assets/theme.yml", {}))
        self._translations()

    def _language(self, rel, value):
        parts = Path(rel).parts
        if len(parts) < 3:
            self.report.error(rel, "语言文件应为 languages/<locale>/<document>.yml")
            return
        locale = parts[1].lower().replace("-", "_")
        prefix = ".".join(parts[2:])[:-4]
        entries = self.languages.setdefault(locale, {})
        for path, leaf in walk(value, prefix):
            if not isinstance(leaf, (dict, list)):
                if path in entries:
                    self.report.error(rel, f"重复语言键 {path}")
                entries[path] = str(leaf)

    def _translations(self):
        baseline = self.catalog["defaults"]["language_keys"] if not self.base else {}
        known = set().union(*(set(v) for v in self.languages.values()), *(set(v) for v in baseline.values()))
        for rel in self.changed:
            for path, value in walk(self.files[rel], rel):
                if isinstance(value, str) and value.startswith("@") and not dynamic(value) and value[1:] not in known:
                    self.report.error(path, f"找不到语言键 {value}")
        tokens = lambda value: set(re.findall(r"\{(?:raw:|expand:)?[a-zA-Z0-9_.-]+\}|%[^%\s]+%", value))
        locales = sorted(self.languages)
        allkeys = set().union(*(set(v) for v in self.languages.values()))
        for key in allkeys:
            variants = [(locale, entries[key]) for locale, entries in self.languages.items() if key in entries]
            if len(variants) > 1 and any(tokens(v) != tokens(variants[0][1]) for _, v in variants[1:]):
                expected = set().union(*(tokens(value) for _, value in variants))
                missing = [locale + " 缺少 " + ", ".join(sorted(expected - tokens(value)))
                           for locale, value in variants if expected - tokens(value)]
                self.report.error(key, "不同语言的占位符不一致；" + "；".join(missing))
            for locale in locales:
                if key not in self.languages[locale] and key not in baseline.get(locale, []):
                    self.report.warn(key, f"{locale} 缺少翻译，将依赖运行时回退")

    def expand(self, value, stack=(), depth=0):
        if depth > 64:
            raise ValueError("模板展开过深或存在递归别名")
        if isinstance(value, list):
            return [self.expand(v, stack, depth + 1) for v in value]
        if not isinstance(value, dict):
            return value
        value = copy.deepcopy(value)
        ref = value.get("template")
        if ref:
            if not isinstance(ref, str) or ref not in self.groups["templates"]:
                raise ValueError(f"缺少模板: {ref}")
            if ref in stack:
                raise ValueError(f"模板循环: {' -> '.join((*stack, ref))}")
            base = self.expand(self.groups["templates"][ref], (*stack, ref), depth + 1)
            value = merge(base, value)
        value.pop("template", None)
        return {k: self.expand(v, stack, depth + 1) for k, v in value.items()}

    def texture_path(self, name):
        for directory in (self.root, self.base):
            if directory is None:
                continue
            base = (directory / "assets/textures").resolve()
            path = (base / name).resolve()
            if not path.is_relative_to(base):
                raise ValueError(f"图片路径越出 assets/textures: {name}")
            if path.is_file():
                return path
        raise ValueError(f"找不到图片: {name}")
