package com.weibo.wesync;

/**
 * @author Eric Liang
 *
 */
public interface CommandHandler {
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean fromListener);
}
