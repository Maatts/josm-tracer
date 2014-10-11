/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openstreetmap.josm.plugins.tracer.connectways;

import static org.openstreetmap.josm.tools.I18n.*;

import java.util.List;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.OsmPrimitive;


public final class MultipolygonBoundaryWayPredicate implements IEdWayPredicate {

    private final Match m_filter;
    private static volatile Match m_multipolygonMatch;

    static {
        try {
            m_multipolygonMatch = SearchCompiler.compile("type=multipolygon", false, false);
        } 
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile pattern"));            
        }
    }

    public MultipolygonBoundaryWayPredicate(Match filter) {
        m_filter = filter;
    }

    public boolean evaluate(EdWay way) {
        List<EdMultipolygon> mps = way.getEditorReferrers(EdMultipolygon.class);
        for (EdMultipolygon mp: mps) {
            if (mp.matches(m_filter))
                return true;
        }

        List<Relation> relations = way.getExternalReferrers(Relation.class);
        for (Relation rel: relations) {
            if (m_multipolygonMatch.match(rel) && m_filter.match(rel))
                return true;
        }

        return false;
    }

    public boolean evaluate(Way way) {
        List<Relation> relations = OsmPrimitive.getFilteredList(way.getReferrers(), Relation.class);
        for (Relation rel: relations) {
            if (m_multipolygonMatch.match(rel) && m_filter.match(rel))
                return true;
        }
        return false;
    }
}

