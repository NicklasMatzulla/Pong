package net.limitmedia.pong.client.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import java.io.Closeable;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.limitmedia.pong.core.match.MatchState;
import net.limitmedia.pong.core.net.LagCompensator;
import net.limitmedia.pong.core.net.NetworkFrame;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientNetworkManager implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(ClientNetworkManager.class);

    private final LagCompensator compensator;
    private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
    private ChannelFuture channel;
    private final AtomicReference<MatchState.Score> score = new AtomicReference<>(new MatchState.Score(0, 0));
    private float elapsed;

    public ClientNetworkManager(LagCompensator compensator) {
        this.compensator = compensator;
    }

    public void connect(String host, int port) {
        Objects.requireNonNull(host, "host");
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ObjectEncoder());
                        ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                        ch.pipeline().addLast(new ClientHandler());
                    }
                });
        if (channel != null && channel.channel().isOpen()) {
            channel.channel().close();
        }
        channel = bootstrap.connect(host, port);
        channel.addListener(future -> {
            if (future.isSuccess()) {
                LOG.info("Connected to {}:{}", host, port);
            } else {
                LOG.error("Failed to connect to {}:{}", host, port, future.cause());
            }
        });
    }

    public void update(float tpf, BallState ball, PaddleState left, PaddleState right) {
        elapsed += tpf;
        if (!isConnected()) {
            // offline simulation
            return;
        }
        NetworkFrame frame = new NetworkFrame(null, 0L, Instant.now(),
                vector(ball.position()), vector(ball.velocity()),
                vector(left.position()), vector(right.position()),
                score.get(), compensator.getAverage());
        channel.channel().writeAndFlush(frame);
    }

    public MatchState.Score getScore() {
        return score.get();
    }

    public float getElapsed() {
        return elapsed;
    }

    public float getPing() {
        return compensator.getAverage();
    }

    public boolean isConnected() {
        return channel != null && channel.isSuccess() && channel.channel().isActive();
    }

    private static float[] vector(com.jme3.math.Vector3f v) {
        return new float[] { v.x, v.y, v.z };
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.channel().close();
        }
        eventLoop.shutdownGracefully(100, 300, TimeUnit.MILLISECONDS);
    }

    private final class ClientHandler extends SimpleChannelInboundHandler<NetworkFrame> {
        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, NetworkFrame msg) {
            compensator.record(msg.serverTime(), Instant.now());
            score.set(msg.score());
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            LOG.error("Network error", cause);
            ctx.close();
        }
    }
}
