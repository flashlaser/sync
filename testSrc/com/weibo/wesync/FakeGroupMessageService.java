package com.weibo.wesync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.weibo.wesync.data.GroupOperationType;

/**
 * @author Eric Liang
 */
public class FakeGroupMessageService implements GroupMessageService{
	Logger log = LoggerFactory.getLogger(FakeNoticeService.class);
	
	private DataService dataService;
	
	@Inject
	public FakeGroupMessageService(DataService dataService){
		this.dataService = dataService;
	}
	
	@Override
	public void broadcast(String groupId, String from, String msgId) {	
		log.debug("Broadcast message "+ msgId +" from "+from+" in group "+groupId);
		dataService.broadcastNewMessage(groupId, from, msgId);
	}

	@Override
	public void broadcast(String groupId, GroupOperationType type, String extend) {
		log.debug("Broadcast group operation "+ type + " "+ extend + " in group "+ groupId);
		//TODO broadcast operation
		dataService.broadcastMemberChange(groupId, type, extend);
	}
}
