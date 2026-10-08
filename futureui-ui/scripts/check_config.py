#!/usr/bin/env python3
"""只读检查 FutureUI 配置增量；不执行插件、业务动作或资源构建。"""
import sys
sys.dont_write_bytecode = True
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

import argparse
import hashlib
import json
from pathlib import Path

try:
    from fui_check.io import Workspace, Report
    from fui_check.menus import check_menus
    from fui_check.assets import check_assets
    from fui_check.commerce import check_shops
except ModuleNotFoundError as exc:
    if exc.name == "yaml":
        print("需要 PyYAML。请在选定的 Python 环境安装：python -m pip install PyYAML", file=sys.stderr)
        raise SystemExit(2)
    raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True, type=Path, help="待交付配置目录，内含 menus/ 等")
    parser.add_argument("--base", type=Path, help="只读合并已有 FutureUI 数据目录")
    parser.add_argument("--source", type=Path, help="只读比对插件源码目录与参考指纹")
    parser.add_argument("--plugin-version", help="已安装插件的版本（不是 Minecraft 版本）")
    parser.add_argument("--json", action="store_true", help="将结构化结果输出到 stdout")
    args = parser.parse_args()
    report = Report()
    catalog_path = Path(__file__).resolve().parents[1] / "references/capabilities.json"
    try:
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        if args.plugin_version and args.plugin_version != catalog["baseline"]["plugin_version"]:
            report.warn("version", "目标插件版本不同于参考快照；须核对字段和行为后使用。")
        if args.source:
            for rel, expected in catalog["baseline"]["source_fingerprints"].items():
                source = args.source / rel
                if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != expected:
                    report.warn(rel, "源码与参考快照不同；该文件涉及的能力需要复核。")
        work = Workspace(args.root, args.base, catalog, report)
        work.load()
        check_menus(work)
        check_assets(work)
        check_shops(work)
    except (OSError, ValueError, RecursionError, TypeError, AttributeError) as exc:
        report.error("input", str(exc))
    result = {
        "status": "failed" if report.errors else "static-check-passed",
        "scope": "UTF-8/YAML、模板、独立主题、状态覆盖、静态引用、布局与份数规则、PNG 结构和分行规格",
        "not_verified": ["Bukkit/PAPI/第三方提供器", "真实交易和所有动作分支", "字体排版、着色器编译和客户端显示", "完整资源包与全部客户端版本兼容"],
        "errors": report.errors,
        "warnings": report.warnings,
    }
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(f"{result['status']}: {len(report.errors)} errors, {len(report.warnings)} warnings")
        for level, entries in (("ERROR", report.errors), ("WARN", report.warnings)):
            for entry in entries:
                print(f"{level} {entry['path']}: {entry['message']}")
        print("仅完成所列静态检查；未执行插件、交易或客户端验证。")
    return 1 if report.errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
