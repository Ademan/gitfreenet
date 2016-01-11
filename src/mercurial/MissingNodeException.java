package gitfreenet.mercurial;

public class MissingNodeException extends Exception {
	NodeId node;
	public MissingNodeException(NodeId node) {
		this.node = node;
	}
	public String getMessage() {
		return "Cannot find node " + node;
	}
}
