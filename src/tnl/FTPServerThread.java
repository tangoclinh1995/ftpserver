package tnl;

import java.io.*;
import java.net.*;



public class FTPServerThread extends Thread {
    private boolean wantToClose;

    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;

    private onFTPThreadTerminateListener connectionClosedListener;

    private String connectionKey;

    private boolean hasLoggedIn;
    private boolean userNameProvided;

    private boolean isRunning;



    public FTPServerThread(Socket socket, onFTPThreadTerminateListener autoTerminateListener) {
        this.socket = socket;
        connectionKey = socket.getInetAddress().getHostAddress() + ":" + String.valueOf(socket.getPort());

        this.connectionClosedListener = autoTerminateListener;

        hasLoggedIn = false;
        userNameProvided = false;

        try {
            this.inputStream = new BufferedReader((new InputStreamReader(this.socket.getInputStream())));
            this.outputStream = new PrintWriter(this.socket.getOutputStream());
        } catch (Exception e) {

        }

        wantToClose = false;
        isRunning = false;
    }

    public void run() {
        isRunning = true;

        String request;

        while (!wantToClose) {
            try {
                request = inputStream.readLine();
            } catch (Exception e) {
                // Input stream error. Cannot recoverable. Terminate
                closeSocket();
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                break;
            }

            // User wants to close this connection
            if (wantToClose) {
                closeSocket();
                connectionClosedListener.onConnectionTerminated(connectionKey);
                break;
            }
        }

        isRunning = false;
    }

    public void close() {
        wantToClose = true;

        if (!isRunning) {
            closeSocket();
        }
    }

    private void closeSocket() {
        try {
            inputStream.close();
            outputStream.close();

            socket.close();
        } catch (Exception e) {
            // Silently ignore exception
        }

    }
}
