package tnl;

import java.util.Scanner;


public class FTPServerConsole {
    private static Scanner scanConsole = new Scanner(System.in);
    private static FTPServer ftpServer;



    public static void main(String[] argv) {
        int port;

        System.out.println("FTP Server");
        System.out.println("----------");
        System.out.println();

        System.out.print("Server port: ");

        try {
            port = scanConsole.nextInt();
        } catch (Exception e) {
            System.out.println("\nInvalid port number! Terminated.");
            return;
        }

        try {
            ftpServer = new FTPServer(port);
        } catch (Exception e) {
            System.out.println(String.format("\nCannot start FTP Server at port %d! Terminated.", port));
        }

        ftpServer.start();

        System.out.println(String.format("\nFTP Server started at port %d", port));
        System.out.println("Type h/H to get help, q/Q to stop the server");

        while (true) {
            System.out.print("> ");
            String command = scanConsole.nextLine().trim();

            if (command.equals("q") || command.equals("Q")) {
                System.out.println("Closing FTP server");
                ftpServer.close();
                return;
            }

            if (command.equals("h") || command.equals("H")) {
                showHelp();
                continue;
            }

            if (command.equals("l") || command.equals("L")) {
                listCurrentConnection();
                continue;
            }

            if (command.length() >= 3 && (command.charAt(0) == 'c' || command.charAt(0) == 'C')) {
                String connectionKey = command.substring(2).trim();

                try {
                    ftpServer.closeConnection(connectionKey);
                } catch (Exception e) {
                    System.out.println(String.format("%s: This connection does not exist.", connectionKey));
                }

                continue;
            }

            // Otherwise, invalid command
            System.out.println("Invalid command!");
        }

    }

    private static void showHelp() {
        System.out.println("l/L                 List all current connection.");
        System.out.println("c/C <connection>    Close a connection.");
        System.out.println("q/Q                 Stop the whole server.");
        System.out.println("h/H                 Get help.");
    }

    private static void listCurrentConnection() {
        for (String conn : ftpServer.getCurrentConnectionMap()) {
            System.out.println(conn);
        }

    }

}