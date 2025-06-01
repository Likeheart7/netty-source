package io.netty.example.myexample;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ReferenceCountUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemoryPoolLeak {

    public static void main(String[] args) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            b.group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new RouterServerHandler());
                    }
                });
            ChannelFuture cf = b.bind(8080).sync();
            cf.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
    
    static class RouterServerHandler extends ChannelInboundHandlerAdapter {
        static ExecutorService executorService = Executors.newSingleThreadExecutor();
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false); // 参数表示是否使用direct memory

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // 这个reqMsg会泄露
            ByteBuf reqMsg = (ByteBuf) msg;
            byte[] body = new byte[reqMsg.readableBytes()];
            System.out.println("recv: " + msg);
            ReferenceCountUtil.release(reqMsg);
            executorService.execute(() -> {
                ByteBuf respMsg = allocator.heapBuffer(body.length);
                respMsg.writeBytes(body);
                /*
                调用writeAndFlush时，netty会帮助应用释放内存
                 */
                ctx.writeAndFlush(respMsg);
            });
        }
    }
}

