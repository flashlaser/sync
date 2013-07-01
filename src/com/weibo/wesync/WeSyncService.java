package com.weibo.wesync;

/**
 * @author Eric Liang
 *
 */
public interface WeSyncService extends CommandHandler{
	public byte version();
	public byte[] request(String username, byte[] uriData, byte[] bodyData);
	
	public boolean registerCommandListener(CommandListener listener);
	public CommandListener getCommandListener(); 
	
	public boolean registerPlugin(PluginHandler handler);
	public boolean isPluginRegistered(String pluginName);
	public PluginHandler getPluginManager();
	
	public boolean addPropertySupport(String propName);
	public boolean isPropertySupported(String propName);
	
	public DataService getDataService();
	public NoticeService getNoticeService();
	public GroupMessageService getGroupMessageService();
	public PrivacyService getPrivacyService();
}
