package net.limitmedia.pong3d.engine;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;

public final class Draw {
    private Draw() {
    }

    public static void rect(float x, float y, float width, float height, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
    }

    public static void circle(float cx, float cy, float radius, float r, float g, float b, float a, int segments) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            float px = (float) (cx + Math.cos(angle) * radius);
            float py = (float) (cy + Math.sin(angle) * radius);
            GL11.glVertex2f(px, py);
        }
        GL11.glEnd();
    }

    public static void text(String text, float x, float y, float scale, float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ByteBuffer buffer = BufferUtils.createByteBuffer(16 * text.length());
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0f);
        GL11.glScalef(scale, scale, 1f);
        int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, buffer);
        GL11.glColor4f(r, g, b, a);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(2, GL11.GL_FLOAT, 16, buffer);
        GL11.glDrawArrays(GL11.GL_QUADS, 0, quads * 4);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glPopMatrix();
    }
}
