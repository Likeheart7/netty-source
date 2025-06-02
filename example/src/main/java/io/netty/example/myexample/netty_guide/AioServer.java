package io.netty.example.myexample.netty_guide;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class AioServer {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
        System.out.println("Server start on port: " + port);
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Void attachment) {
                // 再次调用接收下一个连接
                serverChannel.accept(null, this);
                ByteBuffer buf = ByteBuffer.allocate(1024);
                clientChannel.read(buf, buf, new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer length, ByteBuffer attachment) {
                        if (length == -1) {
                            try {
                                clientChannel.close();
                                System.out.println("Client disconnected.");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return;
                        }

                        buf.flip();
                        byte[] data = new byte[buf.limit()];
                        buf.get(data);
                        String message = new String(data);
                        System.out.println("Received: " + message);

                        // 响应客户端
                        ByteBuffer resp = ByteBuffer.wrap("Got your message".getBytes(StandardCharsets.UTF_8));
                        clientChannel.write(resp, resp, new CompletionHandler<Integer, ByteBuffer>() {
                            @Override
                            public void completed(Integer result, ByteBuffer attachment) {
                                try {
                                    clientChannel.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer attachment) {
                                exc.printStackTrace();
                            }
                        });
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        exc.printStackTrace();
                    }
                });
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });

        while (true) {
            try {
                TimeUnit.SECONDS.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}