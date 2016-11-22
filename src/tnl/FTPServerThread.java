package tnl;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;


public class FTPServerThread extends Thread {
    private class FTPRequest {
        public String code;
        public ArrayList<String> arguments;

        public FTPRequest(String request) throws Exception {
            arguments = new ArrayList<String>();

            int index = request.indexOf(" ");

            if (index == -1) {
                code = request;
                if (!FTPRequestCode.isValidCode(code)) {
                    throw new Exception("Invalid request");
                }

                return;
            }

            code = request.substring(0, index).toUpperCase();
            if (!FTPRequestCode.isValidCode(code)) {
                throw new Exception("Invalid request");
            }

            request = request.substring(index + 1).trim();

            index = 0;
            int len = request.length();
            int prev;

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
            return REQUEST_CODES.contains(code);
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
        public static final int REQUEST_FILE_ACTION_FAILED = 450;

        public static final int SYNTAX_ERROR = 501;
    }



    private HashMap<String, String> USERS;



    private boolean wantToClose;

    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;

    private Path serverDirectory;
    private Path currentAccessDirectory;

    private onFTPThreadTerminateListener connectionClosedListener;

    private String connectionKey;

    private boolean hasLoggedIn;
    private String username;

    private boolean isRunning;



    public FTPServerThread(Socket socket, String connectionKey, Path serverDirectory, onFTPThreadTerminateListener autoTerminateListener)
            throws Exception
    {
        this.socket = socket;
        this.connectionKey = connectionKey;
        this.serverDirectory = serverDirectory;

        this.connectionClosedListener = autoTerminateListener;

        this.currentAccessDirectory = this.serverDirectory.toAbsolutePath();

        hasLoggedIn = false;
        username = null;

        try {
            this.inputStream = new BufferedReader((new InputStreamReader(this.socket.getInputStream())));
            this.outputStream = new PrintWriter(this.socket.getOutputStream(), true);
        } catch (Exception e) {
            throw e;
        }

        initializeUSERS();

        wantToClose = false;
        isRunning = false;
    }

    public void run() {
        isRunning = true;

        String request;
        boolean closedHasBeenAnnouced = false;

        while (!wantToClose) {
            try {
                request = inputStream.readLine();
            } catch (Exception e) {
                // Input stream error. Cannot recoverable. Terminate
                closeSocket(true);
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                break;
            }

            // Console user wants to close this connection
            if (wantToClose) {
                closeSocket(false);
                connectionClosedListener.onConnectionTerminated(connectionKey);

                closedHasBeenAnnouced = true;
                break;
            }

            System.out.println(String.format("%s: %s", connectionKey, request));

            FTPRequest ftpRequest;
            try {
                ftpRequest = new FTPRequest(request);
            } catch (Exception e) {
                // Invalid request, will close the connection immediately
                System.out.println(String.format("%s: Invalid request!", connectionKey));

                closeSocket(true);
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                closedHasBeenAnnouced = true;
                break;
            }

            // Right now, there is no different between the ways to handle InvalidRequest
            // and ServerUnrecoverableException. We just simply close this connection
            try {
                handleRequest(ftpRequest);

            } catch (InvalidRequestException e) {
                System.out.println(String.format("%s: Invalid request!.", connectionKey));

                closeSocket(true);
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                closedHasBeenAnnouced = true;
                break;

            } catch (ServerUnrecoverableException e) {
                System.out.println(String.format("%s: %s!", connectionKey, e.getMessage()));

                closeSocket(true);
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                closedHasBeenAnnouced = true;
                break;
            }

        }

        isRunning = false;

        if (!closedHasBeenAnnouced) {
            connectionClosedListener.onConnectionTerminated(connectionKey);
            closeSocket(false);
        }

    }

    public void close() {
        Logger.getGlobal().info(String.format("%s want to close", connectionKey));

        wantToClose = true;

        if (!isRunning) {
            closeSocket(false);
        }

    }

    private void initializeUSERS() {
        USERS = new HashMap<String, String>();

        USERS.put("usernopass", "");
        USERS.put("user1", "user1");
        USERS.put("user2", "user2");
    }

    private void closeSocket(boolean forced) {
        try {
            if (forced) {
                outputStream.println(FTPResponseCode.FORCED_LOGGED_OUT + " Forced Logged out");
            } else {
                outputStream.println(FTPResponseCode.LOGGED_OUT + "Logged out");
            }

        } catch (Exception e) {
            // Silently ignore exception
        }

        try {
            inputStream.close();
            outputStream.close();

            socket.close();
        } catch (Exception e) {
            // Silently ignore exception
        }

    }

    private void handleRequest(FTPRequest request)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (request.code.equals(FTPRequestCode.USERNAME)) {
            loginWithUsername(request.arguments);

        } else if (request.code.equals(FTPRequestCode.PASSWORD)) {
            loginWithPassword(request.arguments);

        } else if (request.code.equals(FTPRequestCode.LOGOUT)) {
            wantToClose = true;

        } else {
            throw new InvalidRequestException();
        }

    }

    private void loginWithUsername(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 1) {
            throw new InvalidRequestException();
        }

        if (hasLoggedIn || username != null) {
            throw new InvalidRequestException();
        }

        String password = USERS.get(requestArguments.get(0));

        if (password == null) {
            throw new ServerUnrecoverableException("User does not exist");
        }

        username = requestArguments.get(0);

        try {
            if (password.equals("")) {
                // No password required, logged in successfully
                outputStream.println(FTPResponseCode.LOGGED_IN + " Logged in successfully");
                hasLoggedIn = true;

                System.out.println(String.format("%s: User %s logged in successfully", connectionKey, username));
            } else {
                // Password required
                outputStream.println(FTPResponseCode.ENTER_PASS + " Enter password");
            }

        } catch (Exception e) {
            throw new ServerUnrecoverableException("Error writing to output stream");
        }

    }

    private void loginWithPassword(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 1) {
            throw new InvalidRequestException();
        }

        if (hasLoggedIn || username == null) {
            throw new InvalidRequestException();
        }

        String userPassword = USERS.get(username);

        if (userPassword.equals(requestArguments.get(0))) {
            // Logged in successfully
            try {
                outputStream.println(FTPResponseCode.LOGGED_IN + " Logged in successfully");
                hasLoggedIn = true;

                System.out.println(String.format("%s: User %s logged in successfully", connectionKey, username));
            } catch (Exception e) {
                throw new ServerUnrecoverableException("Error writing to output stream");
            }

        } else {
            // Loggin unsuccessfully
            throw new ServerUnrecoverableException("Invalid password");
        }

    }

}
