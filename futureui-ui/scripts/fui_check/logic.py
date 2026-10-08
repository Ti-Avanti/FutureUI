"""条件、动作与跨文件静态引用；不求值任何表达式。"""
import re
from decimal import Decimal, InvalidOperation

from .io import dynamic


def fields(work, node, allowed, path):
    if ":" in str(node.get("type", "")) or ":" in str(node.get("source", "")):
        return
    for key in node:
        if key not in allowed:
            work.report.error(path, f"未知核心字段: {key}")


def number(work, value, path, low=None, high=None, integer=False, multiple=None):
    if dynamic(value):
        return None
    try:
        if isinstance(value, bool):
            raise ValueError("布尔值不能作为数值")
        n = Decimal(str(value))
        if not n.is_finite() or integer and n != n.to_integral_value():
            raise ValueError("必须为有限整数" if integer else "必须为有限数字")
        if low is not None and n < low or high is not None and n > high:
            raise ValueError(f"不在允许范围 {low}..{high}")
        if multiple and n % multiple:
            raise ValueError(f"必须为 {multiple} 的倍数")
        return n
    except (InvalidOperation, ValueError, TypeError) as exc:
        work.report.error(path, f"无效数字 {value!r}: {exc}")
        return None


def link(work, group, target, path):
    if not target or dynamic(target):
        return
    if not isinstance(target, str) or target not in work.groups[group]:
        work.report.error(path, f"找不到 {group} 引用: {target}")


def condition(work, node, path, depth=0):
    if isinstance(node, bool) or node == {} or node is None:
        return
    if not isinstance(node, dict):
        work.report.error(path, "条件必须为映射或布尔值")
        return
    if depth > 24:
        work.report.error(path, "条件嵌套超过 24")
        return
    fields(work, node, work.catalog["core_fields"], path)
    raw_kind = node.get("type", "all" if "conditions" in node else "")
    kind = (str(raw_kind).lower() if isinstance(raw_kind, bool) else str(raw_kind)).removeprefix("!")
    if kind not in work.catalog["conditions"]:
        if ":" in kind:
            work.report.warn(path, "扩展条件需要运行时提供器验证")
        else:
            work.report.error(path, f"未知条件: {kind}")
    if kind in ("all", "any", "at-least", "requirements"):
        children = node.get("conditions")
        if not isinstance(children, list):
            work.report.error(path, "组合条件必须包含 conditions 列表")
        else:
            if kind == "at-least":
                number(work, node.get("minimum", 1), path + ".minimum", 1, len(children), True)
            for i, child in enumerate(children):
                condition(work, child, f"{path}.conditions[{i}]", depth + 1)
    if kind == "not":
        if "condition" not in node:
            work.report.error(path, "not 缺少 condition")
        condition(work, node.get("condition"), path + ".condition", depth + 1)
    if kind in ("permission", "has-permission") and not node.get("permission"):
        work.report.error(path, "权限条件缺少 permission")
    if kind == "named":
        link(work, "rules", node.get("rule"), path)
    if kind == "regex":
        work.report.warn(path, "pattern 需由插件的 RE2J 验证；未使用 Python 正则代替")
    for key in ("on-success", "on-failure"):
        actions(work, node.get(key, []), path + "." + key, depth + 1)


def actions(work, values, path, depth=0):
    if not isinstance(values, list):
        work.report.error(path, "动作必须为列表")
        return
    if depth > 24 or len(values) > 128:
        work.report.error(path, "动作列表超过 128 项或嵌套超过 24")
        return
    for i, node in enumerate(values):
        at = f"{path}[{i}]"
        if not isinstance(node, dict):
            work.report.error(at, "动作项必须为映射")
            continue
        fields(work, node, work.catalog["core_fields"], at)
        kind = node.get("type", "")
        if kind not in work.catalog["actions"]:
            if isinstance(kind, str) and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", kind):
                work.report.warn(at, "扩展动作需要运行时提供器验证")
            else:
                work.report.error(at, f"未知动作: {kind}")
        condition(work, node.get("condition"), at + ".condition", depth + 1)
        if "chance" in node:
            number(work, node["chance"], at + ".chance", 0, 1)
        if "timeout-seconds" in node:
            number(work, node["timeout-seconds"], at + ".timeout-seconds", 1, 600 if kind == "input" else None, True)
        if kind in ("if", "require", "for-players"):
            key = {"if": "when", "require": "requirements", "for-players": "filter"}[kind]
            condition(work, node.get(key), at + "." + key, depth + 1)
        if kind in ("open-menu", "call", "hud"):
            group, key = {"open-menu": ("menus", "menu"), "call": ("functions", "function"), "hud": ("hud", "hud")}[kind]
            if not node.get(key):
                work.report.error(at, f"动作缺少 {key}")
            link(work, group, node.get(key), at)
        if kind in ("purchase", "sell", "cart-add", "cart-remove"):
            shop = node.get("shop", "system")
            link(work, "shops", shop, at)
            product = node.get("product", "")
            if isinstance(shop, str) and shop in work.groups["shops"] and not dynamic(product):
                ids = {p.get("id") for p in work.groups["shops"][shop].get("products", []) if isinstance(p, dict)}
                if product not in ids:
                    work.report.error(at, f"商店 {shop} 不含商品 {product}")
        if kind == "transaction":
            steps = node.get("actions", [])
            if not steps:
                work.report.error(at, "事务不能为空")
            if isinstance(steps, list):
                for step in steps:
                    if not isinstance(step, dict):
                        continue
                    if step.get("type") not in work.catalog["transaction_actions"]:
                        work.report.error(at, f"事务不支持步骤 {step.get('type')}")
                    if any(key in step for key in ("on-success", "on-failure", "finally")) or step.get("scope") == "session":
                        work.report.error(at, "事务步骤不能带回调或 session 写入")
        if kind == "retry":
            number(work, node.get("attempts", 2), at + ".attempts", 1, 3, True)
            for step in node.get("actions", []) if isinstance(node.get("actions", []), list) else []:
                if isinstance(step, dict) and step.get("type") not in ("transaction", "require", "delay", "fail"):
                    work.report.error(at, "retry 包含不可重试的步骤")
        if kind in ("repeat", "schedule"):
            number(work, node.get("times", 1), at + ".times", 0 if kind == "repeat" else 1, 256 if kind == "repeat" else 1200, True)
        if kind == "schedule":
            number(work, node.get("interval-ticks", 20), at + ".interval-ticks", 1, integer=True)
        if kind == "input":
            if not dynamic(node.get("mode")) and node.get("mode", "dialog") not in ("dialog", "chat", "sign", "anvil", "book"):
                work.report.error(at, "未知输入模式")
            number(work, node.get("max-length", 128), at + ".max-length", 1, 32768, True)
        for key in ("then", "else", "on-success", "on-failure", "finally", "actions", "on-cancel", "on-timeout"):
            if key in node:
                actions(work, node[key], at + "." + key, depth + 1)
        for j, choice in enumerate(node.get("choices", []) if isinstance(node.get("choices", []), list) else []):
            if isinstance(choice, dict):
                actions(work, choice.get("actions", []), f"{at}.choices[{j}]", depth + 1)
