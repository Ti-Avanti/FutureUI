package gg.fotia.futureui.config;

import com.google.re2j.Pattern;
import java.util.Set;

/** 配置阶段发现无效条件，避免拼写错误静默放行。 */
public final class ConditionValidator {
    public static final Set<String> TYPES=Set.of("all","any","at-least","not","permission","has-permission","permissions","currency","has-money","item","has-item","experience","world","gamemode","health","food","level","world-time","weather","sneaking","flying","op","empty-slots","cooldown","equals","string-equals","equals-ignore-case","contains","starts-with","ends-with","length","regex","in","exists","number","integer","uuid","compare","==","!=",">",">=","<","<=","expression","pdc","online-player","true","false","scoreboard-tag","biome","distance","day-of-week");
    private ConditionValidator(){}
    public static void validate(Node node,String path,int depth){
        if(node.empty())return;if(depth>24)throw new IllegalArgumentException(path+": condition nesting exceeds 24");
        ConfigFields.core(node,path);
        String type=node.text("type",node.has("conditions")?"all":"");if(type.startsWith("!"))type=type.substring(1);
        if(!TYPES.contains(type)&&!Set.of("named","requirements","data","plugin","list-contains").contains(type)&&!type.contains(":"))throw new IllegalArgumentException(path+": unknown condition "+type);
        if(type.equals("regex"))Pattern.compile(node.text("pattern",""));
        if((type.equals("permission")||type.equals("has-permission"))&&node.text("permission","").isBlank())throw new IllegalArgumentException(path+": missing permission");
        if(Set.of("all","any","at-least","requirements").contains(type)){
            if(!(node.get("conditions") instanceof java.util.List<?>))throw new IllegalArgumentException(path+": conditions must be a list");
            if(type.equals("at-least")&&!ActionValidator.dynamic(node.text("minimum","1"))&&(node.integer("minimum",1)<1||node.integer("minimum",1)>node.nodes("conditions").size()))throw new IllegalArgumentException(path+": invalid minimum");
            for(Node child:node.nodes("conditions"))validate(child,path+".conditions",depth+1);
        }
        if(type.equals("not")){if(!node.has("condition"))throw new IllegalArgumentException(path+": not requires condition");validate(node.condition("condition"),path+".condition",depth+1);}
        if(depth<24){ActionValidator.validate(node.nodes("on-success"),path+".on-success",depth+1);ActionValidator.validate(node.nodes("on-failure"),path+".on-failure",depth+1);}
    }
}
