package gg.fotia.futureui.item;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.i18n.TextService;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

/** 精确匹配不修改物品；禁止仅按材质误扣其他插件的带数据物品。 */
public final class ItemMatcher {
    private final ValueResolver values;private final TextService text;private final ExtensionRegistry<ItemProvider> providers;
    public ItemMatcher(ValueResolver values,TextService text,ExtensionRegistry<ItemProvider> providers){this.values=values;this.text=text;this.providers=providers;}
    public int count(MenuContext ctx,Node spec){Node node=values.bind(ctx,spec,Set.of("name","lore"));int count=0;for(var slot:ItemSlots.select(ctx,node))if(matches(ctx,slot.get(),node))count=Math.addExact(count,slot.get().getAmount());return count;}
    public boolean matches(MenuContext ctx,ItemStack item,Node source){
        Node node=values.bind(ctx,source,Set.of("name","lore"));
        if(item==null||item.getType().isAir())return node.text("material","").equalsIgnoreCase("AIR");
        if(node.has("material")&&item.getType()!=Material.matchMaterial(node.text("material","")))return false;
        if(node.has("provider")&&!node.text("provider","vanilla").equals("vanilla")){ItemProvider provider=providers.get(node.text("provider",""));if(provider==null||!provider.matches(ctx,item,node))return false;}
        var meta=item.getItemMeta();
        if(node.bool("plain",false)&&item.hasItemMeta())return false;
        if(node.has("name")&&!match(plain(meta.displayName()),text.plain(ctx.player(),ctx.session().locale,node.text("name",""),ctx.variables()),node))return false;
        if(node.has("lore")){
            List<String> actual=meta.lore()==null?List.of():meta.lore().stream().map(ItemMatcher::plain).toList();
            List<String> expected=node.strings("lore").stream().map(s->text.plain(ctx.player(),ctx.session().locale,s,ctx.variables())).toList();
            if(node.text("lore-mode","exact").equals("contains")){if(expected.stream().anyMatch(e->actual.stream().noneMatch(a->match(a,e,node))))return false;}
            else{if(actual.size()!=expected.size())return false;for(int i=0;i<actual.size();i++)if(!match(actual.get(i),expected.get(i),node))return false;}
        }
        if(node.has("damage")&&(!(meta instanceof Damageable d)||d.getDamage()!=node.integer("damage",0)))return false;
        if(node.has("custom-model-data")&&(!meta.hasCustomModelData()||meta.getCustomModelData()!=node.integer("custom-model-data",0)))return false;
        Node model=node.child("model-data");var data=meta.getCustomModelDataComponent();
        if(model.has("floats")&&!data.getFloats().equals(model.list("floats").stream().map(v->Float.valueOf(values.resolve(ctx,v.toString()))).toList()))return false;
        if(model.has("strings")&&!data.getStrings().equals(model.strings("strings").stream().map(v->values.resolve(ctx,v)).toList()))return false;
        if(model.has("flags")&&!data.getFlags().equals(model.list("flags").stream().map(v->Boolean.valueOf(values.resolve(ctx,v.toString()))).toList()))return false;
        if(model.has("colors")&&!data.getColors().equals(model.strings("colors").stream().map(ItemAttributes::color).toList()))return false;
        if(node.has("item-model")&&!Objects.equals(meta.getItemModel(),NamespacedKey.fromString(node.text("item-model",""))))return false;
        for(var e:node.child("pdc").values().entrySet()){
            NamespacedKey key=NamespacedKey.fromString(e.getKey());Node rule=Node.of(e.getValue());String expected=values.resolve(ctx,rule.text("value",String.valueOf(e.getValue())));var pdc=meta.getPersistentDataContainer();
            Object actual=switch(rule.text("type","string")){case "integer"->pdc.get(key,PersistentDataType.INTEGER);case "long"->pdc.get(key,PersistentDataType.LONG);case "double"->pdc.get(key,PersistentDataType.DOUBLE);case "boolean"->pdc.get(key,PersistentDataType.BOOLEAN);default->pdc.get(key,PersistentDataType.STRING);};
            if(actual==null||!String.valueOf(actual).equals(expected))return false;
        }
        for(var e:node.child("enchantments").values().entrySet()){var enchant=Registry.ENCHANTMENT.get(ItemAttributes.key(e.getKey()));if(enchant==null||meta.getEnchantLevel(enchant)<Integer.parseInt(values.resolve(ctx,e.getValue().toString())))return false;}
        return true;
    }
    private static boolean match(String actual,String expected,Node node){if(node.bool("ignore-case",false)){actual=actual.toLowerCase(Locale.ROOT);expected=expected.toLowerCase(Locale.ROOT);}return node.text("match-mode","exact").equals("contains")?actual.contains(expected):actual.equals(expected);}
    private static String plain(Component value){return value==null?"":PlainTextComponentSerializer.plainText().serialize(value);}
}
