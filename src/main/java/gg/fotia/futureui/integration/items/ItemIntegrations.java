package gg.fotia.futureui.integration.items;

import gg.fotia.futureui.api.ItemProvider;
import gg.fotia.futureui.menu.MenuController;
import java.util.function.Supplier;
import org.bukkit.Bukkit;

/** 可选 API 类仅在对应插件启用时实例化。 */
public final class ItemIntegrations {
    private ItemIntegrations(){}
    public static void register(MenuController menus){
        if(enabled("CraftEngine"))register(menus,"CraftEngine","craftengine",CraftEngineProvider::new);
        if(enabled("ItemsAdder"))register(menus,"ItemsAdder","itemsadder",ItemsAdderProvider::new);
        if(enabled("Nexo"))register(menus,"Nexo","nexo",NexoProvider::new);
        if(enabled("Oraxen"))register(menus,"Oraxen","oraxen",OraxenProvider::new);
        if(enabled("MMOItems"))register(menus,"MMOItems","mmoitems",MMOItemsProvider::new);
    }
    private static boolean enabled(String name){return Bukkit.getPluginManager().isPluginEnabled(name);}
    private static void register(MenuController menus,String name,String id,Supplier<ItemProvider> factory){
        var plugin=Bukkit.getPluginManager().getPlugin(name);if(plugin==null||!plugin.isEnabled()||menus.itemProviders.get(id)!=null)return;
        try{menus.registerItems(plugin,id,factory.get());}catch(LinkageError error){menus.plugin.getLogger().severe(name+" API 不兼容，物品适配器未启用: "+error.getMessage());}
    }
}
