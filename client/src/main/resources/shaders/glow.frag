#version 330 core

in vec2 texCoord;

uniform sampler2D m_Texture;
uniform vec4 m_GlowColor;

out vec4 fragColor;

void main() {
    vec4 base = texture(m_Texture, texCoord);
    float glowStrength = clamp(base.a, 0.0, 1.0);
    fragColor = vec4(mix(base.rgb, m_GlowColor.rgb, glowStrength), glowStrength);
}
