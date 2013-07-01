package com.weibo.wesync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.weibo.wesync.data.WeSyncMessage.Meta;

/**
 * @author Eric Liang
 */
public class PluginManager implements PluginHandler {
	private final Logger log = LoggerFactory.getLogger(PluginManager.class);

	private Map<String, PluginHandler> handlers = new ConcurrentHashMap<String, PluginHandler>();
	
	public boolean registerHandler(PluginHandler handler) {
		if( isRegistered(handler.pluginName()) ){
			log.error( "Plugin " + handler.pluginName()+ " has been registered before!");
			return false;
		}
		
		log.info("Register handler for plugin: "+handler.pluginName());
		handlers.put(handler.pluginName(), handler);
		return true;
	}
	
	public boolean isRegistered(String pluginName){
		return handlers.containsKey(pluginName);
	}

	@Override
	public Meta handle(String username, Meta req) {
		do{
			if( !req.hasTo() ){
				log.error("Invalid operation from "+ username);
				break;
			}
			if( !isRegistered(req.getTo()) ){
				log.error("Plugin "+req.getTo() +" not registered, request from "+username );
				break;
			}
			
			Meta resp = handlers.get(req.getTo()).handle(username, req);
			log.debug("Response from plugin: "+ req.getTo()+" : "+resp);
			
			return resp;
		}while(false);
		
		return null;
	}

	@Override
	public String pluginName() {
		//I'm the manager, I'm anonymous.
		return null;
	}
}
