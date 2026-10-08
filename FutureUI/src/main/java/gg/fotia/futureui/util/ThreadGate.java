package gg.fotia.futureui.util;

import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** 主线程边界；不在服务器线程等待异步结果。 */
public final class ThreadGate {
    private final Plugin plugin;
    public ThreadGate(Plugin plugin) { this.plugin=plugin; }
    public void run(Runnable task) { if(!plugin.isEnabled()) return; if(Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin,task); }
    public <T> CompletableFuture<T> call(Supplier<T> task) {
        CompletableFuture<T> result=new CompletableFuture<>();
        if(!plugin.isEnabled()){result.completeExceptionally(new IllegalStateException("Plugin disabled"));return result;}
        run(()->{try{result.complete(task.get());}catch(Exception error){result.completeExceptionally(error);}});return result;
    }
}
