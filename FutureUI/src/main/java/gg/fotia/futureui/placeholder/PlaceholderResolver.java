package gg.fotia.futureui.placeholder;

import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.integration.PlaceholderHook;
import java.util.*;
import java.util.function.Function;
import java.util.regex.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 所有入口共享的有界解析管线。未知变量保留，玩家输入和组件使用不透明占位。 */
public final class PlaceholderResolver {
    private static final Pattern LOCAL=Pattern.compile("\\{(?:(raw|expand):)?([a-zA-Z0-9_.-]+)}");
    private static final Pattern PAPI=Pattern.compile("%[^%\\s]+%");
    public record Resolution(String template,Map<String,Object> literals,List<String> rounds,String stop) {
        public String plain(){String result=template.replaceAll("[\uFDD1\uFDD2]\\d+\uFDEF","");for(var entry:literals.entrySet()){Object value=entry.getValue();result=result.replace(entry.getKey(),value instanceof Component c?net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c):String.valueOf(value));}return result;}
    }
    private final boolean papi;
    private java.util.function.BiPredicate<Player,String> literalOutput=(player,token)->false;
    public void literalOutput(java.util.function.BiPredicate<Player,String> classifier){literalOutput=classifier;}
    private final ThreadLocal<Map<String,String>> frameCache=new ThreadLocal<>();
    public AutoCloseable frame(){Map<String,String> previous=frameCache.get();frameCache.set(new HashMap<>());return ()->{if(previous==null)frameCache.remove();else frameCache.set(previous);};}
    private volatile Node settings;
    public PlaceholderResolver(boolean papi,Node settings){this.papi=papi;configure(settings);}
    public void configure(Node value){
        String mode=value.text("mode","recursive");
        if(!Set.of("raw","once","recursive").contains(mode))throw new IllegalArgumentException("placeholders.mode: raw, once or recursive required");
        if(value.integer("max-rounds",5)<1||value.integer("max-rounds",5)>16)throw new IllegalArgumentException("placeholders.max-rounds: 1..16 required");
        if(value.integer("max-length",32768)<256||value.integer("max-length",32768)>1048576)throw new IllegalArgumentException("placeholders.max-length: 256..1048576 required");
        settings=value;
    }
    public Resolution resolve(Player player,String source,Function<String,Object> lookup,Node overrides){
        Node policy=settings.merge(overrides);String mode=policy.text("mode","recursive");
        int limit=policy.integer("max-length",32768),roundLimit=mode.equals("once")?1:Math.min(16,policy.integer("max-rounds",5));
        if(source.length()>limit)throw new IllegalArgumentException("Placeholder source exceeds max-length");
        Map<String,Object> literals=new LinkedHashMap<>();List<String> trace=new ArrayList<>();Map<String,String> tokenCache=new HashMap<>();
        int[] richId={0};
        if(mode.equals("raw"))return new Resolution(source,Map.of(),List.of(source),"raw");
        String current=source;Set<String> visited=new HashSet<>();String stop="limit";
        for(int round=0;round<roundLimit;round++){
            visited.add(current);trace.add(current);String next=current;
            if(policy.text("order","local-first").equals("papi-first"))next=papi(player,next,tokenCache,literals,richId,limit,policy.bool("text-output",false),policy.bool("expand-input",false));
            next=locals(next,lookup,literals,policy.bool("expand-input",false),limit);
            if(!policy.text("order","local-first").equals("papi-first"))next=papi(player,next,tokenCache,literals,richId,limit,policy.bool("text-output",false),policy.bool("expand-input",false));
            if(next.equals(current)){stop="stable";break;}
            if(visited.contains(next)){stop="cycle";break;}
            current=next;
        }
        return new Resolution(current,Collections.unmodifiableMap(literals),List.copyOf(trace),stop);
    }
    private String locals(String source,Function<String,Object> lookup,Map<String,Object> literals,boolean expandInput,int limit){
        Matcher matcher=LOCAL.matcher(source);StringBuilder out=new StringBuilder();
        while(matcher.find()){
            String key=matcher.group(2);Object value=lookup.apply(key);String replacement=matcher.group();
            if(value!=null){
                boolean raw="raw".equals(matcher.group(1))||value instanceof Component||(!expandInput&&!"expand".equals(matcher.group(1))&&(untrusted(key)||value instanceof LiteralValue));
                if(raw){replacement="\uFDD0"+literals.size()+"\uFDEF";literals.put(replacement,value);}else replacement=value instanceof java.math.BigDecimal decimal?decimal.stripTrailingZeros().toPlainString():String.valueOf(value);
            }
            matcher.appendReplacement(out,Matcher.quoteReplacement(replacement));check(out.length(),limit);
        }
        matcher.appendTail(out);check(out.length(),limit);return out.toString();
    }
    private String papi(Player player,String source,Map<String,String> cache,Map<String,Object> literals,int[] richId,int limit,boolean textOutput,boolean expandInput){
        if(!papi||player==null||!source.contains("%"))return source;
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("PAPI must run on the server thread");
        Matcher matcher=PAPI.matcher(source);StringBuilder out=new StringBuilder();
        while(matcher.find()){
            String token=matcher.group(),parameterized=token;boolean safe=true;
            for(var entry:literals.entrySet())if(parameterized.contains(entry.getKey())){String literal=String.valueOf(entry.getValue());if(literal.contains("%")||literal.contains("{")||literal.contains("}")){safe=false;break;}parameterized=parameterized.replace(entry.getKey(),literal);}
            final String requested=parameterized;
            String value=safe?cache.computeIfAbsent(requested,key->{Map<String,String> frame=frameCache.get();return frame==null?PlaceholderHook.parse(player,key):frame.computeIfAbsent(player.getUniqueId()+":"+key,unused->PlaceholderHook.parse(player,key));}):token;
            if(!expandInput&&value!=null&&!value.equals(requested)&&literalOutput.test(player,requested)){String marker="\uFDD0"+literals.size()+"\uFDEF";literals.put(marker,new LiteralValue(value));value=marker;}
            else if(textOutput&&value!=null&&!value.equals(requested)&&!value.equals(token)){int id=richId[0]++;value="\uFDD1"+id+"\uFDEF"+value.replace("<","\\<")+"\uFDD2"+id+"\uFDEF";}
            matcher.appendReplacement(out,Matcher.quoteReplacement(value==null?token:value));check(out.length(),limit);
        }
        matcher.appendTail(out);check(out.length(),limit);return out.toString();
    }
    public static boolean untrusted(String key){return key.startsWith("input.")||key.startsWith("argument.")||key.startsWith("data.")||key.startsWith("global.")||key.equals("player.display");}
    private static void check(int length,int limit){if(length>limit)throw new IllegalArgumentException("Placeholder output exceeds max-length");}
}
