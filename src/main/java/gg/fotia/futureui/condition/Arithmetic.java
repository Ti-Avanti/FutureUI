package gg.fotia.futureui.condition;

import java.math.*;
import java.util.*;

/** 有界十进制公式，不调用脚本引擎、反射或 Java 代码。 */
public final class Arithmetic {
    private final String source;private int position,tokens,depth;
    private static final MathContext MC=MathContext.DECIMAL64;
    private Arithmetic(String source){if(source.length()>2048)throw new IllegalArgumentException("Formula exceeds limit");this.source=source;}
    public static BigDecimal evaluate(String source){Arithmetic parser=new Arithmetic(source);BigDecimal result=parser.expression();parser.space();if(parser.position!=source.length())throw new IllegalArgumentException("Unexpected formula token at "+parser.position);if(result.precision()>128||Math.abs(result.scale())>128)throw new IllegalArgumentException("Formula magnitude exceeds limit");return result.stripTrailingZeros();}
    private BigDecimal expression(){enter();try{BigDecimal value=product();while(true){if(take('+'))value=value.add(product(),MC);else if(take('-'))value=value.subtract(product(),MC);else return value;}}finally{depth--;}}
    private BigDecimal product(){BigDecimal value=atom();while(true){if(take('*'))value=value.multiply(atom(),MC);else if(take('/'))value=value.divide(atom(),MC);else if(take('%'))value=value.remainder(atom(),MC);else return value;}}
    private BigDecimal atom(){if(++tokens>256)throw new IllegalArgumentException("Too many formula tokens");space();if(take('+'))return atom();if(take('-'))return atom().negate();if(take('(')){BigDecimal v=expression();need(')');return v;}
        int start=position;if(position<source.length()&&Character.isLetter(source.charAt(position))){while(position<source.length()&&Character.isLetter(source.charAt(position)))position++;String name=source.substring(start,position);need('(');List<BigDecimal> args=new ArrayList<>();args.add(expression());while(take(','))args.add(expression());need(')');return function(name,args);}
        while(position<source.length()&&(Character.isDigit(source.charAt(position))||source.charAt(position)=='.'))position++;
        if(start==position)throw new IllegalArgumentException("Expected number at "+position);return new BigDecimal(source.substring(start,position),MC);
    }
    private BigDecimal function(String name,List<BigDecimal> a){return switch(name){
        case "min"->a.stream().min(BigDecimal::compareTo).orElseThrow();case "max"->a.stream().max(BigDecimal::compareTo).orElseThrow();
        case "abs"->{arity(a,1);yield a.getFirst().abs();}case "floor"->{arity(a,1);yield a.getFirst().setScale(0,RoundingMode.FLOOR);}case "ceil"->{arity(a,1);yield a.getFirst().setScale(0,RoundingMode.CEILING);}
        case "round"->{if(a.size()<1||a.size()>2)throw new IllegalArgumentException("round takes 1 or 2 arguments");int scale=a.size()==2?a.get(1).intValueExact():0;if(Math.abs(scale)>16)throw new IllegalArgumentException("round scale exceeds 16");yield a.getFirst().setScale(scale,RoundingMode.HALF_UP);}
        case "clamp"->{arity(a,3);yield a.getFirst().max(a.get(1)).min(a.get(2));}
        case "pow"->{arity(a,2);int power=a.get(1).intValueExact();if(Math.abs(power)>16)throw new IllegalArgumentException("pow exponent exceeds 16");yield power<0?BigDecimal.ONE.divide(a.getFirst().pow(-power,MC),MC):a.getFirst().pow(power,MC);}
        default->throw new IllegalArgumentException("Unknown math function "+name);};}
    private void enter(){if(++depth>24)throw new IllegalArgumentException("Formula nesting exceeds 24");}
    private void space(){while(position<source.length()&&Character.isWhitespace(source.charAt(position)))position++;}
    private boolean take(char c){space();if(position<source.length()&&source.charAt(position)==c){position++;return true;}return false;}
    private void need(char c){if(!take(c))throw new IllegalArgumentException("Expected "+c+" at "+position);}
    private static void arity(List<?> args,int count){if(args.size()!=count)throw new IllegalArgumentException("Wrong function argument count");}
}
