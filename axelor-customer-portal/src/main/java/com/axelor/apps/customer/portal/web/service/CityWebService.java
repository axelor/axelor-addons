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
package com.axelor.apps.customer.portal.web.service;

import com.axelor.apps.base.db.City;
import com.axelor.apps.customer.portal.service.response.PortalRestResponse;
import com.axelor.apps.customer.portal.service.response.ResponseGeneratorFactory;
import com.axelor.apps.customer.portal.service.response.generator.ResponseGenerator;
import com.axelor.common.StringUtils;
import com.axelor.db.JpaSecurity;
import com.axelor.db.JpaSecurity.AccessType;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/portal/cities")
public class CityWebService extends AbstractWebService {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public PortalRestResponse fetch(
      @QueryParam("sort") String sort,
      @QueryParam("page") int page,
      @QueryParam("limit") int limit,
      @QueryParam("searchInput") String searchInput) {

    final Map<String, Object> params = new HashMap<String, Object>();
    StringBuilder filter = new StringBuilder();
    if (StringUtils.notBlank(searchInput)) {
      filter.append("(lower(self.name) like :pattern)");
      params.put("pattern", String.format("%%%s%%", searchInput.toLowerCase()));
    }

    List<City> cities = fetch(City.class, filter.toString(), params, sort, limit, page);
    Beans.get(JpaSecurity.class)
        .check(AccessType.READ, City.class, cities.stream().map(City::getId).toArray(Long[]::new));

    ResponseGenerator generator = ResponseGeneratorFactory.of(City.class.getName());

    List<Map<String, Object>> data =
        cities.stream().map(generator::generate).collect(Collectors.toList());

    PortalRestResponse response = new PortalRestResponse();
    response.setTotal(totalQuery(City.class, null, null));
    response.setOffset((page - 1) * limit);
    return response.setData(data).success();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{id}")
  public PortalRestResponse fetch(@PathParam("id") Long id) throws AxelorException {

    final Map<String, Object> params = new HashMap<String, Object>();
    final String filter = "self.id = :id";
    params.put("id", id);

    List<City> cities = fetch(City.class, filter, params, null, 0, 1);
    City city = cities.get(0);
    Beans.get(JpaSecurity.class).check(AccessType.READ, City.class, city.getId());

    Map<String, Object> data = ResponseGeneratorFactory.of(City.class.getName()).generate(city);
    PortalRestResponse response = new PortalRestResponse();
    return response.setData(data).success();
  }
}
