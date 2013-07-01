package com.weibo.wesync.command;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.MetaMessageType;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.MetaSet;

/**
 * @author Eric Liang
 */

public class ItemOperationsHandler extends BaseHandler {

	public ItemOperationsHandler(WeSyncService weSync) {
		super(weSync);
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);
		
		MetaSet req;
		try {
			req = MetaSet.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username+" : "+uri);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		log.debug("ItemOperations request by "+username+", "+req);
		
		MetaSet.Builder respBuilder = MetaSet.newBuilder();
		for(Meta mReq : req.getMetaList() ){
			if( (mReq.hasFrom() && !mReq.getFrom().equals(username))
				|| !mReq.hasTo() 
				|| !mReq.hasContent()
				|| !MetaMessageType.valueOf( mReq.getType().byteAt(0) ).equals(MetaMessageType.operation) ){
				log.warn("Invalid request from user "+username+" : "+uri);
				//TODO error code should be returned to client
				throw new RuntimeException();
			}
			Meta mResp = weSync.getPluginManager().handle(username, mReq);
			if( null != mResp ) respBuilder.addMeta(mResp);
		}
		
		return respBuilder.build().toByteArray();
	}
}
