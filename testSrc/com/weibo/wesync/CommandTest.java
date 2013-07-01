package com.weibo.wesync;

import org.junit.Test;

import junit.framework.TestCase;

/**
 * @author Eric Liang
 */
public class CommandTest extends TestCase {
	@Test
	public void testCommand(){
		assertTrue(Command.Sync == Command.valueOf("Sync"));
		assertTrue(Command.Sync == Command.valueOf((byte)0x0));	
		assertTrue(Command.Unknown == Command.valueOf((byte)0xEE));		
	}
}
