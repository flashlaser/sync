package com.weibo.wesync;

import java.io.IOException;

import org.junit.Test;

import junit.framework.TestCase;

/**
 * @author Eric Liang
 */
public class WeSyncURITest extends TestCase {
	@Test
	public void testWesyncURI() throws IOException{
		WeSyncURI uri = new WeSyncURI();
		uri.protocolVersion = 10;
		uri.command = Command.Sync.toByte();
		uri.guid = "1234567890abcdefg";
		uri.deviceType = "iphone";
		
		uri.args[0] = "123"; //unread number
		uri.args[1] = "juliet-romeo-101";//sync key
		
		WeSyncURI uri2 = WeSyncURI.fromBytes( WeSyncURI.toBytes(uri) );
		assertTrue( uri2.equals(uri) );
	}
}
