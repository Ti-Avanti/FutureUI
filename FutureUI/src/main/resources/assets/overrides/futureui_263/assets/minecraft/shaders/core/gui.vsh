#version 330
#extension GL_ARB_separate_shader_objects : require

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 vertexColor;

layout(location = 1) out vec2 fuiQuad;
layout(location = 2) out vec2 fuiGuiPosition;
layout(location = 3) flat out vec2 fuiViewport;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    int fuiVertex = gl_VertexIndex % 4;
    fuiQuad = vec2(fuiVertex >= 2 ? 1.0 : 0.0, fuiVertex == 1 || fuiVertex == 2 ? 1.0 : 0.0);
    fuiGuiPosition = (ModelViewMat * vec4(Position, 1.0)).xy;
    fuiViewport = abs(vec2(2.0 / ProjMat[0][0], 2.0 / ProjMat[1][1]));

}
