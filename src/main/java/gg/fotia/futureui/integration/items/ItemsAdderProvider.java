package gg.fotia.futureui.integration.items;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import dev.lone.itemsadder.api.CustomStack;

/** 通过官方 API 按需构建和识别物品，不缓存外部插件的可变物品对象。 */
public final class ItemsAdderProvider implements ItemProvider {
    public java.util.List<Node> catalog(){return CustomStack.getNamespacedIdsInRegistry().stream().sorted().map(id->Node.of(java.util.Map.of("id",id))).toList();}
    public ItemStack create(MenuContext ctx,Node node){CustomStack stack=CustomStack.getInstance(node.text("id",""));return stack==null?null:stack.getItemStack();}
    public boolean matches(MenuContext ctx,ItemStack item,Node node){CustomStack stack=CustomStack.byItemStack(item);return stack!=null&&node.text("id","").equals(stack.getNamespacedID());}
}
