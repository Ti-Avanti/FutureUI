package gg.fotia.futureui.action;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;
import java.math.BigDecimal;

/** 动作参数在执行时读取一次，保留类型与不可信文本来源。 */
final class Parameters {
    private final MenuContext ctx;private final Node node;private final ValueResolver values;
    Parameters(MenuContext ctx,Node node,ValueResolver values){this.ctx=ctx;this.node=node;this.values=values;}
    Object object(String key,Object fallback){Object raw=node.has(key)?node.get(key):fallback;if(raw instanceof String s&&!node.child("parse").empty()){Node policy=node.child("parse");var result=values.trace(ctx,s,policy);return !policy.text("mode","recursive").equals("recursive")||!result.literals().isEmpty()?new gg.fotia.futureui.placeholder.LiteralValue(result.plain()):result.plain();}return values.object(ctx,raw);}
    String string(String key,String fallback){return String.valueOf(object(key,fallback));}
    int integer(String key,int fallback){return decimal(key,String.valueOf(fallback)).intValueExact();}
    long longValue(String key,long fallback){return decimal(key,String.valueOf(fallback)).longValueExact();}
    BigDecimal decimal(String key,String fallback){return new BigDecimal(string(key,fallback));}
    boolean bool(String key,boolean fallback){String value=string(key,String.valueOf(fallback));if(!value.equalsIgnoreCase("true")&&!value.equalsIgnoreCase("false"))throw new IllegalArgumentException("Expected a boolean for "+key);return Boolean.parseBoolean(value);}
}
