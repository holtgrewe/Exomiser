package org.monarchinitiative.exomiser.core.dao;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.monarchinitiative.exomiser.core.model.Variant;
import org.monarchinitiative.exomiser.core.model.VariantData;
import org.monarchinitiative.exomiser.core.model.frequency.Frequency;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencyData;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencySource;
import org.monarchinitiative.exomiser.core.model.frequency.RsId;
import org.monarchinitiative.exomiser.core.model.pathogenicity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
@Component
public class VariantDataDaoLucene implements VariantDataDao {

    private static final Logger logger = LoggerFactory.getLogger(VariantDataDaoLucene.class);

    private static final Map<String, FrequencySource> FREQUENCY_SOURCE_MAP;
    private static final ScoreDoc[] EMPTY_DOCS = {};
    private static final Document EMPTY_DOCUMENT = new Document();

    static {
        FREQUENCY_SOURCE_MAP = new HashMap<>();
        FREQUENCY_SOURCE_MAP.put("KG", FrequencySource.THOUSAND_GENOMES);
        FREQUENCY_SOURCE_MAP.put("ESP_AA", FrequencySource.ESP_AFRICAN_AMERICAN);
        FREQUENCY_SOURCE_MAP.put("ESP_EA", FrequencySource.ESP_EUROPEAN_AMERICAN);
        FREQUENCY_SOURCE_MAP.put("ESP_ALL", FrequencySource.ESP_ALL);
        FREQUENCY_SOURCE_MAP.put("EXAC_AFR", FrequencySource.EXAC_AFRICAN_INC_AFRICAN_AMERICAN);
        FREQUENCY_SOURCE_MAP.put("EXAC_AMR", FrequencySource.EXAC_AMERICAN);
        FREQUENCY_SOURCE_MAP.put("EXAC_EAS", FrequencySource.EXAC_EAST_ASIAN);
        FREQUENCY_SOURCE_MAP.put("EXAC_FIN", FrequencySource.EXAC_FINNISH);
        FREQUENCY_SOURCE_MAP.put("EXAC_NFE", FrequencySource.EXAC_NON_FINNISH_EUROPEAN);
        FREQUENCY_SOURCE_MAP.put("EXAC_SAS", FrequencySource.EXAC_SOUTH_ASIAN);
        FREQUENCY_SOURCE_MAP.put("EXAC_OTH", FrequencySource.EXAC_OTHER);
    }

    //TODO make final, inject in constructor
    private IndexSearcher searcher;

    public VariantDataDaoLucene() {
        try {
            Directory index = FSDirectory.open(Paths.get("C:/Users/hhx640/Documents/exomiser-8.0.0/data/lucene_alleles"));
            IndexReader reader = DirectoryReader.open(index);
            searcher = new IndexSearcher(reader);
        } catch (IOException e) {
            logger.error("Unable to access Lucene index {}", e);
        }
    }

    @Override
    public VariantData getVariantData(Variant variant) {
        Query query = buildQuery(variant);
        ScoreDoc[] hits = runQuery(query);
        if (hits.length == 0) {
            return VariantData.EMPTY;
        }
        //we're only expecting one back, if any
        int docId = hits[0].doc;
        Document document = getDocument(docId);
        return buildVariantData(document);
    }

    private Query buildQuery(Variant variant) {
        Query chrQuery = IntPoint.newExactQuery("chr", variant.getChromosome());
        Query posQuery = IntPoint.newExactQuery("pos", variant.getPosition());
        TermQuery refQuery = new TermQuery(new Term("ref", variant.getRef()));
        TermQuery altQuery = new TermQuery(new Term("alt", variant.getAlt()));

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(chrQuery, BooleanClause.Occur.MUST);
        builder.add(posQuery, BooleanClause.Occur.MUST);
        builder.add(refQuery, BooleanClause.Occur.MUST);
        builder.add(altQuery, BooleanClause.Occur.MUST);
        return builder.build();
    }

    private ScoreDoc[] runQuery(Query query) {
        try {
            TopDocs docs = searcher.search(query, 1);
            return docs.scoreDocs;
        } catch (IOException e) {
            logger.error("{}", e);
        }
        return EMPTY_DOCS;
    }

    private Document getDocument(int docId) {
        try {
            return searcher.doc(docId);
        } catch (IOException e) {
            logger.error("{}", e);
        }
        return EMPTY_DOCUMENT;
    }

    private VariantData buildVariantData(Document document) {
        FrequencyData frequencyData = buildFrequencyData(document);
        PathogenicityData pathogenicityData = buildPathogenicityData(document);
        return new VariantData(frequencyData, pathogenicityData);
    }

    private FrequencyData buildFrequencyData(Document document) {
        RsId rsId = getRsId(document.get("rsId"));
        List<Frequency> frequencies = new ArrayList<>();

        for (IndexableField field : document.getFields()) {
            String name = field.name();
            if ("rsId".equals(name)) {
                continue;
            }
            if (FREQUENCY_SOURCE_MAP.containsKey(name)) {
                FrequencySource source = FREQUENCY_SOURCE_MAP.get(name);
                float value = document.getField(name).numericValue().floatValue();
                frequencies.add(Frequency.valueOf(value, source));
            }
        }

        return new FrequencyData(rsId, frequencies);
    }

    private RsId getRsId(String rsString) {
        if (rsString == null) {
            return RsId.empty();
        }
        return RsId.valueOf(rsString);
    }

    private PathogenicityData buildPathogenicityData(Document document) {
        List<PathogenicityScore> pathogenicityScores = new ArrayList<>();
        for (IndexableField field : document.getFields()) {
            String name = field.name();
            if ("rsId".equals(name)) {
                continue;
            }
            float value = document.getField(name).numericValue().floatValue();
            if ("SIFT".equals(name)) {
                pathogenicityScores.add(SiftScore.valueOf(value));
            }
            if ("POLYPHEN".equals(name)) {
                pathogenicityScores.add(PolyPhenScore.valueOf(value));
            }
            if ("MUT_TASTER".equals(name)) {
                pathogenicityScores.add(MutationTasterScore.valueOf(value));
            }
        }
        return new PathogenicityData(pathogenicityScores);
    }
}
