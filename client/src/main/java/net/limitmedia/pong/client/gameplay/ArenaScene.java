package net.limitmedia.pong.client.gameplay;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.debug.WireBox;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Line;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;

/**
 * Builds and animates the 3D arena, keeping the rendering code separate from
 * the application lifecycle.
 */
public final class ArenaScene {
    private final ArenaDimensions dimensions;
    private final boolean enableBallTrail;

    private Node environmentRoot;
    private Node ballNode;
    private Line ballTrailMesh;
    private Geometry leftPaddle;
    private Geometry rightPaddle;
    private Geometry auroraRing;
    private Material auroraMaterial;
    private final Vector3f lastBallPosition = new Vector3f();
    private float environmentTimer;

    public ArenaScene(ArenaDimensions dimensions, boolean enableBallTrail) {
        this.dimensions = dimensions;
        this.enableBallTrail = enableBallTrail;
    }

    public void attach(Node rootNode, AssetManager assets) {
        environmentRoot = new Node("ArenaEnvironment");
        rootNode.attachChild(environmentRoot);

        Geometry table = new Geometry("ArenaFloor", new Box(dimensions.width() / 2f, 0.15f, dimensions.depth() / 2f));
        Material floorMat = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        floorMat.setBoolean("UseMaterialColors", true);
        floorMat.setColor("Diffuse", new ColorRGBA(0.18f, 0.2f, 0.24f, 1f));
        floorMat.setColor("Ambient", new ColorRGBA(0.08f, 0.09f, 0.12f, 1f));
        table.setMaterial(floorMat);
        table.setShadowMode(RenderQueue.ShadowMode.Receive);
        environmentRoot.attachChild(table);

        WireBox bounds = new WireBox(dimensions.width() / 2f, dimensions.height() / 2f, dimensions.depth() / 2f);
        Geometry boundsGeom = new Geometry("Bounds", bounds);
        Material boundsMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        boundsMat.setColor("Color", new ColorRGBA(0.25f, 0.3f, 0.35f, 0.55f));
        boundsMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        boundsGeom.setMaterial(boundsMat);
        boundsGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        environmentRoot.attachChild(boundsGeom);

        Torus ring = new Torus(64, 128, 0.35f, dimensions.width());
        auroraRing = new Geometry("Aurora", ring);
        auroraMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        auroraMaterial.setColor("Color", new ColorRGBA(0.3f, 0.5f, 0.6f, 0.25f));
        auroraMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        auroraRing.setMaterial(auroraMaterial);
        auroraRing.rotate(FastMath.HALF_PI, 0, 0);
        auroraRing.setQueueBucket(RenderQueue.Bucket.Transparent);
        auroraRing.move(0, dimensions.height() * 0.35f, 0);
        environmentRoot.attachChild(auroraRing);

        Geometry ballGeometry = buildBallGeometry(assets);
        ballNode = new Node("BallNode");
        ballNode.attachChild(ballGeometry);
        if (enableBallTrail) {
            Geometry trailGeometry = buildBallTrail(assets);
            ballTrailMesh = (Line) trailGeometry.getMesh();
            ballNode.attachChild(trailGeometry);
        }
        rootNode.attachChild(ballNode);

        leftPaddle = buildPaddleGeometry(assets, new ColorRGBA(0.4f, 0.72f, 0.92f, 1f));
        rightPaddle = buildPaddleGeometry(assets, new ColorRGBA(0.92f, 0.5f, 0.8f, 1f));
        rootNode.attachChild(leftPaddle);
        rootNode.attachChild(rightPaddle);
    }

    public void reset(BallState ball, PaddleState left, PaddleState right) {
        sync(ball, left, right);
        lastBallPosition.set(ball.position());
    }

    public void sync(BallState ball, PaddleState left, PaddleState right) {
        ballNode.setLocalTranslation(ball.position());
        if (ballTrailMesh != null) {
            ballTrailMesh.updatePoints(lastBallPosition, ball.position());
            lastBallPosition.set(ball.position());
        }
        leftPaddle.setLocalTranslation(left.position());
        rightPaddle.setLocalTranslation(right.position());
    }

    public void animate(float tpf) {
        environmentTimer += tpf;
        if (auroraMaterial != null) {
            float hue = (FastMath.sin(environmentTimer * 0.3f) * 0.5f) + 0.5f;
            ColorRGBA color = fromHsv(hue, 0.45f, 0.75f, 0.28f);
            auroraMaterial.setColor("Color", color);
        }
        if (auroraRing != null) {
            auroraRing.rotate(0, tpf * 0.25f, 0);
        }
    }

    public void dispose() {
        if (environmentRoot != null) {
            environmentRoot.removeFromParent();
        }
        if (ballNode != null) {
            ballNode.removeFromParent();
        }
        if (leftPaddle != null) {
            leftPaddle.removeFromParent();
        }
        if (rightPaddle != null) {
            rightPaddle.removeFromParent();
        }
    }

    private Geometry buildBallGeometry(AssetManager assets) {
        Geometry geometry = new Geometry("Ball", new Sphere(32, 32, 0.6f));
        Material mat = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", new ColorRGBA(0.82f, 0.86f, 1f, 1f));
        mat.setColor("Ambient", new ColorRGBA(0.24f, 0.32f, 0.45f, 1f));
        mat.setColor("GlowColor", new ColorRGBA(0.35f, 0.55f, 1f, 1f));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(mat);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.move(0, 0.6f, 0);
        return geometry;
    }

    private Geometry buildBallTrail(AssetManager assets) {
        Line streak = new Line(Vector3f.ZERO.clone(), Vector3f.ZERO.clone());
        streak.setLineWidth(2f);
        Geometry geometry = new Geometry("BallTrail", streak);
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0.35f, 0.6f, 1f, 0.45f));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geometry;
    }

    private Geometry buildPaddleGeometry(AssetManager assets, ColorRGBA tint) {
        Geometry geometry = new Geometry("Paddle", new Box(1.2f, 2.4f, 0.25f));
        Material mat = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", tint);
        mat.setColor("Ambient", tint.mult(0.55f));
        mat.setColor("Specular", ColorRGBA.White.mult(0.35f));
        mat.setFloat("Shininess", 16f);
        geometry.setMaterial(mat);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return geometry;
    }

    private static ColorRGBA fromHsv(float h, float s, float v, float alpha) {
        h = (h % 1f + 1f) % 1f;
        s = FastMath.clamp(s, 0f, 1f);
        v = FastMath.clamp(v, 0f, 1f);
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6f) % 2 - 1));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 1f / 6f) {
            r = c;
            g = x;
            b = 0;
        } else if (h < 2f / 6f) {
            r = x;
            g = c;
            b = 0;
        } else if (h < 3f / 6f) {
            r = 0;
            g = c;
            b = x;
        } else if (h < 4f / 6f) {
            r = 0;
            g = x;
            b = c;
        } else if (h < 5f / 6f) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }
        return new ColorRGBA(r + m, g + m, b + m, alpha);
    }
}
