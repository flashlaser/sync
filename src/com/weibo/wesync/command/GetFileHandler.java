package com.weibo.wesync.command;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.FileID;
import com.weibo.wesync.data.Group;
import com.weibo.wesync.data.WeSyncMessage.FileData;

/**
 * @author Eric Liang
 */
public class GetFileHandler extends BaseHandler {

	public GetFileHandler(WeSyncService weSync) {
		super(weSync);
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);
		
		FileData req;
		try {
			req = FileData.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username+" : "+uri);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		log.debug("FolderGetFile request by "+username+", id: "+req.getId()
				+" sliceCount:"+req.getSliceCount()+ " serializedSize: "+req.getSerializedSize() );
		
		String fileId = req.getId();
		
		do{
			if( FileID.isOwner(username, fileId) ) break;
			
			String receiver = FileID.getReceiver(fileId);
			if( Group.isGroupID( receiver) ){
				if( weSync.getDataService().isMember(username, receiver) ) break;
			}else{
				if( username.equals(receiver) ) break;
			}
			
			log.warn("Unpermitted access on file id: "+ fileId + " by user " + username);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}while(false);
		
		FileData resp = null;		
		if( req.getSliceCount() > 0 ){
			resp = weSync.getDataService().getFileByIndex(req);
		}else{
			resp = weSync.getDataService().getFileById(fileId);
		}
		
		if( null == resp ){
			log.warn("Missing file on id: "+fileId);
			resp = FileData.newBuilder().setId(fileId).build();
		}
		
		return resp.toByteArray();
	}

}
