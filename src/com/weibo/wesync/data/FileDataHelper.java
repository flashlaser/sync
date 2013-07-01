package com.weibo.wesync.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.weibo.wesync.data.WeSyncMessage.DataSlice;
import com.weibo.wesync.data.WeSyncMessage.FileData;

/**
 * 
 * @author Eric Liang
 *
 */
public final class FileDataHelper {
	public static byte[] pad( FileData file ){
		//FIXME maybe exception? 
//		if( !isSane() ) return null;
		
		Map<Integer, DataSlice> sliceMap = new HashMap<Integer, DataSlice>();
		for( DataSlice s : file.getSliceList() ){
			sliceMap.put(Integer.valueOf(s.getIndex()), s);
		}
		
		//Just pad for some media file is not necessary for sanity
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			for(int i=1; i<=file.getSliceCount(); i++){
				bos.write( sliceMap.get(Integer.valueOf(i)).getData().toByteArray() );
			}
			bos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return bos.toByteArray();
	}
	
	private static int getMaxLimit( List<DataSlice> slices){
		//TODO this might be optimized if not support variable limit
		int limit = 0;
		for(DataSlice s : slices ){
			if( limit < s.getLimit() ) limit = s.getLimit();
		}
		return limit;
	}
	private static BitSet createBitSet( List<DataSlice> slices, int limit ){
		BitSet set = new BitSet(limit+1);
		for(DataSlice s : slices ){
			//skip invalid data slice
			if(s.getIndex() > limit) continue;
			
			set.set(s.getIndex());
		}
		return set;
	}
	
	public static List<DataSlice> findMissing( FileData file ){
		List<DataSlice> slices = file.getSliceList();

		int limit = getMaxLimit( slices );
		BitSet set = createBitSet( slices, limit );

		List<DataSlice> missing = new LinkedList<DataSlice>();
		DataSlice.Builder builder = DataSlice.newBuilder();
		builder.setLimit(limit);
		
		for(int i=1,j=1; i<=limit; i=++j){
			j = set.nextClearBit(i);
			if(j < 0) break;
			
			builder.setIndex(j);
			missing.add( builder.build() );
		}
		
		return missing;
	}
	
	public static List<DataSlice> extract( FileData file, FileData index ){
		int limit = getMaxLimit( file.getSliceList() );
		BitSet set = createBitSet( index.getSliceList(), limit );

		List<DataSlice> extracted = new LinkedList<DataSlice>();
		DataSlice.Builder builder = DataSlice.newBuilder();
		builder.setLimit(limit);
		
		for(DataSlice s : file.getSliceList() ){
			if( set.get( s.getIndex() ) ){
				builder.setIndex( s.getIndex() );
				builder.setData( s.getData() );
				extracted.add( builder.build() );
			}
		}
		
		return extracted;
	}
	
	public static boolean isSane( FileData file ){
		List<DataSlice> slices = file.getSliceList();

		int limit = getMaxLimit( slices );
		BitSet set = createBitSet( slices, limit );
		
		//The number of bits set should be equal to the limit
		//Check by BitSet since slices might contain duplicate data.
		if( set.cardinality() != limit ) return false;
		
		return true;
	}

	//Return true if the file is near complete, that is, the server have received the last data slice (index==limit)
	public static boolean nearComplete(FileData file) {
		final double threshold = 0.9;
		
		List<DataSlice> slices = file.getSliceList();
		int limit = getMaxLimit( slices );
		if( file.getSliceCount() / limit >= threshold ) return true;
		
		return false;
	}

	public static FileData removeDuplicate(FileData file) {
		int limit = getMaxLimit(file.getSliceList());
		if( file.getSliceCount() <= limit ) return file;
		
		Map<Integer, DataSlice> sliceMap = new HashMap<Integer, DataSlice>();
		for( DataSlice s : file.getSliceList() ){
			sliceMap.put(Integer.valueOf(s.getIndex()), s);
		}
		
		FileData newFile = FileData.newBuilder()
				.setId( file.getId() )
				.addAllSlice( sliceMap.values() )
				.build();
		return newFile;
	}
}
