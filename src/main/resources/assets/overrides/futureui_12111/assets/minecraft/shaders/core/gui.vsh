#version 330

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

out vec2 fuiQuad;
out vec2 fuiGuiPosition;
flat out vec2 fuiViewport;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    int fuiVertex = gl_VertexID % 4;
    fuiQuad = vec2(fuiVertex >= 2 ? 1.0 : 0.0, fuiVertex == 1 || fuiVertex == 2 ? 1.0 : 0.0);
    fuiGuiPosition = (ModelViewMat * vec4(Position, 1.0)).xy;
    fuiViewport = abs(vec2(2.0 / ProjMat[0][0], 2.0 / ProjMat[1][1]));

}
