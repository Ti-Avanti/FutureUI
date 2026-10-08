package gg.fotia.futureui.item;

import com.destroystokyo.paper.profile.ProfileProperty;
import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.i18n.TextService;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.trim.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;

/** 只使用公开 Bukkit/Paper API，创建和编辑复用同一套属性定义。 */
public final class ItemAttributes {
    private final TextService text;private final ValueResolver values;
    public ItemAttributes(TextService text,ValueResolver values){this.text=text;this.values=values;}
    public ItemStack apply(MenuContext ctx,ItemStack original,Node source){
        ItemStack item=original.clone();Node node=values.bind(ctx,source,Set.of("name","lore"));if(item.getType().isAir())return item;var meta=item.getItemMeta();
        if(node.has("name"))meta.displayName(text.render(ctx.player(),ctx.session().locale,node.text("name",""),ctx.variables()));
        if(node.has("lore"))meta.lore(node.strings("lore").stream().map(line->text.render(ctx.player(),ctx.session().locale,line,ctx.variables())).toList());
        if(node.has("unbreakable"))meta.setUnbreakable(node.bool("unbreakable",false));
        if(node.has("glow"))meta.setEnchantmentGlintOverride(node.bool("glow",false));
        if(node.has("item-model"))meta.setItemModel(key(node.text("item-model","")));
        if(node.has("max-stack-size")){int count=node.integer("max-stack-size",64);if(count<1||count>99)throw new IllegalArgumentException("max-stack-size must be 1..99");meta.setMaxStackSize(count);}
        if(node.has("rarity"))meta.setRarity(ItemRarity.valueOf(node.text("rarity","COMMON").toUpperCase(Locale.ROOT)));
        if(node.has("damage")){if(!(meta instanceof Damageable damage))throw new IllegalArgumentException("This item is not damageable");int amount=node.integer("damage",0);if(amount<0)throw new IllegalArgumentException("Damage cannot be negative");damage.setDamage(amount);}
        if(node.has("custom-model-data"))meta.setCustomModelData(node.integer("custom-model-data",0));
        Node model=values.bind(ctx,node.child("model-data"),Set.of());
        if(!model.empty()){
            var cmd=meta.getCustomModelDataComponent();
            if(model.has("floats"))cmd.setFloats(model.list("floats").stream().map(v->Float.valueOf(v.toString())).toList());
            if(model.has("strings"))cmd.setStrings(model.strings("strings"));
            if(model.has("flags"))cmd.setFlags(model.list("flags").stream().map(v->Boolean.valueOf(v.toString())).toList());
            if(model.has("colors"))cmd.setColors(model.strings("colors").stream().map(ItemAttributes::color).toList());meta.setCustomModelDataComponent(cmd);
        }
        if(node.has("flags")){meta.removeItemFlags(ItemFlag.values());for(String flag:node.strings("flags"))meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));}
        node.child("enchantments").values().forEach((id,value)->{var enchant=Registry.ENCHANTMENT.get(key(id));if(enchant==null)throw new IllegalArgumentException("Unknown enchantment "+id);int level=Integer.parseInt(values.resolve(ctx,value.toString()));if(level==0)meta.removeEnchant(enchant);else if(level<0||level>255)throw new IllegalArgumentException("Enchantment level must be 0..255");else meta.addEnchant(enchant,level,true);});
        if(meta instanceof SkullMeta skull){
            if(node.text("head","").equals("viewer"))skull.setPlayerProfile(ctx.player().getPlayerProfile());
            if(node.has("head-player")){String name=node.text("head-player","");var player=Bukkit.getPlayerExact(name);if(player!=null)skull.setPlayerProfile(player.getPlayerProfile());else skull.setPlayerProfile(Bukkit.createProfile(UUID.fromString(name)));}
            if(node.has("head-texture")){String texture=node.text("head-texture","");if(texture.length()>16384)throw new IllegalArgumentException("Head texture exceeds limit");Base64.getDecoder().decode(texture);var profile=Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(java.nio.charset.StandardCharsets.UTF_8)));profile.setProperty(new ProfileProperty("textures",texture));skull.setPlayerProfile(profile);}
        }
        if(node.has("color")){Color color=color(node.text("color","#FFFFFF"));if(meta instanceof LeatherArmorMeta leather)leather.setColor(color);else if(meta instanceof PotionMeta potion)potion.setColor(color);else if(meta instanceof MapMeta map)map.setColor(color);else throw new IllegalArgumentException("This item cannot be dyed");}
        if(meta instanceof ArmorMeta armor&&node.has("trim")){Node trim=values.bind(ctx,node.child("trim"),Set.of());TrimMaterial material=Registry.TRIM_MATERIAL.get(key(trim.text("material","quartz")));TrimPattern pattern=Registry.TRIM_PATTERN.get(key(trim.text("pattern","sentry")));if(material==null||pattern==null)throw new IllegalArgumentException("Unknown armor trim");armor.setTrim(new ArmorTrim(material,pattern));}
        if(meta instanceof PotionMeta potion){
            if(node.has("potion"))potion.setBasePotionType(PotionType.valueOf(node.text("potion","").toUpperCase(Locale.ROOT)));
            for(Node raw:node.nodes("effects")){Node effect=values.bind(ctx,raw,Set.of());PotionEffectType type=Registry.EFFECT.get(key(effect.text("type","")));if(type==null)throw new IllegalArgumentException("Unknown potion effect");potion.addCustomEffect(new PotionEffect(type,effect.integer("ticks",200),effect.integer("amplifier",0),effect.bool("ambient",false),effect.bool("particles",true),effect.bool("icon",true)),true);}
        }
        for(Node raw:node.nodes("attributes")){
            Node attribute=values.bind(ctx,raw,Set.of());Attribute kind=Registry.ATTRIBUTE.get(key(attribute.text("attribute","")));if(kind==null)throw new IllegalArgumentException("Unknown attribute");
            meta.addAttributeModifier(kind,new AttributeModifier(key(attribute.text("id","futureui:modifier")),attribute.number("amount",0),AttributeModifier.Operation.valueOf(attribute.text("operation","ADD_NUMBER").toUpperCase(Locale.ROOT)),EquipmentSlotGroup.getByName(attribute.text("slot","any"))));
        }
        node.child("pdc").values().forEach((id,raw)->{
            NamespacedKey key=key(id);Node property=values.bind(ctx,Node.of(raw),Set.of());String value=property.text("value",values.resolve(ctx,String.valueOf(raw)));var pdc=meta.getPersistentDataContainer();
            switch(property.text("type","string")){
                case "remove"->pdc.remove(key);case "integer"->pdc.set(key,PersistentDataType.INTEGER,Integer.valueOf(value));case "long"->pdc.set(key,PersistentDataType.LONG,Long.valueOf(value));
                case "double"->pdc.set(key,PersistentDataType.DOUBLE,Double.valueOf(value));case "boolean"->pdc.set(key,PersistentDataType.BOOLEAN,Boolean.valueOf(value));default->pdc.set(key,PersistentDataType.STRING,value);
            }
        });
        item.setItemMeta(meta);if(node.has("amount")){int amount=node.integer("amount",1);if(amount<1||amount>item.getMaxStackSize())throw new IllegalArgumentException("Item amount exceeds its stack size; use the action amount for bulk delivery");item.setAmount(amount);}return item;
    }
    public static NamespacedKey key(String value){NamespacedKey key=NamespacedKey.fromString(value.contains(":")?value:"minecraft:"+value);if(key==null)throw new IllegalArgumentException("Invalid namespaced key "+value);return key;}
    public static Color color(String value){return Color.fromRGB(Integer.parseInt(value.replace("#","").replace("0x",""),16));}
}
