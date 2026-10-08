// FutureUI marker glyph positioning. Ordinary glyphs keep the vanilla pipeline.
void fui_position() {
    fuiHover = 0;
    fuiOrigin = ivec2(0);
    fuiLocal = vec2(0.0);
    ivec3 rgb = ivec3(round(Color.rgb * 255.0));
    int data = (rgb.r << 16) | (rgb.g << 8) | rgb.b;
    int vertex = FUI_VERTEX_ID % 4;
    vec2 corner = vec2(vertex >= 2 ? 1.0 : 0.0, vertex == 1 || vertex == 2 ? 1.0 : 0.0);
    if ((data & 0xffff00) == 0xfffe00) {
        float frameHeight = rgb.b == 4 ? 17.0 : rgb.b == 2 ? 9.0 : 13.0;
        ivec2 base = ivec2(round(UV0 * textureSize(Sampler0, 0))) - ivec2(corner * vec2(2.0, frameHeight));
        vec4 tag = texelFetch(Sampler0, base, 0);
        if (all(equal(ivec3(round(tag.rgb * 255.0)), ivec3(250, 23, 233))) && tag.a < 0.01) {
            gl_Position = ProjMat * ModelViewMat * vec4(Position + vec3(corner.x * 9.0, 0.0, 0.0), 1.0);
            fuiHover = 2;
            fuiOrigin = base;
            fuiLocal = corner * vec2(11.0, frameHeight);
        }
        return;
    }
    if ((data & 0x800000) == 0) return;
    ivec2 origin = ivec2(round(UV0 * textureSize(Sampler0, 0))) - ivec2(corner * 16.0);
    ivec3 marker = ivec3(round(texelFetch(Sampler0, origin, 0).rgb * 255.0));
    if (any(notEqual(marker, ivec3(250, 23, 233)))) return;

    ivec3 size = ivec3(round(texelFetch(Sampler0, origin + ivec2(1, 1), 0).rgb * 255.0));
    float width = float((data >> 11) & 2047);
    float height = float(data & 2047);
    // 原生布局使用向上取整后的 GUI 尺寸；奇数窗口像素不能保留半像素视口。
    vec2 viewport = ceil(abs(vec2(2.0 / (ProjMat[0][0] * ModelViewMat[0][0]), 2.0 / (ProjMat[1][1] * ModelViewMat[1][1]))) - vec2(0.001));
    if (size.g == 128) {
        width = min(width, viewport.x - 8.0);
        // 紧凑输入框围绕原生内容区绘制；高度为零时保留整页底板。
        bool compact = height > 0.0;
        float panelHeight = compact ? min(height, viewport.y - 8.0) : viewport.y - 8.0;
        float panelTop = compact ? max(4.0, min(54.0, viewport.y - panelHeight - 4.0)) : 4.0;
        vec2 point = vec2((viewport.x - width) * 0.5, panelTop) + corner * vec2(width, panelHeight);
        gl_Position = ProjMat * ModelViewMat * vec4(point, Position.z, 1.0);
        fuiHover = 3;
        fuiOrigin = origin;
        return;
    }
    if (Position.x > -1.0e10) return;
    int position = int(round(-Position.x / 65536.0)) - 0x400000;
    vec2 offset = vec2(position >> 11, position & 2047);
    // Notice: 33 像素页脚；无退出栏的 DialogList: 5 像素页脚和 10 像素空行。
    bool noExitButton = (data & 0x400000) != 0;
    float bodyHeight = height + (noExitButton ? 18.0 : 8.0);
    float footerHeight = noExitButton ? 5.0 : 33.0;
    // 内容进入原生滚动区后无法获得滚动偏移，避免画出错位高亮。
    float canvasLeft = max(26.0, floor((viewport.x - width) * 0.5));
    if (bodyHeight > viewport.y - 33.0 - footerHeight || canvasLeft + width > viewport.x) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    vec2 anchor = vec2(canvasLeft, min(67.0, viewport.y - footerHeight - bodyHeight + 4.0));
    vec2 point = anchor + offset + corner * vec2(size.r, size.b);
    gl_Position = ProjMat * ModelViewMat * vec4(point, Position.z, 1.0);
    fuiHover = 1;
    fuiOrigin = origin;
    fuiLocal = corner * vec2(size.r, size.b);
}
