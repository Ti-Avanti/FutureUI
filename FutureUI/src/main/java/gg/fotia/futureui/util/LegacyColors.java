package gg.fotia.futureui.util;

/** 先转换旧颜色，再交给 MiniMessage，避免非法 section sign。 */
public final class LegacyColors {
    private static final String[] COLORS={"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
    private LegacyColors() {}
    public static String convert(String value) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if((c=='&'||c=='§')&&i+1<value.length()) {
                char code=Character.toLowerCase(value.charAt(i+1));
                if(code=='#'&&i+7<value.length()&&hex(value.substring(i+2,i+8))) { out.append("<reset><#").append(value,i+2,i+8).append('>');i+=7;continue; }
                if(code=='x'&&i+13<value.length()) {
                    StringBuilder rgb=new StringBuilder();boolean valid=true;
                    for(int n=0;n<6;n++){int p=i+2+n*2;if((value.charAt(p)!='&'&&value.charAt(p)!='§')||Character.digit(value.charAt(p+1),16)<0){valid=false;break;}rgb.append(value.charAt(p+1));}
                    if(valid){out.append("<reset><#").append(rgb).append('>');i+=13;continue;}
                }
                int index=Character.digit(code,16);
                if(index>=0){out.append("<reset><").append(COLORS[index]).append('>');i++;continue;}
                String tag=switch(code){case 'k'->"obfuscated";case 'l'->"bold";case 'm'->"strikethrough";case 'n'->"underlined";case 'o'->"italic";case 'r'->"reset";default->null;};
                if(tag!=null){out.append('<').append(tag).append('>');i++;continue;}
            }
            out.append(c=='§'?'&':c);
        }
        return out.toString();
    }
    private static boolean hex(String value){return value.chars().allMatch(c->Character.digit(c,16)>=0);}
}
