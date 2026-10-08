package gg.fotia.futureui.api;

/** 扩展数据源提供的不可变像素快照；渲染前完成 I/O 和模型计算，不含任何客户端代码。 */
public final class RasterImage {
    private final int width, height;
    private final int[] argb;
    public RasterImage(int width, int height, int[] argb) {
        if (width < 1 || height < 1 || width > 192 || height > 192 || argb.length != width * height)
            throw new IllegalArgumentException("Raster dimensions must be 1..192 and match the pixel count");
        this.width = width; this.height = height; this.argb = argb.clone();
    }
    public int width() { return width; }
    public int height() { return height; }
    public int pixel(int x, int y) { return argb[y * width + x]; }
}
