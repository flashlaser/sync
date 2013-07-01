package com.weibo.wesync;

import com.weibo.wesync.data.GroupOperationType;


/**
 * For group message broadcast
 * @author Eric Liang
 */
public interface GroupMessageService {
	public void broadcast(String groupId, String from, String msgId);
	
	//broadcast successful operations on group, the extend is additional message for notice to client,
	//e.g. extend is the affected username when group member changes 
	public void broadcast(String groupId, GroupOperationType type, String extend);
}
