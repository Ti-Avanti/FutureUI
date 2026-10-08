package gg.fotia.futureui.state;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** 内存快照与串行异步落盘；只持久化菜单偏好，不存储玩家语言偏好。 */
public final class PlayerStateStore implements AutoCloseable {
    private final Map<UUID,Map<String,String>> players=new ConcurrentHashMap<>();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("FutureUI-state").factory());
    private final Gson gson=new Gson();private final Path directory;private final Logger logger;
    public PlayerStateStore(Path directory,Logger logger){this.directory=directory;this.logger=logger;}
    public CompletionStage<Void> load(UUID id){return CompletableFuture.runAsync(()->{
        Path file=directory.resolve(id+".json");try{
            Map<String,String> data=new HashMap<>();
            if(Files.exists(file))JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->data.put(e.getKey(),e.getValue().getAsString()));
            Map<String,String> current=players.computeIfAbsent(id,k->new ConcurrentHashMap<>());
            data.forEach(current::putIfAbsent);
        }catch(Exception error){logger.warning("读取玩家菜单偏好失败 "+id+": "+error.getMessage());players.putIfAbsent(id,new ConcurrentHashMap<>());}
    },writer);}
    public String get(UUID player,String key,String fallback){return players.getOrDefault(player,Map.of()).getOrDefault(key,fallback);}
    public Map<String,String> snapshot(UUID player){return Map.copyOf(players.getOrDefault(player,Map.of()));}
    public void set(UUID player,String key,String value){players.computeIfAbsent(player,k->new ConcurrentHashMap<>()).put(key,value);save(player);}
    public void save(UUID player){writer.execute(()->{if(players.containsKey(player))write(player,snapshot(player));});}
    private void write(UUID player,Map<String,String> snapshot){try{
        Files.createDirectories(directory);Path target=directory.resolve(player+".json"),temp=directory.resolve(player+".tmp");
        Files.writeString(temp,gson.toJson(snapshot),StandardCharsets.UTF_8);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }catch(Exception error){logger.severe("保存玩家菜单偏好失败 "+player+": "+error.getMessage());}}
    public void unload(UUID player){writer.execute(()->{Map<String,String> data=players.remove(player);if(data!=null)write(player,Map.copyOf(data));});}
    public void close(){players.keySet().forEach(this::save);writer.shutdown();}
}
