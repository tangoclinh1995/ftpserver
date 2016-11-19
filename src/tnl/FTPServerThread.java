package tnl;

import java.io.*;
import java.net.*;
import java.util.*;




public class FTPServerThread extends Thread {
    private class FTPRequest {
        public String code;
        public ArrayList<String> arguments;

        public FTPRequest(String request) throws Exception {
            int index, prev, len;

            index = request.indexOf(" ");

            code = request.substring(0, index).toUpperCase();
            if (!FTPRequestCode.isValidCode(code)) {
                throw new Exception("Invalid request");
            }

            request = request.substring(index + 1).trim();

            arguments = new ArrayList<String>();

            index = 0;
            len = request.length();

            while (index < len) {
                prev = index;

                if (request.charAt(index) == '\"') {
                    ++index;
                    while (index < len && request.charAt(index) != '\"') {
                        ++index;
                    }

                    if (index == len) {
                        throw new Exception("Invalid request");
                    }

                    arguments.add(request.substring(prev + 1, index));

                    ++index;
                } else {
                    while (index < len && request.charAt(index) != ' ') {
                        ++index;
                    }

                    arguments.add(request.substring(prev, index));
                }

                while (index < len && request.charAt(index) == ' ') {
                    ++index;
                }

            }

        }

    }



    private static class FTPRequestCode {
        public static final String USERNAME = "USER";
        public static final String PASSWORD = "PASS";

        public static final String OPEN_DATA_CONNECTION = "PORT";

        public static final String LIST_FILE_DIRECTORY = "LIST";
        public static final String GOTO_DIRECTORY = "CWD";

        public static final String DOWNLOAD_FILE = "RETR";
        public static final String UPLOAD_FILE_NO_OVERWITE = "STOU";
        public static final String UPLOAD_FILE_OVERWRITE = "STORE";

        public static final String LOGOUT = "QUIT";

        private static final List<String> REQUEST_CODES = Arrays.asList(new String[] {
            "USER", "PASS",
            "PORT",
            "LIST", "CWD",
            "RETR", "STOU", "STORE",
            "QUIT"
        });



        public static boolean isValidCode(String code) {
            return REQUEST_CODES.indexOf(code) != -1;
        }
    }



    private static class FTPResponseCode {
        public static final int SIGNAL_DATA_CONNECTION_OPEN = 150;

        public static final int LOGGED_IN = 230;
        public static final int LOGGED_OUT = 221;
        public static final int ACTION_DONE = 250;
        public static final int DATA_TRANSFER_COMPLETED = 226;
        public static final int DATA_CONNECTION_OPEN_DONE = 200;

        public static final int ENTER_PASS = 331;

        public static final int FORCED_LOGGED_OUT = 421;
        public static final int DATA_CONNECTION_OPEN_FAILED = 425;
        public static final int DATA_TRANSFER_ERROR = 426;

        public static final int SYNTAX_ERROR = 501;
    }



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
                closeSocket(true);
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                break;
            }

            // User wants to close this connection
            if (wantToClose) {
                closeSocket(false);
                connectionClosedListener.onConnectionTerminated(connectionKey);
                break;
            }

            FTPRequest ftpRequest;
            try {
                ftpRequest = new FTPRequest(request);
            } catch (Exception e) {
                // Invalid request, will close the connection immediately
                closeSocket(true);
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                break;
            }

            handleRequest(ftpRequest);
        }

        isRunning = false;
    }

    public void close() {
        wantToClose = true;

        if (!isRunning) {
            closeSocket(false);
        }
    }

    private void closeSocket(boolean forced) {
        try {
            if (forced) {
                outputStream.println(FTPResponseCode.FORCED_LOGGED_OUT + " Forced Logged out");
            } else {
                outputStream.println(FTPResponseCode.LOGGED_OUT + "Logged out");
            }

            inputStream.close();
            outputStream.close();

            socket.close();
        } catch (Exception e) {
            // Silently ignore exception
        }

    }

    private void handleRequest(FTPRequest ftpRequest) {

    }
}
