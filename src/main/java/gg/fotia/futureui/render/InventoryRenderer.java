package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;

/** 更新现有容器，输入槽保留真实物品，未改变的展示槽不发送更新。 */
public final class InventoryRenderer {
    public static final class Holder implements InventoryHolder {
        public final MenuContext context;public UUID token;
        public final Map<Integer,Node> buttons=new HashMap<>(),inputs=new HashMap<>();
        public final Set<Integer> inputSlots=new HashSet<>();private Inventory inventory;private boolean returned;
        Holder(MenuContext context){this.context=context;token=context.session().token;}
        public Inventory getInventory(){return inventory;}
    }
    private final MenuController menus;
    public InventoryRenderer(MenuController menus){this.menus=menus;}
    public void render(MenuContext ctx,List<Node> components){
        Node source=ctx.session().menu.source();List<String> rows=source.strings("Layout");String layout=String.join("",rows);
        InventoryType type=InventoryType.valueOf(source.text("inventory-type","CHEST").toUpperCase(Locale.ROOT));
        int size=type==InventoryType.CHEST?rows.size()*9:type.getDefaultSize();
        var title=menus.text.render(ctx.player(),ctx.session().locale,ctx.session().menu.title(),ctx.variables());
        Inventory current=ctx.player().getOpenInventory().getTopInventory();
        boolean reuse=current.getHolder() instanceof Holder old&&old.context.session()==ctx.session()&&current.getSize()==size&&current.getType()==type;
        Holder holder=reuse?(Holder)current.getHolder():new Holder(ctx);
        Inventory inventory=reuse?current:type==InventoryType.CHEST?Bukkit.createInventory(holder,size,title):Bukkit.createInventory(holder,type,title);
        holder.inventory=inventory;holder.token=ctx.session().token;
        Set<Integer> previous=new HashSet<>(holder.inputSlots);holder.inputSlots.clear();holder.inputs.clear();holder.buttons.clear();
        List<Node> flat=new ArrayList<>(ViewCompiler.flatten(components));flat.sort(Comparator.comparingInt(n->n.integer("priority",0)));
        for(int slot=0;slot<size;slot++){
            String marker=slot<layout.length()?String.valueOf(layout.charAt(slot)):"#";final int index=slot;
            Node node=flat.stream().filter(n->n.list("slots").stream().anyMatch(v->Integer.parseInt(v.toString())==index)||(!marker.equals("#")&&n.text("slot-key","").equals(marker))).findFirst().orElse(null);
            if(node!=null&&node.text("type","").equals("slot")){holder.inputSlots.add(slot);holder.inputs.put(slot,node);if(!previous.contains(slot))inventory.setItem(slot,null);continue;}
            if(previous.contains(slot))returnSlot(holder,slot);
            ItemStack display=null;
            if(node!=null){Node item=node.child("item");if(item.empty())item=Node.of(Map.of("material",node.text("material","PAPER"),"name",node.text("text",""),"lore",node.strings("lore")));
                display=menus.items.create(ctx.scoped(node.child("context").values()),item);holder.buttons.put(slot,node);}
            if(!Objects.equals(inventory.getItem(slot),display))inventory.setItem(slot,display);
        }
        if(!reuse)ctx.player().openInventory(inventory);
        else if(!ctx.player().getOpenInventory().title().equals(title))ctx.player().getOpenInventory().setTitle(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title));
        populate(holder);
    }
    public static void populate(Holder holder){
        Map<String,Integer> counts=new HashMap<>();for(var entry:holder.inputs.entrySet()){
            ItemStack item=holder.inventory.getItem(entry.getKey());String id=entry.getValue().text("id","input");counts.merge(id,item==null?0:item.getAmount(),Integer::sum);
            holder.context.variables().put("slot."+entry.getKey()+".amount",item==null?0:item.getAmount());holder.context.variables().put("slot."+entry.getKey()+".material",item==null?"AIR":item.getType().name());
        }
        counts.forEach((id,count)->holder.context.variables().put("slot."+id+".amount",count));
    }
    public static void returnInputs(Holder holder){if(holder.returned)return;holder.returned=true;for(int slot:holder.inputSlots)returnSlot(holder,slot);}
    private static void returnSlot(Holder holder,int slot){var player=holder.context.player();ItemStack item=holder.inventory.getItem(slot);if(item==null||item.getType().isAir())return;holder.inventory.setItem(slot,null);player.getInventory().addItem(item).values().forEach(left->player.getWorld().dropItemNaturally(player.getLocation(),left));}
}
