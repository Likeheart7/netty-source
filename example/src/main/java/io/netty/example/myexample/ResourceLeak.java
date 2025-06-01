package io.netty.example.myexample;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LoggingHandler;

public class ResourceLeak {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 8080;
    // 错误的方式
//    static void initClientPool(int poolSize) throws Exception {
//        for (int i = 0; i < poolSize; i++) {
//            NioEventLoopGroup group = new NioEventLoopGroup();
//            Bootstrap b = new Bootstrap();
//            b.group(group)
//                .channel(NioSocketChannel.class)
//                .option(ChannelOption.TCP_NODELAY, true)
//                .handler(new ChannelInitializer<SocketChannel>() {
//                    @Override
//                    protected void initChannel(SocketChannel ch) throws Exception {
//                        ChannelPipeline pipeline = ch.pipeline();
//                        pipeline.addLast(new LoggingHandler());
//                    }
//                });
//            ChannelFuture f = b.connect(HOST, PORT).sync();
//            f.channel().closeFuture().addListener((r) -> {
//                group.shutdownGracefully();
//            });
//        }
//    }

    static void initClientPool(int poolSize) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new LoggingHandler());
                }
            });
        // 在一个NioEventLoopGroup里处理所有连接而不是给每个连接创建一个NioEventLoopGroup
        for (int i = 0; i < poolSize; i++) {
            ChannelFuture future = b.connect(HOST, PORT).sync();// 不推荐sync
        }

    }

    public static void main(String[] args) throws Exception {
        initClientPool(10);
    }
}
