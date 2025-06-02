package io.netty.example.myexample.netty_guide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class BioClient {
    public static void main(String[] args) {
        int port = 8080;
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            socket = new Socket("127.0.0.1", 8080);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("hello, this is client");
            System.out.println("Send message to server.");
            String resp = in.readLine();
            System.out.println(resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                assert socket != null;
                socket.close();
            } catch (IOException e) {
                // pass
            }
        }
    }
}
