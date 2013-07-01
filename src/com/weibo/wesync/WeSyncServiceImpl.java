package com.weibo.wesync;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author Eric Liang
 */
@Singleton
public class WeSyncServiceImpl implements WeSyncService {
	private final Logger log = LoggerFactory.getLogger(WeSyncServiceImpl.class);
	private final byte version10 = 10; //1.0
	private final byte version = 20; //2.0
	
	private Set<String> propertySet;
	private CommandListener commandListener;
	private CommandProcessor commandProcessor10;
	private CommandProcessor commandProcessor;
	private PluginManager pluginManager;
	private final DataService dataService;
	private final NoticeService noticeService;
	private final GroupMessageService groupMessageService;
	private final PrivacyService privacyService;
	
	@Inject
	public WeSyncServiceImpl(DataService dataService, NoticeService noticeService, 
			GroupMessageService groupMessageService, PrivacyService privacyService){
		this.dataService = dataService;
		this.noticeService = noticeService;
		this.groupMessageService = groupMessageService;
		this.privacyService = privacyService;
		setupCommandProcessor();
		pluginManager = new PluginManager();
		propertySet = Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
	}
	
	private void setupCommandProcessor(){
		commandProcessor10 = new CommandProcessor(this, version10);
		commandProcessor = new CommandProcessor(this);
	}
	
	@Override
	public byte[] request(String username, byte[] uriData, byte[] bodyData) {
		try{
			WeSyncURI uri = WeSyncURI.fromBytes(uriData);
			return handle(username, uri, bodyData, false);
		}catch(RuntimeException e){
			//FIXME
			log.error("Exception when processing request: "+ e.getCause()+ " : "+ e.getMessage() +" : "+ Arrays.toString( e.getStackTrace()));
		}
		return null;
	}

	@Override
	public byte version() {
		return version;
	}

	@Override
	public DataService getDataService() {
		return dataService;
	}

	@Override
	public NoticeService getNoticeService() {
		return noticeService;
	}

	@Override
	public boolean registerPlugin(PluginHandler handler) {
		return pluginManager.registerHandler(handler);
	}

	@Override
	public boolean isPluginRegistered(String pluginName) {
		return pluginManager.isRegistered(pluginName);
	}

	@Override
	public boolean addPropertySupport(String propName) {
		return propertySet.add(propName);
	}

	@Override
	public boolean isPropertySupported(String propName) {
		return propertySet.contains(propName);
	}

	@Override
	public GroupMessageService getGroupMessageService() {
		return groupMessageService;
	}

	@Override
	public PluginHandler getPluginManager() {
		return pluginManager;
	}

	@Override
	public PrivacyService getPrivacyService() {
		return privacyService;
	}

	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean fromListener) {
		log.debug("Got request:" + uri);
		switch (uri.protocolVersion) {
		case version10:
			return commandProcessor10.handle(username, uri, data, fromListener);
		case version:
			return commandProcessor.handle(username, uri, data, fromListener);
		default:
			log.error("Version not supported:" + uri.protocolVersion);
		}
		return null;
	}

	@Override
	public boolean registerCommandListener(CommandListener listener) {
		this.commandListener = listener;
		return true;
	}

	@Override
	public CommandListener getCommandListener() {
		return commandListener;
	}

}
