package com.weibo.wesync.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.ByteString;
import com.weibo.wesync.data.WeSyncMessage.DataSlice;
import com.weibo.wesync.data.WeSyncMessage.FileData;

/**
 * 
 * @author Eric Liang
 *
 */
public class FileDataHelperTest extends TestCase {
	private FileData file;
	private int limit = 100;
	
	@Before
	public void prepare(){
		FileData.Builder fileBuilder = FileData.newBuilder();
		DataSlice.Builder sliceBuilder = DataSlice.newBuilder();
		
		fileBuilder.setId( "TestFileID" );
		sliceBuilder.setLimit(limit);
		for(int i=2; i<limit; i+=2){
			sliceBuilder.setIndex(i);
			sliceBuilder.setData( ByteString.copyFromUtf8( String.valueOf(i) ) );
			fileBuilder.addSlice( sliceBuilder.build() );
		}
		file = fileBuilder.build();
	}
	
	@Test
	public void testFindMissing(){
		prepare();
		
		List<DataSlice> missing = FileDataHelper.findMissing(file);
		assertTrue( missing.size() == limit/2+1 );
		Map<Integer, DataSlice> sliceMap = new HashMap<Integer, DataSlice>();
		for(DataSlice slice : missing ){
			sliceMap.put(Integer.valueOf(slice.getIndex()), slice);
		}
		
		for(int i=1; i<=limit; i+=2 ){
			assertTrue( sliceMap.containsKey(Integer.valueOf(i)) );
		}
		assertTrue( sliceMap.containsKey(Integer.valueOf(limit)) );
	}
	
	@Test
	public void testExtract(){
		prepare();
		
		FileData.Builder indexBuilder = FileData.newBuilder()
				.setId( file.getId() );
		DataSlice.Builder sliceBuilder = DataSlice.newBuilder()
				.setLimit(101);
		
		for(int i=2; i<10; i+=2){
			sliceBuilder.setIndex(i);
			indexBuilder.addSlice( sliceBuilder.build() );
		}
		
		List<DataSlice> slices = FileDataHelper.extract(file, indexBuilder.build() );
		String[] array = new String[slices.size()];
		int i = 0;
		for(DataSlice s: slices){
			array[i++] = s.getData().toStringUtf8();
		}
		Arrays.sort( array );
		
//		System.out.println(Arrays.toString( array ) );
		assertTrue( Arrays.toString(array).equals("[2, 4, 6, 8]") );
	}
}
