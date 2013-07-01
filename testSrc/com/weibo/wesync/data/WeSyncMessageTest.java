package com.weibo.wesync.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

import org.junit.Test;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.weibo.wesync.data.WeSyncMessage.CommandMessage;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.SyncReq;
import com.weibo.wesync.data.WeSyncMessage.SyncResp;
/**
 * @author Eric Liang
 */
public class WeSyncMessageTest extends TestCase {
	@Test
	public void testCommandRequest() throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		
		//write a folderSync request
		CommandMessage.Builder folderSync1 = CommandMessage.newBuilder();
		folderSync1.setCode( ByteString.copyFrom( new byte[]{com.weibo.wesync.Command.FolderSync.toByte()} ));
		byte[] folderSync1Data = folderSync1.build().toByteArray(); 
		dos.writeInt( folderSync1Data.length );
		dos.write( folderSync1Data );

		//then a sync request
		CommandMessage.Builder sync1 = CommandMessage.newBuilder();
		sync1.setCode( ByteString.copyFrom( new byte[]{com.weibo.wesync.Command.Sync.toByte()} ));
		SyncReq.Builder syncReq1 = SyncReq.newBuilder();
		syncReq1.setKey("sync-key-test");
		sync1.setData(syncReq1.build().toByteString());	
		byte[] sync1Data = sync1.build().toByteArray(); 
		dos.writeInt(sync1Data.length);
		dos.write(sync1Data);
		
		dos.close();
		bos.close();
		byte[] commData = bos.toByteArray();
		ByteArrayInputStream bis = new ByteArrayInputStream( commData );
		DataInputStream dis = new DataInputStream( bis );
		
		//read the folderSync request
		int lenFolderSync = dis.readInt();
		byte[] folderSync2Data = new byte[lenFolderSync];
		int lenFolderSync2 = dis.read(folderSync2Data);
		assertTrue( lenFolderSync == lenFolderSync2 );
		CommandMessage folderSync2 = CommandMessage.parseFrom(folderSync2Data);
		assertTrue( com.weibo.wesync.Command.FolderSync.equals(com.weibo.wesync.Command.valueOf( folderSync2.getCode().byteAt(0) )));
		
		//read the sync request
		int lenSync = dis.readInt();
		byte[] sync2Data = new byte[lenSync];
		dis.read(sync2Data);
		CommandMessage sync2 = CommandMessage.parseFrom(sync2Data);
		assertTrue( com.weibo.wesync.Command.Sync.equals(com.weibo.wesync.Command.valueOf( sync2.getCode().byteAt(0) )));
		
		SyncReq syncReq2 = SyncReq.parseFrom( sync2.getData() );
		assertTrue( syncReq2.getKey().equals(syncReq1.getKey()));
	}
	
	public void testCommandResponse() throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		
		//write a folderSync response
		CommandMessage.Builder folderSync1 = CommandMessage.newBuilder();
		folderSync1.setCode( ByteString.copyFrom( new byte[]{com.weibo.wesync.Command.FolderSync.toByte()} ));
		byte[] folderSync1Data = folderSync1.build().toByteArray(); 
		dos.writeInt( folderSync1Data.length );
		dos.write( folderSync1Data );

		//then write a sync response
		CommandMessage.Builder sync1 = CommandMessage.newBuilder();
		sync1.setCode( ByteString.copyFrom( new byte[]{com.weibo.wesync.Command.Sync.toByte()} ));
		
		SyncResp.Builder syncResp1 = SyncResp.newBuilder();
		syncResp1.setNextKey("next-sync-key");
		FieldDescriptor fieldServerChanges = SyncResp.getDescriptor().findFieldByName("serverChanges");
		Meta meta1= Meta.newBuilder()
			.setFrom("juliet")
			.setTo("romeo")
			.setType(ByteString.copyFrom( new byte[]{com.weibo.wesync.Command.FolderSync.toByte()}))
			.build();
//		syncResp1.setRepeatedField(fieldServerChanges, 0, meta1);
//		syncResp1.addRepeatedField(fieldServerChanges, meta1);
		syncResp1.addServerChanges(meta1);
		
		sync1.setData(syncResp1.build().toByteString());	
		byte[] sync1Data = sync1.build().toByteArray(); 
		dos.writeInt(sync1Data.length);
		dos.write(sync1Data);
		
		dos.close();
		bos.close();
		byte[] commData = bos.toByteArray();
		ByteArrayInputStream bis = new ByteArrayInputStream( commData );
		DataInputStream dis = new DataInputStream( bis );
		
		//read the folderSync response
		int lenFolderSync = dis.readInt();
		byte[] folderSync2Data = new byte[lenFolderSync];
		int lenFolderSync2 = dis.read(folderSync2Data);
		assertTrue( lenFolderSync == lenFolderSync2 );
		CommandMessage folderSync2 = CommandMessage.parseFrom(folderSync2Data);
		assertTrue( com.weibo.wesync.Command.FolderSync.equals(com.weibo.wesync.Command.valueOf( folderSync2.getCode().byteAt(0) )));
		assertTrue( Arrays.equals( folderSync2.getData().toByteArray(), new byte[0]) );
		
		//read the sync response
		int lenSync = dis.readInt();
		byte[] sync2Data = new byte[lenSync];
		dis.read(sync2Data);
		CommandMessage sync2 = CommandMessage.parseFrom(sync2Data);
		assertTrue( com.weibo.wesync.Command.Sync.equals(com.weibo.wesync.Command.valueOf( sync2.getCode().byteAt(0) )));
		
		SyncResp syncResp2 = SyncResp.parseFrom( sync2.getData() );
		assertTrue( syncResp2.getNextKey().equals(syncResp1.getNextKey()));
		int metaNum = syncResp2.getRepeatedFieldCount(fieldServerChanges);
		assertTrue( metaNum == 1 );
//		Meta meta2 = (Meta) syncResp2.getRepeatedField(fieldServerChanges, 0);
		Meta meta2 = syncResp2.getServerChanges(0);
		
		assertTrue( meta2.getFrom().equals( meta1.getFrom() ));
		assertTrue( meta2.getTo().equals( meta1.getTo() ));
		assertTrue( meta2.getType().equals( meta1.getType() ));
	}

}
