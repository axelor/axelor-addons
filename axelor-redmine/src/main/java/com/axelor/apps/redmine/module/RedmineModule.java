package com.axelor.apps.redmine.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.redmine.service.app.AppRedmineService;
import com.axelor.apps.redmine.service.app.AppRedmineServiceImpl;

public class RedmineModule extends AxelorModule{

	@Override
	protected void configure() {
		bind(AppRedmineService.class).to(AppRedmineServiceImpl.class);
	}

}
