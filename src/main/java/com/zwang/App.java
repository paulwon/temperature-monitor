package com.zwang;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;



public class App {
    private static  int intervalMinutes;
    private static  int temperatureThresholdYellow;
    private static  int temperatureThresholdRed;
    private static  int temperatureRecoveryThreshold;
    private static  int temperatureThresholdDiff;
    private static boolean mailYellowAlertSent = false;
    private static boolean mailRedAlertSent = false;
    private static boolean mailRecoverSent = false;
    private static String emailUsername = "";
    private static String emailPassword = "";
    private static String emailTo = "";
    private static String jsonFilePath = "./temperature.json";
    private static String msgTemperatureThresholdDetails;
    private static String msgTemperatureHistory = "";
    private static int numberOfLinesTemperature;
    private static String usernameSpeaker, passwordSpeaker, hostSpeaker;
    private static int portSpeaker;
    private static String commandSpeaker;
    private static long defaultTimeoutSeconds = 3l;
    private static long defaultTimeoutSecondsSpeaker = 40l;


    
    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        InputStream input = null;
        input = new FileInputStream("./config.properties");
        prop.load(input);

        emailUsername = prop.getProperty("emailUsername");
        emailPassword = prop.getProperty("emailPassword");
        emailTo = prop.getProperty("emailTo");
        temperatureThresholdYellow =  Integer.parseInt(prop.getProperty("temperatureThresholdYellow"));
        temperatureThresholdRed =  Integer.parseInt(prop.getProperty("temperatureThresholdRed"));
        temperatureThresholdDiff =  Integer.parseInt(prop.getProperty("temperatureThresholdDiff"));
        temperatureRecoveryThreshold = temperatureThresholdYellow - temperatureThresholdDiff;
        intervalMinutes = Integer.parseInt(prop.getProperty("intervalMinutes"));
        msgTemperatureThresholdDetails = 
            "\nThreshold triggering yellow alert notification:  " + temperatureThresholdYellow + 
            "\nThreshold triggering red alert notification:  " + temperatureThresholdRed + 
            "\nThreshold triggering recovery notification:  " + temperatureRecoveryThreshold
            ;
        numberOfLinesTemperature = Integer.parseInt(prop.getProperty("numberOfLinesTemperature"));

        // Connection info of the monitored device
        String username = prop.getProperty("username");
        String password = prop.getProperty("password");
        String host = prop.getProperty("host");
        int port = Integer.parseInt(prop.getProperty("port"));
        String command = prop.getProperty("command");

        // Read the properties for the connection information of the Speaker (MacMini)
        usernameSpeaker = prop.getProperty("usernameSpeaker");
        passwordSpeaker = prop.getProperty("passwordSpeaker");
        hostSpeaker = prop.getProperty("hostSpeaker");
        portSpeaker = Integer.parseInt(prop.getProperty("portSpeaker"));
        
        new FileOutputStream(jsonFilePath).close();

        // appEntry(username, password, host, port, defaultTimeoutSeconds, command);

