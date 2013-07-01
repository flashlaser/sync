package com.weibo.wesync;

import java.util.SortedSet;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.ByteString;
import com.weibo.wesync.data.FakeDataStore;
import com.weibo.wesync.data.FolderChange;
import com.weibo.wesync.data.FolderChild;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.MetaMessageType;
import com.weibo.wesync.data.SyncKey;
import com.weibo.wesync.data.WeSyncMessage.Meta;

/**
 * @author Eric Liang
 */
public class DataServiceTest extends TestCase {
	DataService ds = new DataServiceImpl( new FakeDataStore() );
	
	String juliet = "Juliet";
	String romeo = "Romeo";
	String lawrence = "Lawrence";
	
	@Before
	public void prepare(){
		ds.prepareForNewUser(romeo);
		ds.prepareForNewUser(juliet);
		
		//Romeo and Juliet have chats with eatch other before
		ds.newConversation(romeo, juliet);
		ds.newConversation(juliet, romeo);
		
		//Clean the unread
		String rootRomeo = FolderID.onRoot(romeo);
		ds.cleanupSynchronizedChanges( rootRomeo, SyncKey.emptySyncKey(rootRomeo, false) );
		String rootJuliet = FolderID.onRoot(juliet);
		ds.cleanupSynchronizedChanges( rootJuliet, SyncKey.emptySyncKey(rootJuliet, false) );
	}
	
	@Test
	public void testNormal(){
		//TODO @Before annotation may not work
		prepare();
		
		//An text from Romeo to Juliet
		Meta msgText = Meta.newBuilder()
				.setType( ByteString.copyFrom( new byte[]{MetaMessageType.text.toByte()} ))
				.setId("Client-00")
				.setContent( ByteString.copyFromUtf8( "Miss U" ) )
				.setFrom(romeo)
				.setTo(juliet)
				.setTime( (int) (System.currentTimeMillis() / 1000L) )
				.build();
		ds.store(msgText);
		
		//Romeo's outbox
		String rootRomeo = FolderID.onRoot(romeo);
		assertTrue( ds.getUnreadNumber(rootRomeo) == 0 );
		
		//Juliet should receive that notice
		String rootJuliet = FolderID.onRoot(juliet);
		assertTrue( ds.getUnreadNumber(rootJuliet) == 1 );
		
		SortedSet<FolderChange> rootChanges = ds.getFolderChanges( rootJuliet );
		FolderChange convChange = rootChanges.first();
		assertTrue( convChange != null );
		assertTrue( ds.getUnreadNumber(convChange.childId) == 1 );
		
		SortedSet<FolderChange> convChanges = ds.getFolderChanges( convChange.childId ); 
		FolderChange msgChange = convChanges.first();
		assertTrue( msgChange != null );
		
		//Check the message
		String msgId = FolderChild.generateId(convChange.childId, Long.valueOf(msgChange.childId));
		Meta msgRecv = ds.getMetaMessage( msgId );
		assertTrue( msgRecv != null );
		assertTrue( MetaMessageType.valueOf( msgRecv.getType().byteAt(0) ).equals( MetaMessageType.text ));
		assertTrue( msgRecv.getContent().toStringUtf8().equals("Miss U"));
		assertTrue( msgRecv.getFrom().equals(romeo));
		assertTrue( msgRecv.getTo().equals(juliet));
		
	}
	
	@Test
	public void testNewUserAndChat(){
		//TODO @Before annotation may not work
		prepare();
		
		ds.prepareForNewUser(lawrence);
		
		//An text from Romeo to Lawrence
		Meta msgText = Meta.newBuilder()
				.setType( ByteString.copyFrom( new byte[]{MetaMessageType.text.toByte()} ))
				.setId( "client-00" )
				.setContent( ByteString.copyFromUtf8( "Help me, please" ) )
				.setFrom(romeo)
				.setTo(lawrence)
				.setTime( (int) (System.currentTimeMillis() / 1000L) )
				.build();
		
		ds.store(msgText);
		
		//Lawrence should receive the nocie ( conversation and message )
		String rootLawrenceId = FolderID.onRoot(lawrence);
		assertTrue( ds.getUnreadNumber(rootLawrenceId) == 1 ); //same changes will merge
		
		SortedSet<FolderChange> rootChanges = ds.getFolderChanges(rootLawrenceId);
		FolderChange convChange = rootChanges.first();
		assertTrue( ds.getUnreadNumber(convChange.childId) == 1 );
		
		SortedSet<FolderChange> convChanges = ds.getFolderChanges( convChange.childId );
		FolderChange msgChange = convChanges.first();
		String msgId = FolderChild.generateId(convChange.childId, Long.valueOf(msgChange.childId));
		assertTrue( ds.getMetaMessage(msgId) != null );
	}	
}
