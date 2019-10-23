package json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import thridparty.TransmitNode;
import java.util.Date;
import java.util.HashMap;

public class Message implements Cloneable{
    public String callType;
    public int channelId;
    public SysMessage sysMessage;
    public HashMap<Integer, TransmitNode> extMessage;
    public int state;
    public int errCode;
    public int channelType;
    public int targetId;
    @JsonIgnore
    public double recvTime;

    public Message() {
        recvTime = new Date().getTime() / 1000.0;
    }

    @Override
    public Message clone() throws CloneNotSupportedException {
        return (Message) super.clone();
    }
}
