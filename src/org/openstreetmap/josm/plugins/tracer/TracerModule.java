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

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationTask;
import static org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.Functions.tr;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.tracer.connectways.EdMultipolygon;
import org.openstreetmap.josm.plugins.tracer.connectways.EdNode;
import org.openstreetmap.josm.plugins.tracer.connectways.EdObject;
import org.openstreetmap.josm.plugins.tracer.connectways.EdWay;
import org.openstreetmap.josm.plugins.tracer.connectways.LatLonSize;
import org.openstreetmap.josm.plugins.tracer.connectways.MultipolygonMatch;
import org.openstreetmap.josm.plugins.tracer.connectways.WayEditor;

/**
 * Base class for Tracer modules
 *
 */

public abstract class TracerModule {
    /**
     *  Function for initialization
     */
    public abstract void init();

    /**
     *  Returns cursor image
     *  @return Module cursor image
     */
    public abstract Cursor getCursor();

    /**
     *  Returns module name
     *  @return the module name
     */
    public abstract String getName();

    /**
     *  Returns whether is module enabled or not
     *  @return True/False
     */
    public abstract boolean moduleIsEnabled();

    /**
     *  Sets module status to Enabled or Disabled
     *  @param enabled True/False
     */
    public abstract void setModuleIsEnabled(boolean enabled);

    /**
     *  Returns a tracer task that extracts the object for given position
     *  @param pos position to trase
     */
    public abstract PleaseWaitRunnable trace(LatLon pos, boolean ctrl, boolean alt, boolean shift);

    public abstract class AbstractTracerTask extends PleaseWaitRunnable {

        protected final LatLon m_pos;
        protected final boolean m_ctrl;
        protected final boolean m_alt;
        protected final boolean m_shift;

        protected boolean m_performRetrace;
        protected boolean m_performClipping;
        protected boolean m_performWayMerging;

        private TracerRecord m_record;
        private boolean m_cancelled;

        private static final double resurrectNodesDistanceMeters = 10.0;
        private static final double defaultAutomaticOsmDownloadMeters = 500.0;

        private final PostTraceNotifications m_postTraceNotifications = new PostTraceNotifications();

        protected AbstractTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
            this.m_record = null;
            this.m_cancelled = false;

            this.m_performClipping = !m_ctrl;
            this.m_performRetrace = !m_ctrl;
            this.m_performWayMerging = !m_ctrl;
        }

