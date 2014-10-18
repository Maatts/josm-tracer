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

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.Relation;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.BBox;


public class EdWay extends EdObject {
    private Way m_way;
    private List<EdNode> m_nodes;

    // Note: m_way has the following properties:
    // (a) before finalization:
    // - if this.hasOriginal(), m_way is a clone of originalNode();
    // - otherwise, m_way is a newly created Way object.
    // - in all cases, m_way contains NO Nodes, nodes are stored only in m_nodes array!
    // (b) after finalization:
    // - contains final way

    EdWay (WayEditor editor, List<EdNode> ednodes) {
        super(editor, null);
        m_way = new Way();

        if (!this.getEditor().ownedByEditor (ednodes))
            throw new IllegalArgumentException(tr("EdNode(s) from a different WayEditor"));

        if (ednodes != null)
            m_nodes = new ArrayList<EdNode>(ednodes);
        else
            m_nodes = new ArrayList<EdNode>();

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    EdWay(WayEditor editor, Way original_way) {
        super(editor, original_way);
        m_way = new Way(original_way);
        m_way.setNodes(null);

        m_nodes = new ArrayList<EdNode>(original_way.getNodesCount());
        for (int i = 0; i < original_way.getNodesCount(); i++)
            m_nodes.add(editor.useNode(original_way.getNode(i)));

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    public void removeAllNodes() {
        checkEditable();
        if (m_nodes.size() == 0)
            return;

        for (EdNode en: m_nodes)
            en.removeRef(this);

        m_nodes = new ArrayList<EdNode>();
        setModified();
    }

    public void setNodes(List<EdNode> ednodes) {
        checkEditable();
        if (!this.getEditor().ownedByEditor (ednodes))
            throw new IllegalArgumentException(tr("EdNode(s) from a different WayEditor"));

        if (ednodes == null) {
            removeAllNodes();
            return;
        }

        setModified();

        for (EdNode en: m_nodes)
            en.removeRef(this);

        m_nodes = new ArrayList<EdNode>(ednodes);

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    public void addNode(int offs, EdNode ednode) {
        checkEditable();
        if (!this.getEditor().ownedByEditor (ednode))
            throw new IllegalArgumentException(tr("EdNode(s) from a different WayEditor"));

        m_nodes.add(offs, ednode); // throws exception if offs is out or range

        setModified();
        ednode.addRef(this);        
    }

    public void setKeys(Map<String,String> keys) {
        checkEditable();
        m_way.setKeys(keys);
        setModified();
    }

    public Map<String,String> getKeys() {
        checkNotDeleted();
        return new HashMap<String,String> (m_way.getKeys());
    }

    public int getNodesCount() {
        return m_nodes.size();
    }

    public EdNode getNode(int idx) {
        return m_nodes.get(idx);
    }

    public boolean isClosed() {
        if (isFinalized())
            return m_way.isClosed();
        return (m_nodes.size() >= 3) && (m_nodes.get(0) == m_nodes.get(m_nodes.size() - 1));
    }

    protected OsmPrimitive currentPrimitive() {
        return m_way;
    }    

    public Way originalWay() {
        if (!hasOriginal())
            throw new IllegalStateException(tr("EdWay has no original Way"));
        return (Way)originalPrimitive();
    }

    public Way finalWay() {
        checkNotDeleted();
        if (isFinalized())
            return m_way;

        setFinalized();
        if (hasOriginal() && !isModified()) {
            m_way = originalWay();
        }
        else {
            Way fin = new Way(m_way);
            ArrayList<Node> nodes = new ArrayList<Node>(m_nodes.size());
            for (EdNode en: m_nodes)
                nodes.add(en.finalNode());
            fin.setNodes(nodes);            
            m_way = fin;
        }
        return m_way;
    }

    public BBox getBBox(double dist) {
        checkEditable(); // #### maybe support finalized ways
        BBox bbox = new BBox(m_way);
        for (EdNode n: m_nodes)
            bbox.add(n.getBBox(dist));
        return bbox;
    }

    public void reuseExistingNodes(IEdNodePredicate filter) {
        checkEditable ();
        if (filter == null)
            throw new IllegalArgumentException(tr("No filter specified"));

        boolean modified = false;
        List<EdNode> new_nodes = new ArrayList<EdNode> (m_nodes.size());
        for (EdNode en: m_nodes) {
            EdNode nn = getEditor().findExistingNodeForDuplicateMerge(en, filter);
            if (nn != null) {
                new_nodes.add (nn);
                modified = true;
            }
            else {
                new_nodes.add (en);
            }
        }

        if (modified) {
            if (isClosed() && (new_nodes.get(0) != new_nodes.get(new_nodes.size() - 1)))
                throw new AssertionError(tr("EdWay.reuseExistingNodes on a closed way created a non-closed way!"));
            setNodes (new_nodes);
        }
    }

    /**
     * Add all existing nodes that touch this way (i.e. are very close to a way segment)
     * and satisfy given predicate.
     *
     */
    public void connectExistingTouchingNodes(IEdNodePredicate filter) {
        checkEditable();
        if (filter == null)
            throw new IllegalArgumentException(tr("No filter specified"));

        int i = 0;
        while (i < m_nodes.size() - 1) {
            final LatLon x = m_nodes.get(i).getCoor();
            final LatLon y = m_nodes.get(i+1).getCoor();
            Set<EdNode> tn = getEditor().findExistingNodesTouchingWaySegment(x, y, filter);
            List<EdNode> add_nodes = new ArrayList<EdNode> ();
            for (EdNode n: tn) {
                if (n.isReferredBy(this))
                    continue;
                if (add_nodes.contains(n))
                    continue;
                // #### if EdWay is a member of a multipolygon, node must not be referred by any of
                // multipolygon ways!
                add_nodes.add(n);
            }

            if (add_nodes.size() > 0) {
                // Sort nodes according to the distance from "x"
                Collections.sort(add_nodes, new Comparator<EdNode>(){
                    public int compare(EdNode d1, EdNode d2) {
                        return Double.compare(x.distance(d1.getCoor()), x.distance(d2.getCoor()));
                    }
                });
                for (EdNode n: add_nodes) {
                    i++;
                    System.out.println("Connecting node " + Long.toString(n.getUniqueId()) + " into way " + Long.toString(this.getUniqueId()));
                    this.addNode(i, n);
                }
            }
            i++;
        }
    }

    /**
     * Add all nodes occurring in "other" EdWay that touch this way.
     * Nodes are added to the right positions into way segments.
     *
     */
    public void connectTouchingNodes(EdWay other) {
        checkEditable();
        other.checkEditable();

        if (this == other)
            return;

        Set<EdNode> other_nodes = new HashSet<EdNode>(other.m_nodes);

        int i = 0;
        while (i < m_nodes.size() - 1) {
            final LatLon x = m_nodes.get(i).getCoor();
            final LatLon y = m_nodes.get(i+1).getCoor();

            List<EdNode> add_nodes = new ArrayList<EdNode> ();
            for (EdNode n: other_nodes) {
                if (!getEditor().geomUtils().pointOnLine(n.getCoor(), x, y))
                    continue;
                // disallow touching itself
                if (n.isReferredBy(this))
                    continue;
                // #### if EdWay is a member of a multipolygon, node must not be referred by any of
                // multipolygon ways!
                add_nodes.add(n);
            }

            i++;
            if (add_nodes.size() <= 0)
                continue;

            // Sort nodes according to the distance from "x"
            Collections.sort(add_nodes, new Comparator<EdNode>(){
                public int compare(EdNode d1, EdNode d2) {
                    return Double.compare(x.distance(d1.getCoor()), x.distance(d2.getCoor()));
                }
            });
            for (EdNode n: add_nodes) {
                System.out.println("Connecting node " + Long.toString(n.getUniqueId()) + " into way " + Long.toString(this.getUniqueId()));
                this.addNode(i, n);
                i++;
            }
        }
    }

    public boolean isMemberOfAnyMultipolygon() {
        List<EdMultipolygon> mps = this.getEditorReferrers(EdMultipolygon.class);
        if (mps.size() > 0)
            return true;

        List<Relation> relations = this.getExternalReferrers(Relation.class);
        for (Relation rel: relations)
            if (MultipolygonMatch.match(rel))
                return true;
        
        return false;
    }

    public long getUniqueId() {
        checkNotDeleted();
        return m_way.getUniqueId();
    }

    public boolean hasIdenticalEdNodeGeometry(List<EdNode> list, boolean allow_inverted_orientation) {
        checkEditable(); // #### maybe support finalized ways

        if (list.size() != m_nodes.size())
            return false;

        if (!isClosed()) {
            for (int i = 0; i < list.size(); i++)
                if (list.get(i) != m_nodes.get(i))
                    return false;
            return true;
        }

        int n = list.size() - 1;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (identicalEdNodeGeometryFromOffsets(m_nodes, list, n, i, j, false))
                    return true;                
                if (allow_inverted_orientation && 
                    identicalEdNodeGeometryFromOffsets(m_nodes, list, n, i, j, true))
                    return true;
            }
        }
        return false;
    }

    private static boolean identicalEdNodeGeometryFromOffsets(List<EdNode> l1, List<EdNode> l2, int n, int i, int j, boolean inv) {
        for (int k = 0; k < n; k++) {
            if (l1.get(i) != l2.get(j))
                return false;
            i = (i + 1) % n;
            if (inv)
                j = (j - 1 + n) % n;
            else
                j = (j + 1) % n;
        }
        return true;
    }
}


