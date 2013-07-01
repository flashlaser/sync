package com.weibo.wesync;

import com.weibo.wesync.data.WeSyncMessage.Meta;

/**
 * Check the receiver privacy settings and related message filter.
 * 
 * @author Eric Liang
 */
public interface PrivacyService {
	boolean isMessagePermitted(String from, String to, Meta meta);
}
