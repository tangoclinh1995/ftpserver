package tnl;

import java.io.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;



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

        public static final String MAKE_NEW_DIRECTORY = "MKD";
        public static final String LIST_FILE_DIRECTORY = "LIST";
        public static final String GOTO_DIRECTORY = "CWD";

        public static final String DELETE = "DELE";

        public static final String DOWNLOAD_FILE = "RETR";
        public static final String UPLOAD_FILE_NO_OVERWITE = "STOU";
        public static final String UPLOAD_FILE_OVERWRITE = "STORE";

        public static final String LOGOUT = "QUIT";

        private static final List<String> REQUEST_CODES = Arrays.asList(new String[] {
            "USER", "PASS",
            "PORT",
            "MKD", "LIST", "CWD",
            "DELE",
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
        public static final int REQUEST_ACTION_DONE = 250;
        public static final int DATA_TRANSFER_COMPLETED = 226;
        public static final int DATA_CONNECTION_OPEN_DONE = 200;

        public static final int ENTER_PASS = 331;

        public static final int FORCED_LOGGED_OUT = 421;
        public static final int DATA_CONNECTION_OPEN_FAILED = 425;
        public static final int DATA_TRANSFER_ERROR = 426;
        public static final int REQUEST_FILE_ACTION_FAILED = 450;
        public static final int REQUEST_ACTION_FAILED = 451;

        public static final int SYNTAX_ERROR = 501;
    }



    private HashMap<String, String> USERS;
    private final int BUFFER_SIZE = 1024;
    private final int READ_TIMEOUT = 8000;
    private final Charset ENCODING_UTF8 = Charset.forName("UTF-8");



    private boolean wantToClose;

    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;

    private String clientDataAddress;
    private int clientDataPort;

    private Path serverDirectory;
    private Path currentAccessDirectory;

    private onFTPThreadTerminateListener connectionClosedListener;

    private String connectionKey;

    private String statusHeader;

    private boolean hasLoggedIn;
    private String username;

    private boolean isRunning;



    public FTPServerThread(Socket socket, String connectionKey, Path serverDirectory, onFTPThreadTerminateListener autoTerminateListener)
            throws Exception
    {
        this.socket = socket;
        this.connectionKey = connectionKey;
        this.serverDirectory = serverDirectory;

        this.clientDataAddress = null;
        this.clientDataPort = -1;

        this.connectionClosedListener = autoTerminateListener;

        this.currentAccessDirectory = this.serverDirectory.toRealPath();

        hasLoggedIn = false;
        username = null;

        statusHeader = this.connectionKey;

        try {
            socket.setSoTimeout(READ_TIMEOUT);

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

        String request = null;
        boolean closedHasBeenAnnouced = false;

        boolean readTimeout;

        while (!wantToClose) {
            readTimeout = false;

            try {
                request = inputStream.readLine();
            } catch (SocketTimeoutException e) {
                // Timeout due to waiting. Continue as usual

                readTimeout = true;
            } catch (IOException e) {
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

            if (readTimeout) {
                continue;
            }

            System.out.println(String.format("%s: %s", statusHeader, request));

            FTPRequest ftpRequest;
            try {
                ftpRequest = new FTPRequest(request);
            } catch (Exception e) {
                // Invalid request, will close the connection immediately
                System.out.println(String.format("%s: Invalid request!", statusHeader));

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
                System.out.println(String.format("%s: Invalid request!.", statusHeader));

                closeSocket(true);
                connectionClosedListener.onConnectionAutoTerminated(connectionKey);

                closedHasBeenAnnouced = true;
                break;

            } catch (ServerUnrecoverableException e) {
                System.out.println(String.format("%s: %s!", statusHeader, e.getMessage()));

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
        USERS.put("user3", "user3");
        USERS.put("user4", "user4");
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

    private void sendResponse(String response) throws ServerUnrecoverableException {
        System.out.println(String.format("%s: %s", statusHeader, response));

        outputStream.println(response);

        if (outputStream.checkError()) {
            throw new ServerUnrecoverableException("Error sending response to client");
        }

    }

    private void handleRequest(FTPRequest request)
            throws InvalidRequestException, ServerUnrecoverableException {
        if (request.code.equals(FTPRequestCode.USERNAME)) {
            loginWithUsername(request.arguments);

        } else if (request.code.equals(FTPRequestCode.PASSWORD)) {
            loginWithPassword(request.arguments);

        } else if (request.code.equals(FTPRequestCode.LOGOUT)) {
            wantToClose = true;

        } else if (request.code.equals(FTPRequestCode.OPEN_DATA_CONNECTION)) {
            saveDataConnectionArugments(request.arguments);

        } else if (request.code.equals(FTPRequestCode.DOWNLOAD_FILE)) {
            serveDownloadRequest(request.arguments);

        } else if (request.code.equals(FTPRequestCode.UPLOAD_FILE_NO_OVERWITE)) {
            serverUploadRequest(request.arguments, false);

        } else if (request.code.equals(FTPRequestCode.UPLOAD_FILE_OVERWRITE)) {
            serverUploadRequest(request.arguments, true);

        } else if (request.code.equals(FTPRequestCode.DELETE)) {
            serveDeleteRequest(request.arguments);

        } else if (request.code.equals(FTPRequestCode.MAKE_NEW_DIRECTORY)) {
            serveMakeNewDirectoryRequest(request.arguments);

        } else if (request.code.equals(FTPRequestCode.GOTO_DIRECTORY)) {
            serveChangeDirectoryRequest(request.arguments);
        } else if (request.code.equals(FTPRequestCode.LIST_FILE_DIRECTORY)) {
            serveListDirectoryContentRequest(request.arguments);

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

        if (password.equals("")) {
            // No password required, logged in successfully
            sendResponse(FTPResponseCode.LOGGED_IN + " Logged in successfully");
            hasLoggedIn = true;

            statusHeader = username + "@" + connectionKey;

            System.out.println(String.format("%s: User %s logged in successfully", statusHeader, username));
        } else {
            // Password required
            sendResponse(FTPResponseCode.ENTER_PASS + " Enter password");
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
            sendResponse(FTPResponseCode.LOGGED_IN + " Logged in successfully");
            hasLoggedIn = true;

            statusHeader = username + "@" + connectionKey;

            System.out.println(String.format("%s: User %s logged in successfully", statusHeader, username));

        } else {
            // Logged in unsuccessfully
            throw new ServerUnrecoverableException("Invalid password");
        }

    }

    private void saveDataConnectionArugments(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 2) {
            throw new InvalidRequestException();
        }

        clientDataAddress = requestArguments.get(0);

        try {
            clientDataPort = Integer.parseInt(requestArguments.get(1));
        } catch (Exception e) {
            throw new InvalidRequestException();
        }

        sendResponse(FTPResponseCode.DATA_CONNECTION_OPEN_DONE + " Data connection parameters saved");
    }

    private Socket establishDataConnection() throws Exception {
        Socket dataSocket = new Socket(clientDataAddress, clientDataPort);
        return dataSocket;
    }

    private void serveDownloadRequest(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 1) {
            throw new InvalidRequestException();
        }

        if (clientDataAddress == null || clientDataPort == -1) {
            throw new InvalidRequestException();
        }

        File fileOut = currentAccessDirectory.resolve(requestArguments.get(0)).toFile();

        // If file does not exist
        if (!fileOut.exists()) {
            sendResponse(FTPResponseCode.REQUEST_FILE_ACTION_FAILED + " File not exist");
            return;
        }

        FileInputStream fileRequestedInpStream;

        try {
            fileRequestedInpStream = new FileInputStream(fileOut);
        } catch (Exception e) {
            sendResponse(FTPResponseCode.REQUEST_FILE_ACTION_FAILED + " Error reading requested file");
            return;
        }

        try {
            sendResponse(FTPResponseCode.SIGNAL_DATA_CONNECTION_OPEN + " Data connection about to open");
        } catch (Exception e) {
            System.out.println(String.format(
                    "%s: Error establishing data connection to %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            return;
        }

        Socket dataSocket = null;
        DataOutputStream dataSocketOutStream = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            dataSocket = establishDataConnection();
            dataSocketOutStream = new DataOutputStream(dataSocket.getOutputStream());
        } catch (Exception e) {
            // Close data socket, if already created. Close file stream
            try {
                dataSocketOutStream.close();
                dataSocket.close();

                fileRequestedInpStream.close();
            } catch (Exception se) {
                // Silently ignore the exception
            }

            System.out.println(String.format(
                    "%s: Error establishing data connection to %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            return;
        }

        int byteRead;
        int errorOccured = 0;

        while (true) {
            try {
                byteRead = fileRequestedInpStream.read(buffer, 0, BUFFER_SIZE);
            } catch (Exception e) {
                errorOccured = 1;
                break;
            }

            if (byteRead == -1) {
                break;
            }

            try {
                dataSocketOutStream.write(buffer, 0, byteRead);
                dataSocketOutStream.flush();
            } catch (Exception e) {
                errorOccured = 2;
                break;
            }

        }

        try {
            // Close data socket
            dataSocketOutStream.close();
            dataSocket.close();

            // Close file
            fileRequestedInpStream.close();
        } catch (Exception e) {
            // Silently ignore the exception
        }

        if (errorOccured == 1) {
            System.out.println(String.format(
                    "%s: Error reading data from file '%s'",
                    statusHeader, requestArguments.get(0)
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_ERROR + " Error in file access on server");
        } else if (errorOccured == 2) {
            System.out.println(String.format(
                    "%s: Error sending file data to client at %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_ERROR + " File data transmission error");
        } else {
            System.out.println(String.format(
                    "%s: File '%s' successfully sent to %s:%s",
                    statusHeader, requestArguments.get(0), clientDataAddress, clientDataPort
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_COMPLETED + " Data transmission completed");
        }

    }

    private void serverUploadRequest(ArrayList<String> requestArguments, boolean overwrite)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 1) {
            throw new InvalidRequestException();
        }

        if (clientDataAddress == null || clientDataPort == -1) {
            throw new InvalidRequestException();
        }

        File fileIn = currentAccessDirectory.resolve(requestArguments.get(0)).toFile();

        // If file does not exist
        if (fileIn.exists() && !overwrite) {
            sendResponse(FTPResponseCode.REQUEST_FILE_ACTION_FAILED + " File exist");
            return;
        }

        FileOutputStream fileRetrievedInpStream;

        try {
            fileRetrievedInpStream = new FileOutputStream(fileIn);
        } catch (Exception e) {
            sendResponse(FTPResponseCode.REQUEST_FILE_ACTION_FAILED + " Error creating new file");
            return;
        }

        try {
            sendResponse(FTPResponseCode.SIGNAL_DATA_CONNECTION_OPEN + " Data connection about to open");
        } catch (Exception e) {
            System.out.println(String.format(
                    "%s: Error establishing data connection to %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            return;
        }

        Socket dataSocket = null;
        DataInputStream dataSocketInpStream = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            dataSocket = establishDataConnection();
            dataSocketInpStream = new DataInputStream(dataSocket.getInputStream());
        } catch (Exception e) {
            // Close data socket, if already created. Close file stream
            try {
                dataSocketInpStream.close();
                dataSocket.close();

                fileRetrievedInpStream.close();
            } catch (Exception se) {
                // Silently ignore the exception
            }

            System.out.println(String.format(
                    "%s: Error establishing data connection to %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            return;
        }

        int byteRead;
        int errorOccured = 0;

        while (true) {
            try {
                byteRead = dataSocketInpStream.read(buffer, 0, BUFFER_SIZE);
            } catch (Exception e) {
                errorOccured = 2;
                break;
            }

            if (byteRead == -1) {
                break;
            }

            try {
                fileRetrievedInpStream.write(buffer, 0, byteRead);
                fileRetrievedInpStream.flush();
            } catch (Exception e) {
                errorOccured = 1;
                break;
            }

        }

        try {
            // Close data socket
            dataSocketInpStream.close();
            dataSocket.close();

            // Close file
            fileRetrievedInpStream.close();
        } catch (Exception e) {
            // Silently ignore the exception
        }

        // If error occurs during file uploading process, then we should delete the data already received
        if (errorOccured != 0) {
            try {
                fileIn.delete();
            } catch (Exception e) {
                //Silently ignore the exception
            }

        }

        if (errorOccured == 1) {
            System.out.println(String.format(
                    "%s: Error writing data from file '%s'",
                    statusHeader, requestArguments.get(0)
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_ERROR + " Error in file access on server");
        } else if (errorOccured == 2) {
            System.out.println(String.format(
                    "%s: Error receiving transmitted file data at %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_ERROR + " File data transmission error");
        } else {
            System.out.println(String.format(
                    "%s: File '%s' successfully uploaded from %s:%s",
                    statusHeader, requestArguments.get(0), clientDataAddress, clientDataPort
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_COMPLETED + " Data transmission completed");
        }

    }

    private void serveDeleteRequest(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 1) {
            throw new InvalidRequestException();
        }

        File pathToDeleted = currentAccessDirectory.resolve(requestArguments.get(0)).toFile();

        if (!pathToDeleted.exists()) {
            sendResponse(FTPResponseCode.REQUEST_ACTION_FAILED + " Path not exist");

            return;
        }

        try {
            boolean deleteResult = pathToDeleted.delete();

            if (!deleteResult) {
                throw new Exception();
            }

        } catch (Exception e) {
            sendResponse(
                    FTPResponseCode.REQUEST_ACTION_FAILED +
                            " Error deleting path (due to access permission or non-empty directory)"
            );

            return;
        }

        sendResponse(FTPResponseCode.REQUEST_ACTION_DONE + " Done");
    }

    private void serveMakeNewDirectoryRequest(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 1) {
            throw new InvalidRequestException();
        }

        File pathToCreated = currentAccessDirectory.resolve(requestArguments.get(0)).toFile();

        if (pathToCreated.exists()) {
            sendResponse(FTPResponseCode.REQUEST_ACTION_FAILED + " Directory already exists");
            return;
        }

        try {
            boolean createResult = pathToCreated.mkdir();

            if (!createResult) {
                throw new Exception();
            }

        } catch (Exception e) {
            sendResponse(
                    FTPResponseCode.REQUEST_ACTION_FAILED + " Error creating new directory");

            return;
        }

        sendResponse(FTPResponseCode.REQUEST_ACTION_DONE + " Done");
    }

    private void serveChangeDirectoryRequest(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() > 1) {
            throw new InvalidRequestException();
        }

        // Go back to root directory
        if (requestArguments.size() == 0) {
            currentAccessDirectory = serverDirectory.toAbsolutePath();

            statusHeader = String.format("%s@%s", username, connectionKey);

            sendResponse(String.valueOf(FTPResponseCode.REQUEST_ACTION_DONE));

            return;
        }

        Path newPath = null;
        try {
            newPath = currentAccessDirectory
                    .resolve(requestArguments.get(0))
                    .toRealPath();

        } catch (Exception e) {
            sendResponse(FTPResponseCode.REQUEST_ACTION_FAILED + " Cannot resolve path");

            return;
        }

        if (!Files.exists(newPath)) {
            sendResponse(FTPResponseCode.REQUEST_ACTION_FAILED + " Path does not exist");

            return;
        }

        if (!Files.isDirectory(newPath)) {
            sendResponse(FTPResponseCode.REQUEST_ACTION_FAILED + " Path is not a directory");

            return;
        }

        // If new path is "parent" of the root server path, reject the request
        if (
                serverDirectory.toString().startsWith(newPath.toString())
                && !serverDirectory.toString().equals(newPath.toString())
        ) {
            sendResponse(
                    FTPResponseCode.REQUEST_ACTION_FAILED +
                    " New path is parent of root path and not permitted to access"
            );

            return;
        }

        // Change the path
        currentAccessDirectory = newPath;

        String relativePath = serverDirectory.relativize(newPath).toString();

        // Change the status header
        statusHeader = String.format("%s@%s %s%s", username, connectionKey, File.separator, relativePath);

        sendResponse(FTPResponseCode.REQUEST_ACTION_DONE + " " + relativePath);
    }

    private String joinArrayListOfString(String delimiter, ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        boolean notFirst = false;

        for (String s: list) {
            if (notFirst) {
                sb.append(delimiter);
            }

            sb.append(s);

            notFirst = true;
        }

        return sb.toString();
    }

    private void serveListDirectoryContentRequest(ArrayList<String> requestArguments)
            throws InvalidRequestException, ServerUnrecoverableException
    {
        if (requestArguments.size() != 0) {
            throw new InvalidRequestException();
        }

        if (clientDataAddress == null || clientDataPort == -1) {
            throw new InvalidRequestException();
        }

        // If current directory has been deleted
        if (!currentAccessDirectory.toFile().exists()) {
            sendResponse(FTPResponseCode.REQUEST_ACTION_FAILED + " Current directory not exist anymore");
            return;
        }

        try {
            sendResponse(FTPResponseCode.SIGNAL_DATA_CONNECTION_OPEN + " Data connection about to open");
        } catch (Exception e) {
            System.out.println(String.format(
                    "%s: Error establishing data connection to %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            return;
        }

        Socket dataSocket = null;
        DataOutputStream dataSocketOutStream = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            dataSocket = establishDataConnection();
            dataSocketOutStream = new DataOutputStream(dataSocket.getOutputStream());
        } catch (Exception e) {
            // Close data socket, if already created. Close file stream
            try {
                dataSocketOutStream.close();
                dataSocket.close();
            } catch (Exception se) {
                // Silently ignore the exception
            }

            System.out.println(String.format(
                    "%s: Error establishing data connection to %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            return;
        }

        File[] contents = currentAccessDirectory.toFile().listFiles();

        ArrayList<String> fileList = new ArrayList<String>();
        ArrayList<String> directoryList = new ArrayList<String>();

        for (File content: contents) {
            if (content.isDirectory()) {
                directoryList.add(content.getName() + File.separator);
            } else {
                fileList.add(content.getName());
            }

        }

        Collections.sort(fileList);
        Collections.sort(directoryList);

        String fileListStr = joinArrayListOfString("\n", fileList);
        String directoryListStr = joinArrayListOfString("\n", directoryList);

        String result = directoryListStr;
        if (fileList.size() != 0) {
            if (directoryList.size() != 0) {
                result += "\n\n";
            }

            result += fileListStr;
        }

        byte[] resultByteArray = result.getBytes(ENCODING_UTF8);

        int byteReadStart = 0;
        int byteReadLength;
        int errorOccured = 0;

        while (byteReadStart < resultByteArray.length) {
            if (byteReadStart + BUFFER_SIZE >= resultByteArray.length) {
                byteReadLength = resultByteArray.length - byteReadStart;
            } else {
                byteReadLength = BUFFER_SIZE;
            }

            try {
                dataSocketOutStream.write(resultByteArray, byteReadStart, byteReadLength);
                dataSocketOutStream.flush();
            } catch (Exception e) {
                errorOccured = 2;
                break;
            }

            byteReadStart += byteReadLength;
        }

        try {
            // Close data socket
            dataSocketOutStream.close();
            dataSocket.close();
        } catch (Exception e) {
            // Silently ignore the exception
        }

        if (errorOccured == 2) {
            System.out.println(String.format(
                    "%s: Error sending list of files and directories to client at %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_ERROR + " Data transmission error");
        } else {
            System.out.println(String.format(
                    "%s: List of files and directories successfully sent to %s:%s",
                    statusHeader, clientDataAddress, clientDataPort
            ));

            sendResponse(FTPResponseCode.DATA_TRANSFER_COMPLETED + " Data transmission completed");
        }

    }

}
