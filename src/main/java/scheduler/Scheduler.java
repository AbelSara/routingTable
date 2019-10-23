package scheduler;

import com.fasterxml.jackson.databind.ser.impl.MapEntrySerializer;
import conn.Channel;
import json.Message;
import main.Main;
import thridparty.RoutingTable;
import thridparty.TransmitNode;

import java.util.HashMap;
import java.util.Map;

public class Scheduler {
    private Channel channel;
    Action[] action;
    Map<Integer, Integer> channelMap;
    private float fastDelay;
    private float normalDelay;

    public Scheduler(Channel channel) {
        this.channel = channel;
        int N = Main.config.mainConfig.nodeCount;
        action = new Action[N + 1];
        for (int i = 1; i <= N; i++) {
            action[i] = new Action(i, this);
        }
        channelMap = new HashMap<>();
        fastDelay = Main.config.channelConfig.highSpeed.lag;
        normalDelay = Main.config.channelConfig.normalSpeed.lag;
    }

    public int getId() {
        return channel.getId();
    }

    public void onRecv(Message message) {
        if (message.errCode != Const.ERR_CODE_NONE) {
            return;
        }
        switch (message.callType) {
            case Const.CALL_TYPE_PREPARE:
                //如果路由表无法将消息正确发送channel_build消息
                if (!RoutingTable.getRoutingTable().containsKey(message.sysMessage.target)) {
                    action[message.sysMessage.target].onPrepare(message);
                }
                break;
            case Const.CALL_TYPE_SEND:
                //收到消息时如果extMessage中由路由表信息则更新当前路由表；
                if (message.extMessage.size() > 1) {
                    float delay = message.channelType == Const.CHANNEL_TYPE_FAST ? fastDelay : normalDelay;
                    int nextNodeId = channelMap.get(message.channelId);
                    RoutingTable.updateRoutingTable(message.extMessage, delay, nextNodeId, getId());
                }
                //选择发送路由
                if (null != RoutingTable.getRoutingTable().get(message.sysMessage.target)) {
                    int nextNodeId = RoutingTable.getRoutingTable().get(message.sysMessage.target).getNextNodeId();
                    message.targetId = nextNodeId;
                    action[nextNodeId].onSend(message);
                }else{
                    message.targetId=message.sysMessage.target;
                    action[message.sysMessage.target].onSend(message);
                }
                break;
            case Const.CALL_TYPE_SYS:
                break;
            case Const.CALL_TYPE_CHANNEL_BUILD:
                if (message.channelId != 0) {
                    action[message.sysMessage.target].onSucc(message);
                    channelMap.put(message.channelId, message.sysMessage.target);
                    //建立信道时在路由表中添加必须结点
                    float delay = message.channelType == Const.CHANNEL_TYPE_FAST ? fastDelay : normalDelay;
                    RoutingTable.addElement(message.sysMessage.target, new TransmitNode(message.sysMessage.target, delay));
                } else {
                    switch (message.state) {
                        case Const.STATE_NOTICE:
                            action[message.sysMessage.target].onRequest(message);
                            break;
                        case Const.STATE_REFUSE:
                            action[message.sysMessage.target].onRefuse(message);
                            break;
                    }
                }
                break;
            case Const.CALL_TYPE_CHANNEL_DESTROY:
                int target = channelMap.getOrDefault(message.channelId, 0);
                if (target != 0) {
                    action[target].onDestroy(message);
                    channelMap.remove(message.channelId);
                    //删除当前路由表结点
                    RoutingTable.removeElement(target);
                }
                break;
        }
    }

    public void sendChannelBuild(int target, int state, int errCode, int channelType) {
        Message message = Const.GetEmptyMessage();
        message.callType = Const.CALL_TYPE_CHANNEL_BUILD;
        message.state = state;
        message.sysMessage.target = target;
        message.errCode = errCode;
        message.channelType = channelType;
        doSend(message, 0);
    }

    public void doSend(Message message, int target) {
        try {
            channel.send(message, target);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
