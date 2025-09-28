#version 330 core

in vec3 inPosition;
in vec2 inTexCoord;

out vec2 texCoord;

uniform mat4 g_WorldViewProjectionMatrix;

void main() {
    texCoord = inTexCoord;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
