package com.weibo.wesync.command;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.WeSyncMessage.FolderCreateReq;
import com.weibo.wesync.data.WeSyncMessage.FolderCreateResp;

/**
 * @author Eric Liang
 */
public class FolderCreateHandler extends BaseHandler {

	public FolderCreateHandler(WeSyncService weSync) {
		super(weSync);
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);
		
		FolderCreateReq req;
		try {
			req = FolderCreateReq.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username+" : "+uri);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		log.debug("FolderCreate request by "+username+", "+req);

		String folderId;
		if( req.getAnotherUserCount() > 0 ){
			//GroupChat
			String groupId = weSync.getDataService().createGroup( username, req.getAnotherUserList() );
			folderId = weSync.getDataService().newGroupChat(username, groupId);
		}else{
			weSync.getDataService().newConversation(username, req.getUserChatWith() );
			//TODO return folder id from data service?
			folderId = FolderID.onConversation(username, req.getUserChatWith() );
		}
				
		FolderCreateResp resp = FolderCreateResp.newBuilder()
				.setFolderId( folderId )
				.setUserChatWith( req.getUserChatWith() )
				.build();
		
		return resp.toByteArray();
	}

}