        System.out.println("Temperature yellow alert threshold: " + temperatureThresholdYellow);
        System.out.println("Temperature red alert threshold: " + temperatureThresholdRed);
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
        exec.scheduleAtFixedRate(appEntryRunnable , 0, intervalMinutes, TimeUnit.MINUTES);

    }
    
    private static void appEntry(String username, String password, String host, int port, long defaultTimeoutSeconds, String command) throws Exception {
        String temperatureStr = sshToDevice("AP",username, password, host, port, defaultTimeoutSeconds, command);
        // System.out.println(temperatureStr);
        Temperature temperature =  getTemperature(temperatureStr);
        System.out.println(temperature.getTimestampString() + ", " + temperature.getValue());
        writeFile(temperature);
        // Send yellow alert
        if (temperature.getValue() >= temperatureThresholdYellow && temperature.getValue() < temperatureThresholdRed) {
            String msg = "Yellow alert: Temperature is high!. The current temperature is " + temperature.getValue();
            System.out.println(msg );
            // Speaker
            commandSpeaker = "say The temperature is high in the server room && sleep 10 && say The temperature is high in the server room && sleep 10 && say The temperature is high in the server room  \n";
            try {
               sshToDevice("Speaker",usernameSpeaker, passwordSpeaker, hostSpeaker, portSpeaker, defaultTimeoutSecondsSpeaker, commandSpeaker);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Email
            if (mailYellowAlertSent == false) {
                System.out.println("Send yellow alert email ...");
                String mailSubject = "Lab temperature monitoring - yellow alert";
                sendEmail(msg, mailSubject);
                mailYellowAlertSent = true;
                mailRecoverSent = false;
            } else {
                System.out.println("Not sending the alert mail because it has already been sent");
            }
                    
        }
        // Send red alert
        if (temperature.getValue() >= temperatureThresholdRed) {
            String msg = "Red alert: Temperature is too high !!!. The current temperature is " + temperature.getValue();
            System.out.println(msg );
            // speaker
            commandSpeaker = "say The temperature is extremely high in the server room && sleep 10 && say The temperature is extremely high in the server room && sleep 10 && say The temperature is extremely high in the server room  \n";
            try {
                sshToDevice("Speaker",usernameSpeaker, passwordSpeaker, hostSpeaker, portSpeaker, defaultTimeoutSecondsSpeaker, commandSpeaker);
            } catch (Exception e) {
                 e.printStackTrace();
            }
            // email
            if (mailRedAlertSent == false) {
            System.out.println("Send red alert email ...");
            String mailSubject = "Lab temperature monitoring - red alert";
            sendEmail(msg, mailSubject);
            mailRedAlertSent = true;
            mailRecoverSent = false;
            } else {
                System.out.println("Not sending the alert mail because it has already been sent");
            }
                    
        }
        // Send recovery notification
        if (temperature.getValue() <= (temperatureRecoveryThreshold)) {
            String msg = "Temperature is normal. The current temperature is " + temperature.getValue();
            System.out.println(msg);
            if ((mailYellowAlertSent == true || mailRedAlertSent == true ) && mailRecoverSent == false) {
                System.out.println("Send email for for recovery ...");
                String mailSubject = "Lab temperature monitoring - recovered";
                sendEmail(msg, mailSubject);
                mailYellowAlertSent = false;
                mailRedAlertSent = false;
                mailRecoverSent = true;
            }  else {
                System.out.println("Not sending the recovery mail because it has already been sent or the alert mail has not been sent");
            }
            
        }

    }

    private static String getTemperatureHistory(String jsonFilePath) throws IOException {
        String content = "";
        Path path = Paths.get(jsonFilePath);
        long lines = 0;
        lines = Files.lines(path).count();
                   
        if (lines < numberOfLinesTemperature) {
            content = Files.readString(path);

        } else {
            List<String> linesList = readLastLine(new File(jsonFilePath), numberOfLinesTemperature);
            content = String.join("\n", linesList);
        }

        return content;

    }

    public static List<String> readLastLine(File file, int numLastLineToRead) {

        List<String> result = new ArrayList<>();

        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(file, StandardCharsets.UTF_8)) {

            String line = "";
            while ((line = reader.readLine()) != null && result.size() < numLastLineToRead) {
                result.add(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;

    }

    private static void writeFile(Temperature temperature) throws IOException {
        String textToAppend = "{\"timestampString\":"  + "\"" +temperature.getTimestampString()  + "\"" 
                             + "," + "\"value\":" + temperature.getValue() + "}";
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
       String timestampString = date.toString();

    //    System.out.println("Time in Milliseconds: " + timestamp);
       return new Temperature(timestamp, timestampString, value);
       
    }
    
    public static String sshToDevice(String deviceType, String username, String password, String host, int port,long defaultTimeoutSeconds, String command) throws Exception {
        System.out.println("ssh to "+ deviceType +" host " + host);
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

    
    private static void sendEmail(String msg, String mailSubject) throws AddressException, MessagingException, IOException {
        msgTemperatureHistory =  "\nHistory of temperature reading:\n" + getTemperatureHistory(jsonFilePath);

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
            message.setSubject(mailSubject);
            message.setText(msg + msgTemperatureThresholdDetails + msgTemperatureHistory);

            Transport.send(message);

            System.out.println("Email has been sent.");

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
