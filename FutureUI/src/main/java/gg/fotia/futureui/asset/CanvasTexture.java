package gg.fotia.futureui.asset;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** 按配置为生成包对齐像素图片；不修改管理员提供的源文件。 */
final class CanvasTexture {
    private CanvasTexture() {}
    static BufferedImage normalize(BufferedImage source, int height) {
        if (height == 0) return source;
        if (height < 16 || height > 240) throw new IllegalArgumentException("canvas-height must be 16..240");
        int width = Math.max(1, (int)Math.round((double)source.getWidth() * height / source.getHeight()));
        // 位图字形必须装入客户端字体图集；宽幅原图不可直接按数百像素的行写入。
        if (width > 240) throw new IllegalArgumentException("Normalized canvas texture must fit within 240 pixels; reduce canvas-height");
        var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return result;
    }
}
