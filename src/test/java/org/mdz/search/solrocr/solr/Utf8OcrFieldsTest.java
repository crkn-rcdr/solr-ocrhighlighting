package org.mdz.search.solrocr.solr;

import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.handler.component.HighlightComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mdz.search.solrocr.formats.mini.MiniOcrByteOffsetsParser;

public class Utf8OcrFieldsTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeUtf8Class() throws Exception {
    initCore("solrconfig.xml", "schema.xml", "src/test/resources/solr", "miniocr_utf8");

    HighlightComponent hlComp = (HighlightComponent) h.getCore().getSearchComponent("highlight");
    assertTrue("wrong highlighter: " + hlComp.getHighlighter().getClass(),
        hlComp.getHighlighter() instanceof SolrOcrHighlighter);

    Path dataPath = Paths.get("src", "test", "resources", "data").toAbsolutePath();

    assertU(adoc(
        "some_text",
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor "
            + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud "
            + "exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute "
            + "irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla "
            + "pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia "
            + "deserunt mollit anim id est laborum.", "id", "1337"));
    Path ocrPath = dataPath.resolve("31337_utf8ocr.xml");
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MiniOcrByteOffsetsParser.parse(Files.readAllBytes(ocrPath), bos);
    String text = bos.toString(StandardCharsets.UTF_8.toString());
    assertU(adoc("ocr_text", text, "id", "31337"));
    assertU(commit());
  }

  protected static SolrQueryRequest xmlQ(String... extraArgs) throws Exception {
    Map<String, String> args = new HashMap<>(ImmutableMap.<String, String>builder()
        .put("hl", "true")
        .put("hl.fields", "ocr_text")
        .put("hl.usePhraseHighlighter", "true")
        .put("df", "ocr_text")
        .put("hl.ctxTag", "l")
        .put("hl.ctxSize", "2")
        .put("hl.snippets", "10")
        .put("fl", "id")
        .build());
    for (int i = 0; i < extraArgs.length; i += 2) {
      String key = extraArgs[i];
      String val = extraArgs[i + 1];
      args.put(key, val);
    }

    SolrQueryRequest q = req(
        args.entrySet().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).toArray(String[]::new));
    ModifiableSolrParams params = new ModifiableSolrParams(q.getParams());
    params.set("indent", "true");
    q.setParams(params);
    return q;
  }

  @Test
  public void testSingleTerm() throws Exception {
    SolrQueryRequest req = xmlQ("q", "München");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=3",
        "//str[@name='text'][1]/text()='Bayerische Staatsbibliothek <em>München</em> Morgen-Ausgabe. Preſſe.'",
        "//lst[@name='region'][1]/float[@name='x']/text()='0.3714'",
        "//lst[@name='region'][1]/float[@name='y']/text()='0.0071'",
        "//lst[@name='region'][1]/float[@name='w']/text()='0.4384'",
        "//lst[@name='region'][1]/float[@name='h']/text()='0.1033'",
        "count(//arr[@name='highlights'])=3",
        "//arr[@name='highlights'][1]/lst/float[@name='x']/text()='0.3223'",
        "//arr[@name='highlights'][1]/lst/float[@name='y']/text()='0.1481'",
        "//arr[@name='highlights'][1]/lst/float[@name='w']/text()='0.0948'",
        "//arr[@name='highlights'][1]/lst/float[@name='h']/text()='0.059'");
  }

  @Test
  public void testBooleanQuery() throws Exception {
    SolrQueryRequest req = xmlQ("q", "((München AND Italien) OR Landsherr)");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=10");
  }

  @Test
  public void testBooleanQueryNoMatch() throws Exception {
    SolrQueryRequest req = xmlQ("q", "((München AND Rotterdam) OR Mexico)");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=4");
  }

  @Test
  public void testWildcardQuery() throws Exception {
    SolrQueryRequest req = xmlQ("q", "(Mün* OR Magdebur?)");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=10");
  }

  @Test
  public void testWildcardQueryAtTheBeginning() throws Exception {
    SolrQueryRequest req = xmlQ("q", "*deburg");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Magdebur')])=10");
  }

  @Test
  public void testWildcardQueryIntheMiddle() throws Exception {
    SolrQueryRequest req = xmlQ("q", "Mü*hen");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Münche')])=3");
  }

  @Test
  public void testWildcardQueryAtTheEnd() throws Exception {
    SolrQueryRequest req = xmlQ("q", "Münch*");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Münche')])=3");
  }

  @Test
  public void testWildcardQueryWithWildcardOnly() throws Exception {
    SolrQueryRequest req = xmlQ("q", "*");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=10");
  }

  @Test
  public void testWildcardQueryWithAsteriskAndNoResults() throws Exception {
    SolrQueryRequest req = xmlQ("q", "Zzz*");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=0");
  }

  @Test
  public void testWildcardQueryWithNoResults() throws Exception {
    SolrQueryRequest req = xmlQ("q", "Z?z");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=0");
  }

  @Test
  public void testWildcardQueryWithWildcardForUmlautInTheMiddle() throws Exception {
    SolrQueryRequest req = xmlQ("q", "M?nchen");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Münche')])>0");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'manche')])>0");
  }

  @Test
  public void testPhraseQuery() throws Exception {
    SolrQueryRequest req = xmlQ("q", "\"Bayerische Staatsbibliothek\"");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(), '<em>Bayerische</em> <em>Staatsbibliothek</em>')])=1");
  }

  @Test
  public void testPhraseQueryWithNoResults() throws Exception {
    SolrQueryRequest req = xmlQ("q", "\"Münchener Stadtbibliothek\"");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=0");
  }

  @Test
  public void testFuzzyQueryWithSingleTerm() throws Exception {
    SolrQueryRequest req = xmlQ("q", "bayrisch~");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Bayerisch')])=1");
  }

  @Test
  public void testFuzzyQueryWithSingleTermAndGreaterProximity() throws Exception {
    SolrQueryRequest req = xmlQ("q", "baurisch~3");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Bayerisch')])=1");
  }

  @Test
  public void testCombinedFuzzyQuery() throws Exception {
    SolrQueryRequest req = xmlQ("q", "Magdepurg~ OR baurisch~3");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Bayerisch')])=1");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'Magdebur')])>1");
  }

  @Test
  public void testFuzzyQueryWithNoResults() throws Exception {
    SolrQueryRequest req = xmlQ("q", "Makdepurk~ OR baurysk~2");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=0");
  }

  @Test
  public void testProximityQueryWithOneHighlighting() throws Exception {
    SolrQueryRequest req = xmlQ("q", "\"Bayerische München\"~3");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst/str[@name='text' and contains(text(),'<em>Bayerische</em> Staatsbibliothek <em>München</em>')])=1");
  }

  @Test
  public void testProximityQueryWithTwoHighlightings() throws Exception {
    SolrQueryRequest req = xmlQ("q", "\"Bayerische Ausgabe\"~10");
    assertQ(req,
        "count(//lst[@name='highlighting']/lst[@name='31337']/arr[@name='ocr_text']/lst)=2");
  }

  @Test
  public void testWeightMatchesWarning() throws Exception {
    SolrQueryRequest req = xmlQ("q", "\"Bayerische Staatsbibliothek\"", "hl.weightMatches", "true");
    assertQ(req, "count(//lst[@name='warnings']/str[@name='ocr_text'])=1");
  }
}