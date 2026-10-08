package gg.fotia.futureui.config;

import java.util.*;

/** 提前拒绝无效尺寸和渲染器不支持的样式，避免配置成功但画面无变化。 */
final class LayoutValidator {
    private LayoutValidator() {}

    static void canvas(String path,Node node){
        for(String key:List.of("height","gap","padding","min-height"))if(node.has(key)){
            int value=node.integer(key,0);
            if(value<0||value>2043||value%9!=0)fail(path,key+" must be a nonnegative multiple of 9, up to 2043");
        }
        width(path,node,"width");
        choice(path,node,"align",Set.of("start","center","end"));
        choice(path,node,"text-align",Set.of("start","center","end"));
        choice(path,node,"vertical-align",Set.of("start","center","end"));
        if(node.has("columns")&&(node.integer("columns",1)<1||node.integer("columns",1)>9))fail(path,"columns must be 1..9");
    }

    static void container(String path,Node layout){
        canvas(path,layout);choice(path,layout,"type",Set.of("row","column","grid"));
    }

    static void form(String path,Node node){
        String type=node.text("type","");
        if(Set.of("text-input","number-input").contains(type)){
            int max=node.integer("max-length",type.equals("number-input")?32:128),min=node.child("validation").integer("min-length",0);
            if(max<1||max>32767||min<0||min>max)fail(path,"Invalid input length limits");
            if(node.bool("multiline",false)){
                if(!type.equals("text-input"))fail(path,"Only text-input supports multiline");
                if(node.integer("max-lines",4)<1||node.integer("height",48)<1||node.integer("height",48)>512)fail(path,"Invalid multiline dimensions");
            }
        }
        width(path,node,"control-width");
        if(type.equals("select")){
            Set<String> choices=new HashSet<>();boolean dynamic=false;
            for(Node option:node.nodes("options")){
                String value=option.text("value","");dynamic|=value.contains("{")||value.contains("%");
                if(!choices.add(value))fail(path,"Duplicate select value");
            }
            String initial=node.text("initial","");
            if(choices.isEmpty()||node.has("initial")&&!dynamic&&!initial.contains("{")&&!initial.contains("%")&&!choices.contains(initial))fail(path,"Invalid select options/default");
        }
    }

    static void dialog(String path,Node layout){
        width(path,layout,"button-width");width(path,layout,"exit-width");
        if(layout.has("panel-width")&&(layout.integer("panel-width",0)<1||layout.integer("panel-width",0)>2047))fail(path,"panel-width must be 1..2047");
        Node feedback=layout.child("validation-feedback");canvas(path+".validation-feedback",feedback);
    }

    static void nativeComponents(String path,List<Node> nodes){
        for(Node node:nodes){
            String type=node.text("type","");if(type.equals("canvas"))continue;
            if(Set.of("button","toggle").contains(type))for(String key:List.of("height","skin","disabled-skin"))if(node.has(key))fail(path+"."+node.text("id",""),"Native buttons do not support "+key+"; use canvas buttons for individual styling or assets/images.yml native-widgets for shared styling");
            if(Set.of("row","column","grid").contains(type)&&List.of("skin","height","padding","gap").stream().anyMatch(node::has))fail(path+"."+node.text("id",""),"Use type: canvas for styled form display containers");
            if(node.text("type","").equals("text")&&node.has("skin"))canvas(path,node);
            nativeComponents(path,node.nodes("children"));
            if(type.equals("list")){nativeComponents(path,List.of(node.child("item")));nativeComponents(path,node.nodes("after-items"));}
        }
    }

    private static void width(String path,Node node,String key){if(node.has(key)&&(node.integer(key,0)<1||node.integer(key,0)>1024))fail(path,key+" must be 1..1024");}
    private static void choice(String path,Node node,String key,Set<String> allowed){if(node.has(key)&&!allowed.contains(node.text(key,"")))fail(path,"Unknown "+key+": "+node.text(key,""));}
    private static void fail(String path,String message){throw new IllegalArgumentException("menus/"+path+": "+message);}
}
