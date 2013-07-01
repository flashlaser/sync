package com.weibo.wesync;

import java.util.concurrent.atomic.AtomicBoolean;

import com.weibo.wesync.data.WeSyncMessage.Meta;

public class FakePrivacyService implements PrivacyService{
	
	private AtomicBoolean permitted = new AtomicBoolean(true);
	
	public void setPermission(boolean p){
		permitted.set(p);
	}
	
	@Override
	public boolean isMessagePermitted(String from, String to, Meta meta) {
		return permitted.get();
	}
}
