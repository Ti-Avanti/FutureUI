#version 330
#extension GL_ARB_separate_shader_objects : require

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

layout(location = 1) in vec2 fuiQuad;
layout(location = 2) in vec2 fuiGuiPosition;
layout(location = 3) flat in vec2 fuiViewport;
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
