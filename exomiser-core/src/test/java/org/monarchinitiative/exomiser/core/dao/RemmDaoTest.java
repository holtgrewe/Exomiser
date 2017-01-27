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
import htsjdk.variant.variantcontext.VariantContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.monarchinitiative.exomiser.core.model.VariantEvaluation;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityData;
import org.monarchinitiative.exomiser.core.model.pathogenicity.RemmScore;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
@RunWith(MockitoJUnitRunner.class)
public class RemmDaoTest {
    
    private RemmDao instance;
    
    @Mock
    private TabixReader remmTabixReader;
    
    private MockTabixIterator mockIterator;

    @Before
    public void setUp() {
        mockIterator = new MockTabixIterator();
        instance = new RemmDao(remmTabixReader);
    }

    private static VariantEvaluation variant(int chr, int pos, String ref, String alt) {
        VariantEvaluation.Builder builder = new VariantEvaluation.Builder(chr, pos, ref, alt)
                .variantEffect(VariantEffect.REGULATORY_REGION_VARIANT);

        if (ref.equals("-") || alt.equals("-")) {
            //this is used to get round the fact that in real life the variant evaluation
            //is built from a variantContext and some variantAnnotations
            return builder.variantContext(Mockito.mock(VariantContext.class)).build();
        }
        return builder.build();
    }
    
    @Test
    public void testGetPathogenicityData_missenseVariant() {
        //missense variants are by definition protein-coding and therefore cannot be non-coding so we expect nothing 
        VariantEvaluation missenseVariant = new VariantEvaluation.Builder(1, 1, "A", "T").variantEffect(VariantEffect.MISSENSE_VARIANT).build();
        assertThat(instance.getPathogenicityData(missenseVariant), equalTo(PathogenicityData.EMPTY_DATA));
    }
    
    @Test
    public void testGetPathogenicityData_unableToReadFromSource() {
        Mockito.when(remmTabixReader.query("1:1-1")).thenReturn(() -> {
            throw new IOException();
        });
        assertThat(instance.getPathogenicityData(variant(1, 1, "A", "T")), equalTo(PathogenicityData.EMPTY_DATA));
    }

    @Test
    public void testGetPathogenicityData_NumberFormatException() {
        MockTabixIterator mockIterator = new MockTabixIterator(Collections.singletonList("1\t1\tWIBBLE!"));
        Mockito.when(remmTabixReader.query("1:1-1")).thenReturn(mockIterator);
        assertThat(instance.getPathogenicityData(variant(1, 1, "A", "T")), equalTo(PathogenicityData.EMPTY_DATA));
    }

    @Test
    public void testGetPathogenicityData_ArrayIndexException() {
        mockIterator.setValues(Collections.singletonList("1\t1"));
        Mockito.when(remmTabixReader.query("1:1-1")).thenReturn(mockIterator);
        assertThat(instance.getPathogenicityData(variant(1, 1, "A", "T")), equalTo(PathogenicityData.EMPTY_DATA));
    }

    @Test
    public void testGetPathogenicityData_singleNucleotideVariationNoData() {
        mockIterator.setValues(Collections.emptyList());
        Mockito.when(remmTabixReader.query("1:1-1")).thenReturn(mockIterator);

        assertThat(instance.getPathogenicityData(variant(1, 1, "A", "T")), equalTo(PathogenicityData.EMPTY_DATA));
    }
    
    @Test
    public void testGetPathogenicityData_singleNucleotideVariation() {
        mockIterator.setValues(Collections.singletonList("1\t1\t1.0"));
        Mockito.when(remmTabixReader.query("1:1-1")).thenReturn(mockIterator);

        assertThat(instance.getPathogenicityData(variant(1, 1, "A", "T")), equalTo(new PathogenicityData(RemmScore.valueOf(1f))));
    }
    
    @Test
    public void testGetPathogenicityData_insertion() {
        mockIterator.setValues(Arrays.asList("1\t1\t0.0", "1\t2\t1.0"));
        Mockito.when(remmTabixReader.query("1:1-2")).thenReturn(mockIterator);

        assertThat(instance.getPathogenicityData(variant(1, 1, "-", "TTT")), equalTo(new PathogenicityData(RemmScore.valueOf(1f))));
    }
    
    @Test
    public void testGetPathogenicityData_deletion() {
        mockIterator.setValues(Arrays.asList("1\t1\t0.0", "1\t2\t0.5", "1\t3\t1.0"));
        Mockito.when(remmTabixReader.query("1:1-4")).thenReturn(mockIterator);

        assertThat(instance.getPathogenicityData(variant(1, 1, "TTT", "-")), equalTo(new PathogenicityData(RemmScore.valueOf(1f))));
    }
}
