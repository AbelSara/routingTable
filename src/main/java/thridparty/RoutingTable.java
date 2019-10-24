package thridparty;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Honghan Zhu
 */
public class RoutingTable {
    //key: targetNodeId
    //value: nextNodeId & delay
    private static HashMap<Integer, TransmitNode> routingTable = new HashMap<>();

    private RoutingTable() {
    }

    public static HashMap<Integer, TransmitNode> getRoutingTable() {
        return routingTable;
    }

    //通道建立成功时增加表项
    public static void addElement(Integer targetNodeId, TransmitNode transmitNode) {
        routingTable.put(targetNodeId, transmitNode);
    }

    //通道拆除时删除表项
    public static void removeElement(Integer targetNodeId) {
        Iterator<Map.Entry<Integer, TransmitNode>> iterator = routingTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TransmitNode> entry = iterator.next();
            if (entry.getValue().getNextNodeId() == targetNodeId) {
                iterator.remove();
            }
        }
    }

    public static void updateRoutingTable(HashMap otherRoutingTable, float delay, int nextNodeId, int selfNodeId) {
        //将可达信息添加到该结点的路由表中
        Iterator<Map.Entry<Integer, TransmitNode>> iterator = otherRoutingTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TransmitNode> entry = iterator.next();
            int targetNodeId = entry.getKey();
            //如果更新的targetNode为当前结点，则不需要更新
            if (targetNodeId == selfNodeId) {
                continue;
            }
            TransmitNode transmitNode = entry.getValue();
            float transmitDelay = delay + transmitNode.getDelay();
            if (null == routingTable.get(targetNodeId)) {
                //没有到达targetNode的路由则添加进路由表
                routingTable.put(targetNodeId, new TransmitNode(nextNodeId, transmitDelay));
            } else if (routingTable.get(targetNodeId).getDelay() > transmitNode.getDelay() + delay) {
                //当前传输时间大于更新后的传输时间则更新路由表
                routingTable.put(targetNodeId, new TransmitNode(nextNodeId, transmitDelay));
            }
        }
        //将当前路由表中由下一跳转发但下一跳中没有转发信息的路由项删除
        iterator = routingTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TransmitNode> entry = iterator.next();
            int targetNodeId = entry.getKey();
            TransmitNode transmitNode = entry.getValue();
            if (targetNodeId != nextNodeId && transmitNode.getNextNodeId() == nextNodeId && null == otherRoutingTable.get(targetNodeId)) {
                iterator.remove();
            }
        }
    }

    public static String toStr() {
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<Integer, TransmitNode>> iterator = routingTable.entrySet().iterator();
        sb.append("Routing Table:{");
        while (iterator.hasNext()) {
            Map.Entry<Integer, TransmitNode> entry = iterator.next();
            int key = entry.getKey();
            TransmitNode transmitNode = entry.getValue();
            sb.append('[');
            sb.append("target:").append(key).append(' ');
            sb.append("nextId:").append(transmitNode.getNextNodeId()).append(' ');
            sb.append("delay:").append(transmitNode.getDelay()).append(']');
        }
        sb.append('}');
        return sb.toString();
    }
}
