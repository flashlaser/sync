package com.weibo.wesync.command;

import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;

/**
 * I'll do nothing, and I mean it. Bad guys will talk to me.
 * 
 * @author Eric Liang
 *
 */
public class NullHandler extends BaseHandler {

	public NullHandler(WeSyncService weSync) {
		super(weSync);
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.warn("Suspicious attack from "+username+" : "+uri);
		return null;
	}

}
