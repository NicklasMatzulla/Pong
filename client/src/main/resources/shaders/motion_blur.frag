#version 330 core

in vec2 texCoord;

uniform sampler2D m_Texture;
uniform sampler2D m_LastFrame;
uniform float m_BlurScale;

out vec4 fragColor;

void main() {
    vec4 current = texture(m_Texture, texCoord);
    vec4 last = texture(m_LastFrame, texCoord);
    fragColor = mix(current, last, clamp(m_BlurScale, 0.0, 0.85));
}
