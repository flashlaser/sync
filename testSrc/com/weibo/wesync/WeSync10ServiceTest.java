package com.weibo.wesync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

import junit.framework.TestCase;

import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.ByteString;
import com.weibo.wesync.command.SyncHandler;
import com.weibo.wesync.data.FileDataHelper;
import com.weibo.wesync.data.FileID;
import com.weibo.wesync.data.Folder;
import com.weibo.wesync.data.FolderChild;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.Group;
import com.weibo.wesync.data.GroupOperationType;
import com.weibo.wesync.data.MetaMessageType;
import com.weibo.wesync.data.SyncKey;
import com.weibo.wesync.data.WeSyncMessage.DataSlice;
import com.weibo.wesync.data.WeSyncMessage.FileData;
import com.weibo.wesync.data.WeSyncMessage.FolderCreateReq;
import com.weibo.wesync.data.WeSyncMessage.FolderCreateResp;
import com.weibo.wesync.data.WeSyncMessage.FolderDeleteReq;
import com.weibo.wesync.data.WeSyncMessage.FolderSyncReq;
import com.weibo.wesync.data.WeSyncMessage.FolderSyncResp;
import com.weibo.wesync.data.WeSyncMessage.GetItemUnreadReq;
import com.weibo.wesync.data.WeSyncMessage.GetItemUnreadResp;
import com.weibo.wesync.data.WeSyncMessage.GroupOperation;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.Meta.Builder;
import com.weibo.wesync.data.WeSyncMessage.MetaSet;
import com.weibo.wesync.data.WeSyncMessage.SyncReq;
import com.weibo.wesync.data.WeSyncMessage.SyncResp;
import com.weibo.wesync.data.WeSyncMessage.Unread;
import com.weibo.wesync.plugin.GroupHandler;
import com.weibo.wesync.plugin.NullHandler;

/**
 * @author Eric Liang
 */
public class WeSync10ServiceTest extends TestCase {
	protected Logger log = LoggerFactory.getLogger(WeSync10ServiceTest.class);

	public class LocalStore{
		public Map<String, Folder> folders = new HashMap<String, Folder>();
		public Map<String, Meta> messages = new HashMap<String, Meta>();
		public Map<String, Queue<Meta>> pending = new HashMap<String, Queue<Meta>>();
		public Map<String, Map<String, Meta>> syncing = new HashMap<String, Map<String, Meta>>();
		public Map<String, byte[]> files = new HashMap<String, byte[]>();
		
		public void print(String prompt){
			log.debug( prompt+" LS# Folders:"+folders);
			log.debug( prompt+" LS# Messges:"+messages);
			log.debug( prompt+" LS# Pending:"+pending);
			log.debug( prompt+" LS# Syncing:"+syncing);
			log.debug( prompt+" LS# Files:"+files);
		}
	}

	private WeSyncService weSync;		
	private LocalStore  localStore = new LocalStore();
	private String juliet = "juliet";
	private String romeo = "romeo";
	private String lawrence = "lawrence";
	private static String TAG_SYNC_KEY = "0";
	private static int batchSize = SyncHandler.DEFAULT_BATCH_SIZE;
	private final String groupPluginName = "group";
	private final String systemPluginName = "system";
	private int folderChildLimit = 50;
	private int folderChangeLimit = 40;
	private int fileSliceSize = 1200;
	
	@Rule
	public ExpectedException expected = ExpectedException.none();
	
	@Before
	public void prepare() throws IOException{
	    Injector injector = Guice.createInjector(new FakeWeSyncModule());
	    weSync = injector.getInstance(WeSyncService.class);
	    
	    weSync.getDataService().setFolderLimit(folderChildLimit, folderChangeLimit);
	    
	    //for group chat
	    PluginHandler groupHandler = injector.getInstance(GroupHandler.class);
	    weSync.registerPlugin(groupHandler);
	    weSync.addPropertySupport(Group.PROP_MEMBERS);
	    weSync.addPropertySupport(Group.PROP_HISTORY);
	    
	    //for system messages
	    weSync.registerPlugin(new NullHandler(systemPluginName));
	    
	    weSync.getDataService().prepareForNewUser(juliet);
	    weSync.getDataService().prepareForNewUser(romeo);
	    weSync.getDataService().prepareForNewUser(lawrence);
	    
	    cleanupConversation(juliet, romeo);
	    cleanupConversation(romeo, juliet);
	}
	
	@Test
	public void testUtilities(){
		String convId = "a-conv-b";
		assertTrue( new FolderID(convId).toString().equals( convId ) );
		assertTrue( new FolderID("a-xxx-b").toString().equals( "a-unknown" ) );
	}
	
	@Test
	public void testNormalConversation() throws IOException{
		//TODO yes, the Before annotation does not work
		prepare();
		
		//Sync Folder
		folderSync(juliet);
		folderSync(romeo);
		
		createConversation(juliet, romeo);
		sendTextMessage( juliet, romeo, "What are you doing" );
		
		//Assume Romeo has received the notice, then he
		folderSyncAndSyncAllUnreadFolders(romeo);
		
		//Romeo should get the message
		{
			Folder f = localStore.folders.get( FolderID.onConversation(romeo, juliet) );
			assertNotNull( f.children );
			String childId = f.children.first().id;
			
			Meta msg = localStore.messages.get(childId);
			assertTrue(null != msg);
			assertTrue(msg.getFrom().equals(juliet));
			assertTrue(msg.getTo().equals(romeo));
			assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
			assertTrue(msg.getContent().toStringUtf8().equals("What are you doing"));
		}
	}
	
