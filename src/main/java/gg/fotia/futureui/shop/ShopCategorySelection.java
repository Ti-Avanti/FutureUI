package gg.fotia.futureui.shop;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 分类按钮和商品查询共用的选择状态；兼容单个 ID 与多选列表。 */
public final class ShopCategorySelection {
    private ShopCategorySelection() {}

    public static List<String> resolve(ShopDefinition shop, Object value) {
        String fallback = shop.categories().getFirst().id();
        if (value == null) return List.of(fallback);
        Set<String> available = shop.categories().stream()
                .map(ShopDefinition.Category::id).collect(Collectors.toSet());
        if (value instanceof Collection<?> values) {
            // 显式空列表表示不限制分类，不回填第一个分类。
            if (values.isEmpty()) return List.of();
            List<String> selected = values.stream().filter(Objects::nonNull)
                    .map(String::valueOf).filter(available::contains).distinct().toList();
            return selected.isEmpty() ? List.of(fallback) : selected;
        }
        String selected = String.valueOf(value);
        if (selected.isBlank()) return List.of();
        return List.of(available.contains(selected) ? selected : fallback);
    }
}
