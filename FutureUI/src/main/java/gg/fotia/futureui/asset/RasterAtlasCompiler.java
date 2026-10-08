package gg.fotia.futureui.asset;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** 固定白色像素片由文本颜色着色；独立字体不占用菜单图片的字形编号。 */
public final class RasterAtlasCompiler {
    private RasterAtlasCompiler() {}
    public static int code(int width, int offset, int height) {
        return 0xe000 + Integer.numberOfTrailingZeros(width) * 45 + offset * (19 - offset) / 2 + height - 1;
    }
    public static String space(int advance) {
        StringBuilder value=new StringBuilder();
        while(advance!=0){int part=Math.max(-1024,Math.min(1024,advance));value.append((char)(0xec00+part));advance-=part;}
        return value.toString();
    }
    public static void generate(Path root) throws Exception {
        int count = 8 * 45, columns = 16, rows = (count + columns - 1) / columns;
        BufferedImage atlas = new BufferedImage(columns * 128, rows * 9, BufferedImage.TYPE_INT_ARGB);
        List<String> chars = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                line.append(index < count ? (char)(0xe000 + index) : '\u0000');
            }
            chars.add(line.toString());
        }
        for (int width = 1; width <= 128; width *= 2) for (int offset = 0; offset < 9; offset++)
            for (int height = 1; height <= 9 - offset; height++) {
                int index = code(width, offset, height) - 0xe000, x = index % columns * 128, y = index / columns * 9;
                for (int dy = offset; dy < offset + height; dy++) for (int dx = 0; dx < width; dx++)
                    atlas.setRGB(x + dx, y + dy, 0xffffffff);
            }
        Path texture = root.resolve("assets/futureui/textures/font/raster.png");
        Files.createDirectories(texture.getParent()); ImageIO.write(atlas, "png", texture.toFile());
        Path font = root.resolve("assets/futureui/font/raster.json"); Files.createDirectories(font.getParent());
        Map<String,Integer> spaces=new LinkedHashMap<>();for(int i=-1024;i<=1024;i++)spaces.put(Character.toString(0xec00+i),i);
        Files.writeString(font, new Gson().toJson(Map.of("providers", List.of(Map.of("type","space","advances",spaces),Map.of(
                "type", "bitmap", "file", "futureui:font/raster.png", "height", 9, "ascent", 7, "chars", chars)))), StandardCharsets.UTF_8);
    }
}
