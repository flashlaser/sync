package com.weibo.wesync.command;

import java.util.SortedSet;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.FolderChange;
import com.weibo.wesync.data.FolderChild;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.SyncKey;
import com.weibo.wesync.data.WeSyncMessage.FolderSyncReq;
import com.weibo.wesync.data.WeSyncMessage.FolderSyncResp;

/**
 * @author Eric Liang
 */
public class FolderSyncHandler extends BaseHandler {	
	
	public FolderSyncHandler(WeSyncService weSync) {
		super(weSync);
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);
		
		FolderSyncReq req;
		try {
			req = FolderSyncReq.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username+" : "+uri);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		log.debug("FolderSync request by "+username+", "+req);

		String folderId = req.getId();		
		if( !FolderID.isBelongTo(folderId, username) ){
			log.warn("Unpermited access: "+ username + " on folder " + folderId);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		FolderSyncResp.Builder respBuilder = FolderSyncResp.newBuilder();
		respBuilder.setId( folderId );
		
		String nextKey = SyncKey.emptySyncKey(folderId, false);
		String syncKey = req.getKey();
		if( syncKey.equals(TAG_SYNC_KEY) ){
			//initial foldersync will get all the content of root folder
			SortedSet<FolderChild> children = weSync.getDataService().getChildren(folderId);
			if( null != children ){
				for( FolderChild c : children){
					respBuilder.addChildId(c.id);
				}
			}
		}else{
			weSync.getDataService().cleanupSynchronizedChanges(folderId, syncKey);
			SortedSet<FolderChange> changes = weSync.getDataService().getFolderChanges(folderId);
			if( null != changes && !changes.isEmpty() ){
				nextKey = SyncKey.syncKeyOnChange(folderId, changes.last() );
				for (FolderChange fc : changes) {
					// FIXME judge the operation: add or delete
					if( fc.isAdd ){
						respBuilder.addChildId(fc.childId);
					}
				}
			}
		}
		
		respBuilder.setNextKey( nextKey );
		return respBuilder.build().toByteArray();
	}
}