        /**
         * Returns list of all existing incomplete multipolygons that might participate in
         * traced polygon clipping. These relations must be downloaded first, clipping
         * doesn't support incomplete multipolygons.
         * @return List of incomplete multipolygon relations
         */
        private List<Relation> getIncompleteMultipolygonsForDownload() {
            DataSet ds = Main.main.getCurrentDataSet();
            ds.getReadLock().lock();
            BBox bbox = m_record.getBBox();
            try {
                List<Relation> list = new ArrayList<>();
                for (Relation rel : ds.searchRelations(bbox)) {
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

        protected void wayIsOutsideDownloadedAreaDialog() {
            ExtendedDialog ed = new ExtendedDialog(
                Main.parent, tr("Way is outside downloaded area"),
                new String[] {tr("Ok")});
            ed.setButtonIcons(new String[] {"ok"});
            ed.setIcon(JOptionPane.ERROR_MESSAGE);
            ed.setContent(tr("Sorry.\nThe traced way (or part of the way) is outside of the downloaded area.\nPlease download area around the way and try again."));
            ed.showDialog();
        }

        @Override
        protected final void finish() {
        }

        @Override
        protected final void cancel() {
            m_cancelled = true;
            // #### TODO: break the connection to remote server
        }

        protected boolean cancelled() {
            return m_cancelled;
        }

        protected TracerRecord getRecord() {
            if (m_record == null)
                throw new IllegalStateException("Record is null");
            return m_record;
        }

        protected PostTraceNotifications postTraceNotifications() {
            return m_postTraceNotifications;
        }

        @Override
        @SuppressWarnings({"CallToPrintStackTrace", "UseSpecificCatch", "BroadCatchBlock", "TooBroadCatch"})
        protected final void realRun() {

            System.out.println("");
            System.out.println("-----------------");
            System.out.println("----- Trace -----");
            System.out.println("-----------------");
            System.out.println("");

            progressMonitor.indeterminateSubTask(tr("Downloading {0} data..." , getName()));
            try {
                m_record = downloadRecord(m_pos);
            }
            catch (final Exception e) {
                e.printStackTrace();
                TracerUtils.showNotification(tr("{0} download failed ({1}).\nException: {2}", getName(), m_pos.toDisplayString(), e.getLocalizedMessage()), "error");
                return;
            }

            if (cancelled())
                return;

            // No data available?
            if (!m_record.hasData()) {
                TracerUtils.showNotification(tr("Data not available.")+ "\n(" + m_pos.toDisplayString() + ")", "warning");
                return;
            }

            // Schedule all required tasks
            LatLonSize downloadsize = LatLonSize.get (m_pos, this.getAutomaticOsmDownloadMeters ());
            LatLonSize extrasize = this.getMissingAreaCheckExtraSize(m_pos);
            Bounds area = m_record.getMissingAreaToDownload(Main.main.getCurrentDataSet(), extrasize, downloadsize);
            if (area != null) {
                DownloadOsmTask task = new DownloadOsmMissingAreaTask();
                final Future<?> future = task.download(false, area, null);
                // Note: we don't start PostDownloadHandler after download because we're
                // not interested in download errors.

                // Note: be careful, this download doesn't guarantee that the required area will be available.
                // First, the download can fail at any time. Second, getMissingAreaToDownload() doesn't
                // consider object retracing. That is, if the retraced object is significantly different
                // from the traced one, it still can be outside of downloaded bounds. Because retrace object
                // is selected inside WayEditor transaction, there's no easy way to get it now. Also, retraced
                // object might be an incomplete multipolygon that is not downloaded yet. So, we ignore these
                // corner cases here and all modules still must must carefully check if data bounds requirements
                // are satisfied.

                Main.worker.submit(new Runnable() {
                    @Override
                    public void run() {
                        try { future.get(); } catch (Exception e) {}
                        downloadIncompleteMultipolygonsTask();
                    }
                });
                Main.worker.submit(new Runnable() {
                    @Override
                    public void run() {
                        createTracedPolygon();
                    }
                });
            }
            else if (downloadIncompleteMultipolygonsTask()) {
                Main.worker.submit(new Runnable() {
                    @Override
                    public void run() {
                        createTracedPolygon();
                    }
                });
            }
            else {
                progressMonitor.subTask(tr("Creating {0} polygon...", getName()));
                createTracedPolygon();
            }
        }

        private boolean downloadIncompleteMultipolygonsTask() {

            // Look for incomplete multipolygons that might participate in clipping
            List<Relation> incomplete_multipolygons = null;
            if (m_performClipping)
                incomplete_multipolygons = getIncompleteMultipolygonsForDownload ();

            if (incomplete_multipolygons == null || incomplete_multipolygons.isEmpty())
                return false;

            // Schedule task to download incomplete multipolygons
            Main.worker.submit(new DownloadRelationTask(incomplete_multipolygons, Main.main.getEditLayer()));
            return true;
        }

        protected abstract LatLonSize getMissingAreaCheckExtraSize(LatLon pos);

        protected double getAutomaticOsmDownloadMeters () {
            return defaultAutomaticOsmDownloadMeters;
        }

        protected EdWay getOuterWay(EdObject obj) {
            if (obj.isWay()) {
                return (EdWay)obj;
            }
            if (obj.isMultipolygon()) {
                EdMultipolygon mp = (EdMultipolygon)obj;
                List<EdWay> ways = mp.outerWays();
                if (ways.size() == 1) {
                    return ways.get(0);
                }
            }
            throw new AssertionError("Cannot determine outer way of EdObject");
        }

        protected EdWay getAnyOuterWay(EdObject obj) {
            if (obj.isWay()) {
                return (EdWay)obj;
            }
            if (obj.isMultipolygon()) {
                EdMultipolygon mp = (EdMultipolygon)obj;
                List<EdWay> ways = mp.outerWays();
                if (ways.size() >= 1) {
                    return ways.get(0);
                }
            }
            throw new AssertionError("Cannot determine outer way of EdObject");
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
                        WayEditor editor = new WayEditor (data_set);
                        EdObject object = createTracedPolygonImpl (editor);
                        if (object != null) {
                            finalizeEdit(editor, object);
                        }
                        postTraceNotifications().show();
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

                private void finalizeEdit(WayEditor editor, EdObject object) {

                    List<Command> commands = editor.finalizeEdit(object, getResurrectNodesDistanceMeters());

                    if (commands.isEmpty()) {
                        postTraceNotifications().add(tr("Nothing changed."));
                        return;
                    }

                    long start_time = System.nanoTime();

                    Main.main.undoRedo.add(new SequenceCommand(tr("Trace object"), commands));

                    OsmPrimitive sel = null;

                    if (object.isMultipolygon()) {
                        sel = ((EdMultipolygon)object).finalMultipolygon();
                    }
                    else if (object.isWay()) {
                        sel = ((EdWay)object).finalWay();
                    }
                    else {
                        sel = ((EdNode)object).finalNode();
                    }

                    if (m_shift) {
                        editor.getDataSet().addSelected(sel);
                    } else {
                        editor.getDataSet().setSelected(sel);
                    }
                    long end_time = System.nanoTime();
                    long time_msecs = (end_time - start_time) / (1000 * 1000);
                    System.out.println("undoRedo time (ms): " + Long.toString(time_msecs));
                }
            });
        }

        protected double getResurrectNodesDistanceMeters() {
            return resurrectNodesDistanceMeters;
        }

        protected abstract EdObject createTracedPolygonImpl(WayEditor editor);
        protected abstract TracerRecord downloadRecord(LatLon pos) throws Exception;
    }
}

