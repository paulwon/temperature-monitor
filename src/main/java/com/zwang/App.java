package com.zwang;

import com.zwang.Temperature;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;

import java.util.Date;
import java.sql.Timestamp;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;


public class App {
    private static final int minutes = 1;
    private static  int temperatureThreshold;
    private static  int temperatureRecoveryThreshold;
    private static  int temperatureThresholdDiff;
    private static boolean mailAlertSent = false;
    private static boolean mailRecoverSent = false;
    private static String emailUsername = "";
    private static String emailPassword = "";
    private static String emailTo = "";
    private static String jsonFilePath = "./temperature.json";

    
    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        InputStream input = null;
        input = new FileInputStream("./config.properties");
        prop.load(input);
        String username = prop.getProperty("username");
        String password = prop.getProperty("password");
        emailUsername = prop.getProperty("emailUsername");
        emailPassword = prop.getProperty("emailPassword");
        emailTo = prop.getProperty("emailTo");
        temperatureThreshold =  Integer.parseInt(prop.getProperty("temperatureThreshold"));
        temperatureThresholdDiff =  Integer.parseInt(prop.getProperty("temperatureThresholdDiff"));

        temperatureRecoveryThreshold = temperatureThreshold - temperatureThresholdDiff;

        String host = prop.getProperty("host");
        int port = Integer.parseInt(prop.getProperty("port"));
        long defaultTimeoutSeconds = 3l;
        String command = prop.getProperty("command");;

        new FileOutputStream(jsonFilePath).close();

        // appEntry(username, password, host, port, defaultTimeoutSeconds, command);

        System.out.println("Temperature alert threshold: " + temperatureThreshold);
        System.out.println("Temperature recovery threshold: " + temperatureRecoveryThreshold);

        Runnable appEntryRunnable = new Runnable() {
            public void run() {
                try {
                    appEntry(username, password, host, port, defaultTimeoutSeconds, command);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };

        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
        exec.scheduleAtFixedRate(appEntryRunnable , 0, minutes, TimeUnit.MINUTES);

    }
    
    private static void appEntry(String username, String password, String host, int port, long defaultTimeoutSeconds, String command) throws Exception {
        String temperatureStr = sshToAp(username, password, host, port, defaultTimeoutSeconds, command);
        // System.out.println(temperatureStr);
        Temperature temperature =  getTemperature(temperatureStr);
        System.out.println(temperature.getTimestamp() + " " + temperature.getValue());
        writeFile(temperature);
        if (temperature.getValue() >= temperatureThreshold) {
            String msg = "Temperature is too high !!!. The current temperature is " + temperature.getValue();
            System.out.println(msg );
            if (mailAlertSent == false) {
                System.out.println("Send alert email ...");
                sendEmail(msg);
                mailAlertSent = true;
                mailRecoverSent = false;
            } else {
                System.out.println("Not sending the alert mail because it has already been sent");
            }
                    
        }

        if (temperature.getValue() <= (temperatureRecoveryThreshold)) {
            String msg = "Temperature is normal. The current temperature is " + temperature.getValue();
            System.out.println(msg);
            if (mailAlertSent == true && mailRecoverSent == false) {
                System.out.println("Send email for for recovery ...");
                sendEmail(msg);
                mailAlertSent = false;
                mailRecoverSent = true;
            }  else {
                System.out.println("Not sending the recovery mail because it has already been sent or the alert mail has not been sent");
            }
            
        }

    }

    private static void writeFile(Temperature temperature) throws IOException {
        String textToAppend = "{\"timestamp\":" + temperature.getTimestamp() + "," + "\"value\":" + temperature.getValue() + "}";
        FileWriter fileWriter = new FileWriter(jsonFilePath, true);
        try (PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.println(textToAppend);
        }
        


    }

    private static void printMsgAndExit(String msg) {
        System.out.println(msg);
        System.exit(1);

    }
    private static Temperature getTemperature(String temperatureValue) {
       String[] temperatureStrSplit = temperatureValue.split("\n");
       String temperatureLine = "";
       for (String i: temperatureStrSplit) {
        if (i.contains("Current temperature")) {
            temperatureLine = i;
            break;
        } 
       }
       if (temperatureLine == "") {
        printMsgAndExit("Temperature value is not found in the CLI output");
        
       }
       int value = Integer.parseInt(temperatureLine.split(":")[1].split("\\(")[0].strip());

       Date date= new Date();
       long timestamp = date.getTime();
    //    System.out.println("Time in Milliseconds: " + timestamp);
       return new Temperature(timestamp, value);
       
    }
    
    public static String sshToAp(String username, String password, String host, int port,long defaultTimeoutSeconds, String command) throws Exception {
        System.out.println("ssh to AP. host " + host);
        SshClient client = SshClient.setUpDefaultClient();
        client.start();
        try (ClientSession session = client.connect(username, host, port).verify(defaultTimeoutSeconds, TimeUnit.SECONDS).getSession()) {
            session.addPasswordIdentity(password);
            session.auth()
                    .verify(defaultTimeoutSeconds, TimeUnit.SECONDS);
            try (ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
                    ByteArrayOutputStream errorResponseStream = new ByteArrayOutputStream();
                    ClientChannel channel = session.createChannel(Channel.CHANNEL_SHELL)) {
                channel.setOut(responseStream);
                channel.setErr(errorResponseStream);
                try {
                    channel.open()
                            .verify(defaultTimeoutSeconds, TimeUnit.SECONDS);
                    try (OutputStream pipedIn = channel.getInvertedIn()) {
                        pipedIn.write(command.getBytes());
                        pipedIn.flush();
                    }
                    channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED),
                            TimeUnit.SECONDS.toMillis(defaultTimeoutSeconds));
                    String errorString = new String(errorResponseStream.toByteArray());
                    if (!errorString.isEmpty()) {
                        throw new Exception(errorString);
                    }
                    String responseString = new String(responseStream.toByteArray());
                    return responseString;
                } finally {
                    channel.close(false);
                }
            }
        } finally {
            client.stop();
        }
    }

    private static void sendEmail(String msg) throws AddressException, MessagingException {
        final String username = App.emailUsername;
        final String password = App.emailPassword;
        final String emailTo = App.emailTo;

        // System.out.println(emailUsername + " " + emailPassword);

        Properties prop = new Properties();
		prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true"); //TLS
        prop.put("mail.smtp.ssl.protocols", "TLSv1.2");
        
        Session session = Session.getInstance(prop,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(emailTo)
            );
            message.setSubject("Lab temperature alert");
            message.setText(msg);

            Transport.send(message);

            System.out.println("Email has been sent.");

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
