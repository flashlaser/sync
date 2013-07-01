package com.weibo.wesync.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.DataService;
import com.weibo.wesync.GroupMessageService;
import com.weibo.wesync.PluginHandler;
import com.weibo.wesync.data.GroupOperationType;
import com.weibo.wesync.data.MetaMessageType;
import com.weibo.wesync.data.WeSyncMessage.GroupOperation;
import com.weibo.wesync.data.WeSyncMessage.Meta;

/**
 * 
 * @author Eric Liang
 *
 */
public class GroupHandler implements PluginHandler {
	protected Logger log = LoggerFactory.getLogger(GroupHandler.class);
	private final static String pluginName = "group";

	private final DataService dataService;
	private final GroupMessageService groupMessageService;

	@Inject
	public GroupHandler(DataService dataService, GroupMessageService groupMessageService) {
		this.dataService = dataService;
		this.groupMessageService = groupMessageService;
	}

	@Override
	public Meta handle(String username, Meta req) {
		log.debug("Got request from "+username+", "+req);
		
		GroupOperation oper;
		try {
			oper = GroupOperation.parseFrom(req.getContent());
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		log.debug("Group operation from "+username+", "+oper);

		if( !dataService.isMember(username, oper.getGroupId()) ){
			log.warn("Can't modify group "+oper.getGroupId()+" for non-member user "+username);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		GroupOperationType operType = GroupOperationType.valueOf(oper.getType().byteAt(0));
		String extendToBroadcast = null;
		switch( operType ){
		case addMember:
			extendToBroadcast = oper.getUsername();
			dataService.addMember( oper.getGroupId(), oper.getUsername() );
			break;
		case removeMember:
			extendToBroadcast = oper.getUsername();
			dataService.removeMember(oper.getGroupId(), oper.getUsername());
			break;
		case quitGroup:
			extendToBroadcast = username;
			dataService.removeMember(oper.getGroupId(), username);
			break;
		default:
			log.error("Undefined group operation from user: "+ username);
			throw new RuntimeException();
		}
		
		groupMessageService.broadcast(oper.getGroupId(), operType, extendToBroadcast);
		
		return Meta.newBuilder()
				.setId(req.getId())
				.setType( ByteString.copyFrom( new byte[]{ MetaMessageType.operation.toByte() }) )
				.build();
	}

	@Override
	public String pluginName() {
		return pluginName;
	}
}
