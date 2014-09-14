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
package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import static org.openstreetmap.josm.tools.I18n.*;
import java.awt.Cursor;
import java.awt.Point;
import java.util.*;
import java.lang.StringBuilder;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.ConnectWays;

// import org.openstreetmap.josm.plugins.tracer.modules.lpis.LpisRecord;

import org.xml.sax.SAXException;

public class LpisModule implements TracerModule  {

    private final long serialVersionUID = 1L;

    protected boolean cancel;
    private boolean moduleEnabled;
    private String  source = "lpis";

    private StringBuilder msg = new StringBuilder();
    private ConnectWays connectWays = new ConnectWays();

    protected LpisServer server = new LpisServer();

    public LpisModule(boolean enabled) {
      moduleEnabled = enabled;
    }

    public void init() {
    }

    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-lpis-sml");
    }

    public String getName() {
        return tr("LPIS");
    }

    public boolean moduleIsEnabled() {
      return moduleEnabled;
    };

    public void setModuleIsEnabled(boolean enabled){
      moduleEnabled = enabled;
    };

    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new LpisTracerTask (pos, ctrl, alt, shift);
    }

    class LpisTracerTask extends PleaseWaitRunnable {

        private final LatLon m_pos;
        private final boolean m_ctrl;
        private final boolean m_alt;
        private final boolean m_shift;

        LpisTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
        }

        protected void realRun() throws SAXException {
            ProgressMonitor pm = progressMonitor.createSubTaskMonitor(ProgressMonitor.ALL_TICKS, false);
            final Collection<Command> commands = new LinkedList<Command>();
            TracerPreferences pref = TracerPreferences.getInstance();

            String sUrl = "http://eagri.cz/public/app/wms/plpis_wfs.fcgi";
            double dAdjX = 0, dAdjY = 0;

//         if (pref.m_customRuianUrl)
//           sUrl = pref.m_customRuianUrlText;
//
//         if (pref.m_ruianAdjustPosition) {
//           dAdjX = pref.m_ruianAdjustPositionLat;
//           dAdjY = pref.m_ruianAdjustPositionLon;
//         }

            System.out.println("");
            System.out.println("-----------------");
            System.out.println("----- Trace -----");
            System.out.println("-----------------");
            System.out.println("");
            pm.beginTask(null, 3);

            LpisRecord record;

            try {
                record = server.getElementBasicData(m_pos, sUrl);
                System.out.println("  LPIS ID: " + record.getLpisID());
                System.out.println("  LPIS usage: " + record.getUsage());
                if (record.getLpisID() == -1) {
                    TracerUtils.showNotification(tr("Data not available.")+ "\n(" + m_pos.toDisplayString() + ")", "warning");
                    return;
                }

                Way outer = new Way();
                Relation rel = new Relation();

                // Create Outer way
                List<Bounds> dsBounds = Main.main.getCurrentDataSet().getDataSourceBounds();
                Node firstNode = null;
                // record.getCoorCount() - 1 - ommit last node
                for (int i = 0; i < record.getOuter().size() - 1; i++) {
                    Node node;
//                 // Apply corrections to node coordinates
//                 if (!pref.m_ruianAdjustPosition) {
                    node = new Node(record.getOuter().get(i));
//                 } else {
//                   node = new Node(new LatLon(LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lat()+dAdjX),
//                                              LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lon()+dAdjY)));
//                 }
                    if (firstNode == null) {
                        firstNode = node;
                    }
                // Check. whether traced node is inside downloaded area
                    int insideCnt = 0;
                    for (Bounds b: dsBounds) {
                        if (b.contains(node.getCoor())) {
                            insideCnt++;
                        }
                    }
                    if (insideCnt == 0) {
                        ExtendedDialog ed = new ExtendedDialog(
                            Main.parent, tr("Way is outside downloaded area"),
                                new String[] {tr("Ok")});
                        ed.setButtonIcons(new String[] {"ok"});
                        ed.setIcon(JOptionPane.ERROR_MESSAGE);
                        ed.setContent(tr("Sorry.\nThe traced way (or part of the way) is outside of the downloaded area.\nPlease download area around the way and try again."));
                        ed.showDialog();
                        return;
                    }
                    commands.add(new AddCommand(node));
                    outer.addNode(node);
                }

                // Insert first node again - close the polygon.
                outer.addNode(firstNode);

                System.out.println("TracedWay: " + outer.toString());
                if (! record.hasInners()) {
                    tagOuterWay(record, outer);
                }

                commands.add(new AddCommand(outer));

                if (record.hasInners()) {
                    // Inners found - create multipolygon

                    rel.addMember(new RelationMember("outer", outer));

                    for (int i = 0; i < record.getInnersCount(); i++) {
                        ArrayList<LatLon> in = record.getInner(i);
                        Way inner = new Way();
                        firstNode = null;
                        for (int j = 0; j < in.size() -1 ; j++) {
                            Node node;
  //                        // Apply corrections to node coordinates
  //                        if (!pref.m_ruianAdjustPosition) {
                            node = new Node(in.get(j));
  //                        } else {
  //                            node = new Node(new LatLon(LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lat()+dAdjX),
  //                                              LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lon()+dAdjY)));
  //                        }
                            if (firstNode == null) {
                                firstNode = node;
                            }
                            commands.add(new AddCommand(node));
                            inner.addNode(node);
                        }

                        // Insert first node again - close the polygon.
                        inner.addNode(firstNode);

                        System.out.println("Traced Inner Way: " + inner.toString());

                        commands.add(new AddCommand(inner));
                        rel.addMember(new RelationMember("inner", inner));
                    }

                    // Add relation
                    tagMultipolygon(record, rel);
                    commands.add(new AddCommand(rel));
                }

                // connect to other landuse or modify existing landuse
                Command connCmd;
                if (record.hasInners()) {
                    connCmd = connectWays.connect(outer, rel, m_pos, m_ctrl, m_alt, source);
                } else {
                    connCmd = connectWays.connect(outer, m_pos, m_ctrl, m_alt, source);
                }

                String s[] = connCmd.getDescriptionText().split(": ");

                if (s[1].equals("Nothing")) {
                    TracerUtils.showNotification(tr("Nothing changed."), "info");

                    if (m_shift) {
                        Main.main.getCurrentDataSet().addSelected(record.hasInners()?rel:outer);
                    } else {
                        Main.main.getCurrentDataSet().setSelected(record.hasInners()?rel:outer);
                    }
                } else {
                    commands.add(connCmd);

                    if (!commands.isEmpty()) {
                        String msg = tr("Tracer(LPIS): add an area") + " \"" + record.getUsage() + "\"";
                        if (record.hasInners()) {
                            msg = msg + trn(" with {0} inner.", " with {0} inners.", record.getInnersCount(), record.getInnersCount());
                        } else {
                            msg = msg + ".";
                        }

                        TracerUtils.showNotification(msg, "info");
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                Main.main.undoRedo.add(new SequenceCommand(tr("Trace object"), commands));
                            }
                        });

                        if (m_shift) {
                            Main.main.getCurrentDataSet().addSelected(record.hasInners()?rel:connectWays.getWay());
                        } else {
                            Main.main.getCurrentDataSet().setSelected(record.hasInners()?rel:connectWays.getWay());
                        }
                    } else {
                        System.out.println("Failed");
                    }
                }
            } finally {
                pm.finishTask();
            }
        }

        protected void finish() {
        }

        protected void cancel() {
            // #### FIXME - resolve cancellation
        }

        private void tagMultipolygon (LpisRecord record, Relation rel) {
            Map <String, String> map = new HashMap <String, String> (record.getUsageOsm());
            map.put("type", "multipolygon");
            map.put("source", source);
            map.put("ref", Long.toString(record.getLpisID()));
            rel.setKeys(map);
        }

        private void tagOuterWay (LpisRecord record, Way way) {
            Map <String, String> map = new HashMap <String, String> (record.getUsageOsm());
            map.put("source", source);
            map.put("ref", Long.toString(record.getLpisID()));
            way.setKeys(map);
        }
    }
}

