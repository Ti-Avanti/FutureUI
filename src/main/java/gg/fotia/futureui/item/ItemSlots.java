package gg.fotia.futureui.item;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.render.InventoryRenderer;
import java.util.*;
import java.util.function.*;
import org.bukkit.inventory.*;

/** 显式槽位视图；所有消费、匹配与编辑共用相同的范围语义。 */
public final class ItemSlots {
    public record Slot(String id,Supplier<ItemStack> reader,Consumer<ItemStack> writer){
        public ItemStack get(){return reader.get();}public void set(ItemStack item){writer.accept(item);}
    }
    private ItemSlots(){}
    public static List<Slot> select(MenuContext ctx,Node definition){
        List<String> names=definition.strings("slots");if(names.isEmpty())names=List.of(definition.text("slot","storage"));
        Map<String,Slot> result=new LinkedHashMap<>();PlayerInventory inventory=ctx.player().getInventory();
        for(String name:names)switch(name.toLowerCase(Locale.ROOT)){
            case "storage"->{for(int i=0;i<36;i++)add(result,inventory,i,"player:");}
            case "hotbar"->{for(int i=0;i<9;i++)add(result,inventory,i,"player:");}
            case "armor"->{for(int i=36;i<40;i++)add(result,inventory,i,"player:");}
            case "all"->{for(int i=0;i<=40;i++)add(result,inventory,i,"player:");}
            case "mainhand","hand"->add(result,inventory,inventory.getHeldItemSlot(),"player:");
            case "offhand"->add(result,inventory,40,"player:");
            case "helmet"->add(result,inventory,39,"player:");case "chestplate"->add(result,inventory,38,"player:");
            case "leggings"->add(result,inventory,37,"player:");case "boots"->add(result,inventory,36,"player:");
            case "cursor"->result.put("cursor",new Slot("cursor",ctx.player()::getItemOnCursor,ctx.player()::setItemOnCursor));
            case "inputs"->{Inventory top=ctx.player().getOpenInventory().getTopInventory();if(top.getHolder() instanceof InventoryRenderer.Holder holder&&holder.context.session()==ctx.session())holder.inputSlots.stream().sorted().forEach(i->add(result,top,i,"input:"));}
            default->{if(name.startsWith("input:")){int index=Integer.parseInt(name.substring(6));Inventory top=ctx.player().getOpenInventory().getTopInventory();if(!(top.getHolder() instanceof InventoryRenderer.Holder h)||h.context.session()!=ctx.session()||!h.inputSlots.contains(index))throw new IllegalArgumentException("Not a menu input slot: "+name);add(result,top,index,"input:");}else{int index=Integer.parseInt(name);if(index<0||index>40)throw new IllegalArgumentException("Player slot must be 0..40");add(result,inventory,index,"player:");}}
        }
        return List.copyOf(result.values());
    }
    private static void add(Map<String,Slot> out,Inventory inventory,int index,String prefix){String id=prefix+index;out.put(id,new Slot(id,()->inventory.getItem(index),item->inventory.setItem(index,item)));}
}
