public class Command {
	char cmd;
	String addr;
	
	public Command(char cmd, String addr) {
		this.cmd = cmd; // 'r' or 'w'
		this.addr = addr; // Hex string address
	}

    public String toString() {
        return "command: " + cmd + " address: " + addr;
    }
}
