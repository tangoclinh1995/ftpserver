package tnl;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.TreeMap;



public class FTPServer extends Thread {
    private static final int MAX_ALLOWED_CONNECTION = 2;



    private ServerSocket serverSocket;
    private boolean wantToClose;

    private TreeMap<String, FTPServerThread> currentConnection;



    public FTPServer(int port) throws Exception {
        serverSocket = new ServerSocket(port);
        wantToClose = false;

        currentConnection = new TreeMap<String, FTPServerThread>();
    }

    public void run() {
        while (!wantToClose) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
            } catch (SocketException e) {
                // The close() command is issued. Silently ignore as
                // it will be handled later on
            } catch (IOException e) {
                System.out.println("I/O error occur while waiting for connection. ");
            }

            // If close command is issued
            if (wantToClose) {
                try {
                    socket.close();
                } catch (Exception e) {
                    // Silently ignore the exception
                }

                for (Map.Entry<String, FTPServerThread> entry : currentConnection.entrySet()) {
                    entry.getValue().close();
                }

                return;
            }

            // Create new connection
            String connectionMapKey = socket.getInetAddress().toString() + String.valueOf(socket.getPort());
            FTPServerThread ftpThread = null;

            System.out.println("New connection from " + connectionMapKey);

            try {
                ftpThread = new FTPServerThread(socket);
                currentConnection.put(connectionMapKey, ftpThread);

                ftpThread.start();
            } catch (Exception e) {
                System.out.println(String.format("%s: Error establishing connection. Terminate immediately", connectionMapKey));
            }

        }

    }

    public void close() {
        wantToClose = true;

        try {
            serverSocket.close();
        } catch (Exception e) {
            // Silently ignore the exception
        }

    }

}