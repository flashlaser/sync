package com.weibo.wesync;

import com.weibo.wesync.data.WeSyncMessage.Meta;

/**
 * @author Eric Liang
 */
public interface PluginHandler {
	public String pluginName();
	public Meta handle(String username, Meta req);
}
