"""商店的静态标识、价格与图片引用检查；不查询经济提供器。"""
import re

from .io import dynamic
from .logic import condition, number


def quantity_rule(work, product, path):
    """对应 QuantityRule.parse；预设去重后最多 16 项，不把 64 当订单上限。"""
    rule = product.get("quantity", {})
    if not isinstance(rule, dict):
        work.report.error(path + ".quantity", "quantity 必须为映射")
        return
    raw = {"min": rule.get("min", 1), "max": rule.get("max", product.get("maximum", 64)),
           "step": rule.get("step", 1), "initial": rule.get("initial", rule.get("min", 1))}
    values = {}
    for key, value in raw.items():
        at = path + ".quantity." + key
        if dynamic(value):
            work.report.error(at, "商品份数规则只支持静态整数")
        else:
            values[key] = number(work, value, at, 1, 2147483647, True)
    if "maximum" in product:
        if dynamic(product["maximum"]):
            work.report.error(path + ".maximum", "旧 maximum 也只支持静态整数")
        old = number(work, product["maximum"], path + ".maximum", 1, 2147483647, True)
        if "max" in rule and old is not None and values.get("max") is not None and old != values["max"]:
            work.report.error(path, "maximum 与 quantity.max 冲突")
    if len(values) != 4 or any(v is None for v in values.values()):
        return
    low, high, step, initial = (values[k] for k in ("min", "max", "step", "initial"))
    if high < low or not low <= initial <= high or (initial - low) % step:
        work.report.error(path + ".quantity", "min/max/initial/step 不构成有效份数范围")
    presets = rule.get("presets", [])
    if not isinstance(presets, list):
        work.report.error(path + ".quantity.presets", "presets 必须为列表")
        return
    unique = set()
    for i, value in enumerate(presets):
        at = f"{path}.quantity.presets[{i}]"
        if dynamic(value):
            work.report.error(at, "预设份数只支持静态整数")
            continue
        preset = number(work, value, at, 1, 2147483647, True)
        if preset is not None:
            unique.add(preset)
            if not low <= preset <= high or (preset - low) % step:
                work.report.error(at, "预设份数超出范围或未对齐 min + n × step")
    if len(unique) > 16:
        work.report.error(path + ".quantity.presets", "去重后最多允许 16 个预设")


def check_shops(work):
    images = set(work.assets.get("images", {}))
    if not work.base:
        images.update(work.catalog["defaults"]["builtin_images"])
    for ident, shop in work.groups["shops"].items():
        path = "shops/" + ident
        categories, products = shop.get("categories", []), shop.get("products", [])
        if not isinstance(categories, list) or not categories or not isinstance(products, list):
            work.report.error(path, "商店需要非空 categories 列表及 products 列表")
            continue
        category_ids = [n.get("id") for n in categories if isinstance(n, dict)]
        if len(category_ids) != len(categories) or len(category_ids) != len(set(category_ids)) or any(not isinstance(v, str) or not v for v in category_ids):
            work.report.error(path, "分类 ID 为空、重复或类型错误")
        seen = set()
        for i, product in enumerate(products):
            at = f"{path}.products[{i}]"
            if not isinstance(product, dict):
                work.report.error(at, "商品必须为映射")
                continue
            pid = product.get("id")
            if not isinstance(pid, str) or not re.fullmatch(r"[a-z0-9_.-]+", pid) or pid in seen:
                work.report.error(at, "商品 ID 无效或重复，只允许小写字母、数字、下划线、点和短横线")
            seen.add(str(pid))
            if product.get("category") not in category_ids:
                work.report.error(at, "商品引用了不存在的分类")
            number(work, product.get("amount", 1), at + ".amount", 1, integer=True)
            number(work, product.get("price", 0), at + ".price", 0)
            quantity_rule(work, product, at)
            for key in ("stock", "daily-limit", "daily-sell-limit"):
                number(work, product.get(key, -1), at + "." + key, -1, 2147483647, True)
            discounts = product.get("discounts", [])
            if not isinstance(discounts, list):
                work.report.error(at + ".discounts", "discounts 必须为列表")
            else:
                for index, discount in enumerate(discounts):
                    if not isinstance(discount, dict):
                        work.report.error(at + ".discounts", "折扣项必须为映射")
                        continue
                    condition(work, discount.get("condition"), f"{at}.discounts[{index}].condition")
            item = product.get("item", {})
            if not isinstance(item, dict):
                work.report.error(at, "item 必须为映射")
            else:
                icon = item.get("icon")
                if isinstance(icon, str) and not dynamic(icon) and icon not in images:
                    work.report.error(at, f"商品图标未注册: {icon}")
            condition(work, product.get("requirements"), at + ".requirements")
