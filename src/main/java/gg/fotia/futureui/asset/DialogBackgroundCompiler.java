package gg.fotia.futureui.asset;

import gg.fotia.futureui.config.Node;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;

/** 原生表单的底板标记，不替换其他插件或系统界面的背景纹理。 */
final class DialogBackgroundCompiler {
    private DialogBackgroundCompiler() {}
    static void generate(Path root,Node theme,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers)throws Exception {
        generate(root,theme,allocation,glyphs,providers,"");
    }
    static void generate(Path root,Node theme,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers,String suffix)throws Exception {
        if(!theme.child("background").bool("enabled",true))return;
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int x:new int[]{0,15})for(int y:new int[]{0,15})image.setRGB(x,y,0xfffa17e9);
        image.setRGB(1,1,0xff008000);
        image.setRGB(2,1,Color.decode(theme.child("background").text("color","#313233")).getRGB());
        // 客户端按真实字形边界安排 GUI 图层；足够大的逻辑边界保证底板先于原生控件绘制。
        CanvasInteractionCompiler.publish(root,"dialog-background"+suffix,image,4096,7,allocation,glyphs,providers);
    }
}
