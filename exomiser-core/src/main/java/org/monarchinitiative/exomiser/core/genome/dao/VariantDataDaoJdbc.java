package org.monarchinitiative.exomiser.core.genome.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
//@Component
public class VariantDataDaoJdbc implements VariantDataDao {

    private static final Logger logger = LoggerFactory.getLogger(VariantDataDaoJdbc.class);

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

    private final DataSource dataSource;

    public VariantDataDaoJdbc() {
        this.dataSource = new HikariDataSource(h2Config());
    }

    private HikariConfig h2Config() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:file:C:/Users/hhx640/Documents/exomiser-build/data/variants;MODE=PostgreSQL;SCHEMA=EXOMISER;DATABASE_TO_UPPER=FALSE;IFEXISTS=TRUE;AUTO_RECONNECT=TRUE;ACCESS_MODE_DATA=r");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setPoolName("variant-data");
        logger.info("Reading variant data from H2 {}", config.getJdbcUrl());
        return config;
    }

    @Cacheable("variant_data")
    @Override
    public VariantData getVariantData(Variant variant) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedFrequencyQuery = createPreparedStatement(connection, variant);
                ResultSet rs = preparedFrequencyQuery.executeQuery()) {

            return processResults(rs);

        } catch (SQLException e) {
            logger.error("Error executing frequency query: ", e);
        }
        return VariantData.empty();
    }

    private PreparedStatement createPreparedStatement(Connection connection, Variant variant) throws SQLException {

        String variantQuery = "SELECT rsid, info "
                + "FROM variant "
                + "WHERE chromosome = ? "
                + "AND position = ? "
                + "AND ref = ? "
                + "AND alt = ? ";

        PreparedStatement ps = connection.prepareStatement(variantQuery);

        ps.setInt(1, variant.getChromosome());
        ps.setInt(2, variant.getPosition());
        ps.setString(3, variant.getRef());
        ps.setString(4, variant.getAlt());

        return ps;
    }

    private VariantData processResults(ResultSet rs) throws SQLException {
        if (rs.next()) {
            Map<String, Float> values = getInfoFields(rs);
            RsId rsId = getRsId(rs);
            if (values.isEmpty() && rsId.isEmpty()) {
                return VariantData.empty();
            }
            FrequencyData frequencyData = parseFrequencyData(rsId, values);
            PathogenicityData pathogenicityData = parsePathogenicityData(values);
            return new VariantData(frequencyData, pathogenicityData);
        }
        return VariantData.empty();
    }

    private Map<String, Float> getInfoFields(ResultSet rs) throws SQLException {
        String info = rs.getString("info");
        if (!rs.wasNull()) {
            return mapInfoFields(info);
        }
        return Collections.emptyMap();
    }

    private RsId getRsId(ResultSet rs) throws SQLException {
        String rsId = rs.getString("rsid");
        if (!rs.wasNull()) {
            return RsId.valueOf(rsId);
        }
        return RsId.empty();
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

}
