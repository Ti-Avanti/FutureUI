package gg.fotia.futureui.form;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;
import java.math.BigDecimal;
import java.util.*;

/** 将动态数值参数和快捷操作展开为普通输入及按钮，所有菜单均可复用。 */
public final class NumericInputs {
    private NumericInputs() {}
    public static List<Node> compile(MenuContext ctx,Node source,ValueResolver resolver){
        Map<String,Object> fields=new LinkedHashMap<>(source.values());
        fields.putIfAbsent("control-width",ctx.session().menu.layout().integer("button-width",140));
        for(String key:List.of("min","max","step","initial"))if(source.has(key))fields.put(key,NumericRange.parse(resolver.resolve(ctx,source.text(key,""))));
        Node field=Node.of(fields);NumericRange range=NumericRange.of(field);
        if(field.text("type","").equals("slider")&&range.min().compareTo(range.max())==0){fields.put("type","number-input");field=Node.of(fields);}
        String id=field.text("id",""),key="input."+id;
        ctx.session().variables.putIfAbsent(key,range.initial().toPlainString());
        List<Node> result=new ArrayList<>();result.add(field);
        if(source.bool("controls",false))for(int sign:new int[]{-1,1}){
            Node action=Node.of(Map.of("type","adjust-number","key",key,"initial",range.initial(),"delta",range.step().multiply(BigDecimal.valueOf(sign)),"min",range.min(),"max",range.max(),"step",range.step()));
            result.add(button(field,id+(sign<0?"_decrease":"_increase"),source.text(sign<0?"decrease-label":"increase-label",sign<0?"@common.decrease":"@common.increase"),List.of(action),true,Map.of()));
        }
        Object presets=source.get("presets");if(presets instanceof String reference&&reference.matches("\\{[a-zA-Z0-9_.-]+}"))presets=resolver.value(ctx,reference.substring(1,reference.length()-1));
        if(presets!=null){if(!(presets instanceof List<?> choices)||choices.size()>16)throw new IllegalArgumentException("Numeric presets must be a list of at most 16 values");int index=0;for(Object choice:choices){BigDecimal value=NumericRange.parse(choice);if(!range.accepts(value))throw new IllegalArgumentException("Numeric preset outside range");result.add(button(field,id+"_preset_"+index++,source.text("preset-label","{preset}"),List.of(Node.of(Map.of("type","set-variable","key",key,"value",value.toPlainString()))),true,Map.of("preset",value.stripTrailingZeros().toPlainString())));}}
        if(source.bool("show-maximum",false)){
            BigDecimal upper=NumericRange.parse(resolver.resolve(ctx,source.text("maximum-value",range.max().toPlainString())));boolean allowed=upper.compareTo(range.min())>=0;BigDecimal value=range.clamp(upper);
            result.add(button(field,id+"_maximum",source.text("maximum-label","@common.maximum"),List.of(Node.of(Map.of("type","set-variable","key",key,"value",value.toPlainString()))),allowed,Map.of()));
        }
        return result;
    }
    private static Node button(Node field,String id,String label,List<Node> actions,boolean enabled,Map<String,?> extra){Map<String,Object> context=new LinkedHashMap<>(field.child("context").values());context.putAll(extra);return Node.of(Map.of("id",id,"type","button","text",label,"width",field.integer("control-width",140),"validate",false,"enabled",enabled,"actions",actions,"context",context));}
}
