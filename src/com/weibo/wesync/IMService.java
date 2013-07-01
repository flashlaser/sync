package com.weibo.wesync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.WeSyncMessage.SyncReq;

public class IMService implements CommandListener {
	private final Logger log = LoggerFactory.getLogger(IMService.class);
	WeSyncService weSync;
	
	@Inject
	public IMService(WeSyncService weSync){
		this.weSync = weSync;
		this.weSync.registerCommandListener(this);
	}

	@Override
	public boolean handle(Command comm, ByteString data) {
		log.debug("Heard about command: "+comm);
		if( comm.equals(Command.Sync) ){
			try {
				return handleSync(SyncReq.parseFrom(data));
			} catch (InvalidProtocolBufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return false;
	}

	private boolean handleSync(SyncReq req) {
		FolderID folderId = new FolderID( req.getFolderId() );
		
		//TODO group support
		
		String fromUser = FolderID.getUsername(folderId);
		String userChatWith = FolderID.getUserChatWith(folderId);
		String receiverFolderId = FolderID.onConversation(userChatWith, fromUser);
		
		SyncReq toReceiver = SyncReq.newBuilder(req)
				.setFolderId(receiverFolderId)
				.build();
		
		WeSyncURI uri = new WeSyncURI();
		uri.command = Command.Sync.toByte();
		uri.protocolVersion = weSync.version();
		
		weSync.handle(userChatWith, uri, toReceiver.toByteArray(), true);
		return true;
	}
	
}
