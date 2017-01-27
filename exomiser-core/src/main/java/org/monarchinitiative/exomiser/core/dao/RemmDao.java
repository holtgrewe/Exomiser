/*
 * The Exomiser - A tool to annotate and prioritize variants
 *
 * Copyright (C) 2012 - 2016  Charite Universitätsmedizin Berlin and Genome Research Ltd.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.monarchinitiative.exomiser.core.dao;

import de.charite.compbio.jannovar.annotation.VariantEffect;
import htsjdk.tribble.readers.TabixReader;
import org.monarchinitiative.exomiser.core.model.Variant;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityData;
import org.monarchinitiative.exomiser.core.model.pathogenicity.RemmScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
@Component
public class RemmDao {

    private final Logger logger = LoggerFactory.getLogger(RemmDao.class);

    private final TabixReader remmTabixReader;

    @Autowired
    public RemmDao(TabixReader remmTabixReader) {
        this.remmTabixReader = remmTabixReader;
    }

    @Cacheable(value = "remm", key = "#variant.hgvsGenome")
    public PathogenicityData getPathogenicityData(Variant variant) {
        // REMM has not been trained on missense variants so skip these
        if (variant.getVariantEffect() == VariantEffect.MISSENSE_VARIANT) {
            return PathogenicityData.EMPTY_DATA;
        }

        String chromosome = variant.getChromosomeName();
        int start = variant.getPosition();
        int end = calculateEndPosition(variant);

        TabixReader.Iterator result = remmTabixReader.query(chromosome + ":" + start + "-" + end);
        List<Double> scores = getScoresFromResult(result);
        return scores.stream().max(Double::compareTo)
                .map(maxScore -> new PathogenicityData(RemmScore.valueOf(maxScore.floatValue())))
                .orElse(PathogenicityData.EMPTY_DATA);
    }

    private int calculateEndPosition(Variant variant) {
        //these end positions are calculated according to recommendation by Max and Peter who produced the REMM score
        //don't change this unless they say. 
        if (isDeletion(variant)) {
            // test all deleted bases
            return variant.getPosition() + variant.getRef().length();
        } else if (isInsertion(variant)) {
            // test bases either side of insertion
            return variant.getPosition() + 1;
        }
        return variant.getPosition();
    }

    private boolean isDeletion(Variant variant) {
        return variant.getAlt().equals("-");
    }

    private boolean isInsertion(Variant variant) {
        return variant.getRef().equals("-");
    }

    private List<Double> getScoresFromResult(TabixReader.Iterator results) {
        String line = null;
        try {
            List<Double> scores = new ArrayList<>();
            while ((line = results.next()) != null) {
                String[] elements = line.split("\t");
                scores.add(Double.valueOf(elements[2]));
            }
            return scores;
        } catch (IOException e) {
            logger.error("Unable to read from REMM tabix file {}", remmTabixReader.getSource(), e);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            logger.error("Unable to parse line '{}' from REMM tabix file {}", line, remmTabixReader.getSource(), e);
        }
        return Collections.emptyList();
    }
}
