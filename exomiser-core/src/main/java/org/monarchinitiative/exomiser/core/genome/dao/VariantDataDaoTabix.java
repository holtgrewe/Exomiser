package org.monarchinitiative.exomiser.core.genome.dao;

import htsjdk.tribble.readers.TabixReader;
import org.monarchinitiative.exomiser.core.model.Variant;
import org.monarchinitiative.exomiser.core.model.VariantData;
import org.monarchinitiative.exomiser.core.model.frequency.Frequency;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencyData;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencySource;
import org.monarchinitiative.exomiser.core.model.frequency.RsId;
import org.monarchinitiative.exomiser.core.model.pathogenicity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
//@Component
public class VariantDataDaoTabix implements VariantDataDao {

    private static final Logger logger = LoggerFactory.getLogger(VariantDataDaoTabix.class);

    private static final String EMPTY_FIELD = ".";
    private static final Map<String, FrequencySource> FREQUENCY_SOURCE_MAP;

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

    private TabixReader tabixReader = null;

    public VariantDataDaoTabix() {
        try {
            tabixReader = new TabixReader("C:/Users/hhx640/Documents/exomiser-8.0.0/data/exomiser-all.vcf.gz");
            logger.info("Reading variant data from tabix {}", tabixReader.getSource());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Cacheable("variant_data")
    @Override
    public VariantData getVariantData(Variant variant) {
        String chromosome = variant.getChromosomeName();
        int start = variant.getPosition();
        int end = variant.getPosition();
        return getVariantData(chromosome, start, end, variant.getRef(), variant.getAlt());
    }

    private VariantData getVariantData(String chromosome, int start, int end, String ref, String alt) {

        List<String> results = getTabixLines(chromosome, start, end);
        for (String line : results) {
            String[] elements = line.split("\t");
            if (ref.equals(elements[3]) && alt.equals(elements[4])) {
                if (EMPTY_FIELD.equals(elements[2]) && EMPTY_FIELD.equals(elements[7])) {
                    return VariantData.empty();
                }
                Map<String, Float> values = mapInfoFields(elements[7]);
                RsId rsId = RsId.valueOf(elements[2]);
                FrequencyData frequencyData = parseFrequencyData(rsId, values);
                PathogenicityData pathogenicityData = parsePathogenicityData(values);
                return new VariantData(frequencyData, pathogenicityData);
            }
        }
        return VariantData.empty();
    }

    private Map<String, Float> mapInfoFields(String info) {
        String[] infoFields = info.split(";");
        Map<String, Float> values = new HashMap<>();
        for (String infoField : infoFields) {
            String[] keyValue = infoField.split("=");
            if (keyValue.length == 2) {
                values.put(keyValue[0], Float.valueOf(keyValue[1]));
            }
        }
        return values;
    }

    private FrequencyData parseFrequencyData(RsId rsId, Map<String, Float> values) {
        List<Frequency> frequencies = new ArrayList<>();
        for (Map.Entry<String, Float> field : values.entrySet()) {
            String key = field.getKey();
            Float value = field.getValue();
            if (FREQUENCY_SOURCE_MAP.containsKey(key)) {
                FrequencySource source = FREQUENCY_SOURCE_MAP.get(key);
                frequencies.add(Frequency.valueOf(value, source));
            }
        }
        return FrequencyData.of(rsId, frequencies);
    }

    private PathogenicityData parsePathogenicityData(Map<String, Float> values) {
        List<PathogenicityScore> pathogenicityScores = new ArrayList<>();
        for (Map.Entry<String, Float> field : values.entrySet()) {
            String key = field.getKey();
            Float value = field.getValue();
            if (key.startsWith("SIFT")) {
                pathogenicityScores.add(SiftScore.valueOf(value));
            }
            if (key.startsWith("POLYPHEN")) {
                pathogenicityScores.add(PolyPhenScore.valueOf(value));
            }
            if (key.startsWith("MUT_TASTER")) {
                pathogenicityScores.add(MutationTasterScore.valueOf(value));
            }
        }
        return PathogenicityData.of(pathogenicityScores);
    }

    private List<String> getTabixLines(String chromosome, int start, int end) {
        List<String> lines = new ArrayList<>();
        try {
            String line;
            TabixReader.Iterator results = tabixReader.query(chromosome + ":" + start + "-" + end);
            while ((line = results.next()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            logger.error("Unable to read from exomiser tabix file {}", tabixReader.getSource(), e);
        }
        return lines;
    }
}