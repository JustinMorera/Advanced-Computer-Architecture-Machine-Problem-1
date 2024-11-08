public class Block {
    Integer clockCycle;
    boolean dirty;
    boolean valid;
    String address; // Hex string address

    public Block(int clockCycle, String address) {
        this.clockCycle = clockCycle;
        this.dirty = false;
        this.valid = true;
        this.address = address; // Hex string address
    }
}
