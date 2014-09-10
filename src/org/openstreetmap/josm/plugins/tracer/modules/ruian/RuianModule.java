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
package org.openstreetmap.josm.plugins.tracer;

import static org.openstreetmap.josm.tools.I18n.*;
import java.awt.Cursor;
import java.awt.Point;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;

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
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;


import org.xml.sax.SAXException;

class RuianModule implements TracerModule {

    private final long serialVersionUID = 1L;

    protected boolean cancel;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;
    private boolean moduleEnabled;
    private String source = "cuzk:ruian";
    private RuianRecord record;

    private ConnectWays connectWays = new ConnectWays();
    protected RuianServer server = new RuianServer();

    public RuianModule(boolean enabled) {
      moduleEnabled = enabled;
    }

    public void init() {

    }

    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-ruian-sml");
    }

    public String getName() {
        return tr("RUIAN");
    }

    public boolean moduleIsEnabled() {
      return moduleEnabled;
    };

    public void setModuleIsEnabled(boolean enabled){
      moduleEnabled = enabled;
    };

    public void trace(LatLon pos, boolean ctrl, boolean alt, boolean shift, ProgressMonitor progressMonitor) {
        final Collection<Command> commands = new LinkedList<Command>();
        TracerPreferences pref = TracerPreferences.getInstance();

        String sUrl = "http://josm.poloha.net";
        double dAdjX = 0, dAdjY = 0;

        if (pref.m_customRuianUrl)
          sUrl = pref.m_customRuianUrlText;

        if (pref.m_ruianAdjustPosition) {
          dAdjX = pref.m_ruianAdjustPositionLat;
          dAdjY = pref.m_ruianAdjustPositionLon;
        }

        System.out.println("");
        System.out.println("-----------------");
        System.out.println("----- Trace -----");
        System.out.println("-----------------");
        System.out.println("");
        progressMonitor.beginTask(null, 3);
        try {
              record = server.trace(pos, sUrl);

              if (record.getCoorCount() == 0) {
                  TracerUtils.showNotification(tr("Data not available.")+ "\n(" + pos.toDisplayString() + ")", "warning");
                  return;
            }

            // make nodes a way
            List<Bounds> dsBounds = Main.main.getCurrentDataSet().getDataSourceBounds();
            Way way = new Way();
            Node firstNode = null;
            // record.getCoorCount() - 1 - ommit last node
            for (int i = 0; i < record.getCoorCount() - 1; i++) {
                Node node;
                // Apply corrections to node coordinates
                if (!pref.m_ruianAdjustPosition) {
                  node = new Node(record.getCoor(i));
                } else {
                  node = new Node(new LatLon(LatLon.roundToOsmPrecision(record.getCoor(i).lat()+dAdjX),
                                             LatLon.roundToOsmPrecision(record.getCoor(i).lon()+dAdjY)));
                }
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
                way.addNode(node);
            }
            // Insert first node again - close the polygon.
            way.addNode(firstNode);

            System.out.println("TracedWay: " + way.toString());
            tagBuilding(way);
            // connect to other buildings or modify existing building
            Command connCmd = connectWays.connect(way, pos, ctrl, alt, source);

            String s[] = connCmd.getDescriptionText().split(": ");

            if (s[1].equals("Nothing")) {
              TracerUtils.showNotification(tr("Nothing changed."), "info");
              if (shift) {
                Main.main.getCurrentDataSet().addSelected(connectWays.getWay());
              } else {
                Main.main.getCurrentDataSet().setSelected(connectWays.getWay());
              }
            } else {
                commands.add(connCmd);

                if (!commands.isEmpty()) {
                  final String strCommand;
                  if (connectWays.isNewWay()) {
                    strCommand = trn("Tracer(RUIAN): add a way with {0} point", "Tracer(RUIAN): add a way with {0} points", connectWays.getWay().getRealNodesCount(), connectWays.getWay().getRealNodesCount());
                  } else {
                    strCommand = trn("Tracer(RUIAN): modify way to {0} point", "Tracer(RUIAN): modify way to {0} points", connectWays.getWay().getRealNodesCount(), connectWays.getWay().getRealNodesCount());
                  }

                  SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                      Main.main.undoRedo.add(new SequenceCommand(strCommand, commands));
                    }
                  } );

                  if (shift) {
                    Main.main.getCurrentDataSet().addSelected(connectWays.getWay());
                  } else {
                    Main.main.getCurrentDataSet().setSelected(connectWays.getWay());
                  }
                } else {
                    System.out.println("Failed");
                }
            }
        } finally {
            progressMonitor.finishTask();
        }
    }

// ---------------------------------------------------------------------------
    private void tagBuilding(Way way) {
        if(!alt) {
          if ( record.getBuildingTagKey().equals("building") &&
               record.getBuildingTagValue().length() > 0) {
            way.put("building", record.getBuildingTagValue());
          }
          else {
            way.put("building", "yes");
          }
        }

        if (record.getBuildingID() > 0 ) {
          way.put("ref:ruian:building", Long.toString(record.getBuildingID()));
        }

        if (record.getBuildingUsageCode().length() > 0) {
          way.put("building:ruian:type", record.getBuildingUsageCode());
        }

        if (record.getBuildingLevels().length() > 0) {
          way.put("building:levels", record.getBuildingLevels());
        }

        if (record.getBuildingFlats().length() > 0) {
          way.put("building:flats", record.getBuildingFlats());
        }

        if (record.getBuildingFinished().length() > 0) {
          way.put("start_date", record.getBuildingFinished());
        }

        if (record.getSource().length() > 0) {
          way.put("source", record.getSource());
        }
        else {
          way.put("source", source);
        }
    }
}

