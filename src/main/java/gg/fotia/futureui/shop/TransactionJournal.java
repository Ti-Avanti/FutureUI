package gg.fotia.futureui.shop;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** 交易审计先于扣款持久化；崩溃留下未决交易时禁止自动再次执行。 */
public final class TransactionJournal implements AutoCloseable {
    private final Path directory;private final Gson gson=new Gson();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("FutureUI-transactions").factory());
    private final ConcurrentMap<UUID,Map<String,String>> unresolved=new ConcurrentHashMap<>();
    private volatile boolean ready;
    private final Set<UUID> active=ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String,Long> sold=new ConcurrentHashMap<>();
    public TransactionJournal(Path directory){this.directory=directory;}
    public CompletionStage<Void> load(){return CompletableFuture.runAsync(()->{try{
        Files.createDirectories(directory);try(var files=Files.list(directory)){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){
            JsonObject json=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();String status=json.get("status").getAsString();
            if(status.equals("DELIVERED"))reserve(json.get("shop").getAsString()+":"+json.get("product").getAsString(),json.get("quantity").getAsInt());
            if(status.equals("DELIVERED"))json.entrySet().stream().filter(e->e.getKey().startsWith("stock:")).forEach(e->reserve(e.getKey().substring(6),e.getValue().getAsInt()));
            if(Set.of("PREPARED","RECOVERY_REQUIRED").contains(status)){Map<String,String> record=new LinkedHashMap<>();json.entrySet().forEach(e->record.put(e.getKey(),e.getValue().getAsString()));unresolved.put(UUID.fromString(record.get("transaction")),Map.copyOf(record));}
        }}ready=true;
    }catch(Exception error){throw new CompletionException(error);}},writer);}
    public CompletionStage<Void> write(UUID id,Map<String,String> source,String status){Map<String,String> record=new LinkedHashMap<>(source);record.put("transaction",id.toString());record.put("status",status);record.put("updated",Instant.now().toString());
        return CompletableFuture.runAsync(()->{try{
            Files.createDirectories(directory);Path file=directory.resolve(id+".json"),temp=directory.resolve(id+".tmp");Files.writeString(temp,gson.toJson(record),StandardCharsets.UTF_8);Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            if(Set.of("PREPARED","RECOVERY_REQUIRED").contains(status))unresolved.put(id,Map.copyOf(record));else unresolved.remove(id);
        }catch(Exception error){throw new CompletionException(error);}},writer);
    }
    public boolean blocked(UUID player){return !ready||active.contains(player)||unresolved.values().stream().anyMatch(r->player.toString().equals(r.get("player")));}
    public boolean tryLock(UUID player){return active.add(player);}public void unlock(UUID player){active.remove(player);}
    public Map<UUID,Map<String,String>> unresolved(){return Map.copyOf(unresolved);}
    public long sold(String key){return sold.getOrDefault(key,0L);}
    public void reserve(String key,int quantity){sold.merge(key,(long)quantity,Long::sum);}
    public CompletionStage<Void> resolve(UUID id,String resolution){Map<String,String> record=unresolved.get(id);if(record==null)return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown transaction"));if(!Set.of("DELIVERED","REFUNDED","CANCELLED").contains(resolution))return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid resolution"));return write(id,record,resolution);}
    public void close(){writer.shutdown();}
}
