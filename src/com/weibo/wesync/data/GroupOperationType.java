package com.weibo.wesync.data;

/**
 * 
 * @author Eric Liang
 *
 */
public enum GroupOperationType {
	addMember((byte) 0x01), 
	removeMember((byte) 0x02), 
	quitGroup((byte)0x03),
	unknown((byte) 0x0);

	private final byte code;

	private GroupOperationType(byte code) {
		this.code = code;
	}

	public byte toByte() {
		return code;
	}

	public static GroupOperationType valueOf(final byte code) {
		for (GroupOperationType t : GroupOperationType.values()) {
			if (code == t.code)
				return t;
		}
		return unknown;
	}
}
