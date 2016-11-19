package tnl;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.TreeMap;




public class FTPServer extends Thread implements onFTPThreadTerminateListener {
    private static final int MAX_ALLOWED_CONNECTION = 2;



    private ServerSocket serverSocket;
    private boolean wantToClose;

    private TreeMap<String, FTPServerThread> currentConnectionMap;



    public FTPServer(int port) throws Exception {
        serverSocket = new ServerSocket(port);
        wantToClose = false;

        currentConnectionMap = new TreeMap<String, FTPServerThread>();
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

                for (Map.Entry<String, FTPServerThread> entry : currentConnectionMap.entrySet()) {
                    entry.getValue().close();
                }

                return;
            }

            // Create new connection
            String connectionMapKey = socket.getInetAddress().getHostAddress() + ":" + String.valueOf(socket.getPort());
            FTPServerThread ftpThread = null;

            System.out.println("New connection from " + connectionMapKey);

            try {
                ftpThread = new FTPServerThread(socket, this);
                currentConnectionMap.put(connectionMapKey, ftpThread);

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

    public void closeConnection(String connectionKey) throws Exception {
        FTPServerThread ftpServerThread = currentConnectionMap.get(connectionKey);

        if (ftpServerThread == null) {
            throw new Exception("Connection does not exist!");
        }

        System.out.println(String.format("%s: Closing.", connectionKey));

        ftpServerThread.close();
    }

    public String[] getCurrentConnectionMap() {
        return (String[]) currentConnectionMap.keySet().toArray();
    }

    public void onConnectionAutoTerminated(String connectionKey) {
        System.out.println(String.format("%s: Error happened. Connection terminated.", connectionKey));

        currentConnectionMap.remove(connectionKey);
    }

    public void onConnectionTerminated(String connectionKey) {
        System.out.println(String.format("%s: Connection terminated.", connectionKey));
    }

}