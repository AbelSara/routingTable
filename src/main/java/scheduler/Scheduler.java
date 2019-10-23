package scheduler;

import conn.Channel;
import json.Message;
import main.Main;
import thridparty.RoutingTable;
import thridparty.TransmitNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Scheduler {
    private Channel channel;
    Action[] action;
    Map<Integer, Integer> channelMap;
    private float fastDelay;
    private float normalDelay;
    //第一次发生ERR_CODE_CHANNEL_BUILD_LIMIT的目标
    Set<Integer> totalLimitLabel;
    //该节点所发消息总数
    private int sendMessageCount;
    //节点总数
    private int N;

    public Scheduler(Channel channel) {
        this.channel = channel;
        N = Main.config.mainConfig.nodeCount;
        action = new Action[N + 1];
        for (int i = 1; i <= N; i++) {
            action[i] = new Action(i, this);
        }
        channelMap = new HashMap<>();
        fastDelay = Main.config.channelConfig.highSpeed.lag;
        normalDelay = Main.config.channelConfig.normalSpeed.lag;
        totalLimitLabel = new HashSet<>();
        sendMessageCount = 0;
    }

    public int getId() {
        return channel.getId();
    }

    public void onRecv(Message message) {
        if (message.errCode != Const.ERR_CODE_NONE) {
            int selectChannelType;
            switch (message.errCode) {
                case Const.ERR_CODE_CHANNEL_BUILD_SOURCE_LIMIT:
                    selectChannelType = destroyChannel();
                    if (selectChannelType != Const.CHANNEL_TYPE_ERROR) {
                        message.channelType = selectChannelType;
                    } else {
                        return;
                    }
                    break;
                case Const.ERR_CODE_CHANNEL_BUILD_TARGET_LIMIT:
                    System.out.println("target limit!");
                    return;
                case Const.ERR_CODE_CHANNEL_BUILD_TOTAL_LIMIT:
                    //如果第一次出现total_limit错误，加入label中在下一次出现该错误时进行处理
                    if (!totalLimitLabel.contains(message.sysMessage.target)) {
                        totalLimitLabel.add(message.sysMessage.target);
                    } else {
                        selectChannelType = destroyChannel();
                        if (selectChannelType != Const.CHANNEL_TYPE_ERROR) {
                            message.channelType = selectChannelType;
                        } else {
                            return;
                        }
                    }
                    break;
            }
        }
        switch (message.callType) {
            case Const.CALL_TYPE_PREPARE:
                //如果路由表无法将消息正确发送channel_build消息
                if (!RoutingTable.getRoutingTable().containsKey(message.sysMessage.target)) {
                    action[message.sysMessage.target].onPrepare();
                }
                break;
            case Const.CALL_TYPE_SEND:
                //收到消息时如果extMessage中存在路由表信息则更新当前路由表；
                if (message.extMessage.size() > 1) {
                    float delay = message.channelType == Const.CHANNEL_TYPE_FAST ? fastDelay : normalDelay;
                    int nextNodeId = channelMap.getOrDefault(message.channelId, 0);
                    if (nextNodeId != 0) {
                        RoutingTable.updateRoutingTable(message.extMessage, delay, nextNodeId, getId());
                    }
                }
                //选择发送路由
                if (null != RoutingTable.getRoutingTable().get(message.sysMessage.target)) {
                    int nextNodeId = RoutingTable.getRoutingTable().get(message.sysMessage.target).getNextNodeId();
                    message.targetId = nextNodeId;
                    action[nextNodeId].onSend(message);
                } else {
                    message.targetId = message.sysMessage.target;
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
                    //如果通道成功建立在label集合中取出该标签
                    if (totalLimitLabel.contains(message.sysMessage.target)) {
                        totalLimitLabel.remove(message.sysMessage.target);
                    }
                } else {
                    switch (message.state) {
                        case Const.STATE_NOTICE:
                            message.channelType = Const.CHANNEL_TYPE_FAST;
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
                    action[target].onDestroy();
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
            if (target != 0) {
                sendMessageCount++;
            }
            channel.send(message, target);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int destroyChannel() {
        int targetId = 0;
        int minMessageCount = Integer.MAX_VALUE;
        for (int i = 1; i <= N; i++) {
            if (action[i].getChannelState() == Const.CHANNEL_STATE_SUCCESS && action[i].getSendMessageCount() < minMessageCount && action[i].getWaitingCount() == 0) {
                minMessageCount = action[i].getSendMessageCount();
                targetId = i;
            }
        }
        //如果没有符合条件的选择 返回error值不进行拆除
        if (targetId == 0) {
            return Const.CHANNEL_TYPE_ERROR;
        }
        int channelType = action[targetId].getChannelType();
        channelMap.remove(action[targetId].getChannelId());
        RoutingTable.removeElement(targetId);
        sendMessageCount -= action[targetId].getSendMessageCount();
        action[targetId].onDestroy();
        return channelType;
    }
}
