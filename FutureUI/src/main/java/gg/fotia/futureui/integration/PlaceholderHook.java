package gg.fotia.futureui.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** PAPI 只在主线程、按观看者解析。 */
public final class PlaceholderHook {
    private PlaceholderHook(){}
    public static String parse(Player player,String text){return PlaceholderAPI.setPlaceholders(player,text);}
}
