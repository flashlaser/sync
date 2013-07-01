package com.weibo.wesync.command;

import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.FolderChange;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.WeSyncMessage.GetItemUnreadReq;
import com.weibo.wesync.data.WeSyncMessage.GetItemUnreadResp;
import com.weibo.wesync.data.WeSyncMessage.Unread;

/**
 * 
 * @author Eric Liang
 *
 */
public class GetItemUnreadHandler extends BaseHandler {

	public GetItemUnreadHandler(WeSyncService weSync) {
		super(weSync);
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);

		GetItemUnreadResp.Builder respBuilder = GetItemUnreadResp.newBuilder();
		List<String> folderIds = new LinkedList<String>();
		if( null != data ){
			try {
				GetItemUnreadReq req = GetItemUnreadReq.parseFrom(data);
				
				log.debug("GetItemUnread request by "+username+", "+req);

				for( String fid : req.getFolderIdList() ){
					if( !isAccessPermitted(username, new FolderID(fid) ) ){
						log.warn("Unpermited access: "+ username + " on folder " + fid);
						//TODO error code should be returned to client
						throw new RuntimeException();
					}
					if( !folderIds.contains(fid) ) folderIds.add(fid);
				}
			} catch (InvalidProtocolBufferException e) {
				log.warn("Invalid request from user "+username+" : "+uri);
				//TODO error code should be returned to client
				throw new RuntimeException();
			} 
		}
		
		SortedSet<FolderChange> changes = weSync.getDataService().removeAllFolderChanges( FolderID.onRoot(username) );
		if( null != changes ){
			for( FolderChange fc : changes ){
				if( fc.isAdd && !folderIds.contains(fc.childId) ){
					folderIds.add( fc.childId );
				}
			}
		}
		
		List<Unread> unreadList = weSync.getDataService().getUnreadNumber( folderIds );
		respBuilder.addAllUnread(unreadList);
		
		return respBuilder.build().toByteArray();
	}

	private boolean isAccessPermitted(String username, FolderID folderId) {
		switch( folderId.type ){
		case Property:
			String propName = FolderID.getProperty(folderId);
			if( weSync.isPropertySupported(propName) ){
				return true;
			}
			break;
		default:
			break;
		}
		
		return FolderID.isBelongTo(folderId, username);
	}
}
