/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.prestashop.service.library;

import com.axelor.apps.prestashop.entities.ListContainer;
import com.axelor.apps.prestashop.entities.Prestashop;
import com.axelor.apps.prestashop.entities.PrestashopContainerEntity;
import com.axelor.apps.prestashop.entities.PrestashopIdentifiableEntity;
import com.axelor.apps.prestashop.entities.PrestashopImage;
import com.axelor.apps.prestashop.entities.PrestashopOrderInvoice;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PSWebServiceClient {
  // HttpClient default content types are ISO-8859-1 encoded (except for JSON)
  private static final ContentType XML_CONTENT_TYPE = ContentType.create("text/xml", Consts.UTF_8);
  /** Header containing the API version of webservices */
  private static final String VERSION_HEADER = "PSWS-Version";

  private final Logger log = LoggerFactory.getLogger(getClass());
  private JAXBContext jaxbContext;

  /** Shop URL */
  protected String url;

  private final CloseableHttpClient httpclient;
  private final Credentials credentials;
  private String wsVersion;

  /**
   * PrestaShopWebservice constructor. <code>
   *
   * try
   * {
   * 	PSWebServiceClient ws = new PSWebServiceClient('http://mystore.com/', 'ZQ88PRJX5VWQHCWE4EE7SQ7HPNX00RAJ', false);
   * 	// Now we have a webservice object to play with
   * }
   * catch (PrestaShopWebserviceException ex)
   * {
   * 	// Handle exception
   * }
   *
   * </code>
   *
   * @param url Root URL for the shop
   * @param key Authentification key
   */
  public PSWebServiceClient(String url, String key) {
    this.url = url;
    credentials = new UsernamePasswordCredentials(key, null);
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(key, ""));
    this.httpclient = HttpClients.createDefault();

    try {
      jaxbContext =
          JAXBContext.newInstance(
              "com.axelor.apps.prestashop.entities:com.axelor.apps.prestashop.entities.xlink");
    } catch (JAXBException e) {
      log.error("Unable to create jaxb context", e);
      throw new RuntimeException("Unable to create JAXB context", e);
    }
  }

  /**
   * Take the status code and throw an exception if the server didn't return 200 or 201 code
   *
   * @param status_code Status code of an HTTP return
   * @throws pswebservice.PrestaShopWebserviceException
   */
  protected void checkStatusCode(CloseableHttpResponse response)
      throws PrestaShopWebserviceException {
    final int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) return;
    String body = null;
    if (response.getEntity() != null) {
      try {
        body = IOUtils.toString(response.getEntity().getContent(), Consts.UTF_8);
      } catch (UnsupportedOperationException | IOException e) {
      }
    }
    throw new PrestashopHttpException(
        statusCode,
        String.format(
            "An underlying call to the Prestashop API failed with status code %d: %s (%s)\n=== Response body ===\n%s\n=== End of response body ===",
            statusCode,
            response.getStatusLine().getReasonPhrase(),
            // HttpStatus.getStatusText(statusCode),
            body));
  }

  /**
   * Handles request to PrestaShop Webservice. Can throw exception.
   *
   * @param url Resource name
   * @param request
   * @return array status_code, response
   * @throws pswebservice.PrestaShopWebserviceException
   */
  protected RequestResult executeRequest(HttpUriRequest request)
      throws PrestaShopWebserviceException {
    final RequestResult result = new RequestResult();
    try {
      request.addHeader(new BasicScheme().authenticate(credentials, request, null));
      result.response = httpclient.execute(request);
      checkStatusCode(result.response);
      result.content = result.response.getEntity().getContent();
      Header versionHeader = result.response.getFirstHeader(VERSION_HEADER);
      if (versionHeader != null) {
        wsVersion = versionHeader.getValue();
      }

      return result;
    } catch (UnsupportedOperationException | IOException | AuthenticationException e) {
      IOUtils.closeQuietly(result.response);
      throw new PrestaShopWebserviceException("Error while processing request", e);
    } catch (PrestaShopWebserviceException e) {
      IOUtils.closeQuietly(result.response);
      throw e;
    }
  }

  /**
   * Fetches a single resource by its ID.
   *
   * @param resourceType Type of resource
   * @param id Id of the resource to fetch
   * @return The requested resource or null if such resource does not exists.
   * @throws PrestaShopWebserviceException
   */
  public <T extends PrestashopContainerEntity> T fetch(
      final PrestashopResourceType resourceType, final int id)
      throws PrestaShopWebserviceException {
    Options options = new Options();
    options.setResourceType(resourceType);
    options.setRequestedId(id);

    HttpGet httpget = new HttpGet(buildUri(options));
    RequestResult result = null;

    try {
      result = executeRequest(httpget);
      return ((Prestashop) jaxbContext.createUnmarshaller().unmarshal(result.content)).getContent();
    } catch (PrestashopHttpException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) return null;
      throw e;
    } catch (JAXBException e) {
      throw new PrestaShopWebserviceException("Error while unmarshalling respoinse from fetch", e);
    } finally {
      log.trace("Closing connection");
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  /**
   * Fetches default values for the given entity types
   *
   * @param resourceType Type of entity to fetch
   * @return An instance of the requested entity with all its attributes set to default from
   *     PrestaShop side. Most of the time this means null, but this can be very useful to
   *     initialize translatable strings.
   * @throws PrestaShopWebserviceException
   */
  public <T extends PrestashopContainerEntity> T fetchDefault(
      final PrestashopResourceType resourceType) throws PrestaShopWebserviceException {
    Options options = new Options();
    options.setResourceType(resourceType);
    options.setSchemaType("blank");

    HttpGet httpget = new HttpGet(buildUri(options));
    RequestResult result = null;

    try {
      result = executeRequest(httpget);
      return ((Prestashop) jaxbContext.createUnmarshaller().unmarshal(result.content)).getContent();
    } catch (JAXBException e) {
      throw new PrestaShopWebserviceException(
          "Error while unmarshalling respoinse from fetchDefault", e);
    } finally {
      log.trace("Closing connection");
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  /**
   * Fetches all entities of a given type, along with their attributes.
   *
   * @param resourceType Type of entity to fetch.
   * @return A possibly empty list containing all entities.
   * @throws PrestaShopWebserviceException
   */
  public <T extends PrestashopContainerEntity> List<T> fetchAll(
      final PrestashopResourceType resourceType) throws PrestaShopWebserviceException {
    return fetchAll(resourceType, Collections.emptyList());
  }

  /**
   * Fetches all entities of a given type, along with their attributes.
   *
   * @param resourceType Type of entity to fetch.
   * @param sort List of sort fields (syntax is field_ASC or field_DESC as per PrestaShop
   *     expectations) or <code>null</code> if no sorting is required.
   * @return A possibly empty list containing all entities.
   * @throws PrestaShopWebserviceException
   */
  public <T extends PrestashopContainerEntity> List<T> fetchAll(
      final PrestashopResourceType resourceType, final List<String> sort)
      throws PrestaShopWebserviceException {
    return fetch(resourceType, Collections.emptyMap(), sort);
  }

  /**
   * Fetches all entities of a given type, along with their attributes, with a filter
   *
   * @param resourceType Type of entity to fetch.
   * @param filter fieldname → value filter
   * @return A possibly empty list containing all entities.
   * @throws PrestaShopWebserviceException
   */
  public <T extends PrestashopContainerEntity> List<T> fetch(
      final PrestashopResourceType resourceType, final Map<String, String> filter)
      throws PrestaShopWebserviceException {
    return fetch(resourceType, filter, Collections.emptyList());
  }
  /**
   * Returns the first entity of thoes matching filter
   *
   * @param resourceType Resource type to fetch
   * @param filter Filter to apply
   * @return The first entry of filtered resultset, or null if resultset is empty
   * @throws PrestaShopWebserviceException If underlying webservice call fails or if filter returns
   *     more than one entity.
   */
  public <T extends PrestashopContainerEntity> T fetchOne(
      final PrestashopResourceType resourceType, final Map<String, String> filter)
      throws PrestaShopWebserviceException {
    List<T> entities = fetch(resourceType, filter);
    if (entities.size() == 0) return null;
    if (entities.size() > 1)
      throw new PrestaShopWebserviceException(
          String.format(
              "fetchOne for %s with filter %s returned %d results",
              resourceType.getLabel(), filter.toString(), entities.size()));
    return entities.get(0);
  }

  /**
   * Fetches a list of entities based on the given filter. Entities will have all their attributes
   * set.
   *
   * @param resourceType Type of resource to fetch.
   * @param filter Filter to apply (depends on entity)
   * @param sort Entities sort criteria (<code>null</code> for no sorting)
   * @return A (possibly empty) list of entities
   * @throws PrestaShopWebserviceException
   */
  @SuppressWarnings("unchecked")
  public <T extends PrestashopContainerEntity> List<T> fetch(
      final PrestashopResourceType resourceType,
      final Map<String, String> filter,
      final List<String> sort)
      throws PrestaShopWebserviceException {
    Options options = new Options();
    options.setResourceType(resourceType);
    options.setFilter(filter);
    options.setDisplay(Collections.singletonList("full"));
    options.setSort(sort);

    HttpGet httpget = new HttpGet(buildUri(options));
    RequestResult result = null;

    try {
      result = executeRequest(httpget);
      return ((ListContainer<T>)
              ((Prestashop) jaxbContext.createUnmarshaller().unmarshal(result.content))
                  .getContent())
          .getEntities();
    } catch (JAXBException e) {
      throw new PrestaShopWebserviceException("Error while unmarshalling response from fetch", e);
    } finally {
      log.trace("Closing connection");
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  /**
   * Fetches a container entity from relative URI. Used for non-resource types URI (eg. /api)
   *
   * @param relativeUri URI of the resource to fetch, relative to {@link #url}
   * @return The fetched entity
   * @throws PrestaShopWebserviceException If any underlying call fails
   */
  public <T extends PrestashopContainerEntity> T fetch(final String relativeUri)
      throws PrestaShopWebserviceException {
    HttpGet httpget = new HttpGet(String.format("%s/%s", this.url, relativeUri));
    RequestResult result = null;

    try {
      result = executeRequest(httpget);
      return ((Prestashop) jaxbContext.createUnmarshaller().unmarshal(result.content)).getContent();
    } catch (JAXBException e) {
      throw new PrestaShopWebserviceException("Error while unmarshalling response from fetch", e);
    } finally {
      log.trace("Closing connection");
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  public <T extends PrestashopIdentifiableEntity> void delete(
      final PrestashopResourceType resourceType, final T entity)
      throws PrestaShopWebserviceException {
    Options options = new Options();
    options.entityId = entity.getId();
    options.resourceType = resourceType;

    HttpDelete httpdelete = new HttpDelete(buildUri(options));
    RequestResult result = null;

    try {
      result = executeRequest(httpdelete);
      // Maybe we should decode response…
    } finally {
      log.trace("Closing connection");
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends PrestashopIdentifiableEntity> T save(
      final PrestashopResourceType resourceType, final T entity)
      throws PrestaShopWebserviceException {
    Options options = new Options();
    options.setResourceType(resourceType);
    options.setRequestedId(entity.getId());

    ByteArrayOutputStream bos = new ByteArrayOutputStream();

    try {
      Prestashop envelop = new Prestashop();
      envelop.setContent(entity);
      jaxbContext.createMarshaller().marshal(envelop, bos);
    } catch (JAXBException e) {
      throw new PrestaShopWebserviceException(
          "Error while marshalling class " + entity.getClass(), e);
    }

    HttpEntityEnclosingRequestBase request;
    if (entity.getId() == null) {
      request = new HttpPost(buildUri(options));
    } else {
      request = new HttpPut(buildUri(options));
    }

    request.setEntity(new ByteArrayEntity(bos.toByteArray(), XML_CONTENT_TYPE));
    RequestResult result = null;

    String content = null;
    try {
      result = executeRequest(request);
      content = IOUtils.toString(result.content, Consts.UTF_8);
      return (T)
          ((Prestashop)
                  jaxbContext
                      .createUnmarshaller()
                      .unmarshal(new ByteArrayInputStream(content.getBytes())))
              .getContent();
    } catch (JAXBException | IOException e) {
      throw new PrestaShopWebserviceException("Error while unmarshalling response from save", e);
    } finally {
      log.trace("Closing connection");
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  /**
   * Add an image to the given entity.
   *
   * @param resourceType Type of resource the image is bound to. Currently only products is really
   *     well-tested.
   * @param boundEntity Entity the image is bound to.
   * @param imageData Stream allowing to get image data.
   * @return Information about the added image (eg. its Id)
   * @throws PrestaShopWebserviceException
   */
  public PrestashopImage addImage(
      final PrestashopResourceType resourceType,
      final PrestashopIdentifiableEntity boundEntity,
      final InputStream imageData)
      throws PrestaShopWebserviceException {
    // Using the imputstream directly fails ($_FILES is empty on remote end…)
    byte[] imageBytes = null;
    try {
      imageBytes = IOUtils.toByteArray(imageData);
    } catch (IOException e) {
      throw new PrestaShopWebserviceException("An error occured while reading source image");
    }
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
    builder.addPart(
        "image", new ByteArrayBody(imageBytes, ContentType.DEFAULT_BINARY, "image.jpg"));

    HttpPost request =
        new HttpPost(
            String.format(
                "%s/api/images/%s/%d", this.url, resourceType.getLabel(), boundEntity.getId()));
    request.setEntity(builder.build());

    RequestResult result = null;

    try {
      result = executeRequest(request);
      return (PrestashopImage)
          ((Prestashop) jaxbContext.createUnmarshaller().unmarshal(result.content)).getContent();
    } catch (Exception e) {
      throw new PrestaShopWebserviceException(
          "An error occured while processing image add response", e);
    } finally {
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  public byte[] fetchImage(
      final PrestashopResourceType resourceType,
      final PrestashopIdentifiableEntity boundEntity,
      final int imageId)
      throws PrestaShopWebserviceException {
    HttpGet request =
        new HttpGet(
            String.format(
                "%s/api/images/%s/%d/%d",
                this.url, resourceType.getLabel(), boundEntity.getId(), imageId));

    RequestResult result = null;
    try {
      result = executeRequest(request);
      return IOUtils.toByteArray(result.content);
    } catch (Exception e) {
      throw new PrestaShopWebserviceException("An error occured while fetching image", e);
    } finally {
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  /**
   * Since Prestashop is unable to assign a correct invoice number, let's "compute" it… This is not
   * concurrency safe, but a quick glance at prestashop's code tells me than it isn't on prestashop
   * side anyway…
   *
   * @return The next number to be assigned
   * @throws PrestaShopWebserviceException
   */
  public int getNextInvoiceNumber() throws PrestaShopWebserviceException {
    Options options = new Options();
    options.resourceType = PrestashopResourceType.ORDER_INVOICES;
    options.display = Collections.singletonList("full");
    options.sort = Collections.singletonList("number_DESC");
    options.limit = 1;

    HttpGet httpget = new HttpGet(buildUri(options));
    RequestResult result = null;

    try {
      result = executeRequest(httpget);
      @SuppressWarnings("unchecked")
      List<PrestashopOrderInvoice> invoices =
          ((ListContainer<PrestashopOrderInvoice>)
                  ((Prestashop) jaxbContext.createUnmarshaller().unmarshal(result.content))
                      .getContent())
              .getEntities();
      if (invoices.size() == 0) return 1;
      return invoices.get(0).getNumber() + 1;
    } catch (JAXBException e) {
      throw new PrestaShopWebserviceException("Error while unmarshalling response from fetch", e);
    } finally {
      log.trace("Closing connection");
      if (result != null) IOUtils.closeQuietly(result.response);
    }
  }

  private String buildUri(Options options) throws PrestaShopWebserviceException {
    final String url;
    if (StringUtils.isEmpty(options.fullUrl)) {
      if (options.entityId == null) {
        url = String.format("%s/api/%s", this.url, options.resourceType.getLabel());
      } else {
        url =
            String.format(
                "%s/api/%s/%d", this.url, options.resourceType.getLabel(), options.entityId);
      }
    } else {
      url = options.fullUrl;
    }
    URIBuilder uriBuilder;
    try {
      uriBuilder = new URIBuilder(url);
    } catch (URISyntaxException e) {
      throw new PrestaShopWebserviceException(
          String.format("Invalid URI %s provided to webservices", url), e);
    }
    if (StringUtils.isNotEmpty(options.schemaType)) {
      uriBuilder.addParameter("schema", options.schemaType);
    }
    if (MapUtils.isNotEmpty(options.filter)) {
      for (Map.Entry<String, String> e : options.filter.entrySet()) {
        uriBuilder.addParameter(String.format("filter[%s]", e.getKey()), e.getValue());
      }
    }
    if (CollectionUtils.isNotEmpty(options.display)) {
      // you've to use display=full or display=[fields,…], display=[full] or display=field wont work
      if (options.display.size() == 1 && options.display.get(0).equals("full"))
        uriBuilder.addParameter("display", "full");
      else uriBuilder.addParameter("display", "[" + StringUtils.join(options.display, ",") + "]");
    }
    if (CollectionUtils.isNotEmpty(options.sort)) {
      uriBuilder.addParameter("sort", "[" + StringUtils.join(options.sort, ",") + "]");
    }
    if (options.limit != null) {
      uriBuilder.addParameter(
          "limit",
          (options.offset != null ? options.offset.toString() + "," : "")
              + options.limit.toString());
    }
    if (options.shopId != null) uriBuilder.addParameter("id_shop", options.shopId.toString());
    if (options.shopGroupId != null)
      uriBuilder.addParameter("id_group_shop", options.shopGroupId.toString());

    return uriBuilder.toString();
  }

  /**
   * Compare this webservice version to the given one (MAJ.MIN.PATCH.REV), assume that all the
   * version parts are numeric.
   *
   * @param version Version to compare to.
   * @return <code>null</code> if no request has been made yet by this
   */
  public Integer compareVersion(final String version) {
    if (wsVersion == null) return null;
    int[] wsVersionParts =
        Arrays.stream(wsVersion.split("\\.")).mapToInt(Integer::valueOf).toArray();
    int[] versionParts = Arrays.stream(version.split("\\.")).mapToInt(Integer::valueOf).toArray();
    int count = Math.max(wsVersionParts.length, versionParts.length);
    for (int i = 0; i < count; ++i) {
      if (wsVersionParts[i] > versionParts[i]) return 1;
      if (wsVersionParts[i] < versionParts[i]) return -1;
    }
    if (wsVersionParts.length == versionParts.length) return 0;
    return wsVersionParts.length > versionParts.length ? 1 : -1;
  }

  public static class Options {
    private PrestashopResourceType resourceType;
    private Integer entityId;
    private String schemaType;
    private String fullUrl;
    private Integer shopId;
    private Integer shopGroupId;
    private Integer limit;
    private Integer offset;
    private List<String> display;
    private Map<String, String> filter;
    private List<String> sort;

    public void setResourceType(PrestashopResourceType resourceType) {
      this.resourceType = resourceType;
    }

    public void setRequestedId(Integer requestedId) {
      this.entityId = requestedId;
    }

    public void setSchemaType(String schemaType) {
      this.schemaType = schemaType;
    }

    public void setFullUrl(String fullUrl) {
      this.fullUrl = fullUrl;
    }

    public void setShopId(int shopId) {
      this.shopId = shopId;
    }

    public void setShopGroupId(int shopGroupId) {
      this.shopGroupId = shopGroupId;
    }

    public void setLimit(int limit) {
      this.limit = limit;
    }

    public void setOffset(int offset) {
      this.offset = offset;
    }

    public void setDisplay(List<String> display) {
      this.display = display;
    }

    public void setFilter(Map<String, String> filter) {
      this.filter = filter;
    }

    public void setSort(List<String> sort) {
      this.sort = sort;
    }

    public void clear() {
      resourceType = null;
      entityId = null;
      fullUrl = null;
      shopId = null;
      shopGroupId = null;
      limit = null;
      offset = null;
      display = null;
      filter = null;
      sort = null;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("resource", resourceType)
          .append("requestedId", entityId)
          .append("fullUrl", fullUrl)
          .append("shopId", shopId)
          .append("shopGroupId", shopGroupId)
          .append("limit", limit)
          .append("offset", offset)
          .append("display", display)
          .append("filter", filter)
          .append("sort", sort)
          .toString();
    }
  }

  private static class RequestResult {
    CloseableHttpResponse response;
    InputStream content;
  }
}
