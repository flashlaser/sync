package com.weibo.wesync;

import com.google.inject.AbstractModule;
import com.weibo.wesync.data.FakeDataStore;

/**
 * @author Eric Liang
 */
public class FakeWeSyncModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(DataService.class).toInstance(new DataServiceImpl( new FakeDataStore() ) );
		bind(NoticeService.class).toInstance( new FakeNoticeService() );
		bind(GroupMessageService.class).to(FakeGroupMessageService.class);
		bind(PrivacyService.class).to(FakePrivacyService.class);
		bind(WeSyncService.class).to(WeSyncServiceImpl.class);
	}
}
