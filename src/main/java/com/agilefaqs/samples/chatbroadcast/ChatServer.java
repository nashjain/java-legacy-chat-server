package com.agilefaqs.samples.chatbroadcast;

import com.google.gson.Gson;
import spark.ResponseTransformer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static spark.Spark.get;

class ChatServer implements Runnable {
    private SocketHandler clients[] = new SocketHandler[50];
    private ServerSocket server = null;
    private Thread thread = null;
    private int clientCount = 0;
    DataStore dataStore = new DataStore();

    private ChatServer(int port) {
        try {
            JsonTransformer transformer = new JsonTransformer();
            get("/hello", "application/json", (req, res) -> "Hello World",
                    transformer);
            get("/send", "application/json", (req, res) -> {
                        try {
                            String message = req.queryParams("message");
                            String senderName = req.queryParams("senderName");
                            dataStore.add(message);
                            handle(null, senderName + ": " + message);
                            return dataStore.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                            return "Internal Server Error";
                        }
                    },
                    transformer);
            get("/fetchAllMessages", "application/json", (req, res) -> {
                        try {
                            return dataStore.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                            return "Internal Server Error";
                        }
                    },
                    transformer);
            System.out.println("Binding to port " + port + ", please wait  ...");
            server = new ServerSocket(port);
            System.out.println("Server started: " + server);
            start();
        } catch (IOException ioe) {
            System.out.println("Can not bind to port " + port + ": " + ioe.getMessage());
        }
    }

    public void run() {
        while (true) {
            try {
                System.out.println("Waiting for a client ...");
                addThread(server.accept());
            } catch (IOException ioe) {
                System.out.println("Server accept error: " + ioe);
                break;
            }
        }
    }

    private int findClient(int id) {
        for (int i = 0; i < clientCount; i++)
            if (clients[i].id == id)
                return i;
        return -1;
    }

    private synchronized void handle(Integer id, String input) {
        if (id == null) {
            for (int i = 0; i < clientCount; i++)
                clients[i].send(input);
        } else if (input.equals(".bye")) {
            clients[findClient(id)].send(".bye");
            remove(id);
        } else {
            for (int i = 0; i < clientCount; i++)
                clients[i].send(id + ": " + input);
        }
    }

    private synchronized void remove(int ID) {
        int pos = findClient(ID);
        if (pos >= 0) {
            SocketHandler toTerminate = clients[pos];
            System.out.println("Removing client thread " + ID + " at " + pos);
            if (pos < clientCount - 1)
                System.arraycopy(clients, pos + 1, clients, pos + 1 - 1, clientCount - (pos + 1));
            clientCount--;
            try {
                toTerminate.close();
            } catch (IOException ioe) {
                System.out.println("Error closing thread: " + ioe);
            }
            toTerminate.stop();
        }
    }

    private void addThread(Socket socket) {
        if (clientCount < clients.length) {
            System.out.println("Client accepted: " + socket);
            clients[clientCount] = new SocketHandler(this, socket);
            try {
                clients[clientCount].open();
                clients[clientCount].start();
                clientCount++;
            } catch (IOException ioe) {
                System.out.println("Error opening thread: " + ioe);
            }
        } else
            System.out.println("Client refused: maximum " + clients.length + " reached.");
    }

    private void start() {
        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    public static void main(String args[]) {
        if (args.length != 1)
            new ChatServer(9090);
        else
            new ChatServer(Integer.parseInt(args[0]));
    }

    private static class JsonTransformer implements ResponseTransformer {
        private Gson gson = new Gson();

        @Override
        public String render(Object model) {
            return gson.toJson(model);
        }
    }

    private static class SocketHandler extends Thread {
        private ChatServer server = null;
        private Socket socket = null;
        private int id = -1;
        private DataInputStream streamIn = null;
        private DataOutputStream streamOut = null;

        SocketHandler(ChatServer server, Socket socket) {
            super();
            this.server = server;
            this.socket = socket;
            id = this.socket.getPort();
        }

        void send(String msg) {
            try {
                streamOut.writeUTF(msg);
                streamOut.flush();
            } catch (IOException ioe) {
                System.out.println(id + " ERROR sending: " + ioe.getMessage());
                server.remove(id);
                stop();
            }
        }

        public void run() {
            System.out.println("Server Thread " + id + " running.");
            while (true) {
                try {
                    server.handle(id, streamIn.readUTF());
                } catch (IOException ioe) {
                    System.out.println(id + " ERROR reading: " + ioe.getMessage());
                    server.remove(id);
                    break;
                }
            }
        }

        void open() throws IOException {
            streamIn = new DataInputStream(new
                    BufferedInputStream(socket.getInputStream()));
            streamOut = new DataOutputStream(new
                    BufferedOutputStream(socket.getOutputStream()));
        }

        void close() throws IOException {
            if (socket != null) socket.close();
            if (streamIn != null) streamIn.close();
            if (streamOut != null) streamOut.close();
        }
    }
}