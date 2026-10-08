package gg.fotia.futureui.config;

import gg.fotia.futureui.model.MenuDefinition;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** 加载、模板展开和静态校验全部在后台执行。 */
public final class ConfigRepository {
    private final JavaPlugin plugin;
    private final Path root;
    public ConfigRepository(JavaPlugin plugin){this.plugin=plugin;root=plugin.getDataFolder().toPath();}
    public Path root(){return root;}
    public void installDefaults(Path craftEngine) throws IOException {
        AssetConfiguration.migrate(plugin,root,craftEngine);
        try(InputStream stream=plugin.getResource("defaults.list")) {
            if(stream==null) throw new IOException("Missing defaults.list");
            List<String> defaults=new String(stream.readAllBytes(),StandardCharsets.UTF_8).lines().filter(s->!s.isBlank()).toList();
            DefaultMenuMigration.migrate(root,defaults);
            DefaultTemplateMigration.migrate(root);
            for(String name:defaults) {
                Path target=root.resolve(name).normalize();
                if(!target.startsWith(root))continue;
                if(Files.exists(target)){if(name.equals("config.yml")||name.startsWith("languages/"))mergeMissing(name,target);continue;}
                Files.createDirectories(target.getParent());
                try(InputStream content=plugin.getResource(name)){
                    if(content==null)throw new IOException("Missing bundled file: "+name);
                    Files.copy(content,target);
                }
            }
        }
    }
    private void mergeMissing(String name,Path target)throws IOException {
        try(InputStream stream=plugin.getResource(name)){
            if(stream==null)return;YamlConfiguration defaults=new YamlConfiguration(),current=new YamlConfiguration();
            defaults.load(new InputStreamReader(stream,StandardCharsets.UTF_8));try(Reader reader=Files.newBufferedReader(target,StandardCharsets.UTF_8)){current.load(reader);}
            boolean changed=false;for(String key:defaults.getKeys(true))if(!defaults.isConfigurationSection(key)&&!current.contains(key,true)){current.set(key,defaults.get(key));current.setComments(key,defaults.getComments(key));changed=true;}
            if(changed)Files.writeString(target,current.saveToString(),StandardCharsets.UTF_8);
        }catch(Exception error){throw new IOException("Cannot merge new defaults into "+target,error);}
    }
    public ConfigurationSnapshot load(long revision) throws Exception {
        Node settings=read(root.resolve("config.yml"));
        new gg.fotia.futureui.placeholder.PlaceholderResolver(false,settings.child("placeholders"));
        if(settings.child("transactions").integer("max-total-items",65536)<1)throw new IllegalArgumentException("transactions.max-total-items must be positive");
        int version=settings.integer("config-version",1);
        if(version!=1)throw new IllegalArgumentException("config.yml: unsupported config-version "+version);
        Map<String,Node> templates=readDirectory("templates"),sources=readDirectory("menus");
        Map<String,MenuDefinition> menus=new LinkedHashMap<>();
        for(var entry:sources.entrySet()) {
            Node node=expand(entry.getValue(),templates,new HashSet<>());
            List<Node> components=node.nodes("components").stream().map(n->expand(n,templates,new HashSet<>())).toList();
            MenuValidator.validate(entry.getKey(),node,components);
            menus.put(entry.getKey(),new MenuDefinition(entry.getKey(),node.text("title","@menus."+entry.getKey()+".title"),node.text("renderer","dialog"),node.child("layout"),components,node.condition("requirements"),node.nodes("on-open"),node.nodes("on-close"),node.integer("refresh-ticks",0),node.integer("timeout-seconds",600),node.text("permission","futureui.use"),node));
        }
        if(menus.isEmpty())throw new IllegalArgumentException("menus/: no menus configured");
        MenuValidator.validateLinks(menus);
        Map<String,Node> functions=readDirectory("functions"),rules=readDirectory("rules");
        functions.forEach((id,node)->ActionValidator.validate(node.nodes("actions"),"functions/"+id,0));rules.forEach((id,node)->ConditionValidator.validate(node,"rules/"+id,0));
        Node assets=AssetConfiguration.load(root);
        ViewportValidator.validate(menus,assets);
        return new ConfigurationSnapshot(revision,settings,menus,readDirectory("shops"),readDirectory("hud"),assets,functions,rules);
    }
    private Node expand(Node node,Map<String,Node> templates,Set<String> stack) {
        String ref=node.text("template","");
        if(!ref.isEmpty()) {
            if(!stack.add(ref))throw new IllegalArgumentException("Template cycle: "+stack);
            Node base=templates.get(ref);if(base==null)throw new IllegalArgumentException("Unknown template: "+ref);
            node=expand(base,templates,stack).merge(node);stack.remove(ref);
        }
        Map<String,Object> map=new LinkedHashMap<>();
        node.values().forEach((key,value)->{if(value instanceof Node child)map.put(key,expand(child,templates,new HashSet<>(stack)));else if(value instanceof List<?> list)map.put(key,list.stream().map(v->v instanceof Node child?expand(child,templates,new HashSet<>(stack)):v).toList());else map.put(key,value);});
        map.remove("template");return new Node(map);
    }
    private Map<String,Node> readDirectory(String directory) throws Exception {
        Map<String,Node> result=new LinkedHashMap<>();Path path=root.resolve(directory);if(!Files.exists(path))return result;
        try(var files=Files.walk(path)){
            for(Path file:files.filter(p->p.toString().endsWith(".yml")).sorted().toList()) {
                Node node=read(file);String defaultName=path.relativize(file).toString().replace('\\','/').replaceFirst("\\.yml$","");String name=directory.equals("templates")?defaultName:node.text("id",defaultName);
                if(!name.matches("[a-z0-9][a-z0-9_/-]*")||result.putIfAbsent(name,node)!=null)throw new IllegalArgumentException(file+": invalid or duplicate id "+name);
            }
        }
        return result;
    }
    public static Node read(Path path) throws IOException {
        return read(path, '.');
    }
    public static Node readLiteralKeys(Path path) throws IOException {
        return read(path, '\u001f');
    }
    private static Node read(Path path,char separator) throws IOException {
        if(!Files.isRegularFile(path))throw new FileNotFoundException(path.toString());
        YamlConfiguration yaml=new YamlConfiguration();
        yaml.options().pathSeparator(separator);
        try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){yaml.load(reader);}
        catch(Exception error){throw new IOException(path+": "+error.getMessage(),error);}
        return Node.of(yaml);
    }
}
