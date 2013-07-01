package com.weibo.wesync;

import com.google.protobuf.ByteString;

public interface CommandListener {
	public boolean handle(Command comm, ByteString data);
}
