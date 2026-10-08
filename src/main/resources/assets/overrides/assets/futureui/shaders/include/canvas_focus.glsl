// FutureUI 画布的原生焦点边线过滤；不绘制背景，不处理按钮/字体贴图。
// 原生正文外宽 = 菜单 layout.width + 32。修改画布宽度时同步此表。
const float FUI_CANVAS_BODY_WIDTHS[8] = float[8](284.0, 482.0, 572.0, 590.0, 608.0, 626.0, 662.0, 698.0);

bool fui_near(float a, float b) { return abs(a - b) < 0.15; }
bool fui_row(float value) { return abs(value - 9.0 * round(value / 9.0)) < 0.15; }

bool fui_canvas_focus(vec4 color, vec2 uv, vec2 position, vec2 viewport) {
    viewport = ceil(viewport - vec2(0.001));
    if (any(lessThan(color, vec4(0.999)))) return false;
    vec2 du = dFdx(uv), dv = dFdy(uv);
    vec2 dx = dFdx(position), dy = dFdy(position);
    float determinant = du.x * dv.y - du.y * dv.x;
    if (abs(determinant) < 1.0e-10) return false;
    vec2 u = (dx * dv.y - dy * du.y) / determinant;
    vec2 v = (dy * du.x - dx * dv.x) / determinant;
    vec2 first = position - uv.x * u - uv.y * v;
    vec2 low = first + min(u, vec2(0.0)) + min(v, vec2(0.0));
    vec2 size = abs(u) + abs(v);
    for (int i = 0; i < 8; i++) {
        float width = FUI_CANVAS_BODY_WIDTHS[i];
        float left = max(10.0, floor((viewport.x - width) * 0.5));
        // 只识别已登记宽度、居中位置和原生一像素边线；窗口高度、页脚和滚动不改变这些特征。
        if (fui_near(size.x, width) && fui_near(size.y, 1.0) && fui_near(low.x, left)) {
            return true;
        }
        if (fui_near(size.x, 1.0) && size.y >= 33.0 && fui_row(size.y - 6.0)
            && (fui_near(low.x, left) || fui_near(low.x, left + width - 1.0))) {
            return true;
        }
    }
    return false;
}
