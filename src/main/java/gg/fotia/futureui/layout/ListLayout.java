package gg.fotia.futureui.layout;

import gg.fotia.futureui.config.Node;
import java.util.*;

/** 列表展开与静态校验使用同一容器，保留列表上的尺寸、底板和上下文。 */
public final class ListLayout {
    private ListLayout() {}

    public static Node wrap(Node source,List<Node> children){
        Map<String,Object> group=new LinkedHashMap<>(source.merge(source.child("layout")).values());
        for(String key:List.of("layout","item","after-items","states"))group.remove(key);
        group.put("type",source.child("layout").text("type","column"));
        group.put("id",source.text("id","list")+"_layout");
        group.put("children",children);
        return Node.of(group);
    }

    public static boolean grouped(Node source){
        return List.of("layout","width","height","min-height","padding","skin").stream().anyMatch(source::has);
    }
}
