package gg.fotia.futureui.form;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;
import java.util.*;

/** 所有原生字段先解析动态默认值和允许值，绘制与提交校验共用编译后的定义。 */
public final class FormInputs {
    public static final Set<String> TYPES=Set.of("text-input","number-input","slider","checkbox","select","multi-select");
    private FormInputs() {}

    public static List<Node> compile(MenuContext ctx,Node source,ValueResolver values){
        String type=source.text("type","");
        if(type.equals("multi-select")){
            String id=source.text("id","");Object saved=ctx.variables().get("input."+id);Object initial=saved==null?values.object(ctx,source.get("initial")):saved;
            Set<String> selected=new HashSet<>();if(initial instanceof List<?> list)list.forEach(v->selected.add(String.valueOf(v)));else if(initial!=null)selected.addAll(Arrays.asList(initial.toString().split(",")));
            List<Node> fields=new ArrayList<>();Set<String> unique=new HashSet<>();int index=0;
            for(Node option:source.nodes("options")){
                String value=values.resolve(ctx,option.text("value",""));if(!unique.add(value))throw new IllegalArgumentException("Duplicate multi-select value "+value);
                fields.add(source.merge(Node.of(Map.of("id",id+"__option_"+index++,"type","checkbox","label",option.text("label",value),"initial",selected.contains(value),"selection-group",Node.of(Map.of("id",id,"value",value,"min",source.integer("min-selected",0),"max",source.integer("max-selected",source.nodes("options").size()),"label",source.text("label",id)))))));
            }
            if(fields.isEmpty())throw new IllegalArgumentException("multi-select requires options");return fields;
        }
        if(Set.of("number-input","slider").contains(type))return NumericInputs.compile(ctx,source,values);
        Map<String,Object> fields=new LinkedHashMap<>(source.values());
        String initial=values.resolve(ctx,source.text("initial",type.equals("checkbox")?"false":""));
        fields.put("initial",initial);
        if(type.equals("checkbox")&&!Set.of("true","false").contains(initial.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Checkbox initial must be boolean: "+source.text("id",""));
        if(type.equals("select")){
            List<Node> options=new ArrayList<>();Set<String> allowed=new HashSet<>();
            for(Node option:source.nodes("options")){
                String value=values.resolve(ctx,option.text("value",""));
                if(!allowed.add(value))throw new IllegalArgumentException("Duplicate select value: "+source.text("id",""));
                options.add(option.merge(Node.of(Map.of("value",value))));
            }
            if(options.isEmpty())throw new IllegalArgumentException("Empty select: "+source.text("id",""));
            if(!source.has("initial"))initial=options.getFirst().text("value","");
            if(!allowed.contains(initial))throw new IllegalArgumentException("Select initial is not an option: "+source.text("id",""));
            fields.put("initial",initial);fields.put("options",options);
        }
        return List.of(Node.of(fields));
    }
}
