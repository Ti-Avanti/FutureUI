package gg.fotia.futureui.action;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.item.ItemSlots;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.shop.InventoryDelivery;
import java.util.*;
import org.bukkit.inventory.ItemStack;

/** 物品改动先生成全部副本，再一次提交，匹配失败不产生部分扣除。 */
final class ItemActions {
    private final MenuController menus;
    ItemActions(MenuController menus){this.menus=menus;}
    ActionResult run(MenuContext ctx,Node node){
        Parameters p=new Parameters(ctx,node,menus.values);String type=node.text("type","");
        if(type.equals("give-item")){
            ItemStack item=menus.items.create(ctx,node.child("item"));int count=p.integer("amount",item.getAmount());if(item.getType().isAir()||count<1||count>menus.snapshot().settings().child("transactions").integer("max-total-items",65536))return ActionResult.fail("messages.invalid-product");
            ItemStack[] delivery=InventoryDelivery.simulate(ctx.player().getInventory().getStorageContents(),item,count);if(delivery==null)return ActionResult.fail("messages.inventory-full");ctx.player().getInventory().setStorageContents(delivery);return ActionResult.ok();
        }
        Node matcher=node.child("match").merge(menus.values.bind(ctx,node,Set.of()).child("selection"));
        if(type.equals("count-item")){ctx.variables().put(p.string("key","item.count"),menus.itemMatcher.count(ctx,matcher));return ActionResult.ok();}
        List<ItemSlots.Slot> slots=ItemSlots.select(ctx,menus.values.bind(ctx,matcher,Set.of()));Map<ItemSlots.Slot,ItemStack> changes=new LinkedHashMap<>();
        int remaining=p.integer("amount",1),matched=0;if(remaining<1)throw new IllegalArgumentException("Item amount must be positive");
        for(var slot:slots){ItemStack item=slot.get();if(item==null||item.getType().isAir()||!menus.itemMatcher.matches(ctx,item,matcher))continue;matched++;
            if(type.equals("take-item")){int take=Math.min(remaining,item.getAmount());changes.put(slot,take==item.getAmount()?null:item.asQuantity(item.getAmount()-take));remaining-=take;if(remaining==0)break;}
            else{Node edits=type.equals("repair-item")?Node.of(Map.of("damage",0)):type.equals("enchant-item")?Node.of(Map.of("enchantments",node.child("enchantments"))):node.child("edit");changes.put(slot,menus.items.attributes.apply(ctx,item,edits));}
        }
        if(matched==0||type.equals("take-item")&&remaining>0)return ActionResult.fail("messages.items-missing");
        changes.forEach(ItemSlots.Slot::set);ctx.variables().put("item.affected",matched);return ActionResult.ok();
    }
}
