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

package org.monarchinitiative.exomiser.core.model.frequency;

import java.util.Objects;

/**
 * Class representing an NCBI dbSNP reference SNP rsID.
 * <p>
 * {@link http://www.ncbi.nlm.nih.gov/projects/SNP/index.html}
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public final class RsId {

    public static final RsId EMPTY_VALUE = new RsId(0);
    private static final String VCF_EMPTY_VALUE = ".";

    private final int id;

    public static RsId valueOf(int id) {
        return new RsId(id);
    }

    /**
     * Parses rs ids from their VCF representation - can be in the form "rs123456" or "." for an empty value.
     * @param id
     * @return
     */
    public static RsId valueOf(String id) {
        if (VCF_EMPTY_VALUE.equals(id)) {
            return EMPTY_VALUE;
        }
        return new RsId(Integer.parseInt(id.substring(2)));
    }

    private RsId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RsId)) {
            return false;
        }
        RsId rsId = (RsId) o;
        return id == rsId.id;
    }

    @Override
    public String toString() {
        if (id == 0) {
            return VCF_EMPTY_VALUE;
        }
        return "rs" + id;
    }
}
