package gg.fotia.futureui.asset;

import com.google.gson.Gson;
import gg.fotia.futureui.config.Node;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** 通用九宫格像素皮肤；任意组件宽度都由同一组小字形拼接。 */
public final class CanvasAtlasCompiler {
    private CanvasAtlasCompiler() {}
    public static void generate(Path root,Node settings,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers)throws Exception{
        for(var entry:settings.child("styles").values().entrySet()){
            String name=entry.getKey();if(!name.matches("[a-z0-9_-]+"))throw new IllegalArgumentException("Invalid canvas skin "+name);Node style=Node.of(entry.getValue());
            for(String row:List.of("top","middle","bottom")){
                tile(root,name,row,"left",2,style,allocation,glyphs,providers);tile(root,name,row,"right",2,style,allocation,glyphs,providers);
                for(int size=1;size<=128;size*=2)tile(root,name,row,"fill"+size,size,style,allocation,glyphs,providers);
            }
        }
        Map<String,Number> spaces=new LinkedHashMap<>();for(int i=-1024;i<=1024;i++)spaces.put(Character.toString(0xe000+i+1024),i);
        spaces.put("\ue801",0.5);
        Path font=root.resolve("assets/futureui/font/space.json");Files.createDirectories(font.getParent());Files.writeString(font,new Gson().toJson(Map.of("providers",List.of(Map.of("type","space","advances",spaces)))),StandardCharsets.UTF_8);
    }
    private static void tile(Path root,String skin,String row,String part,int width,Node style,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers)throws Exception{
        int fill=Color.decode(style.text("fill","#354047")).getRGB(),light=Color.decode(style.text("light","#718087")).getRGB(),dark=Color.decode(style.text("shadow","#172126")).getRGB(),outline=Color.decode(style.text("outline","#10181c")).getRGB();
        BufferedImage image=new BufferedImage(width,9,BufferedImage.TYPE_INT_ARGB);
        boolean bevel=style.bool("bevel",true);int depth=style.integer("shadow-depth",1);if(depth<0||depth>2)throw new IllegalArgumentException("shadow-depth must be 0..2");
        for(int y=0;y<9;y++)for(int x=0;x<width;x++){
            int color=fill;
            if(part.equals("left"))color=x==0?outline:bevel?light:fill;else if(part.equals("right"))color=x==1?outline:bevel?dark:fill;
            if(row.equals("top")){if(y==0)color=outline;else if(y==1&&bevel&&!part.equals("right"))color=light;}
            if(row.equals("bottom")){if(y==8)color=outline;else if(y>=8-depth&&bevel&&!part.equals("left"))color=dark;}
            // 顶部高光和底部阴影不得覆盖最外侧描边，否则角点会向外多出一块。
            if(part.equals("left")&&x==0||part.equals("right")&&x==width-1)color=outline;
            image.setRGB(x,y,color);
        }
        String id="canvas/"+skin+"/"+row+"/"+part;Path target=root.resolve("assets/futureui/textures/font/"+id+".png");Files.createDirectories(target.getParent());ImageIO.write(image,"png",target.toFile());
        int code=GlyphAllocation.code(allocation,id);String character=Character.toString(code);
        providers.add(Map.of("type","bitmap","file","futureui:font/"+id+".png","height",9,"ascent",7,"chars",List.of(character)));glyphs.put(id,new GlyphRegistry.Glyph(character,width+1,9));
    }
}
