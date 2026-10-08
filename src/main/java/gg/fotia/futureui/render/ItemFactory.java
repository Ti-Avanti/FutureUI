package gg.fotia.futureui.render;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.item.ItemAttributes;
import gg.fotia.futureui.i18n.TextService;
import java.util.*;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;

/** 物品配置与业务动作隔离；每个观看者得到独立副本。 */
public final class ItemFactory {
    private final ExtensionRegistry<ItemProvider> providers;private final ValueResolver values;
    public final ItemAttributes attributes;
    public ItemFactory(TextService text,ExtensionRegistry<ItemProvider> providers,ValueResolver values){this.providers=providers;this.values=values;attributes=new ItemAttributes(text,values);}
    public ItemStack create(MenuContext ctx,Node source){
        Node node=values.bind(ctx,source,Set.of("name","lore"));
        String provider=node.text("provider","vanilla");ItemStack item;
        if(node.has("serialized")){String encoded=node.text("serialized","");if(encoded.length()>262144)throw new IllegalArgumentException("Serialized item exceeds limit");item=ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));}
        else if(provider.equals("vanilla")){Material material=Material.matchMaterial(node.text("material","PAPER"));if(material==null)throw new IllegalArgumentException("Unknown item material");item=new ItemStack(material);}
        else{ItemProvider factory=providers.get(provider);if(factory==null)throw new IllegalArgumentException("Unknown item provider "+provider);item=Objects.requireNonNull(factory.create(ctx,node),"Unknown custom item").clone();}
        return attributes.apply(ctx,item,node);
    }
}
