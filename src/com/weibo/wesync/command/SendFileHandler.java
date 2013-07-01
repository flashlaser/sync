package com.weibo.wesync.command;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.FileID;
import com.weibo.wesync.data.WeSyncMessage.FileData;

/**
 * @author Eric Liang
 */
public class SendFileHandler extends BaseHandler {

	public SendFileHandler(WeSyncService weSync) {
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
		
		log.debug("SendFile request by "+username+", id: "+req.getId()
				+" sliceCount:"+req.getSliceCount()+ " serializedSize: "+req.getSerializedSize() );
		
		String fileId = req.getId();		
		if( !FileID.isOwner(username, fileId) ){
			log.warn("Invalid file id: "+ fileId + " from user " + username);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		FileData resp = weSync.getDataService().store(req);
		return resp.toByteArray();
	}

}
