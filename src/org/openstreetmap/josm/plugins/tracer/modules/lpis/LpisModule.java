/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral, Martin Svec
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

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import java.awt.Cursor;
import java.util.*;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationTask;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.tracer.PostTraceNotifications;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.*;

// import org.openstreetmap.josm.plugins.tracer.modules.lpis.LpisRecord;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;
import org.xml.sax.SAXException;

public class LpisModule implements TracerModule  {

    private boolean moduleEnabled;

    private static final String source = "lpis";
    private static final String lpisUrl = "http://eagri.cz/public/app/wms/plpis_wfs.fcgi";
    private static final String reuseExistingLanduseNodePattern =
        "((landuse=* -landuse=no) | natural=scrub | natural=wood | natural=grassland | natural=wood | leisure=garden)";
    private static final String retraceAreaPattern =
        "(landuse=farmland | landuse=meadow | landuse=orchard | landuse=vineyard | landuse=plant_nursery | (landuse=forest source=lpis))";
    
    private static final Match m_reuseExistingLanduseNodeMatch;
    private static final Match m_clipLanduseWayMatch;
    private static final Match m_mergeLanduseWayMatch;
    private static final Match m_retraceAreaMatch;

    static {
        try {
            m_reuseExistingLanduseNodeMatch = SearchCompiler.compile(reuseExistingLanduseNodePattern, false, false);
            m_clipLanduseWayMatch = m_reuseExistingLanduseNodeMatch; // use the same 
            m_mergeLanduseWayMatch = m_clipLanduseWayMatch; // use the same
            m_retraceAreaMatch = SearchCompiler.compile(retraceAreaPattern, false, false);
        } 
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile pattern"));
        }
    }

    public LpisModule(boolean enabled) {
        moduleEnabled = enabled;
    }

    @Override
    public void init() {
    }

    @Override
    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-lpis-sml");
    }

    @Override
    public String getName() {
        return tr("LPIS");
    }

    @Override
    public boolean moduleIsEnabled() {
        return moduleEnabled;
    }

    @Override
    public void setModuleIsEnabled(boolean enabled) {
        moduleEnabled = enabled;
    }

    @Override
    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new LpisTracerTask (pos, ctrl, alt, shift);
    }

    class LpisTracerTask extends PleaseWaitRunnable {

        private final LatLon m_pos;
        private final boolean m_ctrl;
        private final boolean m_alt;
        private final boolean m_shift;
        private final boolean m_performClipping;
        private final boolean m_performWayMerging;
        private final boolean m_performRetrace;

        private LpisRecord m_record;
        private boolean m_cancelled;

        private final PostTraceNotifications m_postTraceNotifications = new PostTraceNotifications();
        
        LpisTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
            this.m_record = null;
            this.m_cancelled = false;

            this.m_performClipping = !m_ctrl;
            this.m_performWayMerging = !m_ctrl;
            this.m_performRetrace = !m_ctrl;
        }

        @Override
        @SuppressWarnings({"CallToPrintStackTrace", "UseSpecificCatch", "BroadCatchBlock", "TooBroadCatch"})
        protected void realRun() throws SAXException {

            System.out.println("");
            System.out.println("-----------------");
            System.out.println("----- Trace -----");
            System.out.println("-----------------");
            System.out.println("");

            progressMonitor.indeterminateSubTask(tr("Downloading LPIS data..."));
            try {
                LpisServer server = new LpisServer();
                m_record = server.getElementData(m_pos, lpisUrl);
            }
            catch (final Exception e) {
                e.printStackTrace();
                TracerUtils.showNotification(tr("LPIS download failed.") + "\n(" + m_pos.toDisplayString() + ")", "error");
                return;
            }

            if (m_cancelled)
                return;

            // No data available?
            if (m_record.getLpisID() == -1) {
                TracerUtils.showNotification(tr("Data not available.")+ "\n(" + m_pos.toDisplayString() + ")", "warning");
                return;
            }

            // Look for incomplete multipolygons that might participate in landuse clipping
            List<Relation> incomplete_multipolygons = null;
            if (m_performClipping)
                incomplete_multipolygons = getIncompleteMultipolygonsForDownload ();
        
            // No multipolygons to download, create traced polygon immediately within this task
            if (incomplete_multipolygons == null || incomplete_multipolygons.isEmpty()) {
                progressMonitor.subTask(tr("Creating LPIS polygon..."));
                createTracedPolygon();
            }
            else {
                // Schedule task to download incomplete multipolygons
                Main.worker.submit(new DownloadRelationTask(incomplete_multipolygons, Main.main.getEditLayer())); 

                // Schedule task to create traced polygon
                Main.worker.submit(new Runnable() {
                    @Override
                    public void run() {
                        createTracedPolygon();
                    }
                });
            }
        }
        
        /**
         * Returns list of all existing incomplete multipolygons that might participate in
         * LPIS polygon clipping. These relations must be downloaded first, clipping
         * doesn't support incomplete multipolygons.
         * @return List of incomplete multipolygon relations
         */
        private List<Relation> getIncompleteMultipolygonsForDownload() {
            DataSet ds = Main.main.getCurrentDataSet();
            ds.getReadLock().lock();
            try {
                List<Relation> list = new ArrayList<>();
                for (Relation rel : ds.searchRelations(m_record.getBBox())) {
                    if (!MultipolygonMatch.match(rel))
                        continue;
                    if (rel.isIncomplete() || rel.hasIncompleteMembers())
                        list.add(rel);
                }
                return list;
            } finally {
                ds.getReadLock().unlock();
            }
        }
        
        private void wayIsOutsideDownloadedAreaDialog() {
            ExtendedDialog ed = new ExtendedDialog(
                Main.parent, tr("Way is outside downloaded area"),
                new String[] {tr("Ok")});
            ed.setButtonIcons(new String[] {"ok"});
            ed.setIcon(JOptionPane.ERROR_MESSAGE);
            ed.setContent(tr("Sorry.\nThe traced way (or part of the way) is outside of the downloaded area.\nPlease download area around the way and try again."));
            ed.showDialog();
        }
                
        private void createTracedPolygon() {
            GuiHelper.runInEDT(new Runnable() {
                @Override
                @SuppressWarnings("CallToPrintStackTrace")
                public void run() {
                    long start_time = System.nanoTime();
                    DataSet data_set = Main.main.getCurrentDataSet();
                    data_set.beginUpdate();
                    try {
                        createTracedPolygonImpl (data_set);
                        m_postTraceNotifications.show();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                    finally {
                        data_set.endUpdate();
                        long end_time = System.nanoTime();
                        long time_msecs = (end_time - start_time) / (1000*1000);
                        System.out.println("Polygon time (ms): " + Long.toString(time_msecs));
                    }
                }
            });
        }
        
        private void createTracedPolygonImpl(DataSet data_set) {

            System.out.println("  LPIS ID: " + m_record.getLpisID());
            System.out.println("  LPIS usage: " + m_record.getUsage());

            GeomUtils geom = new GeomUtils();
            WayEditor editor = new WayEditor (data_set, geom);

            // Look for object to retrace
            EdObject retrace_object = null;
            if (m_performRetrace) {
                Pair<EdObject, Boolean> repl = getObjectToRetrace(editor, m_pos);
                retrace_object = repl.a;
                boolean ambiguous_retrace = repl.b;

                if (ambiguous_retrace) {
                    m_postTraceNotifications.add(tr("Multiple existing LPIS polygons found, retrace is not possible."));
                    return;
                }
            }

            // Create traced object
            Pair<EdWay, EdMultipolygon> trobj = this.createTracedEdObject(editor);
            if (trobj == null)
                return;
            EdWay outer_way = trobj.a;
            EdMultipolygon multipolygon = trobj.b;

            // Retrace simple ways - just use the old way
            if (retrace_object != null) {
                if ((multipolygon != null) || !(retrace_object instanceof EdWay) || retrace_object.hasReferrers()) {
                    m_postTraceNotifications.add(tr("Multipolygon retrace is not supported yet."));
                    return;
                }
                EdWay retrace_way = (EdWay)retrace_object;
                retrace_way.setNodes(outer_way.getNodes());
                outer_way = retrace_way;
            }
            
            // Tag object
            tagTracedObject(multipolygon == null ? outer_way : multipolygon);

            // Connect to touching nodes of near landuse polygons            
            connectExistingTouchingNodes(multipolygon == null ? outer_way : multipolygon);

            // Clip other areas
            if (m_performClipping) {
                // #### Now, it clips using only the outer way. Consider if multipolygon clip is necessary/useful.
                AreaPredicate filter = new AreaPredicate (m_clipLanduseWayMatch);
                ClipAreas clip = new ClipAreas(editor, m_postTraceNotifications);
                clip.clipAreas(outer_way, filter);
            }
            
            // Merge duplicate ways
            // (Note that outer way can be merged to another way too, so we must watch it.
            // Otherwise, outer_way variable would refer to an unused way.)
            if (m_performWayMerging) {
                AreaPredicate merge_filter = new AreaPredicate (m_mergeLanduseWayMatch);
                MergeIdenticalWays merger = new MergeIdenticalWays(editor, merge_filter);
                outer_way = merger.mergeWays(editor.getModifiedWays(), true, outer_way);
            }

            List<Command> commands = editor.finalizeEdit();

            if (!commands.isEmpty()) {

                long start_time = System.nanoTime();                
                
                Main.main.undoRedo.add(new SequenceCommand(tr("Trace object"), commands));

                OsmPrimitive sel = (multipolygon != null ?
                    multipolygon.finalMultipolygon() : outer_way.finalWay());

                if (m_shift) {
                    editor.getDataSet().addSelected(sel);
                } else {
                    editor.getDataSet().setSelected(sel);
                }
                long end_time = System.nanoTime();
                long time_msecs = (end_time - start_time) / (1000*1000);
                System.out.println("undoRedo time (ms): " + Long.toString(time_msecs));                
                
            } else {
                m_postTraceNotifications.add(tr("Nothing changed."));
            }
        }

        @Override
        protected void finish() {
        }        
        
        @Override
        protected void cancel() {
            m_cancelled = true;
            // #### TODO: break the connection to remote LPIS server
        }

        private void tagTracedObject (EdObject obj) {

            Map <String, String> map = obj.getKeys();
            
            Map <String, String> new_keys = new HashMap <> (m_record.getUsageOsm());
            for (Map.Entry<String, String> new_key: new_keys.entrySet()) {
                map.put(new_key.getKey(), new_key.getValue());
            }
            
            // #### delete any existing retraced tags??
            
            map.put("source", source);
            map.put("ref", Long.toString(m_record.getLpisID()));
            obj.setKeys(map);
        }

        private Pair<EdWay, EdMultipolygon> createTracedEdObject (WayEditor editor) {

            IEdNodePredicate reuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);           
            
            // Prepare outer way nodes
            List<LatLon> outer_lls = m_record.getOuter();
            List<EdNode> outer_nodes = new ArrayList<> (outer_lls.size());
            for (int i = 0; i < outer_lls.size() - 1; i++) {
                EdNode node = editor.newNode(outer_lls.get(i));

                if (!editor.insideDataSourceBounds(node)) {
                    wayIsOutsideDownloadedAreaDialog();
                    return null;
                }

                outer_nodes.add(node);
            }
            if (outer_nodes.size() < 3)
                throw new AssertionError(tr("Outer way consists of less than 3 nodes"));
            
            // Close & create outer way
            outer_nodes.add(outer_nodes.get(0));
            EdWay outer_way = editor.newWay(outer_nodes);
            outer_way.reuseExistingNodes(reuse_filter);

            // Simple way?
            if (!m_record.hasInners())
                return new Pair<>(outer_way, null);

            // Create multipolygon
            EdMultipolygon multipolygon = editor.newMultipolygon();
            multipolygon.addOuterWay(outer_way);

            for (List<LatLon> inner_lls: m_record.getInners()) {
                List<EdNode> inner_nodes = new ArrayList<>(inner_lls.size());
                for (int i = 0; i < inner_lls.size() - 1; i++) {
                    inner_nodes.add(editor.newNode(inner_lls.get(i)));
                }

                // Close & create inner way
                if (inner_nodes.size() < 3)
                    throw new AssertionError(tr("Inner way consists of less than 3 nodes"));
                inner_nodes.add(inner_nodes.get(0));
                EdWay way = editor.newWay(inner_nodes);
                way.reuseExistingNodes(reuse_filter);

                multipolygon.addInnerWay(way);
            }
            
            return new Pair<>(outer_way, multipolygon);
        }
        
        private Pair<EdObject, Boolean> getObjectToRetrace(WayEditor editor, LatLon pos) {
            AreaPredicate filter = new AreaPredicate(m_retraceAreaMatch);
            Set<EdObject> areas = editor.useNonEditedAreasContainingPoint(pos, filter);
            
            String lpisref = Long.toString(m_record.getLpisID());
            
            // restrict to LPIS areas only, yet ... #### improve in the future
            boolean multiple_areas = false;
            EdObject lpis_area = null;
            for (EdObject area: areas) {
                String source = area.get("source");
                if (source == null || !source.equals("lpis"))
                    continue;

                if (area instanceof EdWay)
                    System.out.println("Retrace candidate EdWay: " + Long.toString(area.getUniqueId()));
                else if (area instanceof EdMultipolygon)
                    System.out.println("Retrace candidate EdMultipolygon: " + Long.toString(area.getUniqueId()));
                
                String ref = area.get("ref");
                if (ref != null && ref.equals(lpisref)) // exact match ;)
                    return new Pair<>(area, false);
                                
                if (lpis_area == null)
                    lpis_area = area;
                else
                    multiple_areas = true;
            }
            
            if (multiple_areas) {                
                return new Pair<>(null, true);
            }
            
            if (lpis_area != null) {
                return new Pair<>(lpis_area, false);
            }
            
            return new Pair<>(null, false);
        }

        private void connectExistingTouchingNodes(EdObject obj) {
            // Setup filters - include landuse nodes only, exclude all nodes of the object itself
            IEdNodePredicate landuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(obj);
            IEdNodePredicate filter = new EdNodeLogicalAndPredicate (exclude_my_nodes, landuse_filter);

            if (obj instanceof EdWay)
                ((EdWay)obj).connectExistingTouchingNodes(filter);
            else if (obj instanceof EdMultipolygon)
                ((EdMultipolygon)obj).connectExistingTouchingNodes(filter);
            else
                throw new AssertionError("Unsupported EdObject instance");
        }
    }
}

