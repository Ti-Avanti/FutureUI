package gg.fotia.futureui.config;

import gg.fotia.futureui.model.MenuDefinition;
import java.util.Map;

/** 校验成功后一次替换的配置快照。 */
public record ConfigurationSnapshot(long revision,Node settings,Map<String,MenuDefinition> menus,
        Map<String,Node> shops,Map<String,Node> huds,Node assets,Map<String,Node> functions,Map<String,Node> rules) {
    public ConfigurationSnapshot { menus=Map.copyOf(menus);shops=Map.copyOf(shops);huds=Map.copyOf(huds);functions=Map.copyOf(functions);rules=Map.copyOf(rules); }
}
