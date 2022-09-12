/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.docusign.service;

import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.MetaSelect;
import com.axelor.meta.db.MetaSelectItem;
import com.axelor.meta.db.repo.MetaSelectItemRepository;
import com.axelor.meta.db.repo.MetaSelectRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;

public class DocuSignEnvelopeSettingServiceImpl implements DocuSignEnvelopeSettingService {

  protected final MetaSelectItemRepository metaSelectItemRepository;

  @Inject
  public DocuSignEnvelopeSettingServiceImpl(MetaSelectItemRepository metaSelectItemRepository) {
    this.metaSelectItemRepository = metaSelectItemRepository;
  }

  @Override
  @Transactional
  public void addItemToReferenceSelection(MetaModel model) {
    MetaSelect metaSelect =
        Beans.get(MetaSelectRepository.class).findByName("docusign.envelope.related.to.select");
    List<MetaSelectItem> items = metaSelect.getItems();

    String fullName = model.getFullName();
    if (items != null
        && items.stream().map(MetaSelectItem::getValue).anyMatch(value -> value.equals(fullName))) {
      return;
    }

    MetaSelectItem metaSelectItem = new MetaSelectItem();
    metaSelectItem.setTitle(model.getName());
    metaSelectItem.setValue(fullName);
    metaSelectItem.setSelect(metaSelect);
    metaSelectItemRepository.save(metaSelectItem);
  }
}
