package gg.fotia.futureui.render;

import gg.fotia.futureui.asset.GlyphRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;

/** 底板位于标题字形之前，标题及其后的原生输入控件仍由客户端绘制。 */
public final class DialogBackground {
    private DialogBackground() {}
    public static Component title(GlyphRegistry glyphs,Component title,int width,int height) {
        return title(glyphs,title,width,height,"");
    }
    public static Component title(GlyphRegistry glyphs,Component title,int width,int height,String theme) {
        var glyph=glyphs.get("dialog-background"+theme);if(glyph==null)return title;
        int before=glyph.width()/2;
        return Component.empty().append(PixelCanvas.space(-before)).append(glyphs.image("dialog-background"+theme).color(TextColor.color(0x800000|Math.min(2047,width)<<11|Math.max(0,Math.min(2047,height)))).shadowColor(ShadowColor.none())).append(PixelCanvas.space(before-glyph.width())).append(title);
    }
}
