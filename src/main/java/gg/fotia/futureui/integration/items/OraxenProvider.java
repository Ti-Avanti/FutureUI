package gg.fotia.futureui.integration.items;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import io.th0rgal.oraxen.api.OraxenItems;

/** 通过官方 API 按需构建和识别物品，不缓存外部插件的可变物品对象。 */
public final class OraxenProvider implements ItemProvider {
    public java.util.List<Node> catalog(){return OraxenItems.getNames().stream().sorted().map(id->Node.of(java.util.Map.of("id",id))).toList();}
    public ItemStack create(MenuContext ctx,Node node){var builder=OraxenItems.getItemById(node.text("id",""));return builder==null?null:builder.build();}
    public boolean matches(MenuContext ctx,ItemStack item,Node node){return Objects.equals(node.text("id",""),OraxenItems.getIdByItem(item));}
}
