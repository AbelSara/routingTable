package thridparty;

/**
 * @author Honghan Zhu
 */
public class TransmitNode {
    //下一跳结点
    private int nextNodeId;
    //延迟时间
    private float delay;

    public TransmitNode(){

    }

    public TransmitNode(int nextNodeId, float delay) {
        this.nextNodeId = nextNodeId;
        this.delay = delay;
    }

    public int getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(int nextNodeId) {
        this.nextNodeId = nextNodeId;
    }

    public float getDelay() {
        return delay;
    }

    public void setDelay(float delay) {
        this.delay = delay;
    }


}
