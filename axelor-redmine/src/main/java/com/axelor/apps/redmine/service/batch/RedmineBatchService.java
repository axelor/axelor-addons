package com.axelor.apps.redmine.service.batch;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;

public class RedmineBatchService extends AbstractBatchService{

	@Override
	protected Class<? extends Model> getModelClass() {
		return RedmineBatch.class;
	}

	@Override
	public Batch run(Model batchModel) throws AxelorException {
		Batch batch;
		RedmineBatch redmineBatch = (RedmineBatch) batchModel;
		
		switch (redmineBatch.getActionSelect()) {
		
		case RedmineBatchRepository.ACTION_IMPORT:
			batch = eventImportIssues(redmineBatch);
			break;
		
		default:
			throw new AxelorException(IException.INCONSISTENCY, I18n.get(IExceptionMessage.BASE_BATCH_1), redmineBatch.getActionSelect(), redmineBatch.getCode());
		}
		
		return batch;
	}

	/*
	 * Calling BatchImportIssues.class to import the issues from Redmine
	 * */
	public Batch eventImportIssues(RedmineBatch redmineBatch) {
		return Beans.get(BatchImportIssues.class).run(redmineBatch);
	}
}
