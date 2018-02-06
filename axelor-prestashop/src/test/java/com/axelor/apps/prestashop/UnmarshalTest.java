package com.axelor.apps.prestashop;

import java.math.BigDecimal;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.junit.Assert;
import org.junit.Test;

import com.axelor.apps.prestashop.entities.ListContainer.CountriesContainer;
import com.axelor.apps.prestashop.entities.ListContainer.CurrenciesContainer;
import com.axelor.apps.prestashop.entities.Prestashop;
import com.axelor.apps.prestashop.entities.PrestashopCountry;
import com.axelor.apps.prestashop.entities.PrestashopCurrency;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.entities.xlink.ApiContainer;
import com.axelor.apps.prestashop.entities.xlink.XlinkEntry;
import com.google.common.collect.Sets;

public class UnmarshalTest {

	@Test
	public void testApi() throws JAXBException {
		Prestashop envelop = (Prestashop)JAXBContext.newInstance("com.axelor.apps.prestashop.entities:com.axelor.apps.prestashop.entities.xlink")
				.createUnmarshaller()
				.unmarshal(getClass().getResourceAsStream("api.xml"));

		final Set<PrestashopResourceType> expectedEntries = Sets.newHashSet(
				PrestashopResourceType.ADDRESSES,
				PrestashopResourceType.CARTS,
				PrestashopResourceType.CATEGORIES,
				PrestashopResourceType.COUNTRIES,
				PrestashopResourceType.CUSTOMERS,
				PrestashopResourceType.IMAGES,
				PrestashopResourceType.LANGUAGES,
				PrestashopResourceType.ORDER_DETAILS,
				PrestashopResourceType.ORDER_HISTORIES,
				PrestashopResourceType.ORDERS,
				PrestashopResourceType.PRODUCTS
		);

		Assert.assertNotNull(envelop.getContent());
		Assert.assertEquals(ApiContainer.class, envelop.getContent().getClass());
		ApiContainer content = envelop.getContent();
		Assert.assertEquals(expectedEntries.size(), content.getXlinkEntries().size());
		for(XlinkEntry entry : content.getXlinkEntries()) {
			Assert.assertTrue(expectedEntries.remove(entry.getEntryType()));
		}
		Assert.assertEquals(0, expectedEntries.size());
	}

	@Test
	public void testCurrency() throws JAXBException {
		Prestashop envelop = (Prestashop) JAXBContext.newInstance("com.axelor.apps.prestashop.entities")
				.createUnmarshaller()
				.unmarshal(getClass().getResourceAsStream("currency.xml"));

		Assert.assertNotNull(envelop.getContent());
		Assert.assertEquals(PrestashopCurrency.class, envelop.getContent().getClass());
		PrestashopCurrency currency = envelop.getContent();
		Assert.assertEquals(Integer.valueOf(1), currency.getId());
		Assert.assertEquals("euro", currency.getName());
		Assert.assertEquals("EUR", currency.getCode());
		Assert.assertEquals(new BigDecimal("1.000000"), currency.getConversionRate());
		Assert.assertEquals(false, currency.isDeleted());
		Assert.assertEquals(true, currency.isActive());
	}

	@Test
	public void testCurrencies() throws JAXBException {
		Prestashop envelop = (Prestashop) JAXBContext.newInstance("com.axelor.apps.prestashop.entities")
				.createUnmarshaller()
				.unmarshal(getClass().getResourceAsStream("currencies.xml"));

		Assert.assertNotNull(envelop.getContent());
		Assert.assertEquals(CurrenciesContainer.class, envelop.getContent().getClass());
		CurrenciesContainer currencies = envelop.getContent();
		Assert.assertEquals(166, currencies.getEntities().size());
	}

	@Test
	public void testCountry() throws JAXBException {
		Prestashop envelop = (Prestashop) JAXBContext.newInstance("com.axelor.apps.prestashop.entities")
				.createUnmarshaller()
				.unmarshal(getClass().getResourceAsStream("country.xml"));


		Assert.assertNotNull(envelop.getContent());
		Assert.assertEquals(PrestashopCountry.class, envelop.getContent().getClass());
		PrestashopCountry country = envelop.getContent();
		Assert.assertNotNull(country.getName());
		Assert.assertEquals(1, country.getName().getTranslations().size());
		Assert.assertEquals("ALLEMAGNE", country.getName().getTranslations().get(0).getTranslation());
		Assert.assertEquals(Integer.valueOf(1), country.getId());
		Assert.assertEquals(1, country.getZoneId());
		Assert.assertEquals(Integer.valueOf(0), country.getCurrencyId());
	}

	@Test
	public void testCountries() throws JAXBException {
		Prestashop envelop = (Prestashop) JAXBContext.newInstance("com.axelor.apps.prestashop.entities")
				.createUnmarshaller()
				.unmarshal(getClass().getResourceAsStream("countries.xml"));

		Assert.assertNotNull(envelop.getContent());
		Assert.assertEquals(CountriesContainer.class, envelop.getContent().getClass());
		CountriesContainer countries = envelop.getContent();
		Assert.assertEquals(251	, countries.getEntities().size());
	}

}