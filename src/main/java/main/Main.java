package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import conn.Channel;
import conn.GeneralChannel;
import json.Message;
import json.config.Config;
import scheduler.Scheduler;

import java.io.File;
import java.util.Date;
import java.util.List;

public class Main {

    public static Config config;

    public static double curTime() {
        return new Date().getTime() / 1000.0;
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        config = objectMapper.readValue(new File("config/client.json"), Config.class);
        Channel channel = new GeneralChannel();
        channel.initConfig(config);
        mainloop(channel);
    }

    public static void mainloop(Channel channel){
        Scheduler scheduler = new Scheduler(channel);
        while (true) {
            try {
                List<Message> message = channel.recv();
                for (Message msg : message) {
                    scheduler.onRecv(msg);
                }
                Thread.sleep(20);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
    }
}
