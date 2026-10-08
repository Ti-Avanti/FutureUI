package gg.fotia.futureui.asset;

import com.google.gson.*;
import gg.fotia.futureui.config.Node;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** 从 FutureUI 外置资源独立生成完整目录与 ZIP，分发后端另行发布。 */
public final class ResourcePackCompiler {
    public record Result(Path path,String hash,Map<String,GlyphRegistry.Glyph> glyphs,Map<Integer,Integer> metrics,PackManifest manifest,Path distributedZip,Map<String,ViewportRegions.Region> viewports){}
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    public Result compile(Path pluginRoot,Node configuration) throws Exception {
        Path assets=pluginRoot.resolve("assets");Path metadata=ResourcePackFiles.metadata(assets);
        Path staging=pluginRoot.resolve("generated/staging-"+UUID.randomUUID());Files.createDirectories(staging);
        try {
        Map<String,Integer> allocation=new GlyphAllocation(loadAllocation(pluginRoot.resolve("generated/glyph-map.json")));
        Map<String,GlyphRegistry.Glyph> glyphs=new LinkedHashMap<>();List<Map<String,Object>> providers=new ArrayList<>();
        List<Integer> imageSizes=MenuThemeAssets.imageSizes(configuration);
        for(var entry:configuration.child("images").values().entrySet()){
            String id=entry.getKey();if(!id.matches("[a-z0-9_/-]+"))throw new IllegalArgumentException("Invalid image id "+id);
            Node image=Node.of(entry.getValue());
            if(image.has("vanilla")){
                String texture=image.text("vanilla","");if(!texture.matches("minecraft:[a-z0-9_/-]+\\.png"))throw new IllegalArgumentException("Invalid vanilla texture "+texture);
                int code=GlyphAllocation.code(allocation,id);
                int height=image.integer("height",16);String character=Character.toString(code);providers.add(Map.of("type","bitmap","file",texture,"height",height,"ascent",image.integer("ascent",14),"chars",List.of(character)));glyphs.put(id,new GlyphRegistry.Glyph(character,height,height));
                List<Integer> rowWidths=image.list("row-widths").size()==16?image.list("row-widths").stream().map(v->Integer.parseInt(v.toString())).toList():Collections.nCopies(16,image.integer("pixel-width",16));
                CanvasImageCompiler.compile(id,texture,16,rowWidths,imageSizes,allocation,glyphs,providers);continue;
            }
            Path source=pluginRoot.resolve("assets/textures").resolve(image.text("file",id+".png")).normalize();
            if(!source.startsWith(pluginRoot.resolve("assets/textures")))throw new IllegalArgumentException("Image escapes assets directory");
            Path target=staging.resolve("assets/futureui/textures/font/"+id+".png");Files.createDirectories(target.getParent());
            BufferedImage bitmap;
            bitmap=ImageIO.read(source.toFile());
            if(bitmap==null)throw new IllegalArgumentException("Invalid PNG: "+source);
            bitmap=CanvasTexture.normalize(bitmap,image.integer("canvas-height",0));
            if(image.integer("canvas-height",0)==0)Files.copy(source,target);else ImageIO.write(bitmap,"PNG",target.toFile());
            int height=image.integer("height",24),ascent=image.integer("ascent",height-2);if(height<1||height>256||ascent>height)throw new IllegalArgumentException("Invalid image metrics "+id);
            int code=GlyphAllocation.code(allocation,id);
            String character=Character.toString(code);
            providers.add(Map.of("type","bitmap","file","futureui:font/"+id+".png","height",height,"ascent",ascent,"chars",List.of(character)));
            glyphs.put(id,new GlyphRegistry.Glyph(character,(int)Math.ceil((double)bitmap.getWidth()*height/bitmap.getHeight()),height));
            List<Integer> rowWidths=new ArrayList<>();for(int y=0;y<bitmap.getHeight();y++){int right=0;for(int x=0;x<bitmap.getWidth();x++)if((bitmap.getRGB(x,y)>>>24)!=0)right=x+1;rowWidths.add(right);}
            CanvasImageCompiler.compile(id,"futureui:font/"+id+".png",bitmap.getHeight(),rowWidths,imageSizes,allocation,glyphs,providers);
        }
        CanvasAtlasCompiler.generate(staging,configuration.child("canvas"),allocation,glyphs,providers);
        RasterAtlasCompiler.generate(staging);
        CanvasInteractionCompiler.generate(staging,configuration.child("canvas"),allocation,glyphs,providers);
        DialogBackgroundCompiler.generate(staging,configuration.child("native-widgets"),allocation,glyphs,providers);
        MenuThemeAssets.generate(staging,configuration.child("themes"),allocation,glyphs,providers);
        var viewports=ViewportRegions.parse(configuration.child("viewports"));
        ViewportAtlasCompiler.markers(staging,viewports,allocation,glyphs,providers);
        Path font=staging.resolve("assets/futureui/font/images.json");Files.createDirectories(font.getParent());Files.writeString(font,gson.toJson(Map.of("providers",providers)),StandardCharsets.UTF_8);
        NativeThemeCompiler.generate(staging,configuration.child("native-widgets"));
        Node canvas=configuration.child("canvas"),interaction=canvas.child("interaction");
        PackProfiles versions=PackProfiles.parse(configuration.child("versions"));
        boolean shaders=canvas.child("background").bool("enabled",true)||interaction.bool("hover",true)||interaction.bool("cover-focus-outline",true)||configuration.child("native-widgets").child("background").bool("enabled",true);
        VersionedResourceCompiler.compile(assets,staging,versions,shaders||!viewports.isEmpty());
        ResourcePackFiles.overrides(assets,staging,versions.directories());
        ViewportAtlasCompiler.shaders(staging,viewports);
        Path resources=pluginRoot.resolve("generated/resourcepack");
        String hash=hash(staging,metadata,versions.source());Path manifest=pluginRoot.resolve("generated/managed-files.json");
        PackManifest receipt=PackManifest.write(staging,hash,versions);
        Path archive=ResourcePackArchive.write(pluginRoot,staging,receipt);
        List<String> managed=ResourcePackFiles.publish(staging,resources,manifest);
        Files.writeString(pluginRoot.resolve("generated/glyph-map.json"),gson.toJson(allocation),StandardCharsets.UTF_8);
        Files.writeString(manifest,gson.toJson(managed),StandardCharsets.UTF_8);Files.writeString(pluginRoot.resolve("generated/content.sha256"),hash,StandardCharsets.UTF_8);
        Map<Integer,Integer> metrics=new HashMap<>();Path metricFile=pluginRoot.resolve("assets/text-metrics.json");if(Files.exists(metricFile))JsonParser.parseString(Files.readString(metricFile,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->metrics.put(Integer.parseInt(e.getKey()),e.getValue().getAsInt()));
        return new Result(resources,hash,Map.copyOf(glyphs),Map.copyOf(metrics),receipt,archive,viewports);
        } finally {try(var paths=Files.walk(staging)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    private Map<String,Integer> loadAllocation(Path file)throws Exception{Map<String,Integer> result=new LinkedHashMap<>();if(Files.exists(file))JsonParser.parseString(Files.readString(file)).getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsInt()));return result;}
    private String hash(Path root,Path metadata,Node versions)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(Files.readAllBytes(metadata));digest.update(gson.toJson(versions.plain()).getBytes(StandardCharsets.UTF_8));try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).sorted().toList()){digest.update(root.relativize(file).toString().getBytes(StandardCharsets.UTF_8));digest.update(Files.readAllBytes(file));}}return HexFormat.of().formatHex(digest.digest());}
}
