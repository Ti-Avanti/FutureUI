package gg.fotia.futureui.api;

import gg.fotia.futureui.config.Node;

/** 条件必须无副作用且只读取内存。 */
@FunctionalInterface public interface MenuCondition {
    boolean test(MenuContext context,Node options);
}
