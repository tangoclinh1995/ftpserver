/*
Name: TA Ngoc Linh
ID: 20213201
Email: nlta@connect.ust.hk
 */

package tnl;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;




public class FTPServer extends Thread implements onFTPThreadTerminateListener {
    private ServerSocket serverSocket;
    private Path serverDirectory;

    private boolean wantToClose;

    private TreeMap<String, FTPServerThread> currentConnectionMap;



    public FTPServer(int port, String serverDirectory) throws Exception {
        this.serverSocket = new ServerSocket(port);
        this.wantToClose = false;

        this.serverDirectory = Paths.get(serverDirectory).toRealPath();

        currentConnectionMap = new TreeMap<String, FTPServerThread>();
    }

    public void run() {
        while (!wantToClose) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
            } catch (SocketException e) {
                // The closeAll() command is issued. Silently ignore as
                // it will be handled later on
            } catch (IOException e) {
                System.out.println("I/O error occur while waiting for connection. ");
            }

            // If closeAll command is issued
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
                ftpThread = new FTPServerThread(socket, connectionMapKey, serverDirectory, this);
                currentConnectionMap.put(connectionMapKey, ftpThread);

                ftpThread.start();
            } catch (Exception e) {
                System.out.println(String.format("%s: Error establishing connection. Terminate immediately", connectionMapKey));
            }

        }

    }

    public void closeAll() {
        wantToClose = true;

        try {
            serverSocket.close();
        } catch (Exception e) {
            // Silently ignore the exception
        }

    }

    public void closeIndividualConnection(String connectionKey) throws Exception {
        FTPServerThread ftpServerThread = currentConnectionMap.get(connectionKey);

        if (ftpServerThread == null) {
            throw new Exception("Connection does not exist!");
        }

        System.out.println(String.format("%s: Closing.", connectionKey));

        ftpServerThread.close();
    }

    public String[] getCurrentConnectionMap() {
        return currentConnectionMap.keySet().toArray(new String[currentConnectionMap.size()]);
    }

    public void onConnectionAutoTerminated(String connectionKey) {
        System.out.println(String.format("%s: Error happened. Connection terminated.", connectionKey));

        currentConnectionMap.remove(connectionKey);
    }

    public void onConnectionTerminated(String connectionKey) {
        System.out.println(String.format("%s: Connection terminated.", connectionKey));

        currentConnectionMap.remove(connectionKey);
    }

}