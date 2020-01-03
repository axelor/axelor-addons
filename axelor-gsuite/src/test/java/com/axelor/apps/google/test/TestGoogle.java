package com.axelor.apps.google.test;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.gdata.util.ServiceException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

public class TestGoogle {

  private static final String[] SCOPES =
      new String[] {"https://www.google.com/m8/feeds/", "https://www.googleapis.com/auth/drive"};

  public void test() throws IOException {

    String clientId = "372386918132-ar50cgd34mqjn21689edlvdjb5tep1t4.apps.googleusercontent.com";

    String clientSecret = "ufYpQUYIvfvxEFWf7eq-oyFV";

    HttpTransport httpTransport = new NetHttpTransport();
    JacksonFactory jsonFactory = new JacksonFactory();

    System.setProperty("http.proxyPort", "3128");
    System.setProperty("http.proxyHost", "192.168.1.1");
    System.setProperty("https.proxyPort", "3128");
    System.setProperty("https.proxyHost", "192.168.1.1");

    String redirectUrl = "/ws/google-sync-code";
    GoogleAuthorizationCodeTokenRequest tokenRequest =
        new GoogleAuthorizationCodeTokenRequest(
                httpTransport, jsonFactory, clientId, clientSecret, "abc", redirectUrl)
            .setGrantType("client_credentials");

    tokenRequest.execute();
  }

  @Test
  public void testContactService() throws IOException, ServiceException {

    HttpTransport httpTransport = new NetHttpTransport();
    JacksonFactory jsonFactory = new JacksonFactory();

    String clientId = "372386918132-ar50cgd34mqjn21689edlvdjb5tep1t4.apps.googleusercontent.com";

    String clientSecret = "ufYpQUYIvfvxEFWf7eq-oyFV";

    GoogleCredential credential =
        new GoogleCredential.Builder().setClientSecrets(clientId, clientSecret).build();
  }

  @Test
  public void testDriveService() throws IOException {

    FileDataStoreFactory dataStore = new FileDataStoreFactory(new File("/home/axelor/attachments"));
    HttpTransport httpTransport = new NetHttpTransport();
    JacksonFactory jsonFactory = new JacksonFactory();
    String clientId = "372386918132-1dlu6noh7sp01mfr632btk658gc6fdu7.apps.googleusercontent.com";

    String clientSecret = "8B8AENyQSlWUS6YNeor7XLfJ";

    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientId, clientSecret, Arrays.asList(SCOPES))
            .setDataStoreFactory(dataStore)
            .build();
  }
}
