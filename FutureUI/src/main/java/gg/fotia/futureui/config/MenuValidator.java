package gg.fotia.futureui.config;

import gg.fotia.futureui.model.MenuDefinition;
import java.util.*;

/** 对页面结构进行静态校验；未知控件拒绝加载。 */
public final class MenuValidator {
    private static final Set<String> TYPES=Set.of("text","image","item","button","toggle","text-input","number-input","slider","checkbox","select","row","column","grid","spacer","list","shop","progress","slot","canvas","chart","raster","viewport");
    private MenuValidator(){}
    public static void validate(String id,Node source,List<Node> components) {
        ConfigFields.menu(source,"menus/"+id);
        gg.fotia.futureui.command.MenuArguments.validate(source,id);
        for(String command:source.strings("open-commands"))if(!command.matches("[a-z0-9_-]{1,40}"))fail(id,"Invalid open-command "+command);
        for(Node binding:source.nodes("item-bindings"))ConditionValidator.validate(binding.condition("condition"),id+".item-bindings",0);
        for(Node trigger:source.nodes("open-events")){if(!Set.of("join","respawn","world-change").contains(trigger.text("event","")))fail(id,"Unknown open-event");ConditionValidator.validate(trigger.condition("condition"),id+".open-events",0);}
        String renderer=source.text("renderer","dialog");
        if(!Set.of("dialog","canvas","inventory","hud","native").contains(renderer))fail(id,"Unknown renderer "+renderer);
        Set<String> ids=new HashSet<>();walk(id,components,ids,0);
        validateEmbeddedCanvas(id,components,renderer);
        if(renderer.equals("dialog")){LayoutValidator.dialog(id,source.child("layout"));LayoutValidator.nativeComponents(id,components);}
        ConditionValidator.validate(source.condition("requirements"),id+".requirements",0);
        ActionValidator.validate(source.nodes("on-open"),id+".on-open",0);
        ActionValidator.validate(source.nodes("on-close"),id+".on-close",0);
        int columns=source.child("layout").integer("columns",1);
        if(columns<1||columns>9)fail(id,"layout.columns must be 1..9");
        int panelHeight=source.child("layout").integer("panel-height",0);
        if(panelHeight<0||panelHeight>2047)fail(id,"layout.panel-height must be 0..2047");
        if(source.integer("refresh-ticks",0)<0)fail(id,"refresh-ticks cannot be negative");
        if(renderer.equals("canvas")){LayoutValidator.container(id+".layout",source.child("layout"));validateCanvas(id,components);new gg.fotia.futureui.layout.LayoutEngine().layout(sample(components),source.child("layout"));}
        if(renderer.equals("inventory")) {
            List<String> rows=source.strings("Layout");
            var kind=org.bukkit.event.inventory.InventoryType.valueOf(source.text("inventory-type","CHEST").toUpperCase(Locale.ROOT));
            if(!Set.of("CHEST","HOPPER","DISPENSER","DROPPER","FURNACE","BLAST_FURNACE","SMOKER","BREWING","SHULKER_BOX","BARREL").contains(kind.name()))fail(id,"Unsupported inventory-type "+kind);
            if(kind==org.bukkit.event.inventory.InventoryType.CHEST&&(rows.isEmpty()||rows.size()>6||rows.stream().anyMatch(row->row.length()!=9)))fail(id,"Chest Layout requires 1..6 rows of 9 characters");
            if(kind!=org.bukkit.event.inventory.InventoryType.CHEST&&String.join("",rows).length()!=kind.getDefaultSize())fail(id,"Layout length does not match inventory-type");
            Set<Character> used=new HashSet<>();rows.forEach(row->row.chars().filter(c->c!='#').forEach(c->used.add((char)c)));
            for(char key:used)if(components.stream().noneMatch(c->c.text("slot-key","").equals(String.valueOf(key))))fail(id,"Missing component for Layout character "+key);
        }
    }
    private static void walk(String path,List<Node> nodes,Set<String> ids,int depth) {
        if(depth>20)fail(path,"Component nesting exceeds 20");
        for(Node node:nodes) {
            ConfigFields.core(node,path);
            String id=node.text("id","");String type=node.text("type","text");
            if(!TYPES.contains(type)&&!type.equals("multi-select"))fail(path,"Unknown component type "+type);
            if(id.isEmpty()||!id.matches("[a-zA-Z0-9_]+")||!ids.add(id))fail(path,"Invalid or duplicate component id "+id);
            if(node.integer("width",150)<1||node.integer("width",150)>1024)fail(path+"."+id,"width must be 1..1024");
            if(Set.of("slider","number-input").contains(type)&&List.of("min","max","initial","step").stream().noneMatch(k->node.text(k,"").contains("{")||node.text(k,"").contains("%")))gg.fotia.futureui.form.NumericRange.of(node);
            LayoutValidator.form(path+"."+id,node);
            ChartValidator.validate(path+"."+id,node);
            if(type.equals("multi-select")&&(node.nodes("options").isEmpty()||node.integer("min-selected",0)<0||node.integer("max-selected",node.nodes("options").size())<node.integer("min-selected",0)))fail(path+"."+id,"Invalid multi-select bounds or options");
            gg.fotia.futureui.render.AnimationFrames.validate(node,path+"."+id);
            if(type.equals("list")&&node.integer("page-size",9)<1||type.equals("shop")&&node.integer("page-size",9)<1)fail(path+"."+id,"page-size must be positive");
            for(String key:List.of("visible","enabled","requirements"))ConditionValidator.validate(node.condition(key),path+"."+id+"."+key,0);
            ActionValidator.validate(node.nodes("actions"),path+"."+id+".actions",0);
            for(String event:List.of("common-actions","on-place","on-take","on-change"))ActionValidator.validate(node.nodes(event),path+"."+id+"."+event,0);
            ConditionValidator.validate(node.child("validation").condition("condition"),path+"."+id+".validation",0);
            if(node.text("source","").equals("condition-checks"))for(Node check:node.nodes("checks"))ConditionValidator.validate(check.condition("condition"),path+"."+id+".checks",0);
            for(Node state:node.nodes("states")){ConditionValidator.validate(state.condition("visible"),path+"."+id+".states",0);ActionValidator.validate(state.nodes("actions"),path+"."+id+".states.actions",0);}
            node.child("click-types").values().forEach((key,value)->ActionValidator.validate(Node.of(value).nodes("actions"),path+".click-types."+key,0));
            if(type.equals("text-input")&&node.child("validation").has("pattern"))com.google.re2j.Pattern.compile(node.child("validation").text("pattern",""));
            walk(path+"."+id,node.nodes("children"),ids,depth+1);
            if(type.equals("list"))walk(path+"."+id+".after-items",node.nodes("after-items"),ids,depth+1);
            if(type.equals("list")&&!node.child("item").empty()&&!node.child("item").has("template")){Node item=node.child("item");if(!item.has("id"))item=item.merge(Node.of(Map.of("id","entry")));walk(path+"."+id+".item",List.of(item),new HashSet<>(),depth+1);}
        }
    }
    public static void validateLinks(Map<String,MenuDefinition> menus){
        for(MenuDefinition menu:menus.values())checkLinks(menu.id(),menu.source(),menus);
    }
    private static void checkLinks(String path,Node node,Map<String,MenuDefinition> menus){
        if(node.text("type","").equals("open-menu")){
            String target=node.text("menu","");
            if(!target.contains("{")&&!menus.containsKey(target))fail(path,"Unknown target menu "+target);
        }
        node.values().values().forEach(value->{if(value instanceof Node n)checkLinks(path,n,menus);else if(value instanceof List<?> list)list.forEach(v->{if(v instanceof Node n)checkLinks(path,n,menus);});});
    }
    private static void fail(String path,String message){throw new IllegalArgumentException("menus/"+path+": "+message);}
    private static void validateEmbeddedCanvas(String path,List<Node> nodes,String renderer){
        for(Node node:nodes){
            if(node.text("type","").equals("raster")&&!renderer.equals("canvas"))fail(path,"Raster previews require canvas or an embedded canvas display section");
            if(node.text("type","").equals("canvas")){
                if(!renderer.equals("dialog"))fail(path,"Embedded canvas sections require the dialog renderer");
                List<Node> children=node.nodes("children");validateCanvas(path,children);validateDisplayOnly(path,children);
                Node layout=node.child("layout").merge(Node.of(Map.of("width",node.integer("width",420))));
                LayoutValidator.container(path+"."+node.text("id","")+".layout",layout);
                new gg.fotia.futureui.layout.LayoutEngine().layout(sample(children),layout);
            }else validateEmbeddedCanvas(path,node.nodes("children"),renderer);
            if(node.text("type","").equals("list")){validateEmbeddedCanvas(path,List.of(node.child("item")),renderer);validateEmbeddedCanvas(path,node.nodes("after-items"),renderer);}
        }
    }
    private static void validateDisplayOnly(String path,List<Node> nodes){
        for(Node node:nodes){
            if(Set.of("button","toggle").contains(node.text("type",""))||node.text("type","").equals("raster")&&!node.nodes("actions").isEmpty())fail(path,"Put form buttons outside the canvas display section");
            validateDisplayOnly(path,node.nodes("children"));
            if(node.text("type","").equals("list")){validateDisplayOnly(path,List.of(node.child("item")));validateDisplayOnly(path,node.nodes("after-items"));}
        }
    }
    private static void validateCanvas(String path,List<Node> nodes){for(Node node:nodes){String type=node.text("type","text");if(!Set.of("text","button","toggle","image","row","column","grid","spacer","list","progress","chart","raster","viewport").contains(type))fail(path,"Canvas does not support "+type+"; open a dialog form for native input controls");LayoutValidator.canvas(path+"."+node.text("id",""),node);for(Node state:node.nodes("states"))LayoutValidator.canvas(path+".states",node.merge(state));validateCanvas(path,node.nodes("children"));if(type.equals("list")){LayoutValidator.container(path+".layout",node.child("layout"));validateCanvas(path,node.nodes("after-items"));if(!node.child("item").has("template"))validateCanvas(path,List.of(node.child("item")));}}}
    private static List<Node> sample(List<Node> nodes){List<Node> result=new ArrayList<>();for(Node node:nodes){if(node.text("type","").equals("list")){if(node.child("item").has("template"))continue;List<Node> children=new ArrayList<>();children.add(node.child("item"));children.addAll(node.nodes("after-items"));List<Node> rows=sample(children);if(gg.fotia.futureui.layout.ListLayout.grouped(node))result.add(gg.fotia.futureui.layout.ListLayout.wrap(node,rows));else result.addAll(rows);}else if(node.has("children")){Map<String,Object> map=new LinkedHashMap<>(node.values());map.put("children",sample(node.nodes("children")));result.add(Node.of(map));}else result.add(node);}return result;}
}
