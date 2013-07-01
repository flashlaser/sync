package com.weibo.wesync.data;

import org.junit.Test;

import junit.framework.TestCase;

/**
 * @author Eric Liang
 */
public class SyncKeyTest extends TestCase {
	@Test
	public void testSyncKey(){
		assertTrue( SyncKey.isEmpty( "abc-root." ) );
		
		assertFalse( "F".equals('F'));
		assertTrue( "F".indexOf('F') == 0 );
		assertTrue( SyncKey.isEmpty( "Juliet-root.F" ) );

		assertTrue( "+".equals( SyncKey.getChangeString( "Juliet-root.+" )));
		assertTrue( "+100".equals( SyncKey.getChangeString( "Juliet-root.+100" )));
		
		assertTrue( "100".equals( SyncKey.getChildBySyncKey( "Juliet-root.+100" )));
		assertTrue( "100".equals( SyncKey.getChildBySyncKey( "Juliet-root.F100" )));
		
		assertFalse( SyncKey.isFullSyncKey( "abc-root." ) );
		assertFalse( SyncKey.isFullSyncKey( "abc-root.+100" ) );
		assertTrue( SyncKey.isFullSyncKey( "abc-root.F" ) );
		assertTrue( SyncKey.isFullSyncKey( "abc-root.F100" ) ); 
	}
}
