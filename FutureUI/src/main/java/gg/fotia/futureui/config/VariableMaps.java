package gg.fotia.futureui.config;

import java.util.*;

/** Bukkit 会把节内的点号键展开；将变量命名空间还原为运行时的完整变量名。 */
public final class VariableMaps {
    private VariableMaps(){}
    public static Map<String,Object> flatten(Node node,boolean descriptors){Map<String,Object> result=new LinkedHashMap<>();flatten("",node,descriptors,result);return result;}
    private static void flatten(String prefix,Node node,boolean descriptors,Map<String,Object> result){node.values().forEach((key,value)->{String path=prefix.isEmpty()?key:prefix+"."+key;if(value instanceof Node child&&!(descriptors&&(child.has("type")||child.has("value"))))flatten(path,child,descriptors,result);else result.put(path,value);});}
}
