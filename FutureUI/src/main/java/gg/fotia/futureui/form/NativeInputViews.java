package gg.fotia.futureui.form;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.render.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.view.AnvilView;

/** 原生输入视图。虚拟告示牌与书仅修改客户端显示，不覆盖世界方块或真实背包。 */
final class NativeInputViews extends PacketListenerAbstract implements Listener,AutoCloseable {
    private final MenuController menus;private final InputCapture owner;
    NativeInputViews(MenuController menus,InputCapture owner){super(PacketListenerPriority.HIGHEST);this.menus=menus;this.owner=owner;Bukkit.getPluginManager().registerEvents(this,menus.plugin);PacketEvents.getAPI().getEventManager().registerListener(this);}
    void show(InputCapture.Request request,String error){
        var ctx=request.context;var player=ctx.player();if(!error.isEmpty())player.sendMessage(menus.text.message(player,error,ctx.variables()));
        if(request.mode.equals("dialog")){dialog(request,error);return;}player.closeDialog();
        player.sendMessage(menus.text.render(player,menus.text.locale(player),request.source.text("prompt","@messages.input-prompt"),ctx.variables()));
        switch(request.mode){
            case "chat"->player.closeInventory();
            case "anvil"->{
                if(anvil(player.getOpenInventory())==request)return;
                AnvilView view=MenuType.ANVIL.builder().checkReachable(false).title(menus.text.render(player,ctx.session().locale,request.source.text("title","@messages.input-title"),ctx.variables())).build(player);
                request.anvilInventory=view.getTopInventory();ItemStack paper=new ItemStack(Material.PAPER);var meta=paper.getItemMeta();meta.displayName(Component.text(request.initial.isEmpty()?" ":request.initial));paper.setItemMeta(meta);request.anvilInventory.setItem(0,paper);player.openInventory(view);
            }
            case "sign"->{
                var position=player.getLocation();position.setY(Math.min(position.getWorld().getMaxHeight()-1,position.getBlockY()+2));request.signWorld=player.getWorld().getUID();request.signPosition=new com.github.retrooper.packetevents.util.Vector3i(position.getBlockX(),position.getBlockY(),position.getBlockZ());player.sendBlockChange(position,Material.OAK_SIGN.createBlockData());List<Component> lines=new ArrayList<>();String[] saved=request.initial.split("\n",-1);for(int i=0;i<4;i++)lines.add(Component.text(i<saved.length?saved[i]:""));player.sendSignChange(position,lines);player.openVirtualSign(io.papermc.paper.math.Position.block(position),org.bukkit.block.sign.Side.FRONT);
            }
            case "book"->{ItemStack book=new ItemStack(Material.WRITABLE_BOOK);BookMeta meta=(BookMeta)book.getItemMeta();meta.pages(List.of(Component.text(request.initial)));book.setItemMeta(meta);var api=PacketEvents.getAPI().getPlayerManager();api.sendPacket(player,new WrapperPlayServerSetSlot(0,0,36+request.bookSlot,SpigotConversionUtil.fromBukkitItemStack(book)));player.sendMessage(menus.text.message(player,"messages.input-book",ctx.variables()));}
            default->throw new IllegalArgumentException("Unknown native input mode");
        }
    }
    private void dialog(InputCapture.Request request,String error){
        var ctx=request.context;Node style=menus.snapshot().settings().child("input-ui");int width=style.integer("width",594),button=(width-2)/2;
        boolean nativeMode=ctx.session().menu.renderer().equals("native");
        Component heading=menus.text.render(ctx.player(),ctx.session().locale,request.source.text("title","@messages.input-title"),ctx.variables());
        Component title=nativeMode?heading:DialogBackground.title(menus.glyphs,heading,style.integer("panel-width",662),style.integer("panel-height",369));
        Node intro=Node.of(Map.of("id","input_intro","type","text","text",request.source.text("prompt","@messages.input-prompt"),"skin","surface","padding",18,"width",width,"min-height",126));
        Node feedback=Node.of(Map.of("id","input_feedback","type","text","text",error.isEmpty()?request.source.text("hint","@messages.input-hint"):"@"+error,"skin","readout","padding",9,"width",width,"min-height",54));
        List<DialogBody> body=nativeMode?List.of(DialogBody.plainMessage(menus.text.render(ctx.player(),ctx.session().locale,intro.text("text",""),ctx.variables()),width),DialogBody.plainMessage(menus.text.render(ctx.player(),ctx.session().locale,feedback.text("text",""),ctx.variables()),width)):List.of(DialogTextPanel.create(menus,ctx,intro,ctx.variables()),DialogTextPanel.create(menus,ctx,feedback,ctx.variables()));
        var callback=ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(request.source.integer("timeout-seconds",120))).build();
        ActionButton confirm=ActionButton.create(menus.text.message(ctx.player(),"common.confirm",Map.of()),null,button,DialogAction.customClick((response,audience)->{if(audience instanceof org.bukkit.entity.Player p&&p.getUniqueId().equals(ctx.player().getUniqueId()))owner.receive(request,Objects.toString(response.getText("capture"),""));},callback));
        ActionButton cancel=ActionButton.create(menus.text.message(ctx.player(),"common.back",Map.of()),null,button,DialogAction.customClick((response,audience)->{if(audience instanceof org.bukkit.entity.Player p&&p.getUniqueId().equals(ctx.player().getUniqueId()))menus.threads.run(()->owner.cancel(p.getUniqueId(),false));},callback));
        Dialog dialog=Dialog.create(factory->factory.empty().base(DialogBase.builder(title).canCloseWithEscape(false).pause(false).body(body).inputs(List.of(DialogInput.text("capture",width,menus.text.render(ctx.player(),ctx.session().locale,request.source.text("label","@messages.input-label"),ctx.variables()),true,request.initial,request.source.integer("max-length",128),null))).afterAction(DialogBase.DialogAfterAction.NONE).build()).type(DialogType.multiAction(List.of(confirm,cancel),null,2)));ctx.player().showDialog(dialog);
    }
    void restore(InputCapture.Request request){
        var player=request.context.player();if(!player.isOnline())return;
        if(request.signPosition!=null&&player.getWorld().getUID().equals(request.signWorld)){var pos=request.signPosition;Location location=new Location(player.getWorld(),pos.getX(),pos.getY(),pos.getZ());if(location.getWorld().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4))player.sendBlockChange(location,location.getBlock().getBlockData());}
        if(request.mode.equals("book"))player.updateInventory();
        if(request.anvilInventory!=null){request.anvilInventory.clear();if(player.getOpenInventory().getTopInventory().equals(request.anvilInventory))player.closeInventory();}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void chat(io.papermc.paper.event.player.AsyncChatEvent event){InputCapture.Request request=owner.get(event.getPlayer().getUniqueId());if(request==null||!request.mode.equals("chat"))return;event.setCancelled(true);owner.receive(request,PlainTextComponentSerializer.plainText().serialize(event.message()));}
    @EventHandler public void prepare(PrepareAnvilEvent event){if(anvil(event.getView())==null)return;ItemStack output=new ItemStack(Material.PAPER);var meta=output.getItemMeta();meta.displayName(Component.text(Objects.toString(event.getView().getRenameText(),"")));output.setItemMeta(meta);event.setResult(output);event.getView().setRepairCost(1);event.getView().setMaximumRepairCost(Integer.MAX_VALUE);}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){InputCapture.Request pending=owner.get(event.getWhoClicked().getUniqueId());if(pending!=null&&pending.mode.equals("book")){event.setCancelled(true);Bukkit.getScheduler().runTask(menus.plugin,()->owner.cancel(event.getWhoClicked().getUniqueId(),false));return;}InputCapture.Request request=anvil(event.getView());if(request==null)return;event.setCancelled(true);if(event.getRawSlot()==2&&event.getView() instanceof AnvilView view){String value=Objects.toString(view.getRenameText(),"");Bukkit.getScheduler().runTask(menus.plugin,()->owner.receive(request,value));}}
    @EventHandler public void drag(InventoryDragEvent event){if(anvil(event.getView())!=null)event.setCancelled(true);}
    @EventHandler public void closed(InventoryCloseEvent event){InputCapture.Request request=anvil(event.getView());if(request!=null){event.getInventory().clear();Bukkit.getScheduler().runTask(menus.plugin,()->{if(owner.get(event.getPlayer().getUniqueId())==request)owner.cancel(event.getPlayer().getUniqueId(),false);});}}
    private InputCapture.Request anvil(InventoryView view){InputCapture.Request request=owner.get(view.getPlayer().getUniqueId());return request!=null&&request.mode.equals("anvil")&&view.getType()==InventoryType.ANVIL?request:null;}
    @EventHandler public void held(PlayerItemHeldEvent event){InputCapture.Request request=owner.get(event.getPlayer().getUniqueId());if(request!=null&&request.mode.equals("book")){owner.cancel(event.getPlayer().getUniqueId(),false);}}
    @EventHandler public void swapped(PlayerSwapHandItemsEvent event){InputCapture.Request request=owner.get(event.getPlayer().getUniqueId());if(request!=null&&request.mode.equals("book")){event.setCancelled(true);owner.cancel(event.getPlayer().getUniqueId(),false);}}
    @EventHandler public void dropped(PlayerDropItemEvent event){InputCapture.Request request=owner.get(event.getPlayer().getUniqueId());if(request!=null&&request.mode.equals("book")){event.setCancelled(true);Bukkit.getScheduler().runTask(menus.plugin,()->owner.cancel(event.getPlayer().getUniqueId(),false));}}
    @Override public void onPacketReceive(PacketReceiveEvent event){
        UUID id=event.getUser().getUUID();if(id==null)return;InputCapture.Request request=owner.get(id);if(request==null)return;
        if(request.mode.equals("sign")&&event.getPacketType()==PacketType.Play.Client.UPDATE_SIGN){var packet=new WrapperPlayClientUpdateSign(event);if(!packet.getBlockPosition().equals(request.signPosition))return;event.setCancelled(true);owner.receive(request,String.join("\n",packet.getTextLines()).stripTrailing());}
        else if(request.mode.equals("book")&&event.getPacketType()==PacketType.Play.Client.EDIT_BOOK){var packet=new WrapperPlayClientEditBook(event);if(packet.getSlot()!=request.bookSlot)return;event.setCancelled(true);owner.receive(request,String.join("\n",packet.getPages()));}
    }
    @Override public void close(){HandlerList.unregisterAll(this);PacketEvents.getAPI().getEventManager().unregisterListener(this);}
}
