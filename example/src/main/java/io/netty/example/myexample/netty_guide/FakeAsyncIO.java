package io.netty.example.myexample.netty_guide;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 * 基于线程池的BIO
 * 优点是资源可控，不会超过线程池的范围，
 * 缺点是没有解决根源的阻塞问题
 * </pre>
 */
public class FakeAsyncIO {
    public static void main(String[] args) {
        int port = 8080;
        try (
            ServerSocket server = new ServerSocket(port);
        ) {
            System.out.println("server was started on port: " + port);
            ThreadPoolExecutor singleExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 50, 120L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1_000));
            Socket socket = null;
            while (true) {
                socket = server.accept();
                singleExecutor.execute(new TimeServerHandler(socket));

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class TimeServerHandler implements Runnable {
        private final Socket socket;

        public TimeServerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                byte[] buf = new byte[100];
                int len = in.read(buf);
                String string = new String(buf, 0, len, StandardCharsets.UTF_8);
                System.out.println(string);
                out.write("got message".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // pass
                }
            }
        }
    }
}

