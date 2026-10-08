package gg.fotia.futureui.layout;

import gg.fotia.futureui.config.Node;
import java.util.*;

/** 通用行、列、网格布局；固定宽度与自动分配共存，父容器负责间距和内边距。 */
public final class LayoutEngine {
    public record Box(String id,int x,int y,int width,int height,Node component){}
    public record Result(List<Box> boxes,int width,int height){}
    public List<Box> layout(List<Node> nodes,Node settings){return measure(nodes,settings).boxes();}
    public Result measure(List<Node> nodes,Node settings){
        List<Box> result=new ArrayList<>();int width=settings.integer("width",576);
        int intrinsic=place(nodes,settings,0,0,width,result,0),height=settings.integer("height",intrinsic);
        if(height<intrinsic)throw new IllegalArgumentException("Canvas is smaller than its content");
        shift(result,height-intrinsic,settings.text("vertical-align","start"));
        return new Result(List.copyOf(result),width,Math.max(9,height));
    }
    private int place(List<Node> nodes,Node settings,int x,int y,int available,List<Box> output,int depth){
        if(depth>20)throw new IllegalArgumentException("Layout nesting too deep");int padding=settings.integer("padding",0),gap=settings.integer("gap",9),inner=available-padding*2;String mode=settings.text("type","column");
        if(inner<1||padding<0||gap<0)throw new IllegalArgumentException("Invalid layout padding or gap");
        if(!Set.of("row","column","grid").contains(mode))throw new IllegalArgumentException("Unknown layout type: "+mode);
        int columns=mode.equals("row")?Math.max(1,nodes.size()):mode.equals("grid")?Math.max(1,settings.integer("columns",2)):1;
        int[] widths=new int[nodes.size()];int cell=(inner-gap*(columns-1))/columns;
        if(mode.equals("row")){int fixed=nodes.stream().filter(n->n.has("width")).mapToInt(n->n.integer("width",0)).sum(),auto=(int)nodes.stream().filter(n->!n.has("width")).count();int remaining=inner-gap*Math.max(0,nodes.size()-1)-fixed;if(remaining<0)throw new IllegalArgumentException("Row exceeds available width");for(int i=0;i<nodes.size();i++)widths[i]=nodes.get(i).has("width")?nodes.get(i).integer("width",0):remaining/Math.max(1,auto);}
        else for(int i=0;i<nodes.size();i++)widths[i]=Math.min(mode.equals("grid")?cell:inner,nodes.get(i).integer("width",mode.equals("grid")?cell:inner));
        int rowHeight=0,rowY=y+padding,cursor=x+padding;
        for(int i=0;i<nodes.size();i++){
            if(i%columns==0&&i>0){rowY+=rowHeight+gap;rowHeight=0;cursor=x+padding;}
            Node node=nodes.get(i);int width=widths[i];if(width<1)throw new IllegalArgumentException("Layout has no space for "+node.text("id",""));
            int boxX=mode.equals("grid")?x+padding+(i%columns)*(cell+gap):cursor;int slotWidth=mode.equals("grid")?cell:mode.equals("column")?inner:width;
            if(node.text("align","start").equals("center"))boxX+=(slotWidth-width)/2;else if(node.text("align","start").equals("end"))boxX+=slotWidth-width;
            List<Box> children=new ArrayList<>();boolean viewport=node.text("type","").equals("viewport");
            int intrinsic=viewport?node.integer("height",99):node.has("children")?place(node.nodes("children"),node,boxX,rowY,width,children,depth+1):defaultHeight(node);
            int height=Math.max(node.integer("min-height",0),node.integer("height",intrinsic));if(height<intrinsic&&node.has("children"))throw new IllegalArgumentException("Container is smaller than its content: "+node.text("id",""));
            if(height>intrinsic)shift(children,height-intrinsic,node.text("vertical-align","start"));
            output.add(new Box(node.text("id",""),boxX,rowY,width,height,node));output.addAll(children);rowHeight=Math.max(rowHeight,height);cursor+=width+gap;
        }return Math.max(settings.integer("min-height",0),nodes.isEmpty()?padding*2:rowY-y+rowHeight+padding);
    }
    private void shift(List<Box> boxes,int extra,String align){int shift=align.equals("center")?(extra/18)*9:align.equals("end")?extra:0;if(shift==0)return;for(int i=0;i<boxes.size();i++){Box b=boxes.get(i);boxes.set(i,new Box(b.id,b.x,b.y+shift,b.width,b.height,b.component));}}
    private int defaultHeight(Node node){return switch(node.text("type","text")){case "button","toggle"->27;case "image"->node.integer("size",72);case "chart","raster"->108;case "text"->9+node.integer("padding",0)*2;case "spacer"->9;default->9;};}
}
