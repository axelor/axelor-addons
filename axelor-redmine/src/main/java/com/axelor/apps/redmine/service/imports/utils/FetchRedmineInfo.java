package com.axelor.apps.redmine.service.imports.utils;

import com.axelor.common.ObjectUtils;
import com.axelor.studio.db.AppRedmine;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FetchRedmineInfo {

  /**
   * This method extracts users from redmine depending on given parameters params
   *
   * @param redmineManager is the selected redmine manager (preferrably one which oversees all
   *     projects
   * @param includedIdsMap is the list of redmine users' ids
   * @param redmineUserList is the list of redmine Users
   * @param params determines users' params to filter
   * @throws RedmineException specific redmine java API exception thrown when fetching can't
   *     complete
   */
  public static void fillUsersList(
      RedmineManager redmineManager,
      Map<Integer, Boolean> includedIdsMap,
      List<User> redmineUserList,
      Map<String, String> params)
      throws RedmineException {
    int offset = 0;
    int limit = 25;
    List<User> users = new ArrayList<>();
    do {
      params.put("offset", String.valueOf(offset));
      params.put("limit", String.valueOf(limit));

      users = redmineManager.getUserManager().getUsers(params).getResults();

      for (User user : users) {
        if (!includedIdsMap.containsKey(user.getId())) {
          redmineUserList.add(user);
          includedIdsMap.put(user.getId(), true);
        }
      }
      offset += limit;
    } while (users.size() == limit);
  }

  public static Map<String, String> getFillUsersListParams(AppRedmine appRedmine) {
    Map<String, String> params = new HashMap<>();
    params.put("status", appRedmine.getRedmineUsersStatus());
    String onUsersFilter = appRedmine.getOnUsersFilter();
    if (ObjectUtils.notEmpty(onUsersFilter)) {
      params.put("name", onUsersFilter);
    }

    return params;
  }
}
