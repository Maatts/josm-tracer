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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
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
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

class ClassicModule implements TracerModule {

    private final long serialVersionUID = 1L;

    protected boolean cancel;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;
    private boolean moduleEnabled;
    private String source = "cuzk:km";
    private ConnectWays connectWays = new ConnectWays();
    protected ClassicServer server = new ClassicServer();


    public ClassicModule(boolean enabled) {
      moduleEnabled = enabled;
    }


    public void init() {

    }

    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-sml");
    }

    public String getName() {
        return tr("Classic");
    }

    public boolean moduleIsEnabled() {
      return moduleEnabled;
    };

    public void setModuleIsEnabled(boolean enabled){
      moduleEnabled = enabled;
    };

    private void tagBuilding(Way way) {
        if(!alt) way.put("building", "yes");
        way.put("source", source);
    }

    public void trace(LatLon pos, boolean ctrl, boolean alt, boolean shift, ProgressMonitor progressMonitor) {
        final Collection<Command> commands = new LinkedList<Command>();
        TracerPreferences pref = TracerPreferences.getInstance();

        String sUrl = "http://localhost:5050/";
        double dAdjX = 0, dAdjY = 0;

        if (pref.m_customTracerUrl)
          sUrl = pref.m_customTracerUrlText;

        if (pref.m_tracerAdjustPosition) {
          dAdjX = pref.m_tracerAdjustPositionLat;
          dAdjY = pref.m_tracerAdjustPositionLon;
        }

        progressMonitor.beginTask(null, 3);
        try {
              ArrayList<LatLon> coordList = server.trace(pos, sUrl, dAdjX, dAdjY);

            if (coordList.size() == 0) {
                return;
            }

            // make nodes a way
            List<Bounds> dsBounds = Main.main.getCurrentDataSet().getDataSourceBounds();
            Way way = new Way();
            Node firstNode = null;
            for (LatLon coord : coordList) {
                Node node = new Node(coord);
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
            way.addNode(firstNode);

            tagBuilding(way);

            // connect to other buildings or modify existing building
            commands.add(connectWays.connect(way, pos, ctrl, alt, source));

            if (!commands.isEmpty()) {
              final String strCommand;
              if (connectWays.isNewWay()) {
                strCommand = trn("Tracer: add a way with {0} point", "Tracer: add a way with {0} points", coordList.size(), coordList.size());
              } else {
                strCommand = trn("Tracer: modify way to {0} point", "Tracer: modify way to {0} points", coordList.size(), coordList.size());
              }
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  Main.main.undoRedo.add(new SequenceCommand(strCommand, commands));
                }
              } );


              TracerUtils.showNotification(strCommand, "info");

              if (shift) {
                Main.main.getCurrentDataSet().addSelected(connectWays.getWay());
              } else {
                Main.main.getCurrentDataSet().setSelected(connectWays.getWay());
              }
            } else {
                System.out.println("Failed");
            }

        } finally {
            progressMonitor.finishTask();
        }
    }
}

