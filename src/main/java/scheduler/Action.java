package scheduler;

import json.Message;
import json.config.ChannelDetialConfig;
import main.Main;
import thridparty.RoutingTable;

import java.util.ArrayList;
import java.util.List;

public class Action {
    private int target;
    private double timeout;
    private int channelState, waitingCount;
    private List<Message> queue;
    private Scheduler scheduler;
    private int channelType;
    private int channelId;

    public Action(int target, Scheduler scheduler) {
        this.target = target;
        this.scheduler = scheduler;
        timeout = 0;
        waitingCount = 0;
        queue = new ArrayList<>();
        clearChannelInfo();
    }

    private void clearChannelInfo() {
        channelType = Const.CHANNEL_TYPE_ERROR;
        channelState = Const.CHANNEL_STATE_NONE;
        channelId = 0;
    }

    public boolean isValid() {
        return channelType != Const.CHANNEL_TYPE_ERROR && channelState == Const.CHANNEL_STATE_SUCCESS;
    }

    public int getOtherType() {
        return channelType == Const.CHANNEL_TYPE_NORMAL ? Const.CHANNEL_TYPE_FAST : Const.CHANNEL_TYPE_NORMAL;
    }

    public void doRequest(int channelType) {
        if (this.channelType != Const.CHANNEL_TYPE_ERROR) return;
        this.channelType = channelType;
        channelState = Const.CHANNEL_STATE_REQUEST;
        scheduler.sendChannelBuild(target, Const.STATE_REQUEST, Const.ERR_CODE_NONE, channelType);
    }

    public void onRequest(Message message) {
        int target = message.sysMessage.target;
        if (channelState != Const.CHANNEL_STATE_NONE) {
            if (scheduler.getId() < target) {
                scheduler.sendChannelBuild(target, Const.STATE_REFUSE,
                        Const.ERR_CODE_CHANNEL_BUILD_TARGET_REFUSE, message.channelType);
                return;
            }
        }
        scheduler.sendChannelBuild(target, Const.STATE_ACCEPT, Const.ERR_CODE_NONE, message.channelType);
        channelType = message.channelType;
        channelState = Const.CHANNEL_STATE_ACCEPT;
    }

    public void onSucc(Message message) {
        channelType = message.channelType;
        channelState = Const.CHANNEL_STATE_SUCCESS;
        channelId = message.channelId;
        filterQueue();
        queue.forEach(msg -> doSend(msg));
        queue.clear();
    }

    public void onRefuse(Message message) {
        if (channelState != Const.CHANNEL_STATE_SUCCESS) {
            System.out.println("on refuse");
            int next = getOtherType();
            clearChannelInfo();
            filterQueue();
            if (waitingCount > 0 || queue.size() > 0) {
                doRequest(next);
            }
        }
    }

    public void onDestroy(Message message) {
        if (channelState == Const.CHANNEL_STATE_SUCCESS) {
            System.out.println("on destroy");
            clearChannelInfo();
        }
        filterQueue();
        if (waitingCount > 0 || queue.size() > 0) {
            doRequest(getOtherType());
        }
    }

    public void onPrepare(Message message) {
        timeout = Math.max(timeout, Main.curTime() + Main.config.mainConfig.timeOut);
        waitingCount++;
        if (channelState == Const.CHANNEL_STATE_NONE) {
            doRequest(getOtherType());
        }
    }

    public void onSend(Message message) {
        if (scheduler.getId() == message.sysMessage.target) {
            System.out.println("succ received message: " + message.sysMessage.data);
            return;
        }
        waitingCount--;
        if (channelState == Const.CHANNEL_STATE_SUCCESS) {
            System.out.println("send directory");
            doSend(message);
        } else {
            System.out.println("add into cache");
            System.out.printf("channelState:%d\n", channelState);
            queue.add(message);
        }
    }

    //action send message
    public void doSend(Message message) {
        if (message.recvTime + Main.config.mainConfig.timeOut >= Main.curTime() + getConfig().lag) {
            message.channelId = channelId;
            message.extMessage = RoutingTable.getRoutingTable();
            scheduler.doSend(message, message.targetId);
        }
    }

    public void filterQueue() {
        ArrayList<Message> filtered = new ArrayList<>();
        for (Message message : queue) {
            ChannelDetialConfig selfConf = getConfig();
            float lag = selfConf == null ? 0 : selfConf.lag;
            if (message.recvTime + Main.config.mainConfig.timeOut >= Main.curTime() + lag) {
                filtered.add(message);
            }
        }
        queue = filtered;
    }

    public ChannelDetialConfig getConfig() {
        switch (channelType) {
            case Const.CHANNEL_TYPE_FAST:
                return Main.config.channelConfig.highSpeed;
            case Const.CHANNEL_TYPE_NORMAL:
                return Main.config.channelConfig.normalSpeed;
            default:
                return null;
        }
    }
}
