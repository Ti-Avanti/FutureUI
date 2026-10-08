// Return the marker texture color without lightmap or text tint.
vec4 fui_fragment() {
    if (fuiHover == 3) {
        return texelFetch(Sampler0, fuiOrigin + ivec2(2, 1), 0);
    }
    if (fuiHover == 2) {
        int row = int(round(texelFetch(Sampler0, fuiOrigin + ivec2(0, 1), 0).b * 255.0));
        bool filled = texelFetch(Sampler0, fuiOrigin + ivec2(0, 2), 0).a > 0.1;
        if (!filled && fuiLocal.x < 10.0 && !((row == 1 || row == 4) && fuiLocal.y < 1.0) && !(row == 3 && fuiLocal.y >= 12.0) && !(row == 4 && fuiLocal.y >= 16.0)) discard;
        return texelFetch(Sampler0, fuiOrigin + ivec2(1, 1), 0);
    }
    if (fuiHover == 1) {
        ivec3 data = ivec3(round(texelFetch(Sampler0, fuiOrigin + ivec2(1, 1), 0).rgb * 255.0));
        int flags = data.g;
        bool edge = ((flags & 1) != 0 && fuiLocal.y < 2.0)
            || ((flags & 2) != 0 && fuiLocal.y >= float(data.b - 2))
            || ((flags & 4) != 0 && fuiLocal.x < 2.0)
            || ((flags & 8) != 0 && fuiLocal.x >= float(data.r - 2));
        return texelFetch(Sampler0, fuiOrigin + ivec2(edge ? 3 : 2, 1), 0);
    }
    return vec4(0.0);
}
