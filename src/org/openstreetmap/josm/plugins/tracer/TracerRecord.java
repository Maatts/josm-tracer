/**
 *  Tracer - plugin for JOSM
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

package org.openstreetmap.josm.plugins.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import static org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.Functions.tr;
import org.openstreetmap.josm.plugins.tracer.connectways.EdMultipolygon;
import org.openstreetmap.josm.plugins.tracer.connectways.EdNode;
import org.openstreetmap.josm.plugins.tracer.connectways.EdObject;
import org.openstreetmap.josm.plugins.tracer.connectways.EdWay;
import org.openstreetmap.josm.plugins.tracer.connectways.GeomUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.WayEditor;

public abstract class TracerRecord {

    private List<LatLon> m_outer;
    private List<List<LatLon>> m_inners;

    private final double m_adjustLat;
    private final double m_adjustLon;

    public TracerRecord (double adjlat, double adjlon) {
        m_outer = null;
        m_inners = new ArrayList<>();
        m_adjustLat = adjlat;
        m_adjustLon = adjlon;
    }

    protected void init() {
        m_outer = null;
        m_inners = new ArrayList<>();
    }

    /**
     *  Return outer polygon
     *  @return Outer polygon nodes
     */
    public final List <LatLon> getOuter() {
        if (m_outer == null)
            throw new IllegalStateException("No outer geometry available");
        return Collections.unmodifiableList(m_outer);
    }

    /**
     * Returns whether there's an outer way.
     * @return true if outer way is available
     */
    public final boolean hasOuter() {
        return m_outer != null;
    }

    /**
     *  Return whether there are inner polygons
     *  @return True/False
     */
    public final boolean hasInners() {
        return m_inners.size() > 0;
    }

    /**
     *  Returns the list of inner polygons
     *  @return inner polygons
     */
    public final List<List<LatLon>> getInners() {
        return Collections.unmodifiableList(m_inners);
    }

    /**
     * Returns BBox of the traced geometry
     * @return BBox of the geometry
     */
    public final BBox getBBox() {
        List<LatLon> outer = this.getOuter();
        LatLon p0 = outer.get(0);

        BBox bbox = new BBox(p0.lon(), p0.lat());
        for (int i = 1; i < outer.size(); i++) {
            LatLon p = outer.get(i);
            bbox.add(p.lon(), p.lat());
        }

        return bbox;
    }

    protected final void setOuter(List<LatLon> outer) {
        m_outer = adjustWay (outer);
    }

    protected final void addInner(List<LatLon> inner) {
        m_inners.add(adjustWay (inner));
    }

    public abstract boolean hasData();

    private List<LatLon> adjustWay(List<LatLon> way) {
        List<LatLon> list = new ArrayList<>(way.size());
        boolean adj = m_adjustLat != 0.0 && m_adjustLon != 0;
        final double precision = GeomUtils.duplicateNodesPrecision();
        LatLon prev_coor = null;

        for (LatLon ll: way) {
            // apply coordinate corrections
            LatLon latlon = (!adj) ?
                ll.getRoundedToOsmPrecision() :
                new LatLon(
                    LatLon.roundToOsmPrecision(ll.lat() + m_adjustLat),
                    LatLon.roundToOsmPrecision(ll.lon() + m_adjustLon));

            // avoid duplicate nodes
            if (GeomUtils.duplicateNodes(latlon, prev_coor, precision))
                continue;

            list.add(latlon);
            prev_coor = latlon;
        }
        return list;
    }

    public EdObject createObject (WayEditor editor) {

        if (m_outer == null)
            throw new IllegalStateException(tr("No outer geometry available"));

        // Prepare outer way nodes
        List<EdNode> outer_nodes = new ArrayList<> (m_outer.size());
        for (int i = 0; i < m_outer.size() - 1; i++) {
            outer_nodes.add(editor.newNode(m_outer.get(i)));
        }

        // Close & create outer way
        if (outer_nodes.size() < 3)
            throw new AssertionError(tr("Outer way consists of less than 3 nodes"));
        outer_nodes.add(outer_nodes.get(0));
        EdWay outer_way = editor.newWay(outer_nodes);

        // Simple way?
        if (!this.hasInners())
            return outer_way;

        // Create multipolygon
        EdMultipolygon multipolygon = editor.newMultipolygon();
        multipolygon.addOuterWay(outer_way);

        for (List<LatLon> inner_rls: m_inners) {
            List<EdNode> inner_nodes = new ArrayList<>(inner_rls.size());
            for (int i = 0; i < inner_rls.size() - 1; i++) {
                inner_nodes.add(editor.newNode(inner_rls.get(i)));
            }

            // Close & create inner way
            if (inner_nodes.size() < 3)
                throw new AssertionError(tr("Inner way consists of less than 3 nodes"));
            inner_nodes.add(inner_nodes.get(0));
            multipolygon.addInnerWay(editor.newWay(inner_nodes));
        }

        return multipolygon;
    }
}
