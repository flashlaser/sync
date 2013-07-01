package com.weibo.wesync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.weibo.wesync.command.FolderCreateHandler;
import com.weibo.wesync.command.FolderDeleteHandler;
import com.weibo.wesync.command.FolderSyncHandler;
import com.weibo.wesync.command.GetFileHandler;
import com.weibo.wesync.command.GetItemUnreadHandler;
import com.weibo.wesync.command.ItemOperationsHandler;
import com.weibo.wesync.command.NullHandler;
import com.weibo.wesync.command.SendFileHandler;
import com.weibo.wesync.command.Sync10Handler;
import com.weibo.wesync.command.SyncHandler;

/**
 * @author Eric Liang
 */
public class CommandProcessor implements CommandHandler{
	private WeSyncService weSync;
	private Map<Command, CommandHandler> handlers = new ConcurrentHashMap<Command, CommandHandler>();

	public CommandProcessor(WeSyncService weSync) {
		this.weSync = weSync;
		setupHandlers();
	}

	public CommandProcessor(WeSyncService weSync, byte protocolVersion) {
		this.weSync = weSync;
		setupHandlers(protocolVersion);
	}
	
	private void setupHandlers() {
		handlers.put(Command.Unknown, new NullHandler(weSync));
		handlers.put(Command.Sync, new SyncHandler(weSync));
		handlers.put(Command.FolderSync, new FolderSyncHandler(weSync));
		handlers.put(Command.FolderCreate, new FolderCreateHandler(weSync));
		handlers.put(Command.FolderDelete, new FolderDeleteHandler(weSync));
		handlers.put(Command.GetItemUnread, new GetItemUnreadHandler(weSync));
		handlers.put(Command.ItemOperations, new ItemOperationsHandler(weSync));
		handlers.put(Command.SendFile, new SendFileHandler(weSync));
		handlers.put(Command.GetFile, new GetFileHandler(weSync));
	}

	/**
	 * setup the protocol version various handlers 
	 */
	private void setupHandlers(byte version){
		setupHandlers();
		
		if( 10 == version ){
			//just override the latest one
			handlers.put(Command.Sync, new Sync10Handler(weSync));
		}
	}
	
	private CommandHandler getHandler(Command c){
		CommandHandler handler = handlers.get(c);
		if( handler == null ) return handlers.get(Command.Unknown);
		
		return handler;
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data,
			boolean fromListener) {
		CommandHandler handler = getHandler( Command.valueOf(uri.command) ); 
		return handler.handle(username, uri, data, fromListener);
	}
}
