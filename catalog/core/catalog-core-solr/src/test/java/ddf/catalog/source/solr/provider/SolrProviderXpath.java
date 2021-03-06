/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.source.solr.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.operation.SourceResponse;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.UnsupportedQueryException;
import java.util.Arrays;
import java.util.List;
import org.codice.solr.factory.impl.ConfigurationStore;
import org.joda.time.DateTime;
import org.junit.Test;
import org.opengis.filter.Filter;

public class SolrProviderXpath extends SolrProviderTestBase {

  @Test
  public void testXpathCompoundContextualQuery() throws Exception {

    ConfigurationStore.getInstance().setDisableTextPath(false);
    deleteAll();

    String nonexistentXpath = "/this/xpath[does/not/@ex:exist]";

    MockMetacard tampa = new MockMetacard(Library.getTampaRecord());
    tampa.setTitle("Tampa");
    tampa.setLocation(Library.TAMPA_AIRPORT_POINT_WKT);
    MockMetacard flagstaff = new MockMetacard(Library.getFlagstaffRecord());
    flagstaff.setLocation(Library.FLAGSTAFF_AIRPORT_POINT_WKT);

    create(Arrays.asList(tampa, flagstaff));

    /* XPath AND temporal AND spatial. */

    Filter filter =
        filterBuilder.allOf(
            filterBuilder.xpath("/rss/channel[item/link]").exists(),
            filterBuilder
                .attribute(Metacard.MODIFIED)
                .before()
                .date(new DateTime().plus(1).toDate()),
            filterBuilder
                .attribute(Metacard.GEOGRAPHY)
                .intersecting()
                .wkt(Library.FLAGSTAFF_AIRPORT_POINT_WKT));

    SourceResponse sourceResponse = provider.query(new QueryRequestImpl(new QueryImpl(filter)));

    assertEquals("XPath AND temporal AND spatial", ONE_HIT, sourceResponse.getResults().size());

    /* temporal AND (Bad XPath OR XPath) */

    filter =
        filterBuilder.allOf(
            filterBuilder
                .attribute(Metacard.MODIFIED)
                .before()
                .date(new DateTime().plus(1).toDate()),
            filterBuilder.anyOf(
                filterBuilder.xpath(nonexistentXpath).exists(),
                filterBuilder
                    .xpath("//channel/image/title")
                    .is()
                    .like()
                    .text(Library.FLAGSTAFF_QUERY_PHRASE)));

    sourceResponse = provider.query(new QueryRequestImpl(new QueryImpl(filter)));

    assertEquals("temporal AND (Bad XPath OR XPath)", ONE_HIT, sourceResponse.getResults().size());

    /* Bad XPath OR (spatial AND XPath) */

    filter =
        filterBuilder.anyOf(
            filterBuilder.xpath(nonexistentXpath).is().like().text("any phrase"),
            filterBuilder.allOf(
                filterBuilder
                    .attribute(Metacard.GEOGRAPHY)
                    .intersecting()
                    .wkt(Library.TAMPA_AIRPORT_POINT_WKT),
                filterBuilder.xpath("/rss//item/enclosure/@url").exists()));

    sourceResponse = provider.query(new QueryRequestImpl(new QueryImpl(filter)));

    assertEquals("Bad XPath OR (spatial AND XPath)", ONE_HIT, sourceResponse.getResults().size());
    assertEquals("Tampa", sourceResponse.getResults().get(0).getMetacard().getTitle());

    /* spatial AND (Bad XPath OR Bad XPath) */

    filter =
        filterBuilder.allOf(
            filterBuilder
                .attribute(Metacard.GEOGRAPHY)
                .intersecting()
                .wkt(Library.FLAGSTAFF_AIRPORT_POINT_WKT),
            filterBuilder.anyOf(
                filterBuilder.xpath(nonexistentXpath).exists(),
                filterBuilder
                    .xpath("//also/does/not[@exist]")
                    .is()
                    .like()
                    .text(Library.FLAGSTAFF_QUERY_PHRASE)));

    sourceResponse = provider.query(new QueryRequestImpl(new QueryImpl(filter)));

    assertEquals("spatial AND (Bad XPath OR Bad XPath)", 0, sourceResponse.getResults().size());
  }

