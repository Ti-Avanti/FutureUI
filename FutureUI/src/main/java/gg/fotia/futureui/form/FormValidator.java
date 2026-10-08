package gg.fotia.futureui.form;

import com.google.re2j.Pattern;
import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.ConditionEngine;
import gg.fotia.futureui.config.Node;
import java.util.*;

/** 仅验证客户端提交的数据，字段名称和允许值来自服务端配置。 */
public final class FormValidator {
    private final ConditionEngine conditions;
    public FormValidator(ConditionEngine conditions){this.conditions=conditions;}
    public Map<String,String> validate(MenuContext context,List<Node> components,Map<String,Object> submitted){
        Map<String,String> errors=new LinkedHashMap<>();for(Node field:fields(components)){
            String id=field.text("id","");Object value=submitted.get(id);String type=field.text("type","");String error=null;
            if(value==null)error="messages.field-required";
            else if(type.equals("text-input")){
                String input=String.valueOf(value);Node validation=field.child("validation");
                if(validation.bool("required",false)&&input.isBlank())error="messages.field-required";
                else if(input.length()>field.integer("max-length",128)||input.length()<validation.integer("min-length",0))error="messages.field-length";
                else if(validation.has("pattern")&&!Pattern.compile(validation.text("pattern","")).matcher(input).matches())error="messages.field-format";
            }else if(type.equals("slider")||type.equals("number-input")){
                try{var normalized=NumericRange.of(field).submitted(value,type.equals("slider"));context.variables().put("input."+id,normalized.stripTrailingZeros().toPlainString());}
                catch(RuntimeException invalid){error="messages.field-range";}
            }else if(type.equals("checkbox")&&!(value instanceof Boolean))error="messages.field-format";
            else if(type.equals("select")&&field.nodes("options").stream().noneMatch(n->n.text("value","").equals(String.valueOf(value))))error="messages.field-format";
            if(error==null&&!field.child("validation").condition("condition").empty()&&!conditions.test(context.scoped(field.child("context").values()),field.child("validation").condition("condition")))error=field.child("validation").text("message","messages.field-format");
            if(error!=null)errors.put(id,error);
        }
        Map<String,List<Node>> groups=new LinkedHashMap<>();for(Node field:fields(components))if(field.has("selection-group"))groups.computeIfAbsent(field.child("selection-group").text("id",""),key->new ArrayList<>()).add(field);
        groups.forEach((id,fields)->{List<String> selected=new ArrayList<>();for(Node field:fields)if(Boolean.TRUE.equals(submitted.get(field.text("id",""))))selected.add(field.child("selection-group").text("value",""));Node group=fields.getFirst().child("selection-group");context.variables().put("input."+id,List.copyOf(selected));if(selected.size()<group.integer("min",0)||selected.size()>group.integer("max",fields.size()))errors.put(fields.getFirst().text("id",""),"messages.selection-range");});
        return errors;
    }
    public static List<Node> fields(List<Node> nodes){List<Node> result=new ArrayList<>();for(Node node:nodes){if(FormInputs.TYPES.contains(node.text("type","")))result.add(node);result.addAll(fields(node.nodes("children")));}return result;}
}
