package org.monarchinitiative.exomiser.core.genome.dao;

import org.monarchinitiative.exomiser.core.model.Variant;
import org.monarchinitiative.exomiser.core.model.VariantData;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
public interface VariantDataDao {

    public VariantData getVariantData(Variant variant);

}