	@Test
	public void testPrivacy() throws IOException{
		//TODO yes, the Before annotation does not work
		prepare();
		
		//Sync Folder
		folderSync(juliet);
		folderSync(romeo);
		
		createConversation(juliet, romeo);
		
		//Change default message permission
		((FakePrivacyService)weSync.getPrivacyService()).setPermission(false);
		
		sendTextMessage( juliet, romeo, "What are you doing" );
		
		//Romeo should not receive the message
		folderSyncAndSyncAllUnreadFolders(romeo);
		
		{
			Folder f = localStore.folders.get( FolderID.onConversation(romeo, juliet) );
			assertNull( f );
		}
	}
	
	private void sendTextMessage(String from, String to, String content) throws IOException {
		// Juliet send the message
		Meta.Builder msgBuilder = Meta
				.newBuilder()
				.setFrom(from)
				.setTo(to)
				.setType(ByteString.copyFrom(new byte[] { MetaMessageType.text.toByte() }))
				.setContent(ByteString.copyFromUtf8(content));
		String convId = FolderID.onConversation(from, to);

		sendMetaToFolder(from, msgBuilder, convId);
	}


	@Test
	public void testMultipleChangesUnderBatchSize() throws IOException{
		testNormalConversation();
		
		int msgNum = batchSize-1;
		for(int i=0; i<msgNum; i++){
			sendTextMessage( juliet, romeo, "What are you doing "+i );
		}
		
		String convId = FolderID.onConversation(romeo, juliet);
		SyncResp resp1 = requestSync(romeo, TAG_SYNC_KEY, convId);
		assertTrue( resp1.getServerChangesCount() == msgNum );

		SyncResp resp2 = requestSync(romeo, resp1.getNextKey(), convId);
		assertTrue(SyncKey.isEmpty(resp2.getNextKey()));
	}
	
	@Test
	public void testMultipleChangesOverBatchSize() throws IOException{
		testNormalConversation();
		
		int msgNum = batchSize+1;
		for(int i=0; i<msgNum; i++){
			sendTextMessage( juliet, romeo, "What are you doing "+i );
		}
		
		String convId = FolderID.onConversation(romeo, juliet);
		SyncResp resp1 = requestSync(romeo, TAG_SYNC_KEY, convId);
		assertTrue( resp1.getServerChangesCount() == batchSize );

		SyncResp resp2 = requestSync(romeo, resp1.getNextKey(), convId);
		assertTrue( resp2.getServerChangesCount() == msgNum-batchSize );
		
		SyncResp resp3 = requestSync(romeo, resp2.getNextKey(), convId);
		assertTrue(SyncKey.isEmpty(resp3.getNextKey()));
	}
	
	@Test
	public void testFullSync() throws IOException{
		testMultipleChangesOverBatchSize();
		
		String convId = FolderID.onConversation(romeo, juliet);
		SyncResp resp1 = requestSync(romeo, TAG_SYNC_KEY, convId, true);
		log.debug("FS: "+ resp1.getServerChangesCount() + " "+resp1);
		assertFalse(SyncKey.isEmpty(resp1.getNextKey()));
		assertTrue( resp1.getServerChangesCount() == batchSize );

		SyncResp resp2 = requestSync(romeo, resp1.getNextKey(), convId);
		log.debug("FS: "+ resp2.getServerChangesCount() + " "+resp2);
		assertFalse(SyncKey.isEmpty(resp1.getNextKey()));
		assertTrue( resp2.getServerChangesCount() >= 2 );

		SyncResp resp3 = requestSync(romeo, resp2.getNextKey(), convId);
		log.debug("FS: "+ resp3.getServerChangesCount() + " "+resp3);
		assertTrue(SyncKey.isEmpty(resp3.getNextKey()));
	}
	
	@Test
	public void testFullSyncForward() throws IOException{
		testMultipleChangesOverBatchSize();
		
		String convId = FolderID.onConversation(romeo, juliet);
		SyncResp resp1 = requestSync(romeo, TAG_SYNC_KEY, convId, true, true);
		log.debug("FSF: "+ resp1.getServerChangesCount() + " "+resp1);
		assertFalse(SyncKey.isEmpty(resp1.getNextKey()));
		assertTrue( resp1.getServerChangesCount() == batchSize );

		SyncResp resp2 = requestSync(romeo, resp1.getNextKey(), convId, true, true);
		log.debug("FSF: "+ resp2.getServerChangesCount() + " "+resp2);
		assertFalse(SyncKey.isEmpty(resp1.getNextKey()));
		assertTrue( resp2.getServerChangesCount() >= 2 );

		SyncResp resp3 = requestSync(romeo, resp2.getNextKey(), convId, true, true);
		log.debug("FSF: "+ resp3.getServerChangesCount() + " "+resp3);
		assertTrue(SyncKey.isEmpty(resp3.getNextKey()));
	}
	
