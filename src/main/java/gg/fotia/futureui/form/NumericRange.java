package gg.fotia.futureui.form;

import gg.fotia.futureui.config.Node;
import java.math.*;

/** 数值输入、滑块与加减动作共用的范围和步长规则。 */
public record NumericRange(BigDecimal min,BigDecimal max,BigDecimal step,BigDecimal initial,boolean integer) {
    public NumericRange {
        if(min.compareTo(max)>0||step.signum()<=0)throw new IllegalArgumentException("Invalid numeric range/step");
        if(integer)for(BigDecimal value:new BigDecimal[]{min,max,step,initial})value.toBigIntegerExact();
        if(initial.compareTo(min)<0||initial.compareTo(max)>0||initial.subtract(min).remainder(step).signum()!=0)throw new IllegalArgumentException("Invalid numeric initial value");
    }
    public static NumericRange of(Node node){BigDecimal min=node.decimal("min","0");return new NumericRange(min,node.decimal("max","100"),node.decimal("step","1"),node.decimal("initial",min.toPlainString()),node.bool("integer",node.text("type","").equals("number-input")));}
    public static BigDecimal parse(Object value){String input=String.valueOf(value).trim();if(input.length()>64||!input.matches("[+-]?\\d+(?:\\.\\d+)?"))throw new NumberFormatException("Invalid number");return new BigDecimal(input);}
    public boolean accepts(BigDecimal value){return value.compareTo(min)>=0&&value.compareTo(max)<=0&&(!integer||value.stripTrailingZeros().scale()<=0)&&value.subtract(min).remainder(step).signum()==0;}
    public BigDecimal clamp(BigDecimal value){BigDecimal bounded=value.max(min).min(max);return min.add(bounded.subtract(min).divide(step,0,RoundingMode.FLOOR).multiply(step));}
    public BigDecimal submitted(Object raw,boolean slider){BigDecimal value=parse(raw);if(slider){BigDecimal nearest=min.add(value.subtract(min).divide(step,0,RoundingMode.HALF_UP).multiply(step));if(value.subtract(nearest).abs().compareTo(new BigDecimal("0.0001"))<=0)value=nearest;}if(!accepts(value))throw new IllegalArgumentException("Number outside range or step");return value;}
}
