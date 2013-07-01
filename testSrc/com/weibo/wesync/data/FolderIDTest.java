package com.weibo.wesync.data;

import org.junit.Test;

import junit.framework.TestCase;

/**
 * @author Eric Liang
 */
public class FolderIDTest extends TestCase {
	@Test
	public void testType(){
		assertTrue(FolderID.getType(FolderID.onRoot("juliet")).equals(
				FolderID.Type.Root));
		assertTrue(FolderID.getType(FolderID.onConversation("juliet", "romeo"))
				.equals(FolderID.Type.Conversation));
		assertTrue(FolderID.getType(FolderID.onProperty("juliet", "roster"))
				.equals(FolderID.Type.Property));
		
		assertTrue(FolderID.getType("a-b").equals(FolderID.Type.Unknown));
		assertTrue(FolderID.getType("a").equals(FolderID.Type.Unknown));
		assertTrue(FolderID.getType("-").equals(FolderID.Type.Unknown));
		assertTrue(FolderID.getType("-a").equals(FolderID.Type.Unknown));
	}
}
