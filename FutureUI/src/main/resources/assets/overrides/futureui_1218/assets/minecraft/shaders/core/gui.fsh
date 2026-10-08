#version 150

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
    float LineWidth;
};

in vec4 vertexColor;

out vec4 fragColor;

in vec2 fuiQuad;
in vec2 fuiGuiPosition;
flat in vec2 fuiViewport;
#moj_import <futureui:canvas_focus.glsl>


void main() {
    vec4 color = vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    color *= ColorModulator;
    if (fui_canvas_focus(color, fuiQuad, fuiGuiPosition, fuiViewport)) discard;
    fragColor = color;
}
