package com.weibo.wesync;

import com.weibo.wesync.data.WeSyncMessage.Notice;

/**
 * @author Eric Liang
 */
public interface NoticeService {
	public void send(String username, Notice notice);
}
