package gg.fotia.futureui.api;

import java.util.*;
import org.bukkit.plugin.Plugin;

/** 按所属插件管理扩展；提供关闭句柄，避免重载后遗留实例。 */
public final class ExtensionRegistry<T> {
    private final Map<String,Entry<T>> entries=new LinkedHashMap<>();
    public synchronized AutoCloseable register(Plugin owner,String id,T value) {
        if(!id.matches("[a-z0-9][a-z0-9_.:-]*")) throw new IllegalArgumentException("Invalid extension id: "+id);
        if(entries.containsKey(id)) throw new IllegalArgumentException("Duplicate extension: "+id);
        Entry<T> entry=new Entry<>(owner,value);entries.put(id,entry);
        return () -> { synchronized(this) { entries.remove(id,entry); } };
    }
    public synchronized T get(String id) { Entry<T> entry=entries.get(id);return entry==null?null:entry.value; }
    public synchronized Set<String> ids() { return Set.copyOf(entries.keySet()); }
    public synchronized void removeOwner(Plugin owner) { entries.values().removeIf(entry -> entry.owner==owner); }
    private record Entry<T>(Plugin owner,T value) {}
}
