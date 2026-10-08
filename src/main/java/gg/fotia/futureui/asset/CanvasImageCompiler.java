package gg.fotia.futureui.asset;

import java.util.*;

/** 大图拆成九像素行，避免后续文本行覆盖整张图片。 */
public final class CanvasImageCompiler {
    private CanvasImageCompiler(){}
    public static void compile(String id,String texture,int sourceHeight,List<Integer> rowWidths,List<Integer> sizes,Map<String,Integer> allocation,Map<String,GlyphRegistry.Glyph> glyphs,List<Map<String,Object>> providers){
        for(int size:sizes){int count=size/9;if(size<9||size>144||size%9!=0||sourceHeight%count!=0)throw new IllegalArgumentException("Canvas image size must divide source texture into whole rows: "+id+" / "+size);List<String> chars=new ArrayList<>();int rows=sourceHeight/count;
            for(int row=0;row<count;row++){String key="large/"+size+"/"+id+"_"+row;int code=GlyphAllocation.code(allocation,key);String character=Character.toString(code);chars.add(character);int pixels=rowWidths.subList(row*rows,(row+1)*rows).stream().mapToInt(Integer::intValue).max().orElse(0);glyphs.put(key,new GlyphRegistry.Glyph(character,(int)(pixels*(9f/rows)+0.5f)+1,9));}
            providers.add(Map.of("type","bitmap","file",texture,"height",9,"ascent",7,"chars",chars));
        }
    }
}
