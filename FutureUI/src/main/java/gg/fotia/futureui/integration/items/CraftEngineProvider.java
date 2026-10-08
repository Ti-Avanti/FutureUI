package gg.fotia.futureui.integration.items;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;

/** 通过官方 API 按需构建和识别物品，不缓存外部插件的可变物品对象。 */
public final class CraftEngineProvider implements ItemProvider {
    public java.util.List<Node> catalog(){return CraftEngineItems.loadedItems().keySet().stream().sorted(java.util.Comparator.comparing(Object::toString)).map(id->Node.of(java.util.Map.of("id",id.toString()))).toList();}
    public ItemStack create(MenuContext ctx,Node node){var definition=CraftEngineItems.byId(node.text("id",""));return definition==null?null:definition.buildBukkitItem(ctx.player());}
    public boolean matches(MenuContext ctx,ItemStack item,Node node){return Objects.equals(node.text("id",""),String.valueOf(CraftEngineItems.getCustomItemId(item)));}
}
