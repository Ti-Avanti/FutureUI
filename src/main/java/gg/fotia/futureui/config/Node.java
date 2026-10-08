package gg.fotia.futureui.config;

import java.math.BigDecimal;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** 不可变配置节点；运行时不保留可变的 Bukkit 配置对象。 */
public record Node(Map<String, Object> values) {
    public Node { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    public static Node of(Object value) {
        if (value instanceof Node node) return node;
        if (value instanceof ConfigurationSection section) return of(section.getValues(false));
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((k,v) -> result.put(String.valueOf(k), freeze(v)));
        return new Node(result);
    }
    private static Object freeze(Object value) {
        if (value instanceof ConfigurationSection || value instanceof Map<?, ?>) return of(value);
        if (value instanceof List<?> list) return list.stream().map(Node::freeze).toList();
        return value;
    }
    public Object get(String key) { return values.get(key); }
    public String text(String key, String fallback) { Object value=get(key); return value == null ? fallback : String.valueOf(value); }
    public boolean bool(String key, boolean fallback) { Object value=get(key); return value == null ? fallback : Boolean.parseBoolean(value.toString()); }
    public int integer(String key, int fallback) { Object value=get(key); return value == null ? fallback : new BigDecimal(value.toString()).intValueExact(); }
    public double number(String key, double fallback) { Object value=get(key); return value == null ? fallback : Double.parseDouble(value.toString()); }
    public BigDecimal decimal(String key, String fallback) { return new BigDecimal(text(key,fallback)); }
    public Node child(String key) { return of(get(key)); }
    public Node condition(String key) { Object value=get(key);if(value instanceof Boolean b)return of(Map.of("type",b?"true":"false"));if(value!=null&&!(value instanceof Node)&&!(value instanceof Map<?,?>))throw new IllegalArgumentException(key+" must be a condition object or boolean");return of(value); }
    public List<?> list(String key) { return get(key) instanceof List<?> list ? list : List.of(); }
    public List<Node> nodes(String key) { return list(key).stream().map(Node::of).toList(); }
    public List<String> strings(String key) { return list(key).stream().map(String::valueOf).toList(); }
    public boolean has(String key) { return values.containsKey(key); }
    public boolean empty() { return values.isEmpty(); }
    public Node merge(Node override) {
        Map<String,Object> merged=new LinkedHashMap<>(values);
        override.values.forEach((key,value) -> merged.merge(key,value,(old,next) -> old instanceof Node a && next instanceof Node b ? a.merge(b) : next));
        return new Node(merged);
    }
    public Map<String,Object> plain() {
        Map<String,Object> result=new LinkedHashMap<>();
        values.forEach((key,value)->result.put(key,unwrap(value))); return result;
    }
    private static Object unwrap(Object value) {
        if(value instanceof Node node) return node.plain();
        if(value instanceof List<?> list) return list.stream().map(Node::unwrap).toList();
        return value;
    }
}
