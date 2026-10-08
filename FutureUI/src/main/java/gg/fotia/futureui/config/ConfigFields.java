package gg.fotia.futureui.config;

import java.util.*;

/** 拒绝核心节点中未识别的字段；扩展命名空间和自由参数映射由扩展自身校验。 */
public final class ConfigFields {
    private static final Set<String> ROOT=Set.of("id","title","renderer","template","layout","Layout","components","requirements","on-open","on-close","refresh-ticks","timeout-seconds","permission","hud","inventory-type","parameters","open-commands","item-bindings","open-events","defaults","bindings","cart-summary","fallbacks","outside");
    private static final Set<String> FIELDS=Set.copyOf(Arrays.asList((
        "id type template text label name description width height padding gap min-height align text-align vertical-align columns layout children after-items source item entries cache page-size paginate context visible enabled requirements states priority actions common-actions click-types include-default-actions validate validation tooltip disabled-tooltip disabled-text icon suffix selected skin disabled-skin image fallback-image size image-size image-sizes color background value fill fill-skin track-skin opacity shadow lore material slot slot-key slots accept allow-place allow-take max-amount on-place on-take on-change animation refresh-ticks initial min max step integer maximum minimum control-width controls preset-label presets maximum-label maximum-value show-maximum key persist options label-visible max-length max-lines multiline min-selected max-selected "
        +"condition conditions operator input output permission permissions currency amount levels worlds modes weather pattern values expression data-type world x y z days timezone optional stop-at-success rule arguments on-success on-failure filter plugin list "
        +"menu command title subtitle sound volume pitch delta absolute offset language hud url copy value then else when timeout-seconds chance recover continue-on-failure finally parse function parameters exports as times choices weight schedule interval-ticks delay-ticks attempts selector return tasks server message match selection edit enchantments scope ttl-seconds reset limit default operation separator index timestamp offset-seconds format quantity shop product quoted-price stock quotes prompt hint mode cancel-word max-attempts on-cancel on-timeout fade-in-ticks stay-ticks fade-out-ticks tick ticks strength "
        +"plain provider custom-model-data model-data item-model damage ignore-case lore-mode match-mode pdc biomes tag data-type balance-currency detail-menu row-widths checks include presentation on-open on-close integer enabled button-width heading footer required min-length seconds initial-only item-type icon-size icon-gap series chart-type row-height value-skin empty-text background-image background-size region visible-items position direction motion"
    ).trim().split("\\s+")));
    private ConfigFields(){}
    public static void menu(Node node,String path){check(node,path,ROOT);}
    public static void core(Node node,String path){if(node.text("type","").contains(":")||node.text("source","").contains(":"))return;check(node,path,FIELDS);}
    private static void check(Node node,String path,Set<String> keys){for(String key:node.values().keySet())if(!keys.contains(key))throw new IllegalArgumentException(path+": unknown field '"+key+"'");}
}
