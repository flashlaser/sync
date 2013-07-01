package com.weibo.wesync.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.weibo.wesync.PluginHandler;
import com.weibo.wesync.data.MetaMessageType;
import com.weibo.wesync.data.WeSyncMessage.Meta;

/**
 * I'll do nothing except return all the responses as request was handled successfully
 * @author Eric Liang
 *
 */
public class NullHandler implements PluginHandler {
	protected Logger log = LoggerFactory.getLogger("NullPlugin");
	private String pluginName = "null";
	
	public NullHandler(String pluginName){
		this.pluginName = pluginName;
	}
	
	@Override
	public Meta handle(String username, Meta req) {
		log.debug("Got request from user: "+ username + " to plugin "+req.getTo() );
		
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