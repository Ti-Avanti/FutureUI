package gg.fotia.futureui.condition;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** 有限表达式语法：比较、括号、and/or/not；不开放 Java 类、反射或脚本引擎。 */
public final class Expression {
    private final List<String> tokens=new ArrayList<>();private int position;private final Function<String,String> resolver;
    private Expression(String input,Function<String,String> resolver){
        this.resolver=resolver;if(input.length()>4096)throw new IllegalArgumentException("Expression too long");
        for(int i=0;i<input.length();){
            char c=input.charAt(i);if(Character.isWhitespace(c)){i++;continue;}
            if(c=='\''||c=='\"'){char quote=c;StringBuilder value=new StringBuilder();i++;while(i<input.length()&&input.charAt(i)!=quote){char next=input.charAt(i++);if(next=='\\'&&i<input.length())next=input.charAt(i++);value.append(next);}if(i==input.length())throw new IllegalArgumentException("Unclosed expression string");i++;tokens.add("'"+value);continue;}
            if("()!<>=&|".indexOf(c)>=0){String value=String.valueOf(c);i++;if(i<input.length()&&(input.charAt(i)=='='||input.charAt(i)==c&&(c=='&'||c=='|')))value+=input.charAt(i++);tokens.add(value);continue;}
            int begin=i;while(i<input.length()&&!Character.isWhitespace(input.charAt(i))&&"()!<>=&|".indexOf(input.charAt(i))<0)i++;tokens.add(input.substring(begin,i));
        }
        if(tokens.size()>256)throw new IllegalArgumentException("Expression too complex");
    }
    public static boolean evaluate(String input,Function<String,String> resolver){Expression p=new Expression(input,resolver);boolean result=p.or();if(p.position!=p.tokens.size())throw new IllegalArgumentException("Unexpected expression token");return result;}
    private boolean or(){boolean result=and();while(match("||","or")){boolean other=and();result=result||other;}return result;}
    private boolean and(){boolean result=term();while(match("&&","and")){boolean other=term();result=result&&other;}return result;}
    private boolean term(){
        if(match("!","not"))return !term();
        if(match("(")){boolean value=or();expect(")");return value;}
        String left=operand();if(position>=tokens.size())return Boolean.parseBoolean(left);
        String op=tokens.get(position);if(!Set.of("==","!=",">",">=","<","<=").contains(op))return Boolean.parseBoolean(left);
        position++;String right=operand();int compared;
        try{compared=new BigDecimal(left).compareTo(new BigDecimal(right));}catch(NumberFormatException ignored){compared=left.compareTo(right);}
        return switch(op){case "=="->compared==0;case "!="->compared!=0;case ">"->compared>0;case ">="->compared>=0;case "<"->compared<0;default->compared<=0;};
    }
    private String operand(){if(position>=tokens.size())throw new IllegalArgumentException("Missing operand");String value=tokens.get(position++);return resolver.apply(value.startsWith("'")?value.substring(1):value);}
    private boolean match(String... options){if(position<tokens.size()&&Arrays.asList(options).contains(tokens.get(position))){position++;return true;}return false;}
    private void expect(String token){if(!match(token))throw new IllegalArgumentException("Expected "+token);}
}
