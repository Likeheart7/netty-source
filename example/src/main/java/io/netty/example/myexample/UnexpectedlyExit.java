package io.netty.example.myexample;

import com.sun.org.slf4j.internal.Logger;
import com.sun.org.slf4j.internal.LoggerFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;


public class UnexpectedlyExit {
    private static final Logger log = LoggerFactory.getLogger(UnexpectedlyExit.class);
    public static void main(String[] args) throws InterruptedException {
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            try {
//                TimeUnit.SECONDS.sleep(3);
//                System.out.println("Shutdown hook execute end...");
////                System.exit(0); // Shutdown Hook调用System.exit()会导致进程无法退出
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }, "Shutdown-thread"));
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                    }
                });
            ChannelFuture channelFuture = bootstrap.bind(8088).sync();// 同步等待绑定完成
            channelFuture.channel().closeFuture().addListener((ChannelFutureListener) future -> {
                //            bossGroup.shutdownGracefully();
                //            workGroup.shutdownGracefully();
                log.debug("链路关闭");
            });
//                .sync(); // 必须调用sync等待同步，否则会程序会直接执行并退出
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }
}

