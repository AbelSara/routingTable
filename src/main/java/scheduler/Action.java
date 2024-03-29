package scheduler;

import json.Message;
import json.config.ChannelDetialConfig;
import main.Main;
import org.omg.PortableInterceptor.NON_EXISTENT;
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
    private int sendMessageCount;

    public Action(int target, Scheduler scheduler) {
        this.target = target;
        this.scheduler = scheduler;
        timeout = 0;
        waitingCount = 0;
        queue = new ArrayList<>();
        clearChannelInfo();
        sendMessageCount = 0;
    }

    public int getSendMessageCount() {
        return this.sendMessageCount;
    }

    public int getWaitingCount() {
        return this.waitingCount;
    }

    public int getChannelState() {
        return this.channelState;
    }

    public int getChannelId() {
        return this.channelId;
    }

    public int getChannelType() {
        return this.channelType;
    }

    private void clearChannelInfo() {
        channelType = Const.CHANNEL_TYPE_ERROR;
        channelState = Const.CHANNEL_STATE_NONE;
        channelId = 0;
        sendMessageCount = 0;
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
            if (message.errCode == Const.ERR_CODE_CHANNEL_BUILD_SOURCE_LIMIT || message.errCode == Const.ERR_CODE_CHANNEL_BUILD_TOTAL_LIMIT) {
                next = message.channelType;
            }
            clearChannelInfo();
            filterQueue();
            if (waitingCount > 0 || queue.size() > 0) {
                doRequest(next);
            }
        }
    }

    //接受destroy消息
    public void onDestroy() {
        if (channelState == Const.CHANNEL_STATE_SUCCESS) {
            System.out.println("on destroy");
            clearChannelInfo();
            RoutingTable.removeElement(target);
        }
        filterQueue();
        if (waitingCount > 0 || queue.size() > 0) {
            doRequest(getOtherType());
        }
    }

    //主动发送destroy消息
    public void doDestroy() {
        if (channelState == Const.CHANNEL_STATE_SUCCESS) {
            System.out.println("do destroy!");
            Message destroyMessage = getDestroyMessage();
            doSend(destroyMessage);
            clearChannelInfo();
        }
    }

    private Message getDestroyMessage() {
        Message message = Const.GetEmptyMessage();
        message.callType = Const.CALL_TYPE_CHANNEL_DESTROY;
        message.state = Const.CHANNEL_STATE_SUCCESS;
        message.sysMessage.target = target;
        message.errCode = Const.ERR_CODE_NONE;
        message.channelType = channelType;
        message.targetId = target;
        return message;
    }

    public void onPrepare() {
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
//            if(channelState==Const.CHANNEL_STATE_NONE){
//                doRequest(message.channelType);
//            }
            queue.add(message);
        }
    }

    //action send message
    public void doSend(Message message) {
        if (message.recvTime + Main.config.mainConfig.timeOut >= Main.curTime() + getConfig().lag) {
            message.channelId = channelId;
            if (message.callType != Const.CALL_TYPE_CHANNEL_DESTROY) {
                message.extMessage = RoutingTable.getRoutingTable();
                this.sendMessageCount++;
            }
            //发送消息成功时计数器增加
            scheduler.doSend(message, message.targetId);
        }
    }

    public int filterQueue() {
        ArrayList<Message> filtered = new ArrayList<>();
        for (Message message : queue) {
            ChannelDetialConfig selfConf = getConfig();
            float lag = selfConf == null ? 0 : selfConf.lag;
            if (message.recvTime + Main.config.mainConfig.timeOut >= Main.curTime() + lag) {
                filtered.add(message);
            }
        }
        queue = filtered;
        return queue.size();
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
