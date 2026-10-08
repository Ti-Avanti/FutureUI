package gg.fotia.futureui.placeholder;

/** 玩家输入跨参数传递时保留来源，避免改名后被当作占位符或富文本执行。 */
public record LiteralValue(String text) {
    @Override public String toString(){return text;}
}
