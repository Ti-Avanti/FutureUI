package gg.fotia.futureui.asset;

import com.google.gson.Gson;
import gg.fotia.futureui.config.Node;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** 画布交互资源：焦点边线及由客户端悬停提示触发的局部高亮。 */
public final class CanvasInteractionCompiler {
    private CanvasInteractionCompiler() {}

    public static void generate(Path root,Node canvas,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers)throws Exception {
        generate(root,canvas,allocation,glyphs,providers,"");
    }
    static void generate(Path root,Node canvas,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers,String theme)throws Exception {
        Node interaction=canvas.child("interaction");
        boolean background=canvas.child("background").bool("enabled",true);
        if(background||interaction.bool("cover-focus-outline",true)) {
            int color=Color.decode(background?canvas.child("background").text("color","#313233"):interaction.text("outline-color","#263b43")).getRGB();
            for(String row:List.of("top","middle","bottom","single"))for(var part:parts().entrySet()) {
                int width=part.getValue(),height=row.equals("single")?17:row.equals("middle")?9:13;
                BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                // 极低透明度仅维持字体宽度，片元阈值会丢弃这些像素。
                for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,background?color:0x01000000);
                for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((row.equals("top")||row.equals("single"))&&y==0||(row.equals("bottom")||row.equals("single"))&&y==height-1||part.getKey().equals("left")&&x==0||part.getKey().equals("right")&&x==width-1)image.setRGB(x,y,color);
                if(part.getKey().equals("right")){image.setRGB(0,0,0x01fa17e9);image.setRGB(0,1,0x01000000|(row.equals("single")?4:row.equals("top")?1:row.equals("bottom")?3:2));}
                publish(root,"frame/"+theme+row+"/"+part.getKey(),image,height,row.equals("top")||row.equals("single")?11:7,allocation,glyphs,providers);
            }
        }
        if(interaction.bool("hover",true)) {
        Path tooltip=root.resolve("assets/futureui/textures/gui/sprites/tooltip");Files.createDirectories(tooltip);
        for(String suffix:List.of("background","frame"))ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),"png",tooltip.resolve("canvas_"+suffix+".png").toFile());
        for(var entry:canvas.child("styles").values().entrySet()) {
            Node hover=Node.of(entry.getValue()).child("hover");
            int opacity=hover.integer("opacity",56);if(opacity<0||opacity>255)throw new IllegalArgumentException("Canvas hover opacity must be 0..255");
            int fill=Color.decode(hover.text("fill","#ffffff")).getRGB()&0xffffff|opacity<<24;
            int edge=Color.decode(hover.text("outline","#fff0b0")).getRGB();
            List<String> hoverRows=new ArrayList<>(List.of("top","middle","bottom"));
            if(interaction.has("hover-heights")) {
                for(Object value:new LinkedHashSet<>(interaction.list("hover-heights"))) {
                    int height=Integer.parseInt(value.toString());
                    if(height<9||height>252||height%9!=0)throw new IllegalArgumentException("hover-heights must contain multiples of 9 in 9..252");
                    hoverRows.add("h"+height);
                }
            } else for(int height=9;height<=252;height+=9)hoverRows.add("h"+height);
            for(String row:hoverRows)for(var part:parts().entrySet()) {
                BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
                for(int x:new int[]{0,15})for(int y:new int[]{0,15})image.setRGB(x,y,0xfffa17e9);
                boolean full=row.startsWith("h");int height=full?Integer.parseInt(row.substring(1)):9;
                int flags=(full?3:row.equals("top")?1:row.equals("bottom")?2:0)|(part.getKey().equals("left")?4:part.getKey().equals("right")?8:0);
                image.setRGB(1,1,0xff000000|part.getValue()<<16|flags<<8|height);
                image.setRGB(2,1,fill);image.setRGB(3,1,edge);
                publish(root,"hover/"+entry.getKey()+"/"+row+"/"+part.getKey(),image,16,14,allocation,glyphs,providers);
            }
        }
        Map<String,Long> advances=new LinkedHashMap<>();
        for(int bit=0;bit<23;bit++){long advance=1L<<(bit+16);advances.put(Character.toString(0xe000+bit),advance);advances.put(Character.toString(0xe020+bit),-advance);}
        Path font=root.resolve("assets/futureui/font/interaction.json");Files.createDirectories(font.getParent());Files.writeString(font,new Gson().toJson(Map.of("providers",List.of(Map.of("type","space","advances",advances)))),StandardCharsets.UTF_8);
        }
    }

    private static Map<String,Integer> parts(){Map<String,Integer> result=new LinkedHashMap<>();result.put("left",2);result.put("right",2);for(int size=1;size<=128;size*=2)result.put("fill"+size,size);return result;}

    static void publish(Path root,String id,BufferedImage image,int height,int ascent,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers)throws Exception {
        Path target=root.resolve("assets/futureui/textures/font/"+id+".png");Files.createDirectories(target.getParent());ImageIO.write(image,"png",target.toFile());
        int code=GlyphAllocation.code(allocation,id);String character=Character.toString(code);
        providers.add(Map.of("type","bitmap","file","futureui:font/"+id+".png","height",height,"ascent",ascent,"chars",List.of(character)));glyphs.put(id,new GlyphRegistry.Glyph(character,(int)Math.round((double)image.getWidth()*height/image.getHeight())+1,height));
    }
}
