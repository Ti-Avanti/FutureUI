package gg.fotia.futureui.menu;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.render.InventoryRenderer;
import gg.fotia.futureui.shop.InventoryDelivery;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** 原生容器交互边界；过滤、快捷键、拖拽和输入事件集中处理。 */
final class InventoryInteractions {
    private final MenuController menus;
    InventoryInteractions(MenuController menus){this.menus=menus;}
    void click(InventoryClickEvent event,InventoryRenderer.Holder holder){
        if(holder.context.session().busy||!menus.isCurrent(holder.context)){event.setCancelled(true);return;}
        int raw=event.getRawSlot();Inventory top=event.getView().getTopInventory();
        if(event.isShiftClick()&&raw>=top.getSize()){event.setCancelled(true);shiftInto(event,holder);return;}
        if(holder.inputSlots.contains(raw)){
            ItemStack before=copy(top.getItem(raw));Node spec=holder.inputs.get(raw);
            if(!menus.conditions.test(holder.context,spec.condition("requirements"))||event.getClick()==ClickType.DOUBLE_CLICK||event.getAction()==InventoryAction.COLLECT_TO_CURSOR){event.setCancelled(true);return;}
            if(event.isShiftClick()){
                event.setCancelled(true);if(before==null||!spec.bool("allow-take",true))return;
                ItemStack[] delivered=InventoryDelivery.simulate(holder.context.player().getInventory().getStorageContents(),before,before.getAmount());if(delivered==null)return;
                holder.context.player().getInventory().setStorageContents(delivered);top.setItem(raw,null);changed(holder,raw,before);return;
            }
            ItemStack incoming=switch(event.getClick()){case NUMBER_KEY->holder.context.player().getInventory().getItem(event.getHotbarButton());case SWAP_OFFHAND->holder.context.player().getInventory().getItemInOffHand();default->event.getCursor();};
            boolean placing=incoming!=null&&!incoming.getType().isAir()&&Set.of(InventoryAction.PLACE_ALL,InventoryAction.PLACE_ONE,InventoryAction.PLACE_SOME,InventoryAction.SWAP_WITH_CURSOR,InventoryAction.HOTBAR_SWAP,InventoryAction.HOTBAR_MOVE_AND_READD).contains(event.getAction());
            int amount=placing?incoming.getAmount():0;if(event.getAction()==InventoryAction.PLACE_ONE)amount=1;if(before!=null&&incoming!=null&&before.isSimilar(incoming)&&event.getAction()!=InventoryAction.HOTBAR_SWAP)amount+=before.getAmount();
            if(placing&&!accepts(holder,spec,incoming,Math.min(amount,incoming.getMaxStackSize()))||!spec.bool("allow-take",true)&&before!=null&&!placing){event.setCancelled(true);return;}
            Bukkit.getScheduler().runTask(menus.plugin,()->changed(holder,raw,before));return;
        }
        if(raw>=top.getSize()&&!event.isShiftClick()&&event.getClick()!=ClickType.DOUBLE_CLICK)return;
        event.setCancelled(true);Node node=holder.buttons.get(raw);
        if(node==null&&raw<0)node=holder.context.session().menu.source().child("outside").merge(Node.of(Map.of("id","outside","validate",false)));
        if(node==null)return;
        String kind=event.getClick().name().toLowerCase(Locale.ROOT).replace('_','-');if(event.getClick()==ClickType.NUMBER_KEY)kind="number-key-"+(event.getHotbarButton()+1);
        Node clicks=node.child("click-types"),typed=clicks.child(kind);if(typed.empty()&&event.getClick()==ClickType.NUMBER_KEY)typed=clicks.child("number-key");
        List<Node> actions=new ArrayList<>(clicks.child("all").nodes("actions"));actions.addAll(node.nodes("common-actions"));if(typed.empty()||node.bool("include-default-actions",false))actions.addAll(node.nodes("actions"));actions.addAll(typed.nodes("actions"));
        Map<String,Object> context=new LinkedHashMap<>(node.child("context").values());context.put("click.type",kind);context.put("click.slot",raw);context.put("click.hotbar",event.getHotbarButton()+1);context.put("click.shift",event.isShiftClick());
        Node button=node.merge(typed).merge(Node.of(Map.of("context",context,"actions",actions)));UUID token=holder.token;
        Bukkit.getScheduler().runTask(menus.plugin,()->menus.click(holder.context,token,button,Map.of()));
    }
    void drag(InventoryDragEvent event,InventoryRenderer.Holder holder){
        if(holder.context.session().busy){event.setCancelled(true);return;}Map<Integer,ItemStack> before=new LinkedHashMap<>();
        for(var entry:event.getNewItems().entrySet())if(entry.getKey()<holder.getInventory().getSize()){
            Node spec=holder.inputs.get(entry.getKey());if(spec==null||!accepts(holder,spec,entry.getValue(),entry.getValue().getAmount())){event.setCancelled(true);return;}before.put(entry.getKey(),copy(holder.getInventory().getItem(entry.getKey())));
        }
        Bukkit.getScheduler().runTask(menus.plugin,()->before.forEach((slot,item)->changed(holder,slot,item)));
    }
    private void shiftInto(InventoryClickEvent event,InventoryRenderer.Holder holder){
        ItemStack item=event.getCurrentItem();if(item==null||item.getType().isAir())return;int left=item.getAmount();Map<Integer,ItemStack> before=new LinkedHashMap<>();
        for(int slot:holder.inputSlots.stream().sorted().toList()){
            Node spec=holder.inputs.get(slot);ItemStack current=holder.getInventory().getItem(slot);if(current!=null&&!current.isSimilar(item))continue;
            int old=current==null?0:current.getAmount(),capacity=Math.min(item.getMaxStackSize(),spec.integer("max-amount",item.getMaxStackSize()))-old,put=Math.min(left,capacity);if(put<=0||!accepts(holder,spec,item,old+put))continue;
            before.put(slot,copy(current));holder.getInventory().setItem(slot,item.asQuantity(old+put));left-=put;if(left==0)break;
        }
        if(left!=item.getAmount()){event.getClickedInventory().setItem(event.getSlot(),left==0?null:item.asQuantity(left));before.forEach((slot,value)->changed(holder,slot,value));}
    }
    private boolean accepts(InventoryRenderer.Holder holder,Node spec,ItemStack item,int amount){
        Node compiled=menus.values.bind(holder.context,spec,Set.of());return compiled.bool("allow-place",true)&&amount<=compiled.integer("max-amount",item.getMaxStackSize())&&menus.conditions.test(holder.context,compiled.condition("requirements"))&&(compiled.child("accept").empty()||menus.itemMatcher.matches(holder.context,item,compiled.child("accept")));
    }
    private void changed(InventoryRenderer.Holder holder,int slot,ItemStack before){
        if(!menus.isCurrent(holder.context))return;ItemStack after=holder.getInventory().getItem(slot);if(Objects.equals(before,after))return;InventoryRenderer.populate(holder);Node definition=holder.inputs.get(slot);if(definition==null)return;
        int previous=before==null?0:before.getAmount(),next=after==null?0:after.getAmount();Map<String,Object> changes=new LinkedHashMap<>();changes.put("change.slot",slot);changes.put("change.before",previous);changes.put("change.after",next);changes.put("change.material",after==null?"AIR":after.getType().name());changes.put("click.type","input-change");
        MenuContext ctx=holder.context.scoped(changes);List<Node> actions=new ArrayList<>();if(before!=null&&(after==null||!before.isSimilar(after)||next<previous))actions.addAll(definition.nodes("on-take"));if(after!=null&&(before==null||!after.isSimilar(before)||next>previous))actions.addAll(definition.nodes("on-place"));actions.addAll(definition.nodes("on-change"));
        menus.execute(ctx,actions).whenComplete((result,error)->menus.threads.run(()->{if(error==null&&!result.success())ctx.player().sendMessage(menus.text.message(ctx.player(),result.message(),ctx.variables()));menus.refresh(holder.context.player());}));
    }
    private static ItemStack copy(ItemStack item){return item==null||item.getType()==Material.AIR?null:item.clone();}
}
