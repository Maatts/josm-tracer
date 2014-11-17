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
            m_nodes = new ArrayList<>(ednodes);
        else
            m_nodes = new ArrayList<>();

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    EdWay(WayEditor editor, Way original_way) {
        super(editor, original_way);
        m_way = new Way(original_way);
        m_way.setNodes(null);

        m_nodes = new ArrayList<>(original_way.getNodesCount());
        for (int i = 0; i < original_way.getNodesCount(); i++)
            m_nodes.add(editor.useNode(original_way.getNode(i)));

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    public void removeAllNodes() {
        checkEditable();
        if (m_nodes.isEmpty())
            return;

        for (EdNode en: m_nodes)
            en.removeRef(this);

        m_nodes = new ArrayList<>();
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

        m_nodes = new ArrayList<>(ednodes);

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    public List<EdNode> getNodes() {
        checkEditable();
        return Collections.unmodifiableList(m_nodes);
    }

    public void addNode(int offs, EdNode ednode) {
        checkEditable();
        if (!this.getEditor().ownedByEditor (ednode))
            throw new IllegalArgumentException(tr("EdNode(s) from a different WayEditor"));

        m_nodes.add(offs, ednode); // throws exception if offs is out or range

        setModified();
        ednode.addRef(this);        
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

    @Override
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
            ArrayList<Node> nodes = new ArrayList<>(m_nodes.size());
            for (EdNode en: m_nodes)
                nodes.add(en.finalNode());
            fin.setNodes(nodes);            
            m_way = fin;
        }
        return m_way;
    }
    
    @Override
    protected void updateModifiedFlag() {
        checkEditable();
        if (!hasOriginal() || isDeleted() || !isModified())
            return;
        Way orig = originalWay();        
        
        if (orig.getUniqueId() != m_way.getUniqueId())
            return;
        if (orig.getNodesCount() != m_nodes.size())
            return;
        for (int i = 0; i < m_nodes.size(); i++) {
            if (m_nodes.get(i).getUniqueId() != orig.getNode(i).getUniqueId())
                return;
        }
        if (!hasIdenticalKeys(orig))
            return;
        
        resetModified();
    }

    /**
     * Returns a final way that can be referenced in EdMultipolygon finalization.
     * The main difference from finalWay() is that for a modified way, it doesn't
     * return new final Way but -surprisingly- the original Way. Indeed, because
     * JOSM's ChangeCommand does not replace the original object wit the new one but
     * just copies it's contents, EdMultipolygon must be finalized with a reference to
     * the original way. Command ordering in WayEditor.finalizeEdit() guarantees that
     * the original way is changed prior the multipolygon uses it.
     * @return final Way 
     */
    Way finalReferenceableWay() {
        checkNotDeleted();
        Way fin = finalWay();
        if (hasOriginal() && isModified())
            return originalWay();
        else
            return fin;
    }
    
    @Override
    public BBox getBBox() {
        checkNotDeleted();
        if (isFinalized())
            return m_way.getBBox();
        BBox bbox = new BBox(m_way);
        for (EdNode n: m_nodes)
            bbox.add(n.getBBox());
        return bbox;
    }

    public void reuseExistingNodes(IEdNodePredicate filter) {
        checkEditable ();
        if (filter == null)
            throw new IllegalArgumentException(tr("No filter specified"));

        boolean modified = false;
        List<EdNode> new_nodes = new ArrayList<> (m_nodes.size());
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
     * Add all existing nodes that touch this way (i.e. are very close to
     * any of its way segments) and satisfy given predicate.
     * Nodes are added to the right positions into way segments.
     *
     * This function doesn't impose any additional restrictions to matching nodes,
     * except those provided by "filter" predicate.
     *
     */
    public boolean connectExistingTouchingNodes(IEdNodePredicate filter) {
        checkEditable();
        if (filter == null)
            throw new IllegalArgumentException(tr("No filter specified"));

        Map<EdNode, Pair<Double, Integer>> nodes_map = new HashMap<>();

        // get every node touching the way, assign it to closest way segment
        for (int i = 0; i < m_nodes.size() - 1; i++) {
            final EdNode x = m_nodes.get(i);
            final EdNode y = m_nodes.get(i+1);
            Set<EdNode> tn = getEditor().findExistingNodesTouchingWaySegment(x, y, filter);
            for (EdNode node: tn) {
                Pair<Double, Integer> best_segment = nodes_map.get(node);
                double dist = getEditor().geomUtils().distanceToSegmentMeters(node, x, y);
                if (best_segment == null || best_segment.a > dist) {
                    nodes_map.put(node, new Pair<> (dist, i));
                }
            }
        }

        if (nodes_map.size() <= 0)
            return false;

        insertTouchingNodesIntoWaySegments(nodes_map);
        return true;
    }


    
    /**
     * Add all nodes occurring in other EdWay that touch this way.
     * Nodes are added to the right positions into way segments.
     * This function doesn't impose any additional restrictions to matching nodes,
     * except those provided by "filter" predicate.
     * 
     * @param other EdWay whose nodes will be tested and added
     * @param filter predicate to rule out unwanted nodes
     * @return true if any touching nodes were added; false if no nodes
     * were added.
     */
    public boolean connectTouchingNodes(EdWay other, IEdNodePredicate filter) {
        checkEditable();
        other.checkEditable();

        if (this == other)
            return false;
        
        final double tolerance_degs = getEditor().geomUtils().pointOnLineToleranceDegrees();        
        final BBox way_bbox = this.getBBox(tolerance_degs);
        
        // filter nodes
        Set<EdNode> other_nodes = new HashSet<>();
        for (EdNode node: other.m_nodes) {
            if (!way_bbox.bounds(node.getCoor()))
                continue;
            if (filter.evaluate(node))
               other_nodes.add(node);
        }
        
        if (other_nodes.isEmpty())
            return false;
        
        Map<EdNode, Pair<Double, Integer>> nodes_map = new HashMap<>();

        // get every node touching the way, assign it to closest way segment
        for (int i = 0; i < m_nodes.size() - 1; i++) {
            final EdNode x = m_nodes.get(i);
            final EdNode y = m_nodes.get(i+1);
            
            BBox seg_bbox = new BBox(x.currentNodeUnsafe());
            seg_bbox.add(y.getCoor());
            BBoxUtils.extendBBox(seg_bbox, tolerance_degs);
            
            for (EdNode node: other_nodes) {
                if (!seg_bbox.bounds(node.getCoor()))
                    continue;
                if (!getEditor().geomUtils().pointOnLine(node, x, y))
                    continue;
                Pair<Double, Integer> best_segment = nodes_map.get(node);
                double dist = getEditor().geomUtils().distanceToSegmentMeters(node, x, y);
                if (best_segment == null || best_segment.a > dist) {
                    nodes_map.put(node, new Pair<> (dist, i));
                }
            }
        }

        if (nodes_map.size() <= 0)
            return false;
        
        insertTouchingNodesIntoWaySegments(nodes_map);
        return true;
    }

    private void insertTouchingNodesIntoWaySegments(Map<EdNode, Pair<Double, Integer>> nodes_map) {

        Set<Map.Entry<EdNode, Pair<Double, Integer>>> entry_set = nodes_map.entrySet();
        List<EdNode> new_nodes = new ArrayList<>(m_nodes.size() + nodes_map.size());

        // go through all way segments and add touching nodes
        for (int i = 0; i < m_nodes.size(); i++) {
            final EdNode x = m_nodes.get(i);
            new_nodes.add(m_nodes.get(i));

            // get all nodes mapped to this way segment
            List<EdNode> add_nodes = new ArrayList<> ();
            for (Map.Entry<EdNode, Pair<Double, Integer>> entry: entry_set) {
                if (entry.getValue().b != i)
                    continue;
                add_nodes.add(entry.getKey());
            }

            if (add_nodes.size() <= 0)
                continue;

            // sort nodes according to the distance from segment's first node
            Collections.sort(add_nodes, new Comparator<EdNode>(){
                @Override
                public int compare(EdNode d1, EdNode d2) {
                    return Double.compare(x.getCoor().distance(d1.getCoor()), x.getCoor().distance(d2.getCoor()));
                }
            });
            for (EdNode n: add_nodes) {
                System.out.println("Connecting node " + Long.toString(n.getUniqueId()) + " into way " + Long.toString(this.getUniqueId()));
                new_nodes.add(n);
            }
        }

        this.setNodes(new_nodes);        
    }

    /**
     * Add all nodes occurring in "other" EdWay that touch this way and
     * aren't members of this way yet.
     * Nodes are added to the right positions into way segments.
     *
     */
    public boolean connectNonIncludedTouchingNodes(EdWay other) {
        IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(this);
        return connectTouchingNodes(other, exclude_my_nodes);
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

    public boolean hasIdenticalEdNodeGeometry(List<EdNode> list, boolean allow_inverted_orientation) {
        checkEditable(); // #### maybe support finalized ways

        if (list.size() != m_nodes.size())
            return false;

        if (!isClosed()) {
            if (identicalEdNodeGeometryFromOffsets(m_nodes, list, list.size(), 0, 0, false))
                return true;
            return allow_inverted_orientation &&
                identicalEdNodeGeometryFromOffsets(m_nodes, list, list.size(), 0, list.size() - 1, true);
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
    
    /**
     * Returns the number of unique nodes that occur both in this way
     * and in the given list of nodes.
     * @param list the list of nodes to compare with
     * @return the number of shared nodes
     */
    public int getSharedNodesCount(List<EdNode> list) {
        checkEditable();
        Set<EdNode> s1 = new HashSet<>(m_nodes);
        Set<EdNode> s2 = new HashSet<>(list);
        if (s1.size() > s2.size()) {
            Set<EdNode> aux = s1;
            s1 = s2;
            s2 = aux;
        }
        int count = 0;
        for (EdNode n: s1) {
            if (s2.contains(n))
                count++;
        }
        return count;
    }
    
    /**
     * Returns true if EdWay has at least one referrer that matches
     * given area predicate. Both editor and external referrers are tested.
     * @param filter area predicate filter
     * @return true if a matching referrer was found
     */
    public boolean hasMatchingReferrers(IEdAreaPredicate filter) {
        List<EdMultipolygon> edmps = this.getEditorReferrers(EdMultipolygon.class);
        for (EdMultipolygon edmp: edmps) {
            if (filter.evaluate(edmp))
                return true;
        }
        
        List<Relation> rels = this.getExternalReferrers(Relation.class);
        for (Relation rel: rels) {
            if (filter.evaluate(rel))
                return true;
        }
        
        return false;
    }
}


