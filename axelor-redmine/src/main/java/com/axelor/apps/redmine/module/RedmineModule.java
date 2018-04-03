package com.axelor.apps.redmine.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.redmine.service.app.RedmineService;
import com.axelor.apps.redmine.service.app.RedmineServiceImpl;

public class RedmineModule extends AxelorModule{

	@Override
	protected void configure() {
		bind(RedmineService.class).to(RedmineServiceImpl.class);
	}

}
