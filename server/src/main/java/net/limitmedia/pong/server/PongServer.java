package net.limitmedia.pong.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.limitmedia.pong.core.net.LagCompensator;
import net.limitmedia.pong.core.net.NetworkFrame;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import net.limitmedia.pong.core.physics.PhysicsEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PongServer implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(PongServer.class);

    private final ServerConfiguration config;
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private final EventExecutorGroup executorGroup = new DefaultEventExecutorGroup(2);
    private final ScheduledExecutorService tickExecutor = Executors.newSingleThreadScheduledExecutor();
    private final CopyOnWriteArrayList<ClientConnection> clients = new CopyOnWriteArrayList<>();

    private MatchSession session;
    private Channel serverChannel;

    public PongServer(ServerConfiguration config) {
        this.config = config;
    }

    public void start() throws InterruptedException {
        PhysicsEngine physics = new PhysicsEngine(ArenaDimensions.COMPETITIVE, 0.92f, 0.96f, 1.6f, event -> {});
        session = new MatchSession(physics);

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(executorGroup,
                                new ObjectEncoder(),
                                new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                new ServerHandler(new ClientConnection(ch)));
                    }
                });

        serverChannel = bootstrap.bind(config.port()).sync().channel();
        LOG.info("Pong server listening on {}", config.port());

        long period = Math.round(1000d / config.tickRate());
        tickExecutor.scheduleAtFixedRate(() -> {
            try {
                session.update(1f / config.tickRate());
                broadcast();
            } catch (Exception ex) {
                LOG.error("Tick failure", ex);
            }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    private void broadcast() {
        for (ClientConnection client : clients) {
            NetworkFrame frame = session.toNetworkFrame(client.compensator().getAverage());
            client.channel().writeAndFlush(frame);
        }
    }

    public void stop() {
        try {
            close();
        } catch (IOException ex) {
            LOG.warn("Error closing server", ex);
        }
    }

    @Override
    public void close() throws IOException {
        tickExecutor.shutdownNow();
        if (serverChannel != null) {
            serverChannel.close();
        }
        for (ClientConnection client : clients) {
            client.channel().close();
        }
        executorGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    private final class ServerHandler extends SimpleChannelInboundHandler<NetworkFrame> {
        private final ClientConnection connection;

        private ServerHandler(ClientConnection connection) {
            this.connection = connection;
        }

        @Override
        public void channelActive(io.netty.channel.ChannelHandlerContext ctx) {
            clients.add(connection);
            LOG.info("Client connected: {}", ctx.channel().remoteAddress());
        }

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, NetworkFrame msg) {
            connection.compensator().record(msg.serverTime(), Instant.now());
        }

        @Override
        public void channelInactive(io.netty.channel.ChannelHandlerContext ctx) {
            clients.remove(connection);
            LOG.info("Client disconnected: {}", ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            LOG.error("Client error", cause);
            ctx.close();
        }
    }

    private record ClientConnection(Channel channel, LagCompensator compensator) {
        ClientConnection(Channel channel) {
            this(channel, new LagCompensator(20));
        }
    }
}
