package org.monarchinitiative.exomiser.core.model;

import org.junit.Test;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencyData;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityData;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
public class VariantDataTest {

    @Test
    public void testEmpty() {
        System.out.println(VariantData.EMPTY);
        assertThat(VariantData.EMPTY.getFrequencyData(), equalTo(FrequencyData.EMPTY_DATA));
        assertThat(VariantData.EMPTY.getPathogenicityData(), equalTo(PathogenicityData.EMPTY_DATA));
    }

}