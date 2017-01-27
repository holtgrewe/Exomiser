package org.monarchinitiative.exomiser.core.model;

import org.monarchinitiative.exomiser.core.model.frequency.FrequencyData;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityData;

import java.util.Objects;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
public class VariantData {

    public static final VariantData EMPTY = new VariantData(FrequencyData.EMPTY_DATA, PathogenicityData.EMPTY_DATA);

    private final FrequencyData frequencyData;
    private final PathogenicityData pathogenicityData;

    public VariantData(FrequencyData frequencyData, PathogenicityData pathogenicityData) {
        this.frequencyData = frequencyData;
        this.pathogenicityData = pathogenicityData;
    }

    public FrequencyData getFrequencyData() {
        return frequencyData;
    }

    public PathogenicityData getPathogenicityData() {
        return pathogenicityData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariantData that = (VariantData) o;
        return Objects.equals(frequencyData, that.frequencyData) &&
                Objects.equals(pathogenicityData, that.pathogenicityData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frequencyData, pathogenicityData);
    }

    @Override
    public String toString() {
        return "VariantData{" +
                "frequencyData=" + frequencyData +
                ", pathogenicityData=" + pathogenicityData +
                '}';
    }
}
