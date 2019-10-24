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

    public static void mainloop(Channel channel) {
        Scheduler scheduler = new Scheduler(channel);
        while (true) {
            try {
                List<Message> messages = channel.recv();
                for (Message msg : messages) {
                    scheduler.onRecv(msg);
                }
                //将积累的channel_build消息中划得来发送的消息发送出去
                if (messages.size() == 0 && scheduler.getMessageQueue().size() > 0) {
                    Message message=scheduler.getMessageQueue().remove();
                    scheduler.onRecv(message);
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
