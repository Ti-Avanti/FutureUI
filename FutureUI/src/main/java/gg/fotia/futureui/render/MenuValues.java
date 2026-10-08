package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/** 主线程读取即时玩家数据，菜单和 HUD 使用同一份变量定义。 */
public final class MenuValues {
    private MenuValues() {}
    public static void populate(MenuContext ctx,MenuController menus) {
        Player player=ctx.player();Map<String,Object> values=ctx.variables();
        player(values,"player",player);
        values.put("player.display",menus.state.get(player.getUniqueId(),"input.nickname",player.getName()));
        values.put("player.language",menus.text.locale(player));
        values.put("client.language",gg.fotia.futureui.i18n.LanguageCatalog.normalize(player.locale().toLanguageTag()));
        values.put("translator.available",menus.text.backend()!=null&&menus.text.backend().ready());
        values.put("server.online",Bukkit.getOnlinePlayers().size());
        values.put("session.page",ctx.session().page+1);
        values.put("note.saved",new gg.fotia.futureui.placeholder.LiteralValue(menus.state.get(player.getUniqueId(),"input.note","")));
        values.put("display.mode",menus.hud.current(player));
        Player target=player;String id=String.valueOf(values.getOrDefault("target.uuid",player.getUniqueId().toString()));
        try{target=Bukkit.getPlayer(UUID.fromString(id));}catch(IllegalArgumentException ignored){target=Bukkit.getPlayerExact(id);}
        values.put("profile.online",target!=null);
        if(target!=null)player(values,"profile",target);
    }
    private static void player(Map<String,Object> values,String prefix,Player player) {
        var location=player.getLocation();double maximum=Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        values.put(prefix+".name",player.getName());values.put(prefix+".uuid",player.getUniqueId().toString());
        values.put(prefix+".world",player.getWorld().getName());values.put(prefix+".level",player.getLevel());
        values.put(prefix+".health",(int)Math.ceil(player.getHealth()));values.put(prefix+".max-health",(int)Math.ceil(maximum));
        values.put(prefix+".health-fraction",player.getHealth()/Math.max(1,maximum));values.put(prefix+".food",player.getFoodLevel());
        values.put(prefix+".food-fraction",player.getFoodLevel()/20.0);values.put(prefix+".gamemode",player.getGameMode().name());
        values.put(prefix+".x",location.getBlockX());values.put(prefix+".y",location.getBlockY());values.put(prefix+".z",location.getBlockZ());
        values.put(prefix+".ping",player.getPing());values.put(prefix+".time",player.getWorld().getTime());
        values.put(prefix+".empty-slots",Arrays.stream(player.getInventory().getStorageContents()).filter(item->item==null||item.getType().isAir()).count());
    }
}
