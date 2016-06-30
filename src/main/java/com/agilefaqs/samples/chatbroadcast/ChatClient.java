package com.agilefaqs.samples.chatbroadcast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

class ChatClient implements Runnable {
    private Socket socket = null;
    private Thread thread = null;
    private DataInputStream console = null;
    private DataOutputStream streamOut = null;
    private SocketHandler client = null;

    private ChatClient(String serverName, int serverPort) {
        System.out.println("Establishing connection. Please wait ...");
        try {
            socket = new Socket(serverName, serverPort);
            System.out.println("Connected: " + socket);
            start();
        } catch (UnknownHostException uhe) {
            System.out.println("Host unknown: " + uhe.getMessage());
        } catch (IOException ioe) {
            System.out.println("Unexpected exception: " + ioe.getMessage());
        }
    }

    public void run() {
        while (thread != null) {
            try {
                streamOut.writeUTF(console.readLine());
                streamOut.flush();
            } catch (IOException ioe) {
                System.out.println("Sending error: " + ioe.getMessage());
                stop();
            }
        }
    }

    private void handle(String msg) {
        if (msg.equals(".bye")) {
            System.out.println("Good bye. Press RETURN to exit ...");
            stop();
        } else
            System.out.println(msg);
    }

    private void start() throws IOException {
        console = new DataInputStream(System.in);
        streamOut = new DataOutputStream(socket.getOutputStream());
        if (thread == null) {
            client = new SocketHandler(this, socket);
            thread = new Thread(this);
            thread.start();
        }
    }

    private void stop() {
        if (thread != null) {
            thread.stop();
            thread = null;
        }
        try {
            if (console != null) console.close();
            if (streamOut != null) streamOut.close();
            if (socket != null) socket.close();
        } catch (IOException ioe) {
            System.out.println("Error closing ...");
        }
        client.close();
        client.stop();
    }

    public static void main(String args[]) {
        if (args.length != 2)
            new ChatClient("localhost", 9090);
        else
            new ChatClient(args[0], Integer.parseInt(args[1]));
    }

    private static class SocketHandler extends Thread {
        private Socket socket = null;
        private ChatClient client = null;
        private DataInputStream streamIn = null;

        SocketHandler(ChatClient client, Socket socket) {
            this.client = client;
            this.socket = socket;
            try {
                streamIn = new DataInputStream(this.socket.getInputStream());
            } catch (IOException ioe) {
                System.out.println("Error getting input stream: " + ioe);
                this.client.stop();
            }
            start();
        }

        void close() {
            try {
                if (streamIn != null) streamIn.close();
            } catch (IOException ioe) {
                System.out.println("Error closing input stream: " + ioe);
            }
        }

        public void run() {
            while (true) {
                try {
                    client.handle(streamIn.readUTF());
                } catch (IOException ioe) {
                    System.out.println("Listening error: " + ioe.getMessage());
                    client.stop();
                }
            }
        }
    }
}