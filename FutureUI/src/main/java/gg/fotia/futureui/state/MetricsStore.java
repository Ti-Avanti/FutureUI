package gg.fotia.futureui.state;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/** 可选聚合统计，不记录输入内容、聊天或玩家身份。 */
public final class MetricsStore implements AutoCloseable {
    private final ConcurrentMap<String,LongAdder> counts=new ConcurrentHashMap<>();
    private final Path file;private final Logger logger;private final boolean enabled;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("FutureUI-metrics").factory());
    public MetricsStore(Path file,boolean enabled,Logger logger){this.file=file;this.enabled=enabled;this.logger=logger;if(enabled)writer.execute(()->{try{if(Files.exists(file))com.google.gson.JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->counts.computeIfAbsent(e.getKey(),k->new LongAdder()).add(e.getValue().getAsLong()));}catch(Exception error){logger.warning("统计读取失败: "+error.getMessage());}});}
    public void count(String type,String name){if(enabled)counts.computeIfAbsent(type+":"+name,k->new LongAdder()).increment();}
    public void timing(String name,long nanos){if(enabled){counts.computeIfAbsent("time-nanos:"+name,k->new LongAdder()).add(nanos);count("samples",name);}}
    public Map<String,Long> snapshot(){Map<String,Long> result=new TreeMap<>();counts.forEach((k,v)->result.put(k,v.sum()));return result;}
    public void save(){if(!enabled)return;Map<String,Long> value=snapshot();writer.execute(()->{try{Files.createDirectories(file.getParent());Files.writeString(file,new Gson().toJson(value),StandardCharsets.UTF_8);}catch(Exception error){logger.warning("统计保存失败: "+error.getMessage());}});}
    public void close(){save();writer.shutdown();}
}
