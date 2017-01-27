package org.monarchinitiative.exomiser.core.dao;

import com.google.common.collect.Maps;
import org.monarchinitiative.exomiser.core.model.Variant;
import org.monarchinitiative.exomiser.core.model.VariantData;
import org.monarchinitiative.exomiser.core.model.frequency.Frequency;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencyData;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencySource;
import org.monarchinitiative.exomiser.core.model.frequency.RsId;
import org.monarchinitiative.exomiser.core.model.pathogenicity.MutationTasterScore;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityData;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PolyPhenScore;
import org.monarchinitiative.exomiser.core.model.pathogenicity.SiftScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
@Component
public class VariantDataDaoJdbc implements VariantDataDao{

    private static final Logger logger = LoggerFactory.getLogger(VariantDataDaoJdbc.class);
    private final Map<FrequencySource, String> frequencySourceColumnMappings;

    private final DataSource dataSource;

    @Autowired
    public VariantDataDaoJdbc(DataSource dataSource) {
        this.dataSource = dataSource;

        Map<FrequencySource, String> frequencyMap = new EnumMap<>(FrequencySource.class);
        frequencyMap.put(FrequencySource.THOUSAND_GENOMES, "dbSNPmaf");
        frequencyMap.put(FrequencySource.ESP_AFRICAN_AMERICAN, "espAAmaf");
        frequencyMap.put(FrequencySource.ESP_EUROPEAN_AMERICAN, "espEAmaf");
        frequencyMap.put(FrequencySource.ESP_ALL, "espAllmaf");
        frequencyMap.put(FrequencySource.EXAC_AFRICAN_INC_AFRICAN_AMERICAN, "exacAFRmaf");
        frequencyMap.put(FrequencySource.EXAC_AMERICAN, "exacAMRmaf");
        frequencyMap.put(FrequencySource.EXAC_EAST_ASIAN, "exacEASmaf");
        frequencyMap.put(FrequencySource.EXAC_FINNISH, "exacFINmaf");
        frequencyMap.put(FrequencySource.EXAC_NON_FINNISH_EUROPEAN, "exacNFEmaf");
        frequencyMap.put(FrequencySource.EXAC_SOUTH_ASIAN, "exacSASmaf");
        frequencyMap.put(FrequencySource.EXAC_OTHER, "exacOTHmaf");
        frequencySourceColumnMappings = Maps.immutableEnumMap(frequencyMap);
        logger.debug("FrequencySource to columnLabel mappings: {}", frequencySourceColumnMappings);
    }

    public VariantData getVariantData(Variant variant) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = createPreparedStatement(connection, variant);
                ResultSet rs = preparedStatement.executeQuery()) {

            return processResults(rs);

        } catch (SQLException e) {
            logger.error("Error executing pathogenicity query: ", e);
        }
        return VariantData.EMPTY;
    }

    private PreparedStatement createPreparedStatement(Connection connection, Variant variant) throws SQLException {
        String query = "SELECT f.CHROMOSOME, f.position, f.REF, f.ALT, f.RSID, " +
                "f.DBSNPMAF, " +
                "f.ESPEAMAF, f.ESPAAMAF, f.ESPALLMAF, " +
                "f.EXACAFRMAF, f.EXACAMRMAF, f.EXACEASMAF, f.EXACFINMAF, f.EXACNFEMAF, f.EXACOTHMAF, f.EXACSASMAF, " +
                "v.MUT_TASTER, v.POLYPHEN, v.SIFT " +
                "FROM FREQUENCY f " +
                "LEFT JOIN VARIANT v " +
                "ON v.CHROMOSOME = f.CHROMOSOME " +
                "AND v.position = f.position " +
                "AND v.REF = f.REF " +
                "AND v.ALT = f.ALT " +
                "WHERE f.CHROMOSOME = ? AND f.position = ? AND f.REF = ? and f.ALT = ?";
        PreparedStatement ps = connection.prepareStatement(query);

        ps.setInt(1, variant.getChromosome());
        ps.setInt(2, variant.getPosition());
        ps.setString(3, variant.getRef());
        ps.setString(4, variant.getAlt());

        return ps;
    }

    private VariantData processResults(ResultSet rs) throws SQLException {
        if (rs.next()) {
            FrequencyData frequencyData = makeFrequencyData(rs);
            PathogenicityData pathogenicityData = makePathogenicityData(rs);
            return new VariantData(frequencyData, pathogenicityData);
        }
        return VariantData.EMPTY;
    }

    private FrequencyData makeFrequencyData(ResultSet rs) throws SQLException {
        RsId rsId = makeRsId(rs);
        Set<Frequency> frequencies = makeFrequencies(rs);
        return makeFrequencyData(rsId, frequencies);
    }

    private FrequencyData makeFrequencyData(RsId rsId, Set<Frequency> frequencies) {
        if (rsId == null && frequencies.isEmpty()) {
            return FrequencyData.EMPTY_DATA;
        }
        return new FrequencyData(rsId, frequencies);
    }

    private RsId makeRsId(ResultSet rs) throws SQLException {
        int dbSNPid = rs.getInt("rsid");
        if (!rs.wasNull() && dbSNPid != 0) {
            return RsId.valueOf(dbSNPid);
        }
        return null;
    }

    private Set<Frequency> makeFrequencies(ResultSet rs) throws SQLException {
        Set<Frequency> frequencies = new HashSet<>();
        for (Map.Entry<FrequencySource, String> sourceColumnMapping : frequencySourceColumnMappings.entrySet()) {
            FrequencySource source = sourceColumnMapping.getKey();
            String columnLabel = sourceColumnMapping.getValue();
            float freq = rs.getFloat(columnLabel);
            if (!rs.wasNull() && freq != 0) {
                frequencies.add(Frequency.valueOf(freq, source));
            }
        }
        return frequencies;
    }

    private PathogenicityData makePathogenicityData(ResultSet rs) throws SQLException {
        SiftScore siftScore = makeSiftScore(rs);
        PolyPhenScore polyPhenScore = makePolyPhenScore(rs);
        MutationTasterScore mutationTasterScore = makeMutationTasterScore(rs);
        return new PathogenicityData(polyPhenScore, mutationTasterScore, siftScore);
    }

    private SiftScore makeSiftScore(ResultSet rs) throws SQLException {
        float rowVal = rs.getFloat("sift");
        if (!rs.wasNull() && rowVal != 0) {
            return SiftScore.valueOf(rowVal);
        }
        return null;
    }

    private PolyPhenScore makePolyPhenScore(ResultSet rs) throws SQLException {
        float rowVal = rs.getFloat("polyphen");
        if (!rs.wasNull() && rowVal != 0) {
            return PolyPhenScore.valueOf(rowVal);
        }
        return null;
    }

    private MutationTasterScore makeMutationTasterScore(ResultSet rs) throws SQLException {
        float rowVal = rs.getFloat("mut_taster");
        if (!rs.wasNull() && rowVal != 0) {
            return MutationTasterScore.valueOf(rowVal);
        }
        return null;
    }


}
