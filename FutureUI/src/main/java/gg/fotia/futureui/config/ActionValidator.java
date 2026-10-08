package gg.fotia.futureui.config;

import java.util.List;
import java.util.Set;

/** 所有动作入口使用同一校验，扩展动作通过带命名空间的 ID 注册。 */
public final class ActionValidator {
    public static final Set<String> BUILT_INS=Set.of("open-menu","back","close","refresh","message","actionbar","title","sound","player-command","console-command","set-variable","adjust-number","toggle","save-preference","cooldown","delay","if","purchase","give-item","take-currency","give-currency","page","language","hud","hide-hud","open-url","copy-text","call","repeat","for-each","for-players","break","return","stop","random","require","retry","schedule","cancel-task","connect","fail","take-item","edit-item","repair-item","enchant-item","count-item","data-get","data-set","data-add","data-remove","data-toggle","calculate","list","date","quota","transaction","input","cart-add","cart-remove","cart-clear","cart-checkout","sell");
    private ActionValidator() {}
    public static void validate(List<Node> actions,String path,int depth){
        if(depth>24||actions.size()>128)throw new IllegalArgumentException(path+": action chain exceeds limit");
        for(Node action:actions){
            ConfigFields.core(action,path);
            String type=action.text("type","");
            if(!BUILT_INS.contains(type)&&!type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException(path+": unknown action "+type);
            ConditionValidator.validate(action.condition("condition"),path+".condition",0);
            if(type.equals("if"))ConditionValidator.validate(action.condition("when"),path+".when",0);
            if(!dynamic(action.text("timeout-seconds","30"))&&action.integer("timeout-seconds",30)<1)throw new IllegalArgumentException(path+": invalid action timeout");
            if(action.has("chance")&&!dynamic(action.text("chance","1"))&&(action.number("chance",1)<0||action.number("chance",1)>1))throw new IllegalArgumentException(path+": chance must be 0..1");
            if(type.equals("require"))ConditionValidator.validate(action.condition("requirements"),path+".requirements",0);
            if(type.equals("transaction"))gg.fotia.futureui.action.TransactionActions.validate(action);
            if(type.equals("input")){if(!dynamic(action.text("mode","dialog"))&&!Set.of("dialog","chat","sign","anvil","book").contains(action.text("mode","dialog")))throw new IllegalArgumentException(path+": unknown input mode");if(action.integer("max-length",128)<1||action.integer("max-length",128)>32768)throw new IllegalArgumentException(path+": input max-length must be 1..32768");if(action.child("validation").has("pattern"))com.google.re2j.Pattern.compile(action.child("validation").text("pattern",""));}
            if(type.equals("for-players"))ConditionValidator.validate(action.condition("filter"),path+".filter",0);
            if(type.equals("retry")&&action.nodes("actions").stream().anyMatch(n->!Set.of("transaction","require","delay","fail").contains(n.text("type",""))))throw new IllegalArgumentException(path+": retry requires atomic transactions or read-only checks");
            for(String key:List.of("then","else","on-success","on-failure","finally","actions","on-cancel","on-timeout"))validate(action.nodes(key),path+"."+key,depth+1);
            for(Node choice:action.nodes("choices"))validate(choice.nodes("actions"),path+".choices",depth+1);
        }
    }
    static boolean dynamic(String value){return value.contains("{")||value.contains("%");}
}
