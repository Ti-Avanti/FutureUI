package gg.fotia.futureui.integration;

import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;

/** 对异步调用者只读取主线程生成的快照，不在 PAPI 回调中调度阻塞等待。 */
public final class FutureUIExpansion extends PlaceholderExpansion {
    private final MenuController menus;private final Map<UUID,Map<String,String>> snapshots=new ConcurrentHashMap<>();
    public FutureUIExpansion(MenuController menus){this.menus=menus;}
    public String getIdentifier(){return "futureui";}public String getAuthor(){return "Fotia";}public String getVersion(){return menus.plugin.getPluginMeta().getVersion();}public boolean persist(){return true;}
    public void update(){
        Set<UUID> online=new HashSet<>();for(var player:org.bukkit.Bukkit.getOnlinePlayers()){
            UUID id=player.getUniqueId();online.add(id);Map<String,String> values=new LinkedHashMap<>();var session=menus.sessions().get(id);
            values.put("menu",session==null?"":session.menu.id());values.put("locale",menus.text.locale(player));values.put("page",session==null?"0":String.valueOf(session.page+1));values.put("busy",String.valueOf(session!=null&&session.busy));
            if(session!=null)session.variables.forEach((key,value)->{if(!key.startsWith("list."))values.put("variable_"+key,plain(value));});
            menus.snapshot().settings().child("placeholders").child("exports").values().forEach((key,value)->values.put("value_"+key,plain(value)));
            for(String currency:menus.currencies.ids()){var provider=menus.currencies.get(currency);if(provider.available())try{values.put("balance_"+currency,provider.balance(player).toPlainString());}catch(RuntimeException ignored){values.put("balance_"+currency,"");}}
            Map<String,Integer> counts=new HashMap<>();for(var item:player.getInventory().getStorageContents())if(item!=null)counts.merge(item.getType().name().toLowerCase(Locale.ROOT),item.getAmount(),Integer::sum);counts.forEach((key,count)->values.put("items_"+key,count.toString()));snapshots.put(id,Map.copyOf(values));
        }
        snapshots.keySet().retainAll(online);
    }
    @Override public String onRequest(OfflinePlayer player,String params){
        if(player==null)return null;String scope=player.getUniqueId().toString();try{
            if(org.bukkit.Bukkit.isPrimaryThread()&&player.getPlayer()!=null){
                var online=player.getPlayer();var session=menus.sessions().get(player.getUniqueId());
                if(params.startsWith("balance_")){var currency=menus.currencies.get(params.substring(8));return currency==null||!currency.available()?null:currency.balance(online).stripTrailingZeros().toPlainString();}
                if(params.startsWith("variable_")&&session!=null){Object value=session.variables.get(params.substring(9));return value==null?null:plain(value);}
                if(params.startsWith("items_")){org.bukkit.Material type=org.bukkit.Material.matchMaterial(params.substring(6));if(type==null)return null;int count=0;for(var item:online.getInventory().getStorageContents())if(item!=null&&item.getType()==type)count+=item.getAmount();return String.valueOf(count);}
                if(params.equals("menu"))return session==null?"":session.menu.id();if(params.equals("page"))return session==null?"0":String.valueOf(session.page+1);if(params.equals("locale"))return menus.text.locale(online);if(params.equals("busy"))return String.valueOf(session!=null&&session.busy);
            }
            if(params.startsWith("data_"))return plain(menus.storage.get(scope,params.substring(5),""));
            if(params.startsWith("global_"))return plain(menus.storage.get("global",params.substring(7),""));
            if(params.startsWith("cooldown_"))return String.valueOf((menus.storage.remaining(scope,"cooldown."+params.substring(9))+999)/1000);
            if(params.startsWith("items_"))return snapshots.getOrDefault(player.getUniqueId(),Map.of()).getOrDefault(params,"0");
            return snapshots.getOrDefault(player.getUniqueId(),Map.of()).get(params);
        }catch(IllegalArgumentException invalid){return null;}
    }
    private static String plain(Object value){return value instanceof Component c?PlainTextComponentSerializer.plainText().serialize(c):value instanceof java.math.BigDecimal decimal?decimal.stripTrailingZeros().toPlainString():String.valueOf(value);}
}
