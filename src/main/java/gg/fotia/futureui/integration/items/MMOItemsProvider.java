package gg.fotia.futureui.integration.items;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import net.Indyuce.mmoitems.MMOItems;

/** 通过官方 API 按需构建和识别物品，不缓存外部插件的可变物品对象。 */
public final class MMOItemsProvider implements ItemProvider {
    public java.util.List<Node> catalog(){java.util.List<Node> result=new java.util.ArrayList<>();for(var type:MMOItems.plugin.getTypes().getAll())for(var template:MMOItems.plugin.getTemplates().getTemplates(type))result.add(Node.of(java.util.Map.of("id",template.getId(),"item-type",type.getId())));return java.util.List.copyOf(result);}
    public ItemStack create(MenuContext ctx,Node node){String type=node.text("item-type","MATERIAL");return node.has("level")?MMOItems.plugin.getItem(MMOItems.plugin.getTypes().get(type),node.text("id",""),node.integer("level",1),MMOItems.plugin.getTiers().get(node.text("tier",""))):MMOItems.plugin.getItem(type,node.text("id",""));}
    public boolean matches(MenuContext ctx,ItemStack item,Node node){return Objects.equals(node.text("id",""),MMOItems.getID(item))&&Objects.equals(node.text("item-type","MATERIAL"),MMOItems.getTypeName(item));}
}
