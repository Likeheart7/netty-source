package io.netty.example.myexample.netty_guide;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NioServer {
    public static void main(String[] args) {
        int port = 8080;
        try (
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open();
        ) {
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            serverSocketChannel.configureBlocking(false); // 设置为非阻塞
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                int select = selector.select();
                if (select > 0) continue;   // 假死
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectionKeys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (key.isAcceptable()) {
                        handleAccept(key, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void handleWrite(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        try {
            client.write(buf);
            if (!buf.hasRemaining()) {
                System.out.println("finish sending, closing connection");
                closeConnection(key, client);
            }
        } catch (IOException e) {
            e.printStackTrace();
            closeConnection(key, client);
        }
    }

    private static void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        // 简化
        try {

            int len = channel.read(buf);
            if (len == -1) {
                closeConnection(key, channel);
                return;
            }
            if (len > 0) {
                System.out.println("Received: " + new String(buf.array(), 0, buf.position()));
                buf.flip();
                key.interestOps(SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            closeConnection(key, channel);
        }

    }

    private static void closeConnection(SelectionKey key, SocketChannel channel) {
        try {
            key.cancel();
            channel.close();
        } catch (IOException e) {
            key.cancel();
            e.printStackTrace();
        }
    }

    private static void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.socket().setReuseAddress(true);
        System.out.println("一个连接建立");
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        socketChannel.register(selector, SelectionKey.OP_READ, buffer);
    }

    private static class ReactorTask implements Runnable {
        @Override
        public void run() {
            System.out.println("inner");
        }
    }
}
