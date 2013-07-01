package com.weibo.wesync.data;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.Test;

import junit.framework.TestCase;

/**
 * @author Eric Liang
 */
public class FolderChangeTest extends TestCase {
	@Test
	public void testNormal(){
		FolderChange change = new FolderChange("100", true);
		
		String changeStr = change.toString();
		assertTrue( changeStr.equals( "+100" ) );
		
		FolderChange change2 = FolderChange.fromString(changeStr);
		assertTrue( change.childId.equals( change2.childId ) );
		assertTrue( change.equals(change2) );
	}
	
	@Test
	public void testAbnormal(){
		assertTrue( null == FolderChange.fromString("1") );
		assertTrue( null == FolderChange.fromString("+") );
		assertTrue( null == FolderChange.fromString("-") );
	}
	
	@Test
	public void testContainer(){
		Queue<FolderChange> changes = new ConcurrentLinkedQueue<FolderChange>(); 
		for(int i=0; i<100; i++){
			changes.add( new FolderChange(String.valueOf(i), true) );
		}
		
		assertTrue( changes.contains( FolderChange.fromString("+99") ));
	}
}
