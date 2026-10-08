package gg.fotia.futureui.api;

import gg.fotia.futureui.config.Node;
import org.bukkit.inventory.ItemStack;

/** 在主线程构建物品副本；可由任意物品插件注册。 */
@FunctionalInterface public interface ItemProvider {
    ItemStack create(MenuContext context,Node definition);
    default boolean matches(MenuContext context,ItemStack item,Node definition){ItemStack expected=create(context,definition);return expected!=null&&expected.isSimilar(item);}
    default java.util.List<Node> catalog(){return java.util.List.of();}
}
