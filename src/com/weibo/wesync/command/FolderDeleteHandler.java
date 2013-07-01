package com.weibo.wesync.command;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.WeSyncMessage.FolderDeleteReq;

/**
 * 
 * @author Eric Liang
 *
 */
public class FolderDeleteHandler extends BaseHandler {

	public FolderDeleteHandler(WeSyncService weSync) {
		super(weSync);
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);
		
		FolderDeleteReq req;
		try {
			req = FolderDeleteReq.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username+" : "+uri);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		log.debug("FolderDelete request by "+username+", "+req);

		String folderId;
		if( req.hasFolderId() ){
			folderId = req.getFolderId();
		}else{
			folderId = FolderID.onConversation(username, req.getUserChatWith() );
		}
		
		if( req.getIsContentOnly() ){
			weSync.getDataService().cleanupFolder(folderId);
		}else{
			weSync.getDataService().removeFolder(username, folderId);
		}
		
		//No exception means successful
		return null;
	}
}
