package com.weibo.wesync;

import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.weibo.wesync.data.WeSyncMessage.Notice;

/**
 * @author Eric Liang
 */
public class FakeNoticeService implements NoticeService {
	Logger log = LoggerFactory.getLogger(FakeNoticeService.class);
	
	public Queue<Notice> pending = new LinkedList<Notice>();
	
	@Override
	public void send(String username, Notice notice) {	
		log.debug("Send notice to "+username+" : "+notice);
		pending.add(notice);
	}
}