	@Test
	public void testHintedFullSync() throws IOException{
		testMultipleChangesOverBatchSize();
		
		String convId = FolderID.onConversation(romeo, juliet);
		SyncResp resp1 = requestSync(romeo, TAG_SYNC_KEY, convId, true);
		log.debug("HFS: "+ resp1.getServerChangesCount() + " "+resp1);
		assertFalse(SyncKey.isEmpty(resp1.getNextKey()));
		assertTrue( resp1.getServerChangesCount() == batchSize );
		
		String hint = resp1.getServerChanges(0).getId();
		SyncResp resp2 = requestSync(romeo, TAG_SYNC_KEY, convId, hint);
		log.debug("HFS: "+ resp2.getServerChangesCount() + " hint: "+ hint +" "+resp2);
		assertFalse(SyncKey.isEmpty(resp2.getNextKey()));
		assertTrue( resp2.getServerChangesCount() == 2 );

		SyncResp resp3 = requestSync(romeo, resp2.getNextKey(), convId);
		log.debug("HFS: "+ resp3.getServerChangesCount() + " "+resp3);
		assertTrue(SyncKey.isEmpty(resp3.getNextKey()));
	}
	
	@Test
	public void testRepeatedSync() throws IOException{
		testNormalConversation();
		//Synchronization on an already synchronized folder will get nothing.
		String convId = FolderID.onConversation(romeo, juliet); 
		SyncResp resp = requestSync(romeo, TAG_SYNC_KEY, convId);
		
		log.debug( resp.toString() );
		assertTrue( SyncKey.isEmpty( resp.getNextKey() ) );
	}
	
	@Test
	public void testPropertyPushToUser() throws IOException{
		testNormalConversation();
		
		String propRoster = "roster";
		
		//Server add the friend
	    {
	    	weSync.addPropertySupport(propRoster);
	    	Meta friend = Meta.newBuilder()
					.setId(lawrence)
					.setType(ByteString.copyFrom(new byte[] { MetaMessageType.property.toByte() }))
					.setFrom(romeo)
					.setTo(propRoster)
					.build();
		    
		    weSync.getDataService().store(friend);
	    }
	    
		// Assume Romeo has received the notice, then he
		folderSyncAndSyncAllUnreadFolders( romeo );
		
		// System operation should get the message
		{
			Folder f = localStore.folders.get(FolderID.onProperty(romeo, propRoster));
			String childId = f.children.first().id;

			Meta msg = localStore.messages.get(childId);
			assertTrue(null != msg);
			assertTrue( msg.getId().equals(lawrence) );
			//Property folder's children are Metas with id member only
			assertFalse( msg.hasFrom() || msg.hasTo() || msg.hasContent() );
		}
	}
	
	@Test(expected=RuntimeException.class)
	public void testOnNonExistFolder() throws IOException{
		testNormalConversation();
		//FIXME It's wired the Test annotation does not work, so I have to catch the exception by myself. 
		Exception e = null;
		try {
			sync(romeo, FolderID.onConversation(romeo, "TheGhost"));
		}catch(RuntimeException re){
			e = re;
		}
		assertNotNull(e);
	}
	private void folderSyncAndSyncAllUnreadFolders(String user) throws IOException {
		//FIXME folderSync is unnecesary? fix groupMemberPropId
		List<String> requiredSyncFolders = folderSync(user);
		
		GetItemUnreadResp getItemUnreadRespRomeo = requestGetItemUnread(user, requiredSyncFolders);
		for (Unread u : getItemUnreadRespRomeo.getUnreadList()) {
			// Client should display the unread number
			// then sync the folder
			sync(user, u.getFolderId());
		}
	}


	@Test
	public void testSendFile() throws IOException, NoSuchAlgorithmException{
		testNormalConversation();
		
		// Romeo send the Image
		String fileId = sendImageFile( romeo, juliet, false );
		int limit = calculateImageFileLimit();

		// Assume Juliet has received the notice, then he
		folderSyncAndSyncAllUnreadFolders(juliet);

		// Juliet should get the message
		{
			Folder f = localStore.folders.get( FolderID.onConversation(juliet,romeo) );
			//image is the second message
			String childId = f.children.last().id;
			Meta msg = localStore.messages.get(childId);
			assertTrue(null != msg);
			assertTrue(msg.getFrom().equals(romeo));
			assertTrue(msg.getTo().equals(juliet));
			assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.image));
			assertTrue(msg.getContent().toStringUtf8().equals(fileId));
			
			FileData req = FileData.newBuilder()
					.setId( fileId )
					.build();
			FileData resp = requestGetFile(juliet, req);
			
			assertTrue( resp.getSliceCount() == limit );
			assertTrue( FileDataHelper.isSane(resp) );
			
