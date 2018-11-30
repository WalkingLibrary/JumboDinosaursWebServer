package com.jumbodinosaurs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.Scanner;

/*
  Main server control
  On init will initialize DataController then will
  initialize the Operator Console Thread then
  will initialize the domains given via args (if any)
  using google's Dynamic Domain IP A.P.I.
  After Initializing These three things the program will then Wait for in coming connections on port 80.
 */
public class ServerControl
{

    private ServerSocket webServer;
    private final ClientTimer serverInt = new ClientTimer(5000, new ComponentsListener());
    //EveryHour Renew Domain Name Servers
    private final ClientTimer domainInt = new ClientTimer(3600000, new ComponentsListener());
    private final ClientTimer fiveMin = new ClientTimer(300000, new ComponentsListener());
    private static Thread commandThread;
    private int count = 0;
    private int startUpTries = 5;
    private String[][] credentials;
    private String[] domains;
    private boolean[] domainsInit;
    private static DataController dataIO;



    public ServerControl()
    {
        System.out.println("Starting Jumbo Dinosaurs .1");
        this.dataIO = new DataController();
        this.intServer();
        while(this.webServer != null)
        {
            try
            {
                System.out.println("Awaiting Connection");
                Socket client = this.webServer.accept();
                Runnable session = new SessionHandler(new Session(client), this.dataIO);
                Thread sessionThread = new Thread(session);
                sessionThread.start();
                System.out.println("Accepted Client: " + client.getInetAddress().getHostAddress());
            }
            catch (Exception e)
            {
                System.out.println("Error Accepting Client");
                e.printStackTrace();
                System.out.println(e.getCause());
            }
        }
        System.exit(2);
    }

    public ServerControl(String[][] credentials, String[] domains)
    {
        System.out.println("Starting Jumbo Dinosaurs .1");
        this.credentials = credentials;
        this.domains = domains;
        this.domainsInit = new boolean[this.credentials.length];
        this.dataIO = new DataController(this.domains);
        this.intServer();
        this.domainInt.start();
        intDomain();
        while(this.webServer != null)
        {
            try
            {
                System.out.println("Awaiting Connection");
                Socket client = this.webServer.accept();
                Runnable session = new SessionHandler(new Session(client), this.dataIO);
                Thread sessionThread = new Thread(session);
                sessionThread.start();
                System.out.println("Accepted Client: " + client.getInetAddress().getHostAddress());
            }
            catch (Exception e)
            {
                System.out.println("Error Accepting Client");
                e.printStackTrace();
                System.out.println(e.getCause());
            }
        }
        System.exit(2);
    }

    private void intDomain()
    {
        try
        {
            //Process for int Domain
            //First try to tell google domains to update with sh script
            //read from renew.txt for code from google
            //if code is a success status code then domain is initiated else start a 5 min timer to try again in 5 min
            for(int i = 0; i < this.credentials.length; i++)
            {

                //https://username:password@domains.google.com/nic/update?hostname=subdomain.yourdomain.com
                //Credentials Should be in Username Password Domain order
                 Runtime.getRuntime().exec("sudo bash reNewDomain.sh " +
                        this.credentials[i][0] +
                        " " +
                        this.credentials[i][1] +
                        " " +
                        this.domains[i]);
                File wgetOutput = new File(System.getProperty("user.dir") + "/renew.txt");
                System.out.println("wgetOutput Path: " + wgetOutput.getPath());

                String fileContents = "";
                Scanner input = new Scanner(wgetOutput);
                while (input.hasNextLine())
                {
                    fileContents += input.nextLine();
                }
                System.out.println(fileContents);


                if (fileContents.contains("good") ||
                        fileContents.contains("nochg"))
                {
                    this.domainsInit[i] = true;
                }

            }

            boolean allInit = true;
            for(boolean isInited: this.domainsInit)
            {
                if(!isInited)
                {
                    allInit = false;
                    break;
                }
            }
            if (allInit)
            {
                System.out.println("Domain Initialized");
                this.fiveMin.stop();
            }
            else if(this.fiveMin.getStatus())
            {
                System.out.println("A Domain Failed To Initialize Starting 5 Min Timer");
                this.fiveMin.start();
            }
            else
            {
                System.out.println("A Domain Failed To Initialize");
            }
        }
        catch (Exception e)
        {
             System.out.println("Error Setting Up DNS");
             e.printStackTrace();
             System.out.println(e.getCause());
        }
    }

    /*
     * @Function:
     */
    private void intServer()
    {
        try
        {
            if(this.commandThread == null)
            {
                Runnable userInput = new OperatorConsole(this.dataIO);
                this.commandThread = new Thread(userInput);
                this.commandThread.start();
            }
            this.webServer = new ServerSocket(80);
            if(!this.serverInt.getStatus())
            {
                this.serverInt.stop();
            }
        }
        catch (Exception e)
        {
            System.out.println("Error Creating Server on port 80");
            e.printStackTrace();
            System.out.println(e.getCause());
            this.serverInt.start();
        }
    }

    private class ComponentsListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {
            if(e.getSource().equals(serverInt))
            {
                if(count < 5)
                {
                    intServer();
                    count++;
                }
                else if(count > 5)
                {
                    System.out.println("Tried " + startUpTries + " Times and Failed");
                    System.exit(1);
                }
            }
            else if(e.getSource().equals(domainInt))
            {
                intDomain();
            }
            else if(e.getSource().equals(fiveMin))
            {
                intDomain();
            }
        }
    }
}
