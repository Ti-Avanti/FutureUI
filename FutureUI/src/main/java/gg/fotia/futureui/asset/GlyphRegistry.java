package gg.fotia.futureui.asset;

import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** 编译产物和菜单共享的字体映射。 */
public final class GlyphRegistry {
    public record Glyph(String character,int width,int height){}
    private volatile Map<String,Glyph> images=Map.of();
    private volatile Map<String,ViewportRegions.Region> viewports=Map.of();
    public void replace(Map<String,Glyph> values){images=Map.copyOf(values);}
    public void replace(Map<String,Glyph> values,Map<String,ViewportRegions.Region> regions){replace(values);viewports=Map.copyOf(regions);}
    public ViewportRegions.Region viewport(String id){return viewports.get(id);}
    public Component image(String name){Glyph glyph=images.get(name);return glyph==null?Component.text("["+name+"]"):Component.text(glyph.character()).font(Key.key("futureui:images"));}
    public Glyph get(String name){return images.get(name);}
    public Map<String,Glyph> all(){return images;}
}
