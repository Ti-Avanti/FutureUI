package gg.fotia.futureui.shop;

import gg.fotia.futureui.config.Node;
import java.util.*;
import java.math.BigDecimal;

/** 商品购买份数；旧 maximum 只在新配置未提供 max 时回退。 */
public record QuantityRule(int min,int max,int initial,int step,List<Integer> presets) {
    public QuantityRule {presets=List.copyOf(presets);if(min<1||max<min||step<1||initial<min||initial>max||(initial-min)%step!=0||presets.size()>16)throw new IllegalArgumentException("Invalid quantity range or presets");for(int value:presets)if(value<min||value>max||(value-min)%step!=0)throw new IllegalArgumentException("Invalid quantity preset: "+value);}
    public static QuantityRule parse(Node product){Node q=product.child("quantity");if(product.has("maximum")&&q.has("max")&&product.integer("maximum",64)!=q.integer("max",64))throw new IllegalArgumentException("maximum conflicts with quantity.max");int min=q.integer("min",1),max=q.integer("max",product.integer("maximum",64));return new QuantityRule(min,max,q.integer("initial",min),q.integer("step",1),q.list("presets").stream().map(v->new BigDecimal(v.toString()).intValueExact()).distinct().toList());}
    public boolean accepts(int value){return value>=min&&value<=max&&(value-min)%step==0;}
    public int floor(int upper){return upper<min?0:min+(Math.min(upper,max)-min)/step*step;}
    public int selected(Object value){try{return Math.max(min,floor(gg.fotia.futureui.form.NumericRange.parse(value).intValueExact()));}catch(RuntimeException invalid){return initial;}}
}
