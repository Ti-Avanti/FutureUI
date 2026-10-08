package gg.fotia.futureui.asset;

import gg.fotia.futureui.config.Node;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** 程序化像素边框；使用原生 widget 的悬停状态切换贴图，无需监听鼠标数据包。 */
public final class NativeThemeCompiler {
    private NativeThemeCompiler(){}
    public static void generate(Path output,Node theme) throws Exception {
        if(!theme.bool("enabled",true))return;
        Path widgets=output.resolve("assets/minecraft/textures/gui/sprites/widget");Files.createDirectories(widgets);
        write(widgets,"button",theme.child("normal"),"#8e9a9c","#d4dfe0","#566367");
        write(widgets,"button_highlighted",theme.child("hover"),"#d9c837","#fff6a4","#817229");
        write(widgets,"button_disabled",theme.child("disabled"),"#4c5358","#737c82","#30363d");
    }
    private static void write(Path directory,String name,Node style,String fill,String light,String shade) throws Exception {
        BufferedImage image=new BufferedImage(200,20,BufferedImage.TYPE_INT_ARGB);
        int outside=Color.decode(style.text("outline","#151a22")).getRGB(),inside=Color.decode(style.text("fill",fill)).getRGB(),bright=Color.decode(style.text("light",light)).getRGB(),dark=Color.decode(style.text("shadow",shade)).getRGB();
        int depth=style.integer("shadow-depth",1);if(depth<0||depth>2)throw new IllegalArgumentException("Native shadow-depth must be 0..2");
        for(int y=0;y<20;y++)for(int x=0;x<200;x++){
            int color=x==0||y==0||x==199||y==19?outside:x==1||y==1?bright:x==198||y>=19-depth?dark:inside;
            if((x==0||x==199)&&(y==0||y==19))color=0;image.setRGB(x,y,color);
        }
        ImageIO.write(image,"png",directory.resolve(name+".png").toFile());
        Files.writeString(directory.resolve(name+".png.mcmeta"),"{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":200,\"height\":20,\"border\":2}}}",StandardCharsets.UTF_8);
    }
}