			{
				String outputFile = "./work/starry-from-romeo.jpg";
				File file = new File(outputFile);
				if (file.exists())
					file.delete();
				file.createNewFile();
				OutputStream out = new FileOutputStream(file);
				out.write(FileDataHelper.pad(resp));
				out.close();
				
				InputStream input = new FileInputStream(outputFile);
				byte[] digest = calculateMd5(input);
				byte[] digestOrig = calculateMd5( VangoghStarry.inputStream() );
				log.debug( Hex.encodeHex(digest).toString() );
				assertTrue( Arrays.equals(digest, digestOrig) );
			}
		}
	}
	
	@Test
	public void testGroupMemberOperation() throws IOException{
		String groupId = testGroupChatInner();
		String memberPropId = Group.memberFolderId(groupId);

		/*
		 * Remove lawrence from group
		 */
		Meta oper = removeMemberFromGroup(groupId, lawrence);
		MetaSet operations = MetaSet.newBuilder()
				.addMeta(oper)
				.build();
		requestItemOperations(juliet, operations);
		
		cleanupGroupMessagesInLocalStore(groupId);
		//Every member will receive a notice, then he
		folderSyncAndSyncAllUnreadFolders(juliet);
		{
			//Juliet will get the group member folder
			Folder f = localStore.folders.get(memberPropId);
			assertTrue( f.children.size() == 2 );
			assertTrue( f.children.contains( new FolderChild(juliet) ) );
			localStore.folders.remove(memberPropId);
		}
		
		cleanupGroupMessagesInLocalStore(groupId);
		folderSyncAndSyncAllUnreadFolders(romeo);
		{
			//Romeo and Lawrence will get the group member folder
			Folder f = localStore.folders.get(memberPropId);
			assertTrue( f.children.size() == 2 );
			assertTrue( f.children.contains( new FolderChild(romeo) ) );
			localStore.folders.remove(memberPropId);
		}
		
		cleanupGroupMessagesInLocalStore(groupId);
		folderSyncAndSyncAllUnreadFolders(lawrence);
		{
			//lawrence will not the member of group
			Folder f = localStore.folders.get(memberPropId);
			assertNull( f );
		}		

		/*
		 * Test send message after lawrence was removed
		 */
		sendTextMessageToGroupChat( romeo, groupId, "I'm walking" );
		
		cleanupGroupMessagesInLocalStore(groupId);
		//Every member will receive a notice, then he
		folderSyncAndSyncAllUnreadFolders(juliet);
		{
			//Juliet will get the group member folder
			Folder f = localStore.folders.get(memberPropId);
			assertTrue( f.children.size() == 2 );
			assertTrue( f.children.contains( new FolderChild(juliet) ) );
			localStore.folders.remove(memberPropId);
			
			//And a new group chat conversation with the text message
			Folder cf = localStore.folders.get(FolderID.onGroup(juliet, groupId));
			String childId = cf.children.last().id;
			
			Meta msg = localStore.messages.get(childId);
			assertTrue(null != msg);
			assertTrue(msg.getFrom().equals(romeo));
			assertTrue(msg.getTo().equals(groupId));
			assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
			assertTrue(msg.getContent().toStringUtf8().equals("I'm walking"));
		}
		
		cleanupGroupMessagesInLocalStore(groupId);
		folderSyncAndSyncAllUnreadFolders(romeo);
		{
			//Romeo and Lawrence will get the group member folder
			Folder f = localStore.folders.get(memberPropId);
			assertTrue( f.children.size() == 2 );
			assertTrue( f.children.contains( new FolderChild(romeo) ) );
			localStore.folders.remove(memberPropId);
		}
		
		cleanupGroupMessagesInLocalStore(groupId);
		folderSyncAndSyncAllUnreadFolders(lawrence);
		{
			//lawrence will not the member of group
			Folder f = localStore.folders.get(memberPropId);
			assertNull( f );
		}		
		
		/*
		 * Add Lawrence back and check the message
		 */
		Meta oper2 = addMemberToGroup(groupId, lawrence);
		MetaSet operations2 = MetaSet.newBuilder()
				.addMeta(oper2)
				.build();
		requestItemOperations(juliet, operations2);
		
		checkMessageDeliverInGroupChat( groupId, "Are you free tonight" );
	}
	
	@Test
	public void testGroupChat() throws IOException{
		testGroupChatInner();
	}
	
	@Test
	public void testSendFileToGroup() throws IOException, NoSuchAlgorithmException{
		String groupId = testGroupChatInner();
		
		// Romeo send the Image
		String fileId = sendImageFile( romeo, groupId, true );
		int limit = calculateImageFileLimit();

		// Assume Juliet has received the notice, then he
		folderSyncAndSyncAllUnreadFolders(juliet);

		// Juliet should get the message
		{
			Folder f = localStore.folders.get( FolderID.onGroup(juliet,groupId) );
			//image is the second message
			String childId = f.children.last().id;
			Meta msg = localStore.messages.get(childId);
			assertTrue(null != msg);
			assertTrue(msg.getFrom().equals(romeo));
			assertTrue(msg.getTo().equals(groupId));
			assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.image));
			assertTrue(msg.getContent().toStringUtf8().equals(fileId));
			
			FileData req = FileData.newBuilder()
					.setId( fileId )
					.build();
			FileData resp = requestGetFile(juliet, req);
			
			assertTrue( resp.getSliceCount() == limit );
			assertTrue( FileDataHelper.isSane(resp) );
			
			{
				String outputFile = "./work/starry-from-romeo-group.jpg";
				File file = new File(outputFile);
				if (file.exists())
					file.delete();
				file.createNewFile();
				OutputStream out = new FileOutputStream(file);
				out.write(FileDataHelper.pad(resp));
				out.close();
				
				InputStream input = new FileInputStream(outputFile);
				byte[] digest = calculateMd5(input);
				byte[] digestOrig = calculateMd5( VangoghStarry.inputStream() );
				log.debug( Hex.encodeHex(digest).toString() );
				assertTrue( Arrays.equals(digest, digestOrig) );
			}
		}
	}
	
	private int calculateImageFileLimit(){
		int remainder = VangoghStarry.length % fileSliceSize;
		int limit = (0 == remainder ? VangoghStarry.length / fileSliceSize
				: VangoghStarry.length / fileSliceSize + 1);
		
		return limit;
	}
	private String sendImageFile(String from, String to, boolean isGroupChat) throws IOException {
		// SendFile first, client should guarantee the file's identity
		String fileId = FileID.generateId(from, to, "file-" + System.currentTimeMillis());
		int limit = calculateImageFileLimit();

		// Usually, you can send the file in sequence, but here we send the file
		// data for out-of-order for test
		sendPartsOfFile(from, fileId, fileSliceSize, limit, "even_parts", false);
		sendPartsOfFile(from, fileId, fileSliceSize, limit, "odd_parts", true);
		sendLastPartOfFile(from, fileId, fileSliceSize, limit);

		// then send the meta
		Meta.Builder msgBuilder = Meta
				.newBuilder()
				.setFrom(from)
				.setTo(to)
				.setType(
						ByteString.copyFrom(new byte[] { MetaMessageType.image
								.toByte() }))
				.setContent(ByteString.copyFromUtf8(fileId));
		
		String convId = isGroupChat ? FolderID.onGroup(from, to) : FolderID.onConversation(from, to);
		sendMetaToFolder(from, msgBuilder, convId);
		
		return fileId;
	}


	@Test
	public void testFolderChangeLimit() throws IOException{
		testNormalConversation();
		
		sendTextMessage( juliet, romeo, "What are you doing" );
		for( int i=0; i < folderChangeLimit; i++ ){
			sendTextMessage( juliet, romeo, "What are you doing "+i );
		}
		
		Folder f = localStore.folders.get( FolderID.onConversation(romeo, juliet) );
		assertNotNull( f.children );
		f.children.clear();
		
		//Assume Romeo has received the notice, then he
		folderSyncAndSyncAllUnreadFolders(romeo);
		
		//Romeo should get the message (changes)
		{
			assertTrue( f.children.size() == folderChangeLimit );
			
			{
				String childId = f.children.first().id;
				Meta msg = localStore.messages.get(childId);
				assertTrue(null != msg);
				assertTrue(msg.getFrom().equals(juliet));
				assertTrue(msg.getTo().equals(romeo));
				assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
				assertTrue(msg.getContent().toStringUtf8().equals("What are you doing "+0));
			}
			{
				String childId = f.children.last().id;
				Meta msg = localStore.messages.get(childId);
				assertTrue(null != msg);
				assertTrue(msg.getFrom().equals(juliet));
				assertTrue(msg.getTo().equals(romeo));
				assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
				assertTrue(msg.getContent().toStringUtf8().equals("What are you doing "+(folderChangeLimit-1)));
			}
		}
	}
	
	@Test
	public void testFolderChildLimit() throws IOException{
		testNormalConversation();
		
		sendTextMessage( juliet, romeo, "What are you doing" );
		for( int i=0; i < folderChildLimit; i++ ){
			sendTextMessage( juliet, romeo, "What are you doing "+i );
		}
		
		//Remove to trigger full sync
		localStore.folders.remove( FolderID.onConversation(romeo, juliet) );
		
		//Assume Romeo has received the notice, then he
		folderSyncAndSyncAllUnreadFolders(romeo);
		
		//Romeo should get the message (changes)
		{
			Folder f = localStore.folders.get( FolderID.onConversation(romeo, juliet) );
			assertNotNull( f.children );
			assertTrue( f.children.size() == folderChildLimit );
			
			{
				String childId = f.children.first().id;
				Meta msg = localStore.messages.get(childId);
				assertTrue(null != msg);
				assertTrue(msg.getFrom().equals(juliet));
				assertTrue(msg.getTo().equals(romeo));
				assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
				assertTrue(msg.getContent().toStringUtf8().equals("What are you doing "+0));
			}
			{
				String childId = f.children.last().id;
				Meta msg = localStore.messages.get(childId);
				assertTrue(null != msg);
				assertTrue(msg.getFrom().equals(juliet));
				assertTrue(msg.getTo().equals(romeo));
				assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
				assertTrue(msg.getContent().toStringUtf8().equals("What are you doing "+(folderChildLimit-1)));
			}
		}
	}
	
	/*
	 * Test utilities
	 */
	private String testGroupChatInner() throws IOException{
		testNormalConversation();
		
		List<String> groupMembers = new LinkedList<String>();
		groupMembers.add(romeo);
		groupMembers.add(lawrence);
		String convId = createGroupChatConversation(juliet, groupMembers);
		String groupId = FolderID.getGroup(convId);

		checkMessageDeliverInGroupChat( groupId, "What are you doing" );
		
		return groupId;
	}
	
	private void checkMessageDeliverInGroupChat(String groupId, String textMsg ) throws IOException{
		String memberPropId = Group.memberFolderId(groupId);

		sendTextMessageToGroupChat( juliet, groupId, textMsg );
		
		cleanupGroupMessagesInLocalStore(groupId);
		//Every member will receive a notice, then he
		folderSyncAndSyncAllUnreadFolders(juliet);
		{
			//Juliet will get the group member folder
			Folder f = localStore.folders.get(memberPropId);
			assertTrue( f.children.size() == 3 );
			assertTrue( f.children.contains( new FolderChild(juliet) ) );
			localStore.folders.remove(memberPropId);
		}
		
		cleanupGroupMessagesInLocalStore(groupId);
		folderSyncAndSyncAllUnreadFolders(romeo);
		{
			//Romeo and Lawrence will get the group member folder
			Folder f = localStore.folders.get(memberPropId);
			assertTrue( f.children.size() == 3 );
			assertTrue( f.children.contains( new FolderChild(romeo) ) );
			localStore.folders.remove(memberPropId);
			
			//And a new group chat conversation with the text message
			Folder cf = localStore.folders.get(FolderID.onGroup(romeo, groupId));
			String childId = cf.children.last().id;
			
			Meta msg = localStore.messages.get(childId);
			assertTrue(null != msg);
			assertTrue(msg.getFrom().equals(juliet));
			assertTrue(msg.getTo().equals(groupId));
			assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
			assertTrue(msg.getContent().toStringUtf8().equals(textMsg));
		}
		
		cleanupGroupMessagesInLocalStore(groupId);
		folderSyncAndSyncAllUnreadFolders(lawrence);
		{
			//Ditto.
			Folder f = localStore.folders.get(memberPropId);
			assertTrue( f.children.size() == 3 );
			assertTrue( f.children.contains( new FolderChild(lawrence) ) );
			localStore.folders.remove(memberPropId);
			
			Folder cf = localStore.folders.get(FolderID.onGroup(lawrence, groupId));
			String childId = cf.children.last().id;
			
			Meta msg = localStore.messages.get(childId);
			assertTrue(null != msg);
			assertTrue(msg.getFrom().equals(juliet));
			assertTrue(msg.getTo().equals(groupId));
			assertTrue(MetaMessageType.valueOf(msg.getType().byteAt(0)).equals(MetaMessageType.text));
			assertTrue(msg.getContent().toStringUtf8().equals(textMsg));
		}
	}
	
	private void cleanupGroupMessagesInLocalStore(String groupId) {
		String folderId = Group.historyFolderId(groupId);
		Set<String> keys = new TreeSet<String>(localStore.messages.keySet());
		for(String mid : keys ){
			if( mid.startsWith(folderId) ) localStore.messages.remove(mid);
		}
	}

	private void sendTextMessageToGroupChat(String from, String groupId, String content) throws IOException {
		// Juliet send the message
		Meta.Builder msgBuilder = Meta
				.newBuilder()
				.setFrom(from)
				.setTo(groupId)
				.setType(ByteString.copyFrom(new byte[] { MetaMessageType.text.toByte() }))
				.setContent(ByteString.copyFromUtf8(content));
		String convId = FolderID.onGroup(from, groupId);

		sendMetaToFolder(from, msgBuilder, convId);
	}


	private String createGroupChatConversation(String username, List<String> groupMembers) throws IOException {
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.FolderCreate.toByte();
		
		FolderCreateReq req = FolderCreateReq.newBuilder()
				.setUserChatWith(username)
				.addAllAnotherUser(groupMembers)
				.build();
		
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri), req.toByteArray());
		
		FolderCreateResp resp = FolderCreateResp.parseFrom(respData);
		//store in root folder when success
		Folder conv = new Folder( resp.getFolderId() );
		localStore.folders.put(conv.id, conv);
		
		Folder root = localStore.folders.get( FolderID.onRoot(username) );
		root.addChild( new FolderChild(conv.id) );
		
		return conv.id;
	}

	private Meta removeMemberFromGroup(String groupId, String affectedUser){
		GroupOperation oper = GroupOperation.newBuilder()
				.setGroupId(groupId)
				.setType( ByteString.copyFrom( new byte[]{GroupOperationType.removeMember.toByte()} ) )
				.setUsername(affectedUser)
				.build();
		
		Meta operMeta = Meta.newBuilder()
				.setId("GroupOperation")
				.setType( ByteString.copyFrom( new byte[]{MetaMessageType.operation.toByte()}))
				.setTo(groupPluginName)
				.setContent( oper.toByteString() )
				.build();
		
		return operMeta;
	}
	
	private Meta addMemberToGroup(String groupId, String affectedUser){
		GroupOperation oper = GroupOperation.newBuilder()
				.setGroupId(groupId)
				.setType( ByteString.copyFrom( new byte[]{GroupOperationType.addMember.toByte()} ) )
				.setUsername(affectedUser)
				.build();
		
		Meta operMeta = Meta.newBuilder()
				.setId("GroupOperation")
				.setType( ByteString.copyFrom( new byte[]{MetaMessageType.operation.toByte()}))
				.setTo(groupPluginName)
				.setContent( oper.toByteString() )
				.build();
		
		return operMeta;
	}
	
	private MetaSet requestItemOperations(String username, MetaSet req) throws IOException{
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.ItemOperations.toByte();
		
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri),
				req.toByteArray());
		return MetaSet.parseFrom(respData);
	}
	
	private byte[] calculateMd5(InputStream is) throws IOException, NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		is = new DigestInputStream(is, md);
		
		byte[] buf = new byte[512];
		while( is.read(buf)>0 );
		is.close();
		return md.digest();
	}


	private void sendMetaToFolder(String username, Builder msgBuilder, String folderId) throws IOException {
		Queue<Meta> queue = localStore.pending.get(folderId);
		if (queue == null) {
			queue = new LinkedList<Meta>();
			localStore.pending.put(folderId, queue);
		}
		msgBuilder.setId("client-" + queue.size());
		queue.add(msgBuilder.build());

		sync(username, folderId);
	}

	private void sendLastPartOfFile(String username, String fileId, int sliceSize, int limit) throws IOException{
		InputStream fis = VangoghStarry.inputStream();
		byte[] sendBuf = new byte[sliceSize];
		
		FileData.Builder reqBuilder = FileData.newBuilder();
		for( int i = 1; i < limit; i++){
			fis.read(sendBuf);
		}
		
		//Send the second part from the last, duplicate sending just for missing information
		DataSlice sliceSecondFromTheLast = DataSlice.newBuilder().setIndex(limit-1).setLimit(limit)
				.setData(ByteString.copyFrom(sendBuf)).build();
		reqBuilder.setId(fileId).addSlice(sliceSecondFromTheLast);
		FileData missing = requestSendFile(username, reqBuilder.build());
		
		assertTrue(missing.getSliceCount() == 1);
		DataSlice slice0 = missing.getSlice(0);
		assertTrue(slice0.getIndex() == limit && slice0.getLimit()==limit );
		
		//Send the last part
		reqBuilder.clear();
		fis.read(sendBuf);
		DataSlice slice = DataSlice.newBuilder().setIndex(limit).setLimit(limit)
				.setData(ByteString.copyFrom(sendBuf)).build();
		reqBuilder.setId(fileId).addSlice(slice);
		FileData missingFinal = requestSendFile(username, reqBuilder.build());
		
		assertTrue(missingFinal.getSliceCount() == 0);
	}
	
	private void sendPartsOfFile(String username, String fileId, int sliceSize, int limit, String evenOrOdd, boolean batch) throws IOException {
		InputStream fis = VangoghStarry.inputStream();
		byte[] sendBuf = new byte[sliceSize];
		
		FileData.Builder reqBuilder = FileData.newBuilder();
		for( int i = 1; i < limit; i++){
			try {
				fis.read(sendBuf);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if( evenOrOdd.equals("even_parts") && (i%2 != 0) ) continue; 
			if( evenOrOdd.equals("odd_parts") && (i%2 == 0) ) continue;
			
			DataSlice slice = DataSlice.newBuilder().setIndex(i).setLimit(limit)
					.setData(ByteString.copyFrom(sendBuf)).build();
			
			reqBuilder.setId(fileId).addSlice(slice);
			if( !batch ){
				requestSendFile(username, reqBuilder.build());
				reqBuilder.clear();
			}
		}
		
		if( batch ) requestSendFile( username, reqBuilder.build() );
	}


	private FileData requestGetFile(String username, FileData req) throws IOException {
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.GetFile.toByte();
		
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri),
				req.toByteArray());
		return FileData.parseFrom(respData);
	}


	private FileData requestSendFile(String username, FileData req) throws IOException {
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.SendFile.toByte();
		
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri),
				req.toByteArray());
		return FileData.parseFrom(respData);
	}


	private GetItemUnreadResp requestGetItemUnread(String username, List<String> folderIds) throws IOException {
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.GetItemUnread.toByte();
		
		byte[] reqData = null;
		if( null != folderIds ){
			GetItemUnreadReq.Builder reqBuilder = GetItemUnreadReq.newBuilder();
			for(String folderId : folderIds ){
				reqBuilder.addFolderId(folderId);
			}
			reqData = reqBuilder.build().toByteArray();
		}
		
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri), reqData);
		return GetItemUnreadResp.parseFrom(respData);
	}


	private void sync(String username, String folderId) throws IOException {
		String syncKey = TAG_SYNC_KEY;		
		while(true){	
			SyncResp resp = requestSync(username, syncKey, folderId);
			
			String respFolderId = resp.getFolderId();
			Folder f = localStore.folders.get(folderId);
			if (null == f) {
				f = new Folder( respFolderId );
				localStore.folders.put(respFolderId, f);
			}

			if (resp.getIsFullSync()) {
				// TODO cleanup folder
			}
			
			//apply server changes
			for(Meta msg: resp.getServerChangesList() ){
				localStore.messages.put( msg.getId(), msg);
				f.addChildStealthily( new FolderChild(msg.getId()) );
			}
			
			//check client changes applied or not
			for(Meta msg: resp.getClientChangesList()){
				Map<String, Meta> syncMap = localStore.syncing.get(folderId);
				if( msg.hasContent() ){
					//accepted with new id
					Meta msgOriginal = syncMap.get( msg.getId() );
					Meta msgAccepted;
					try {
						msgAccepted = Meta.newBuilder(msgOriginal)
								.setId( msg.getContent().toString("UTF-8") )
								.build();
						
						localStore.messages.put( msgAccepted.getId(), msgAccepted);
						f.addChild( new FolderChild(msgAccepted.getId()) );
						syncMap.remove( msg.getId() );
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				}
			}

			String nextSyncKey = resp.getNextKey();
			log.debug("Syncing on "+folderId+" with syncKey "+syncKey+" and nextSyncKey is "+nextSyncKey);

			//check the advance notice
			if( !resp.getHasNext() )
				break;
			//instead of the old-fashion style
//			//Client change need to be synchronized to server despite of server changes exist or not
//			if( !syncKey.equals(TAG_SYNC_KEY) && SyncKey.isEmpty(nextSyncKey) ) 
//				break;
			
			syncKey = nextSyncKey;
		}		
	}

	private SyncResp requestSync(String username, String syncKey, String folderId) throws IOException {
		boolean isFullSync = false;
		Folder folder = localStore.folders.get(folderId);
		if (null == folder || FolderID.getType(folderId).equals(FolderID.Type.Property)) {
			//Property folder will always use full sync
			isFullSync = true;
		}
		
		return requestSync( username, syncKey, folderId, isFullSync);
	}
	
	private SyncResp requestSync(String username, String syncKey, String folderId, String hintChildId) throws IOException {
		return requestSync(username, syncKey, folderId, true, false, hintChildId);
	}
	
	private SyncResp requestSync(String username, String syncKey, String folderId, boolean isFullSync, boolean isForward) throws IOException {
		return requestSync(username, syncKey, folderId, isFullSync, isForward, null);
	}
	
	private SyncResp requestSync(String username, String syncKey, String folderId, boolean isFullSync) throws IOException {
		return requestSync(username, syncKey, folderId, isFullSync, false, null);
	}
	
	private SyncResp requestSync(String username, String syncKey, String folderId, 
			boolean isFullSync, boolean isForward, String hintChildId) throws IOException {
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.Sync.toByte();
		
		SyncReq.Builder reqBuilder = SyncReq.newBuilder()
				.setFolderId(folderId)
				.setIsFullSync(isFullSync)
				.setIsForward(isForward)
				.setKey(syncKey);
		if( null != hintChildId ) reqBuilder.setHintChildId(hintChildId);
		
		// client changes
		Queue<Meta> pendingQueue = localStore.pending.remove(folderId);
		if (null != pendingQueue) {
			Map<String, Meta> syncingMap = localStore.syncing.get(folderId);
			if (null == syncingMap) {
				syncingMap = new HashMap<String, Meta>();
				localStore.syncing.put(folderId, syncingMap);
			}
			for (Meta m : pendingQueue) {
				reqBuilder.addClientChanges(m);
				syncingMap.put(m.getId(), m);
			}
		}
		
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri), reqBuilder.build().toByteArray());
		return SyncResp.parseFrom(respData);		
	}


	private FolderSyncResp requestFolderSync(String username, String syncKey, String folderId) throws IOException{
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.FolderSync.toByte();
		
		FolderSyncReq req = FolderSyncReq.newBuilder().setId(folderId)
				.setKey(syncKey).build();
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri),
				req.toByteArray());
		return FolderSyncResp.parseFrom(respData);
	}
	
	private void folderSyncOnFolder( String username) throws IOException{
		String folderId = FolderID.onRoot(username);
		localStore.folders.remove(folderId);
		
		String syncKey = TAG_SYNC_KEY;		
		while(true){	
			FolderSyncResp resp = requestFolderSync(username, syncKey, folderId);
			
			folderId = resp.getId();
			Folder f = localStore.folders.get(folderId);
			if (null == f) {
				f = new Folder( folderId );
				localStore.folders.put(folderId, f);
			}
			
			//initial FolderSync with fullSync will get the folder list 
			for (String childId : resp.getChildIdList()) {
				f.addChildStealthily( new FolderChild(childId) );
			}
			
			String nextSyncKey = resp.getNextKey();
			if( SyncKey.isEmpty(nextSyncKey) ) 
				break;
			syncKey = nextSyncKey;
		}		
	}
	
	private List<String> folderSync(String user) throws IOException{
		String rootId = FolderID.onRoot(user);
		folderSyncOnFolder(user);
		
		if( null == localStore.folders.get(rootId).children ){
			localStore.folders.get(rootId).children = new ConcurrentSkipListSet<FolderChild>();
		}
		
		List<String> requiredSyncFolders = new LinkedList<String>();
		for( FolderChild child : localStore.folders.get(rootId).children ){
			String fid = child.id;
			if( localStore.folders.containsKey(fid) ) continue;
			
			requiredSyncFolders.add(fid);
		}
		return requiredSyncFolders;
	}
	
	private void createConversation(String username, String userChatWith) throws IOException {
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.FolderCreate.toByte();
		
		FolderCreateReq req = FolderCreateReq.newBuilder()
				.setUserChatWith(userChatWith)
				.build();
		byte[] respData = weSync.request(username, WeSyncURI.toBytes(uri), req.toByteArray());
		
		FolderCreateResp resp = FolderCreateResp.parseFrom(respData);
		//store in root folder when success
		Folder conv = new Folder( resp.getFolderId() );
		localStore.folders.put(conv.id, conv);
		
		Folder root = localStore.folders.get( FolderID.onRoot(username) );
		root.addChild( new FolderChild(conv.id) );
	}

	private void cleanupConversation(String username, String userChatWith) throws IOException {
		WeSyncURI uri = getWeSyncURI();
		uri.command = Command.FolderDelete.toByte();
		
		FolderDeleteReq req = FolderDeleteReq.newBuilder()
				.setUserChatWith(userChatWith)
				.setIsContentOnly(true)
				.build();
		weSync.request(username, WeSyncURI.toBytes(uri), req.toByteArray());
		
		//no exception means successful
		localStore.folders.remove( FolderID.onConversation(username, userChatWith));
	}
	
	private WeSyncURI getWeSyncURI(){
		WeSyncURI uri = new WeSyncURI();
		uri.protocolVersion = 10;
		uri.guid = "1234567890abcdefg";
		uri.deviceType = "iphone";		
		return uri;
	}
}
