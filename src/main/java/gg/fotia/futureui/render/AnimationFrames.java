package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import java.util.*;

/** 配置帧只覆盖外观字段；帧切换不改变按钮身份或业务动作。 */
public final class AnimationFrames {
    private static final Set<String> FIELDS=Set.of("text","image","skin","color","value","item","lore","background","name","position");
    private AnimationFrames(){}
    public static Node apply(MenuContext ctx,Node node){
        Node animation=node.child("animation");List<Node> frames=animation.nodes("frames");if(frames.isEmpty())return node;
        long elapsed=Math.max(0,(System.currentTimeMillis()-ctx.session().openedAt)/50);int ticks=animation.integer("interval-ticks",20);long index=elapsed/ticks;
        Node frame=frames.get(animation.bool("loop",true)?(int)(index%frames.size()):(int)Math.min(frames.size()-1,index));return node.merge(frame);
    }
    public static int interval(List<Node> nodes){
        int result=0;
        for(Node node:nodes){
            int ticks=node.child("animation").nodes("frames").isEmpty()?node.integer("refresh-ticks",0):node.child("animation").integer("interval-ticks",20);
            if(node.text("type","").equals("viewport")&&!node.child("motion").empty())ticks=node.child("motion").integer("interval-ticks",1);
            if(ticks>0)result=result==0?ticks:Math.min(result,ticks);
            List<Node> nested=new ArrayList<>(node.nodes("children"));nested.addAll(node.nodes("after-items"));
            if(node.text("type","").equals("list"))nested.add(node.child("item"));
            int child=interval(nested);if(child>0)result=result==0?child:Math.min(result,child);
        }
        return result;
    }
    public static void validate(Node node,String path){Node animation=node.child("animation");if(animation.empty())return;if(animation.integer("interval-ticks",20)<1||animation.nodes("frames").isEmpty()||animation.nodes("frames").size()>256)throw new IllegalArgumentException(path+": invalid animation frames or interval");for(Node frame:animation.nodes("frames"))for(String field:frame.values().keySet())if(!FIELDS.contains(field))throw new IllegalArgumentException(path+": animation cannot change "+field);}
}
