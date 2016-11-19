package tnl;

import java.util.Scanner;
import tnl.FTPServer;



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
        System.out.println("Press Q or q anytime to stop the server");

        while (true) {
            String c = scanConsole.nextLine();
            if (c.length() > 0 && (c.charAt(0) == 'q' || c.charAt(0) == 'Q')) {
                System.out.println("Closing FTP server");
                ftpServer.close();

                return;
            }

        }

    }

}