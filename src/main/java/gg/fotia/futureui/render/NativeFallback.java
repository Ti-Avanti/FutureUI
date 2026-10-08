package gg.fotia.futureui.render;

import gg.fotia.futureui.config.Node;
import java.util.*;

/** 无资源包的原生 Dialog 只保留文字、输入和动作，不发送依赖字体的画布。 */
public final class NativeFallback {
    private NativeFallback(){}
    public static List<Node> flatten(List<Node> nodes){List<Node> result=new ArrayList<>();for(Node node:nodes){String type=node.text("type","text");if(!node.nodes("children").isEmpty()){result.addAll(flatten(node.nodes("children")));continue;}if(Set.of("image","spacer","item").contains(type))continue;
        Map<String,Object> values=new LinkedHashMap<>(node.values());for(String key:List.of("skin","disabled-skin","icon","height","padding","background","min-height"))values.remove(key);values.put("width",Math.min(400,node.integer("width",400)));result.add(Node.of(values));}return result;}
}