  @Test
  public void testXpathNestedXpathQuery() throws Exception {

    ConfigurationStore.getInstance().setDisableTextPath(false);
    deleteAll();

    String explicitXpath1 = "//rss/channel/itunes:explicit";
    String explicitXpath2 = "//rss/channel/ITunes:explicit";
    String updateXpath1 = "//rss/channel/sy:updatePeriod";
    String updateXpath2 = "//rss/channel/SY:updatePeriod";
    String updateFreq1 = "//rss/channel/sy:updateFrequency";
    String updateFreq2 = "//rss/channel/SY:updateFrequency";
    String existsXpath = "//rss/channel/language";

    MockMetacard tampa = new MockMetacard(Library.getTampaRecord());
    tampa.setTitle("Tampa");
    tampa.setLocation(Library.TAMPA_AIRPORT_POINT_WKT);
    MockMetacard flagstaff = new MockMetacard(Library.getFlagstaffRecord());
    flagstaff.setLocation(Library.FLAGSTAFF_AIRPORT_POINT_WKT);

    create(Arrays.asList(tampa, flagstaff));

    /* XPath */

    Filter existsFilter = filterBuilder.xpath(existsXpath).exists();
    Filter notFilter =
        filterBuilder.not(filterBuilder.xpath(existsXpath).is().like().text("en-us"));
    Filter firstPart = filterBuilder.allOf(existsFilter, notFilter);

    Filter anyGroupFilter =
        filterBuilder.anyOf(
            filterBuilder.xpath(explicitXpath1).is().like().text("no"),
            filterBuilder.xpath(explicitXpath2).is().like().text("no"));

    Filter anyABfilter =
        filterBuilder.anyOf(
            filterBuilder.xpath(updateXpath1).is().like().text("hourly"),
            filterBuilder.xpath(updateFreq1).is().like().text("1"));
    Filter anyACfilter =
        filterBuilder.anyOf(
            filterBuilder.xpath(updateXpath2).is().like().text("hourly"),
            filterBuilder.xpath(updateFreq2).is().like().text("1"));

    Filter allABACFilter = filterBuilder.allOf(anyABfilter, anyACfilter);

    Filter secondPart = filterBuilder.anyOf(anyGroupFilter, allABACFilter);

    Filter totalFilter = filterBuilder.allOf(firstPart, secondPart);

    SourceResponse sourceResponse =
        provider.query(new QueryRequestImpl(new QueryImpl(totalFilter)));

    assertEquals("Nested XPath - Find 1 result", ONE_HIT, sourceResponse.getResults().size());
  }

  @Test
  public void testXpathNestedNegativeXpathQuery() throws Exception {

    ConfigurationStore.getInstance().setDisableTextPath(false);
    deleteAll();

    String explicitXpath1 = "//rss/channel/itunes:explicit";
    String explicitXpath2 = "//rss/channel/ITunes:explicit";
    String updateXpath1 = "//rss/channel/sy:updatePeriod";
    String updateXpath2 = "//rss/channel/SY:updatePeriod";
    String updateFreq1 = "//rss/channel/sy:updateFrequency";
    String updateFreq2 = "//rss/channel/SY:updateFrequency";
    String existsXpath = "//rss/channel/language";

    MockMetacard tampa = new MockMetacard(Library.getTampaRecord());
    tampa.setTitle("Tampa");
    tampa.setLocation(Library.TAMPA_AIRPORT_POINT_WKT);
    MockMetacard flagstaff = new MockMetacard(Library.getFlagstaffRecord());
    flagstaff.setLocation(Library.FLAGSTAFF_AIRPORT_POINT_WKT);

    create(Arrays.asList(tampa, flagstaff));

    /* XPath */

    Filter existsFilter = filterBuilder.xpath(existsXpath).exists();
    Filter notFilter =
        filterBuilder.not(filterBuilder.xpath(existsXpath).is().like().text("en-us"));
    Filter firstPart = filterBuilder.allOf(existsFilter, notFilter);

    Filter anyGroupFilter =
        filterBuilder.anyOf(
            filterBuilder.xpath(explicitXpath1).is().like().text("yes"),
            filterBuilder.xpath(explicitXpath2).is().like().text("yes"));

    Filter anyABfilter =
        filterBuilder.anyOf(
            filterBuilder.xpath(updateXpath1).is().like().text("daily"),
            filterBuilder.xpath(updateFreq1).is().like().text("2"));
    Filter anyACfilter =
        filterBuilder.anyOf(
            filterBuilder.xpath(updateXpath2).is().like().text("daily"),
            filterBuilder.xpath(updateFreq2).is().like().text("2"));

    Filter allABACFilter = filterBuilder.allOf(anyABfilter, anyACfilter);

    Filter secondPart = filterBuilder.anyOf(anyGroupFilter, allABACFilter);

    Filter totalFilter = filterBuilder.allOf(firstPart, secondPart);

    SourceResponse sourceResponse =
        provider.query(new QueryRequestImpl(new QueryImpl(totalFilter)));

    assertEquals("Nested XPath - Find 0 results", 0, sourceResponse.getResults().size());
  }

