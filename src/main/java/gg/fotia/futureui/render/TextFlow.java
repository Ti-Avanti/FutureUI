package gg.fotia.futureui.render;

import java.util.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** 换行保留颜色、字体和装饰，不把 MiniMessage 重新拼成字符串。 */
public final class TextFlow {
    private TextFlow(){}
    public static List<Component> lines(Component text,int width,int limit){
        List<Component> glyphs=new ArrayList<>();flatten(text,Style.empty(),glyphs);List<Component> rows=new ArrayList<>();Component row=Component.empty();double used=0;
        for(Component glyph:glyphs){String value=((TextComponent)glyph).content();double size=PixelCanvas.advance(glyph);if(value.equals("\n")||used+size>width&&used>0){rows.add(row);if(rows.size()>=limit)return rows;row=Component.empty();used=0;if(value.equals("\n"))continue;}row=row.append(glyph);used+=size;}
        if(rows.size()<limit)rows.add(row);return rows;
    }
    private static void flatten(Component component,Style inherited,List<Component> output){Style style=component.style().merge(inherited,Style.Merge.Strategy.IF_ABSENT_ON_TARGET);String value=component instanceof TextComponent text?text.content():PlainTextComponentSerializer.plainText().serialize(component.children(List.of()));value.codePoints().forEach(cp->output.add(Component.text(Character.toString(cp)).style(style)));component.children().forEach(child->flatten(child,style,output));}
}
