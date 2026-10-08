package gg.fotia.futureui.state;

import com.google.gson.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** 带类型与过期时间的数据快照；所有磁盘操作在单独的串行线程执行。 */
public final class DataStore implements AutoCloseable {
    public record Entry(Object value,long expires){}
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("FutureUI-data").factory());
    private final Path file;private final Logger logger;private final Gson gson=new Gson();private final CompletableFuture<Void> ready;
    private long version,written;
    private CompletableFuture<Void> pending;
    public DataStore(Path file,Logger logger){this.file=file;this.logger=logger;ready=CompletableFuture.runAsync(this::load,writer);}
    public CompletionStage<Void> ready(){return ready;}
    private void load(){try{
        if(!Files.exists(file))return;
        JsonObject root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
        synchronized(this){root.entrySet().forEach(e->{JsonObject node=e.getValue().getAsJsonObject();entries.put(e.getKey(),new Entry(decode(node.get("value")),node.get("expires").getAsLong()));});}
    }catch(Exception error){throw new CompletionException("Cannot load FutureUI data; refusing to overwrite it",error);}}
    private Object decode(JsonElement value){
        if(value.isJsonArray()){List<Object> list=new ArrayList<>();value.getAsJsonArray().forEach(v->list.add(decode(v)));return List.copyOf(list);}
        JsonPrimitive p=value.getAsJsonPrimitive();return p.isBoolean()?p.getAsBoolean():p.isNumber()?p.getAsBigDecimal():p.getAsString();
    }
    public synchronized Object get(String scope,String key,Object fallback){Entry e=entry(scope,key);return e==null?fallback:e.value();}
    public synchronized Entry entry(String scope,String key){Entry e=entries.get(path(scope,key));if(e!=null&&e.expires()>0&&e.expires()<=System.currentTimeMillis()){entries.remove(path(scope,key));version++;return null;}return e;}
    public synchronized void set(String scope,String key,Object value,long ttlMillis){
        requireReady();String path=path(scope,key);Object frozen=freeze(value);
        if(!entries.containsKey(path)&&entries.size()>=100000)throw new IllegalStateException("Data entry limit reached");
        entries.put(path,new Entry(frozen,ttlMillis<=0?0:Math.addExact(System.currentTimeMillis(),ttlMillis)));version++;
    }
    public synchronized void restore(String scope,String key,Entry value){requireReady();if(value==null)entries.remove(path(scope,key));else entries.put(path(scope,key),value);version++;}
    public synchronized void remove(String scope,String key){requireReady();entries.remove(path(scope,key));version++;}
    public synchronized BigDecimal add(String scope,String key,BigDecimal delta,long ttl){BigDecimal value=new BigDecimal(String.valueOf(get(scope,key,BigDecimal.ZERO))).add(delta);set(scope,key,value,ttl);return value;}
    public synchronized long remaining(String scope,String key){Entry e=entry(scope,key);return e==null?0:Math.max(0,e.expires()-System.currentTimeMillis());}
    public CompletionStage<Void> flush(){
        final Map<String,Entry> snapshot;final long next;
        synchronized(this){if(version==written)return pending==null?ready:pending;requireReady();snapshot=Map.copyOf(entries);next=version;written=next;}
        CompletableFuture<Void> scheduled=CompletableFuture.runAsync(()->{try{
            Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,gson.toJson(snapshot),StandardCharsets.UTF_8);
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception error){synchronized(this){written=0;}logger.severe("保存菜单数据失败: "+error.getMessage());throw new CompletionException(error);}},writer);synchronized(this){pending=scheduled;}return scheduled;
    }
    private void requireReady(){if(!ready.isDone()||ready.isCompletedExceptionally())throw new IllegalStateException("Data storage is not ready");}
    private static String path(String scope,String key){if(!scope.matches("global|[a-fA-F0-9-]{36}")||!key.matches("[a-zA-Z0-9_.:-]{1,160}"))throw new IllegalArgumentException("Invalid data scope or key");return scope+"/"+key;}
    private static Object freeze(Object value){
        if(value instanceof Boolean||value instanceof BigDecimal)return value;
        if(value instanceof Number)return new BigDecimal(value.toString());
        if(value instanceof List<?> list){if(list.size()>256)throw new IllegalArgumentException("Data list exceeds 256 entries");return list.stream().map(DataStore::freeze).toList();}
        String text=String.valueOf(value);if(text.length()>32768)throw new IllegalArgumentException("Data value exceeds 32768 characters");return text;
    }
    @Override public void close(){if(ready.isDone()&&!ready.isCompletedExceptionally())flush();writer.shutdown();}
}