  @Test
  public void testXpathQuery() throws Exception {

    prepareXPath(false);

    // POSITIVE
    queryXpathPositiveWithSearchPhrase(
        "/rss/channel/itunes:image/@href",
        "http://www.flagstaffchamber.com/wp-content/plugins/powerpress/itunes_default.jpg",
        Library.FLAGSTAFF_QUERY_PHRASE);
    queryXpathPositiveExists("/rss//item", Library.FLAGSTAFF_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder/comment", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder//comment", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder/items//comment", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder[items//comment]", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists(
        "/purchaseOrder/items/item/comment", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder//item/USPrice", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("purchaseOrder/items/item", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder/items/item", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("./purchaseOrder/items/item", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("//shipTo[@country]", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("//shipTo[@country='US']", Library.PURCHASE_ORDER_QUERY_PHRASE);

    queryXpathPositiveExists("/*/items", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/*/*/item[./comment]", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder/*", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("/purchaseOrder/*/item", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveExists("//*[@country='US']", Library.PURCHASE_ORDER_QUERY_PHRASE);

    queryXpathPositiveWithSearchPhrase(
        "//shipTo/@country", "US", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveWithSearchPhrase(
        "//shipTo/@country", "us", Library.PURCHASE_ORDER_QUERY_PHRASE, false);
    queryXpathPositiveWithSearchPhrase(
        "/purchaseOrder/comment",
        "Hurry, my lawn is going wild!",
        Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveWithSearchPhrase(
        "/purchaseOrder/items//comment", "Confirm this is electric", "Lawnmower");
    queryXpathPositiveWithSearchPhrase(
        "//comment", "Hurry, my lawn is going wild!", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveWithSearchPhrase(
        "//comment", "Confirm this is electric", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveWithSearchPhrase(
        "//items//comment", "Confirm this is electric", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveWithSearchPhrase(
        "/purchaseOrder//item/USPrice", "148.95", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveWithSearchPhrase(
        "/purchaseOrder//item/USPrice", "39.98", Library.PURCHASE_ORDER_QUERY_PHRASE);
    queryXpathPositiveWithSearchPhrase(
        "/purchaseOrder//item[2]/USPrice", "39.98", Library.PURCHASE_ORDER_QUERY_PHRASE);

    // NEGATIVE
    queryXpathNegativeExists("/*/invalid");
    queryXpathNegativeExists("//electric");
    queryXpathNegativeExists("//partNum");
    queryXpathNegativeExists("//shipTo[@country2]");

    queryXpathNegativeWithSearchPhrase("//shipTo/@country", "us");
    queryXpathNegativeWithSearchPhrase("/purchaseOrder/comment", "invalid");
    queryXpathNegativeWithSearchPhrase("/purchaseOrder/billTo", "12345");
    queryXpathNegativeWithSearchPhrase("//comment", "invalid");
    queryXpathNegativeWithSearchPhrase("/purchaseOrder//item/USPrice", "invalid");
  }

  private void queryXpathPositiveExists(String xpath, String recordMatchPhrase) throws Exception {
    SourceResponse sourceResponse = queryXpathExists(xpath);
    assertEquals(
        "Failed to find record for xpath: " + xpath, 1, sourceResponse.getResults().size());

    for (Result r : sourceResponse.getResults()) {
      assertTrue(
          "Wrong record, keyword was not found.",
          r.getMetacard().getMetadata().contains(recordMatchPhrase));
    }
  }

  private void queryXpathNegativeExists(String xpath) throws Exception {
    SourceResponse sourceResponse = queryXpathExists(xpath);
    assertEquals(
        "Should not have found record for xpath: " + xpath, 0, sourceResponse.getResults().size());
  }

  private SourceResponse queryXpathExists(String xpath) throws UnsupportedQueryException {
    Filter filter = filterBuilder.xpath(xpath).exists();
    return provider.query(new QueryRequestImpl(new QueryImpl(filter)));
  }

  private void queryXpathPositiveWithSearchPhrase(
      String xpath, String searchPhrase, String recordMatchPhrase) throws Exception {
    queryXpathPositiveWithSearchPhrase(xpath, searchPhrase, recordMatchPhrase, true);
  }

  private void queryXpathPositiveWithSearchPhrase(
      String xpath, String searchPhrase, String recordMatchPhrase, boolean isCaseSensitive)
      throws Exception {
    SourceResponse sourceResponse = queryXpathWithPhrase(xpath, searchPhrase, isCaseSensitive);
    assertEquals(
        "Failed to find record for xpath: " + xpath, 1, sourceResponse.getResults().size());

    for (Result r : sourceResponse.getResults()) {
      assertTrue(
          "Wrong record, keyword was not found.",
          r.getMetacard().getMetadata().contains(recordMatchPhrase));
    }
  }

  private void queryXpathNegativeWithSearchPhrase(String xpath, String searchPhrase)
      throws Exception {
    SourceResponse sourceResponse = queryXpathWithPhrase(xpath, searchPhrase, true);
    assertEquals(
        "Should not have found record for xpath: " + xpath, 0, sourceResponse.getResults().size());
  }

  private SourceResponse queryXpathWithPhrase(
      String xpath, String searchPhrase, boolean isCaseSensitive) throws UnsupportedQueryException {
    Filter filter;
    if (isCaseSensitive) {
      filter = filterBuilder.xpath(xpath).is().like().caseSensitiveText(searchPhrase);
    } else {
      filter = filterBuilder.xpath(xpath).is().like().text(searchPhrase);
    }
    return provider.query(new QueryRequestImpl(new QueryImpl(filter)));
  }

  /** Checks if it flag is false, text path indexing works */
  @Test
  public void testDisableTextPathFalse() throws Exception {
    prepareXPath(false);

    queryXpathPositiveExists("//comment", "Hurry, my lawn is going wild!");
    queryXpathNegativeExists("//foo");
  }

  /** If we disable text path support, we expect 0 results. */
  @Test
  public void testDisableTextPathTrueExistsFilter() throws Exception {
    prepareXPath(true);

    queryXpathNegativeExists("//comment");
  }

  /** If we disable text path support, we expect 0 results. */
  @Test
  public void testDisableTextPathTrueLikeFilter() throws Exception {
    prepareXPath(true);

    queryXpathNegativeWithSearchPhrase("//comment", "Hurry, my lawn is going wild!");
  }

  /** If we disable text path support, we expect 0 results. */
  @Test
  public void testDisableTextPathTrueFuzzy() throws Exception {
    prepareXPath(true);

    assertNotFilter(
        filterBuilder.xpath("//comment").is().like().fuzzyText("Hurry, my lawn is going wild!"));
  }

  private void assertNotFilter(Filter filter) throws UnsupportedQueryException {
    SourceResponse sourceResponse = provider.query(new QueryRequestImpl(new QueryImpl(filter)));
    assertEquals("Found a metacard and should not have.", 0, sourceResponse.getResults().size());
  }

  private void prepareXPath(boolean isXpathDisabled)
      throws IngestException, UnsupportedQueryException {
    ConfigurationStore.getInstance().setDisableTextPath(isXpathDisabled);

    deleteAll();

    MetacardImpl flagstaffMetacard = new MockMetacard(Library.getFlagstaffRecord());
    MetacardImpl poMetacard = new MockMetacard(Library.getPurchaseOrderRecord());
    List<Metacard> list = Arrays.asList(flagstaffMetacard, poMetacard);

    // CREATE
    create(list);
  }
}
