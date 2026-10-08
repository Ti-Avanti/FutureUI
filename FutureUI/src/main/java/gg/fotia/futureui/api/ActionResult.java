package gg.fotia.futureui.api;

/** 动作结果；失败信息使用翻译键。 */
public record ActionResult(boolean success,String message) {
    public static ActionResult ok() { return new ActionResult(true,""); }
    public static ActionResult fail(String key) { return new ActionResult(false,key); }
}
