package com.weibo.wesync.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.weibo.wesync.Command;
import com.weibo.wesync.CommandHandler;
import com.weibo.wesync.CommandListener;
import com.weibo.wesync.WeSyncService;

/**
 * 
 * @author Eric Liang
 *
 */
public abstract class BaseHandler implements CommandHandler{
	protected Logger log = LoggerFactory.getLogger(CommandHandler.class);
	protected WeSyncService weSync;
	
	protected static String TAG_SYNC_KEY = "0";
	
	public BaseHandler(WeSyncService weSync){
		this.weSync = weSync;
	}
	
	protected boolean noticeListener(Command comm, ByteString data){
		CommandListener listener = weSync.getCommandListener();
		if( null != listener ){
			return listener.handle(comm, data);
		}
		return false;
	}
}
