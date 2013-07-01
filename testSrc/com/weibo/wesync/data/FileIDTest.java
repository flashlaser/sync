package com.weibo.wesync.data;

import org.junit.Test;

import com.weibo.wesync.data.WeSyncMessage.Meta;

import junit.framework.TestCase;

/**
 * 
 * @author Eric Liang
 *
 */
public class FileIDTest extends TestCase {
	String juliet = "juliet";
	String romeo = "romeo";
	
	@Test
	public void testFileID(){
		Meta meta = Meta.newBuilder()
				.setId("test-file-id")
				.setFrom(juliet)
				.setTo(romeo)
				.build();
		
		String suffix = "for-test-123";
		String fileId = FileID.generateId(meta, suffix);
		
		assertTrue( FileID.isOwner(juliet, fileId) );
		assertFalse( FileID.isReceiver(juliet, fileId) );
		
		assertTrue( FileID.isReceiver(romeo, fileId) );
		assertFalse( FileID.isOwner(romeo, fileId) );
		
		assertFalse( FileID.isReceiver("test", fileId) );
	}
}
