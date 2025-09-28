package net.limitmedia.pong3d.engine;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public final class Scene3D {
    private static final ThreadLocal<FloatBuffer> MATRIX_BUFFER = ThreadLocal.withInitial(() -> BufferUtils.createFloatBuffer(16));

    private Scene3D() {
    }

    public static void applyPerspective(float fovDegrees, float aspect, float near, float far) {
        float fovRad = (float) Math.toRadians(fovDegrees);
        float f = (float) (1.0 / Math.tan(fovRad / 2.0));
        float rangeInv = 1.0f / (near - far);

        FloatBuffer buffer = MATRIX_BUFFER.get();
        buffer.clear();
        buffer.put(f / aspect).put(0f).put(0f).put(0f);
        buffer.put(0f).put(f).put(0f).put(0f);
        buffer.put(0f).put(0f).put((far + near) * rangeInv).put(-1f);
        buffer.put(0f).put(0f).put(2f * far * near * rangeInv).put(0f);
        buffer.flip();
        GL11.glMultMatrixf(buffer);
    }

    public static CameraFrame lookAt(float eyeX, float eyeY, float eyeZ,
                                      float centerX, float centerY, float centerZ,
                                      float upX, float upY, float upZ) {
        float fx = centerX - eyeX;
        float fy = centerY - eyeY;
        float fz = centerZ - eyeZ;
        float fLength = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLength == 0f) {
            fLength = 1f;
        }
        fx /= fLength;
        fy /= fLength;
        fz /= fLength;

        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float sLength = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLength == 0f) {
            sLength = 1f;
        }
        sx /= sLength;
        sy /= sLength;
        sz /= sLength;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        FloatBuffer buffer = MATRIX_BUFFER.get();
        buffer.clear();
        buffer.put(sx).put(ux).put(-fx).put(0f);
        buffer.put(sy).put(uy).put(-fy).put(0f);
        buffer.put(sz).put(uz).put(-fz).put(0f);
        buffer.put(-(sx * eyeX + sy * eyeY + sz * eyeZ))
              .put(-(ux * eyeX + uy * eyeY + uz * eyeZ))
              .put(fx * eyeX + fy * eyeY + fz * eyeZ)
              .put(1f);
        buffer.flip();
        GL11.glMultMatrixf(buffer);

        return new CameraFrame(sx, sy, sz, ux, uy, uz, -fx, -fy, -fz);
    }

    public static void drawBox(float centerX, float centerY, float centerZ,
                               float sizeX, float sizeY, float sizeZ,
                               float r, float g, float b, float a) {
        float hx = sizeX * 0.5f;
        float hy = sizeY * 0.5f;
        float hz = sizeZ * 0.5f;

        float sideR = clamp(r * 0.85f + 0.05f);
        float sideG = clamp(g * 0.85f + 0.05f);
        float sideB = clamp(b * 0.85f + 0.05f);
        float topR = clamp(r * 1.1f + 0.08f);
        float topG = clamp(g * 1.1f + 0.08f);
        float topB = clamp(b * 1.1f + 0.08f);

        GL11.glBegin(GL11.GL_QUADS);
        // Front
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3f(centerX - hx, centerY - hy, centerZ + hz);
        GL11.glVertex3f(centerX + hx, centerY - hy, centerZ + hz);
        GL11.glVertex3f(centerX + hx, centerY + hy, centerZ + hz);
        GL11.glVertex3f(centerX - hx, centerY + hy, centerZ + hz);

        // Back
        GL11.glColor4f(sideR, sideG, sideB, a);
        GL11.glVertex3f(centerX + hx, centerY - hy, centerZ - hz);
        GL11.glVertex3f(centerX - hx, centerY - hy, centerZ - hz);
        GL11.glVertex3f(centerX - hx, centerY + hy, centerZ - hz);
        GL11.glVertex3f(centerX + hx, centerY + hy, centerZ - hz);

        // Left
        GL11.glColor4f(sideR, sideG, sideB, a);
        GL11.glVertex3f(centerX - hx, centerY - hy, centerZ - hz);
        GL11.glVertex3f(centerX - hx, centerY - hy, centerZ + hz);
        GL11.glVertex3f(centerX - hx, centerY + hy, centerZ + hz);
        GL11.glVertex3f(centerX - hx, centerY + hy, centerZ - hz);

        // Right
        GL11.glColor4f(sideR, sideG, sideB, a);
        GL11.glVertex3f(centerX + hx, centerY - hy, centerZ + hz);
        GL11.glVertex3f(centerX + hx, centerY - hy, centerZ - hz);
        GL11.glVertex3f(centerX + hx, centerY + hy, centerZ - hz);
        GL11.glVertex3f(centerX + hx, centerY + hy, centerZ + hz);

        // Top
        GL11.glColor4f(topR, topG, topB, a);
        GL11.glVertex3f(centerX - hx, centerY + hy, centerZ + hz);
        GL11.glVertex3f(centerX + hx, centerY + hy, centerZ + hz);
        GL11.glVertex3f(centerX + hx, centerY + hy, centerZ - hz);
        GL11.glVertex3f(centerX - hx, centerY + hy, centerZ - hz);

        // Bottom
        GL11.glColor4f(sideR * 0.8f, sideG * 0.8f, sideB * 0.8f, a);
        GL11.glVertex3f(centerX - hx, centerY - hy, centerZ - hz);
        GL11.glVertex3f(centerX + hx, centerY - hy, centerZ - hz);
        GL11.glVertex3f(centerX + hx, centerY - hy, centerZ + hz);
        GL11.glVertex3f(centerX - hx, centerY - hy, centerZ + hz);
        GL11.glEnd();
    }

    public static void drawPlane(float centerX, float centerY, float centerZ,
                                 float sizeX, float sizeZ,
                                 float r1, float g1, float b1, float r2, float g2, float b2, float a) {
        float hx = sizeX * 0.5f;
        float hz = sizeZ * 0.5f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r1, g1, b1, a);
        GL11.glVertex3f(centerX - hx, centerY, centerZ - hz);
        GL11.glVertex3f(centerX + hx, centerY, centerZ - hz);
        GL11.glColor4f(r2, g2, b2, a);
        GL11.glVertex3f(centerX + hx, centerY, centerZ + hz);
        GL11.glVertex3f(centerX - hx, centerY, centerZ + hz);
        GL11.glEnd();
    }

    public static void drawBillboard(float centerX, float centerY, float centerZ,
                                     float size, CameraFrame frame,
                                     float r, float g, float b, float a) {
        float hx = size * 0.5f;
        float[] right = frame.right;
        float[] up = frame.up;

        float rx = right[0] * hx;
        float ry = right[1] * hx;
        float rz = right[2] * hx;
        float ux = up[0] * hx;
        float uy = up[1] * hx;
        float uz = up[2] * hx;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3f(centerX - rx - ux, centerY - ry - uy, centerZ - rz - uz);
        GL11.glVertex3f(centerX + rx - ux, centerY + ry - uy, centerZ + rz - uz);
        GL11.glVertex3f(centerX + rx + ux, centerY + ry + uy, centerZ + rz + uz);
        GL11.glVertex3f(centerX - rx + ux, centerY - ry + uy, centerZ - rz + uz);
        GL11.glEnd();
    }

    public static void drawSphere(float centerX, float centerY, float centerZ,
                                  float radius, int stacks, int slices,
                                  float r, float g, float b, float a) {
        for (int i = 0; i < stacks; i++) {
            float phi0 = (float) Math.PI * i / stacks;
            float phi1 = (float) Math.PI * (i + 1) / stacks;
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int j = 0; j <= slices; j++) {
                float theta = (float) (2.0 * Math.PI * j / slices);
                float x0 = (float) (Math.sin(phi0) * Math.cos(theta));
                float y0 = (float) Math.cos(phi0);
                float z0 = (float) (Math.sin(phi0) * Math.sin(theta));
                float x1 = (float) (Math.sin(phi1) * Math.cos(theta));
                float y1 = (float) Math.cos(phi1);
                float z1 = (float) (Math.sin(phi1) * Math.sin(theta));

                GL11.glColor4f(r * (0.75f + 0.25f * y0), g * (0.75f + 0.25f * y0), b * (0.8f + 0.2f * y0), a);
                GL11.glVertex3f(centerX + radius * x0, centerY + radius * y0, centerZ + radius * z0);
                GL11.glColor4f(r * (0.75f + 0.25f * y1), g * (0.75f + 0.25f * y1), b * (0.8f + 0.2f * y1), a);
                GL11.glVertex3f(centerX + radius * x1, centerY + radius * y1, centerZ + radius * z1);
            }
            GL11.glEnd();
        }
    }

    private static float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    public static final class CameraFrame {
        public final float[] right = new float[3];
        public final float[] up = new float[3];
        public final float[] forward = new float[3];

        private CameraFrame(float rx, float ry, float rz,
                             float ux, float uy, float uz,
                             float fx, float fy, float fz) {
            this.right[0] = rx;
            this.right[1] = ry;
            this.right[2] = rz;
            this.up[0] = ux;
            this.up[1] = uy;
            this.up[2] = uz;
            this.forward[0] = fx;
            this.forward[1] = fy;
            this.forward[2] = fz;
        }
    }
}
