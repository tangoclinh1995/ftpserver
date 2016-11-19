package tnl;

import java.io.*;
import java.net.*;



public class FTPServerThread extends Thread {
    private boolean wantToClose;

    private Socket socket;
    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;



    public FTPServerThread(Socket socket) {
        this.socket = socket;

        try {
            this.inputStream = new BufferedInputStream(this.socket.getInputStream());
            this.outputStream = new BufferedOutputStream(this.socket.getOutputStream());
        } catch (Exception e) {

        }

        wantToClose = false;
    }

    public void run() {

    }

    public void close() {
        wantToClose = true;
    }
}
