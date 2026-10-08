package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.time.Duration;
import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** HUD 使用已配置通道，统一调度动画帧和动态值。 */
public final class HudRenderer implements AutoCloseable {
    private record Active(MenuContext context,Node definition,BossBar bar,long started){}
    private final Map<UUID,Active> active=new HashMap<>();private final MenuController menus;private long tick;
    public HudRenderer(MenuController menus){this.menus=menus;}
    public void show(MenuContext context,String id){
        hide(context.player());Node definition=menus.snapshot().huds().get(id);if(definition==null){context.player().sendMessage(menus.text.message(context.player(),"messages.hud-unavailable",Map.of()));return;}
        BossBar bar=BossBar.bossBar(Component.empty(),1,BossBar.Color.valueOf(definition.text("color","GREEN").toUpperCase(Locale.ROOT)),BossBar.Overlay.PROGRESS);
        active.put(context.player().getUniqueId(),new Active(context,definition,bar,System.currentTimeMillis()));if(definition.text("channel","actionbar").equals("bossbar"))context.player().showBossBar(bar);draw(active.get(context.player().getUniqueId()));
    }
    public void tick(){tick++;for(Active hud:new ArrayList<>(active.values())){
        if(!hud.context.player().isOnline()||(hud.definition.integer("duration-seconds",0)>0&&System.currentTimeMillis()-hud.started>hud.definition.integer("duration-seconds",0)*1000L)){hide(hud.context.player());continue;}
        if(tick%Math.max(1,hud.definition.integer("refresh-ticks",10))==0)draw(hud);
    }}
    private void draw(Active hud){
        var ctx=hud.context;MenuValues.populate(ctx,menus);Node node=hud.definition;List<String> frames=node.strings("frames");String template=frames.isEmpty()?node.text("text",""):frames.get((int)(tick/Math.max(1,node.integer("frame-ticks",10))%frames.size()));
        Component text=menus.text.render(ctx.player(),menus.text.locale(ctx.player()),template,ctx.variables());
        switch(node.text("channel","actionbar")){
            case "bossbar"->{hud.bar.name(text);double value;try{value=Double.parseDouble(menus.values.resolve(ctx,node.text("progress","1")));}catch(NumberFormatException error){value=1;}hud.bar.progress((float)Math.max(0,Math.min(1,value)));}
            case "subtitle"->ctx.player().showTitle(Title.title(menus.text.render(ctx.player(),menus.text.locale(ctx.player()),node.text("title",""),ctx.variables()),text,Title.Times.times(Duration.ZERO,Duration.ofSeconds(2),Duration.ZERO)));
            case "tab"->ctx.player().sendPlayerListHeaderAndFooter(text,menus.text.render(ctx.player(),menus.text.locale(ctx.player()),node.text("footer",""),ctx.variables()));
            default->ctx.player().sendActionBar(text);
        }
    }
    public void hide(Player player){Active old=active.remove(player.getUniqueId());if(old==null)return;switch(old.definition.text("channel","actionbar")){case "bossbar"->player.hideBossBar(old.bar);case "subtitle"->player.clearTitle();case "tab"->player.sendPlayerListHeaderAndFooter(Component.empty(),Component.empty());default->player.sendActionBar(Component.empty());}}
    public String current(Player player){Active value=active.get(player.getUniqueId());return value==null?"off":value.definition.text("id","off");}
    public void close(){new ArrayList<>(active.values()).forEach(h->hide(h.context.player()));}
}
