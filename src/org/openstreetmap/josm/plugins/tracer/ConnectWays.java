/**
 *  Tracer - plugin for JOSM
 *  Dirk Bruenig, Marian Kyral
 *
 *  Improved version from Tracer2 plugin - Many thanks to Dirk Bruenig
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

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
// import org.openstreetmap.josm.plugins.tracer2.preferences.ServerParam;
import org.openstreetmap.josm.tools.Pair;

import org.openstreetmap.josm.plugins.tracer.TracerDebug;
public class ConnectWays {

    static double s_dDoubleDiff       = 0.0000001; // Maximal difference for double comparison
    static double s_dMinDistance      = 0.000006;  // Minimal distance, for objects
    static double s_dMinDistanceN2N   = 0.000001;  // Minimal distance, when nodes are merged
    static double s_dMinDistanceN2oW  = 0.000001;  // Minimal distance, when node is connected to other way
    static double s_dMinDistanceN2tW  = 0.000001;  // Minimal distance, when other node is connected this way

    static Way s_oWay; // New or updated way
    static Way s_oWayOld; // The original way
    static List<Way>  s_oWays; // List of all ways we will work with
    static List<Node> s_oNodes; // List of all nodes we will work with
    static List<Node> connectedNodes; // List of nodes that are also connected other way

    static List<Way>  secondaryWays; // List of ways connected to connected ways (and are not myWay)
    static List<Node> secondarydNodes; // List of nodes of ways connected to connected ways ;-)

    static List<Way>  s_overlapWays; // List of ways that overlap traced way (s_oWay)

//     static ServerParam s_oParam;
    static boolean s_bCtrl;
    static boolean s_bAlt;

    static int maxDebugLevel = 0;

    static boolean s_bAddNewWay;

    /**
     *  Print debug messages - default level is zero
     *  @param msg mesage
     */
    private static void debugMsg(String msg) {
      debugMsg(msg, 0);
    }

    private static void listWays() {

      debugMsg("");
      debugMsg("  --> List of ways: ");
      for (Way w : s_oWays) {
        debugMsg(new TracerDebug().FormatPrimitive(w.toString()));
        for(Map.Entry<String, String> entry : w.getKeys().entrySet()) {
          debugMsg("          " + entry.getKey() + " = " + entry.getValue());
        }
      }
    }

    /**
     *  Print debug messages
     *  @param msg mesage
     *  @param level debug level of the message - From 0 - important to up
     */
    private static void debugMsg(String msg, int level) {
      if (level <= maxDebugLevel) {
        System.out.println(msg);
      }
    }

    /**
     *  Replace oldWay by newWay in list of working ways
     *  @param oldWay way to be replaced
     *  @param newWay way to replace
     */
    private static void replaceWayInList(Way oldWay, Way newWay) {
      s_oWays.remove(oldWay);
      s_oWays.add(newWay);
    }

    /**
     *  Replace oldNode by newNode in list of working nodess
     *  @param oldNode way to be replaced
     *  @param newNode way to replace
     */
    private static void replaceNodeInList(Node oldNode, Node newNode) {
      s_oNodes.remove(oldNode);
      s_oNodes.add(newNode);
    }

    /**
     *  Calculate minimal distances
     */
    private static void calcDistance()
    {
//        double dTileSize = Double.parseDouble(s_oParam.getTileSize());
//        double dResolution = Double.parseDouble(s_oParam.getResolution());
//        double dMin = dTileSize / dResolution;

      debugMsg("-- calcDistance() --");
      double dMin = (double) 0.0004 / (double) 2048;

      s_dMinDistance = dMin * 30;
      s_dMinDistanceN2N = dMin * 2.5;
      s_dMinDistanceN2oW = dMin * 5;
      s_dMinDistanceN2tW = dMin * 5;
    }

    /**
     *  Get ways close to the way
     *  @param way Way
     */
    private static void getWays(Way way) {
      debugMsg("-- getWays() --");
      s_oWays = new LinkedList<Way>();
      BBox bbox = new BBox(way);
      bbox.addPrimitive(way,s_dMinDistance);

      for (Way w : Main.main.getCurrentDataSet().searchWays(bbox)) {
        if (w.isUsable()) {
          s_oWays.add(w);
        }
      }

      // Get secondary ways and secondary Nodes
      secondaryWays = new LinkedList<Way>();
      secondarydNodes = new LinkedList<Node>();
      for (Way xw : s_oWays) {
        bbox = new BBox(xw);
        bbox.addPrimitive(xw,s_dMinDistance);

        for (Way w : Main.main.getCurrentDataSet().searchWays(bbox)) {
          if (w.isUsable() && s_oWays.indexOf(w) < 0) {
            secondaryWays.add(w);
            for (Node n : w.getNodes()) {
              if (n.isUsable()) {
                secondarydNodes.add(n);
              }
            }
          }
        }
      }
    }

    /**
     *  Get nodes close to the way
     *  @param way Way
     */
    private static void getNodes(Way way) {
      debugMsg("-- getNodes() --");

      s_oNodes = new LinkedList<Node>();
      BBox bbox = new BBox(way);
      bbox.addPrimitive(way,s_dMinDistance);

      for (Node nd : Main.main.getCurrentDataSet().searchNodes(bbox)) {
        if (nd.isUsable()) {
          s_oNodes.add(nd);
        }
      }
    }

    /**
     *  Get ways connected to the Node
     *  @param node Node
     *  @return connected ways
     */
    private static List<Way> getWaysOfNode(Node node) {
      debugMsg("-- getWaysOfNode() --");
      debugMsg("   param: Node = " + node);

      List<Way> ways = new LinkedList<Way>();

      if (node.getDataSet() == null) {
        return ways;
      }

      for (Way way : s_oWays) {
        if (way.isUsable() && way.containsNode(node)) {
          debugMsg("    Use way:" + way);
          ways.add(way);
        }
      }
      debugMsg("<< end of getWaysOfNode()");
      return ways;
    }

    /**
     *  Get list of nodes shared with another way (connected node)
     *  @param way Way
     */
    private static void getConnectedNodes(Way way) {
      debugMsg("-- getConnectedNodes() --");
      if (way == null) {
        return;
      }

      connectedNodes = new LinkedList<Node>();

      for (int i = 0; i < way.getNodesCount(); i++) {
        if (getWaysOfNode(way.getNode(i)).size() > 1) {
          connectedNodes.add(way.getNode(i));
          debugMsg("   Connected node: " + way.getNode(i));
        }
      }
    }

    /**
     *  Calculate angle
     *  @param oP1 Position
     *  @param n   Node
     *  @return deltaAlpha
     */
    private static double calcAlpha(LatLon oP1, Node n) {
      debugMsg("-- calcAlpha() --", 1);
      LatLon oP2 = n.getCoor();

      double dAlpha = Math.atan((oP2.getY() - oP1.getY()) / (oP2.getX() - oP1.getX())) * 180 / Math.PI + (oP1.getX() > oP2.getX() ? 180 : 0);
      return checkAlpha(dAlpha);
    }

    /**
     *  Check angle
     *  @param dAlpha delta Alpha
     *  @return deltaAlpha
     */
    private static Double checkAlpha(Double dAlpha) {
      debugMsg("-- checkAlpha() --", 1);
        if (dAlpha > 180) {
            return dAlpha - 360;
        }
        if (dAlpha <= -180) {
            return dAlpha + 360;
        }
        return dAlpha;
    }

    /**
     *  Check whether point is inside the way
     *  @param pos position
     *  @param way way
     *  @return Return True when all way nodes are around the position (sum of angles to all way nodes is 360 deg)
     */
    private static boolean isNodeInsideWay(LatLon pos, Way way) {
      debugMsg("-- isNodeInsideWay() --");
      List<Node> listNode = way.getNodes();

      double dAlpha;
      double dAlphaOld = calcAlpha(pos, listNode.get(listNode.size()-1));
      double dSumAlpha = 0;

      for (Node n : listNode) {
        dAlpha = calcAlpha(pos, n);
        dSumAlpha += checkAlpha( dAlpha - dAlphaOld );
        dAlphaOld = dAlpha;
      }
      dSumAlpha = Math.abs(dSumAlpha);

      return dSumAlpha > 359 && dSumAlpha < 361;
    }

    /**
     *  Get list of segments of way closest to the given point
     *  @param pos node position
     *  @param way way
     *  @return Return list of way segments closest to the position
     */
    private static List<WaySegment> getClosestWaySegments(LatLon pos, Way way) {
      debugMsg("-- getClosestWaySegments() --");

      List<WaySegment> ws = new LinkedList<WaySegment>();

      double min_distance =999999.;
      for (int i = 0; i < way.getNodesCount()-1; i++) {
        double dst = Math.abs(distance(way.getNode(i).getCoor(), way.getNode(i+1).getCoor()) -
                               (distance(way.getNode(i).getCoor(), pos) + distance(pos, way.getNode(i+1).getCoor()))
                             );
        debugMsg("       First node  : " + way.getNode(i));
        debugMsg("       Second nodes: " + way.getNode(i+1));
        debugMsg("           distance: " + dst);
        if (dst < min_distance && Math.abs(dst - min_distance) > s_dDoubleDiff) {
          ws = new LinkedList<WaySegment>();
          ws.add(new WaySegment(way, i));
          min_distance = dst;
        } else if (Math.abs(dst - min_distance) < s_dDoubleDiff) {
          ws.add(new WaySegment(way, i));
        }
      }

      debugMsg("    Closest segments: ");
      for (WaySegment w : ws) {
        debugMsg("    " + w);
      }

      return ws;
    }

    /**
     *  Get already existing way
     *  @param pos position
     *  @return way
     */
    private static Way getOldWay(LatLon pos) {
      debugMsg("-- getOldWay() --");
      int i;

      for (i = 0; i < s_oWays.size(); i++) {
        Way way = s_oWays.get(i);
        if (!isSameTag(way)) {
          continue;
        }
        if (isNodeInsideWay(pos, way)) {
          s_oWays.remove(way);
          return way;
        }
      }
      return null;
    }

    /**
     *  Check whether ways are overlaped
     *    (some node of one way is inside other way)
     *  @param w1 first way
     *  @param w1 second way
     *  @return True when ways are overlaped
     */
    private static Boolean areWaysOverlaped(Way w1, Way w2) {

      debugMsg("-- areWaysOverlaped() --");
      debugMsg("    w1: " + w1.getId() + "; w2: " + w2.getId());
//       s_overlapWays = new LinkedList<Way>();
      for (int i = 0; i < w1.getNodesCount() - 1; i++) {
        Node n = w1.getNode(i);
        if (isNodeInsideWay(n.getCoor(), w2)) {
          debugMsg("    Found node: " + n.getId() + " inside way: " + w2.getId());
          return true;
        }
      }

      for (int i = 0; i < w2.getNodesCount() - 1; i++) {
        Node n = w2.getNode(i);
        if (isNodeInsideWay(n.getCoor(), w1)) {
          debugMsg("    Found node: " + n.getId() + " inside way: " + w1.getId());
          return true;
        }
      }

      debugMsg("    Ways are not overlaped");
      return false;
    }

    /**
     * Get distance between points
     * @param x First point
     * @param y Second point
     * @return Distance between points
     */
    private static double distance(LatLon x, LatLon y) {
      return Math.abs(Math.sqrt( (y.getX() - x.getX()) * (y.getX() - x.getX()) + (y.getY() - x.getY()) * (y.getY() - x.getY()) ) );
    }

    /**
     * Check whether point is on the line
     * @param p Point
     * @param x First point of line
     * @param y Second point of line
     * @return True when point is on line
     */
    private static boolean pointIsOnLine(LatLon p, LatLon x, LatLon y) {

      // Distance xy should equal to sum of distance xp and distance yp
      if (Math.abs((distance (x, y) - (distance (x, p) + distance (y, p)))) > s_dMinDistanceN2N) {
        return false;
      }
      return true;
    }

    /**
     * Check where there is some node on given position
     * @param p position
     * @return Existing node if found or null
     */
    private static Node getNodeOnPosition(LatLon pos) {
      for (Node n : s_oNodes ) {
        if (distance(n.getCoor(), pos) <= s_dMinDistanceN2N) {
          return n;
        }
      }

      return null;
    }

    /**
     * Return intersection node
     * @param ws1 way segment 1
     * @param ws2 way segment 2
     * @return Node with intersection coordinates
     */
    private static Node getIntersectionNode(WaySegment ws1, WaySegment ws2) {

      StraightLine oStraightLine1 = new StraightLine(
            new Point2D.Double(ws1.getFirstNode().getCoor().getX(), ws1.getFirstNode().getCoor().getY()),
            new Point2D.Double(ws1.getSecondNode().getCoor().getX(), ws1.getSecondNode().getCoor().getY()));

        StraightLine oStraightLine2 = new StraightLine(
            new Point2D.Double(ws2.getFirstNode().getCoor().getX(), ws2.getFirstNode().getCoor().getY()),
            new Point2D.Double(ws2.getSecondNode().getCoor().getX(), ws2.getSecondNode().getCoor().getY()));

        Point2D.Double oPoint = oStraightLine1.GetIntersectionPoint(oStraightLine2);

        return new Node(new LatLon(oPoint.getY(), oPoint.getX()));
    }

    /**
     * Check whether segments are overlaped
     * @param ws1 way segment 1
     * @param ws2 way segment 2
     * @return True when one segment overlap other one
     */
    private static boolean segmentOnSegment(WaySegment ws1, WaySegment ws2) {
      if (pointIsOnLine(ws2.getFirstNode().getCoor(),  ws1.getFirstNode().getCoor(), ws1.getSecondNode().getCoor()) ||
          pointIsOnLine(ws2.getSecondNode().getCoor(), ws1.getFirstNode().getCoor(), ws1.getSecondNode().getCoor())
         ) {
        return true;
      }

      return false;
    }

    /**
     * Correct overlaping of ways
     * @param way overlaped way
     * @return List of Commands.
     */
    private static List<Command> correctOverlaping(Way way) {

      debugMsg("-- correctOverlaping() --");
      debugMsg("    Overlaped way" + way);

      List<Command> cmds = new LinkedList<Command>();
      List<Command> cmds2 = new LinkedList<Command>();

      Way myWay = new Way(s_oWay);
      Way overlapWay = new Way(way);

      Node iNode;

      // Go through all myWay segments
      // Check an intersection with any way segment from
      // If intersection is found - incorporate the node into both ways
      // Remove all nodes from otherWay that are inside myWay
      // Incorporate all nodes from myWay that are inside otherWay to otherWay border

      // 1) Collect list of intersections
      debugMsg("    --> 1) Collect list of intersections");
      List<Node> intNodes = new LinkedList<Node>();
      for (int i = 0; i < s_oWay.getNodesCount()-1; i++) {
        WaySegment myWaySegment = new WaySegment(s_oWay, i);
        for (int j = 0; j < way.getNodesCount()-1; j++) {
          WaySegment overlapWaySegment = new WaySegment(way, j);
          if (! myWaySegment.intersects(overlapWaySegment) && !segmentOnSegment(myWaySegment, overlapWaySegment)) {
            continue;
          }

          // segments are intersected
          iNode = getIntersectionNode(myWaySegment, overlapWaySegment);
          debugMsg("    --------------------------------");
          debugMsg("    myWaySegment:      " + myWaySegment);
          debugMsg("                       " + myWaySegment.getFirstNode() + ", " + myWaySegment.getSecondNode());
          debugMsg("    overlapWaySegment: " + overlapWaySegment);
          debugMsg("                       " + overlapWaySegment.getFirstNode() + ", " + overlapWaySegment.getSecondNode());

          if (pointIsOnLine(iNode.getCoor(), myWaySegment.getFirstNode().getCoor(), myWaySegment.getSecondNode().getCoor())) {
            debugMsg("    Intersection node: " + iNode);

            Node existingNode = getNodeOnPosition(iNode.getCoor());
            if (existingNode != null) {
              // And existing node on intersection position found
              // Use it instead
              debugMsg("    Replaced by: " + existingNode);
              if (intNodes.indexOf(existingNode) == -1) {
                // move node to position of iNode
                cmds.add(new MoveCommand(existingNode,
                          (iNode.getEastNorth().getX() - existingNode.getEastNorth().getX()),
                          (iNode.getEastNorth().getY() - existingNode.getEastNorth().getY())
                          ));
                existingNode.setCoor(iNode.getCoor());
                intNodes.add(existingNode);
              }
            } else {
              // Add intersection point to the list for integration into ways
              if (intNodes.indexOf(iNode) == -1) {
                cmds.add(new AddCommand(iNode));
                intNodes.add(iNode);
                s_oNodes.add(iNode);
              }
            }
          } else {
            if (pointIsOnLine(overlapWaySegment.getFirstNode().getCoor(), myWaySegment.getFirstNode().getCoor(), myWaySegment.getSecondNode().getCoor())) {
              debugMsg("    Intersection node: " + overlapWaySegment.getFirstNode());
              // Add intersection to both ways
              if (intNodes.indexOf(overlapWaySegment.getFirstNode()) == -1) {
                intNodes.add(overlapWaySegment.getFirstNode());
              }
            }
            if (pointIsOnLine(overlapWaySegment.getSecondNode().getCoor(), myWaySegment.getFirstNode().getCoor(), myWaySegment.getSecondNode().getCoor())) {
              debugMsg("    Intersection node: " + overlapWaySegment.getSecondNode());
              // Add intersection to both ways
              if (intNodes.indexOf(overlapWaySegment.getSecondNode()) == -1) {
                intNodes.add(overlapWaySegment.getSecondNode());
              }
            }
          }
        }
      }

      // 2) Integrate intersection nodes into ways
      debugMsg("    --------------------------------");
      debugMsg("    --> 2) Integrate intersection nodes into ways");
      Way tmpWay;
      for (Node intNode : intNodes) {
        // my Way
        if (myWay.getNodes().indexOf(intNode) >= 0) {
          debugMsg("    Node is already in myWay: " + intNode);
        } else {
          tmpWay = new Way(myWay);
          for (int i = 0; i < tmpWay.getNodesCount()-1; i++) {
            if (pointIsOnLine(intNode.getCoor(), tmpWay.getNode(i).getCoor(), tmpWay.getNode(i+1).getCoor())) {
              debugMsg("    --myWay: ");
              debugMsg("      Add node       : "+ intNode);
              debugMsg("        between nodes: (" + i + ")" + tmpWay.getNode(i));
              debugMsg("                     : (" + (i+1) + ")"+ tmpWay.getNode(i+1));
              myWay.addNode((i + 1), intNode);
              break;
             }
          }
        }
        // overlap Way
        if (overlapWay.getNodes().indexOf(intNode) >= 0) {
          debugMsg("    Node is already in overlapWay: " + intNode);
//           overlapWay.removeNode(intNode);
        } else {
          tmpWay = new Way(overlapWay);
          for (int i = 0; i < tmpWay.getNodesCount()-1; i++) {
            if (pointIsOnLine(intNode.getCoor(), tmpWay.getNode(i).getCoor(), tmpWay.getNode(i+1).getCoor())) {
              debugMsg("    --overlapWay: ");
              debugMsg("      Add node       : "+ intNode);
              debugMsg("        between nodes: (" + i + ")" + tmpWay.getNode(i));
              debugMsg("                     : (" + (i+1) + ")"+ tmpWay.getNode(i+1));
              overlapWay.addNode((i + 1), intNode);
              break;
             }
          }
        }
      }

      // 3) Remove all nodes from otherWay that are inside myWay
      debugMsg("    --------------------------------");
      debugMsg("    --> 3) Remove all nodes from otherWay that are inside myWay");
      boolean wayWasClosed = overlapWay.isClosed();
      tmpWay = new Way(overlapWay);
      for (int k = 0; k < tmpWay.getNodesCount() -1 ; k++) {
        Node n = tmpWay.getNode(k);
        if (myWay.getNodes().indexOf(n) < 0 && isNodeInsideWay(n.getCoor(), myWay)) {
          debugMsg("      Remove node from way: " + n);
          overlapWay.removeNode(n);
          if (wayWasClosed && !overlapWay.isClosed() ) {
            // FirstLast node removed - close the way
            debugMsg("      Close way: " + n);
            overlapWay.addNode(overlapWay.getNodesCount() +1, overlapWay.getNode(0));
          }
          replaceWayInList(tmpWay, overlapWay);
          if (getWaysOfNode(n).size() == 0) {
              debugMsg("      Delete node: " + n);
              cmds2.add(new DeleteCommand(n));
              s_oNodes.remove(n);
          }
        }
      }

      // 4) Integrate all nodes of myWay into overlapWay
      debugMsg("    --------------------------------");
      debugMsg("    --> 4) Integrate all nodes of myWay into overlapWay");
      for (int i = 0; i <  myWay.getRealNodesCount(); i++) {
        Node myNode = myWay.getNode(i);
        debugMsg("    -1");
        if (overlapWay.getNodes().indexOf(myNode) >= 0) {
          // skip intersection nodes
          continue;
        }

        if (isNodeInsideWay(myNode.getCoor(), overlapWay)) {
        debugMsg("    -2");

          tmpWay = new Way();
          tmpWay.addNode(new Node(myWay.getBBox().getCenter()));
          tmpWay.addNode(myNode);
          WaySegment myWaySegment = new WaySegment(tmpWay, 0);
          boolean wayChanged = true;

          while (wayChanged) {
          debugMsg("    -3");
            wayChanged = false;
            for (int j = 0; j < overlapWay.getRealNodesCount()-1; j++) {
             debugMsg("    -4");
              WaySegment ows = new WaySegment(overlapWay, j);
              if (! myWaySegment.intersects(ows)) {
                continue;
              }

              if (overlapWay.getNodes().indexOf(myNode) >= 0) {
                continue;
              }

              debugMsg("       First node : " + ows.getFirstNode());
              debugMsg("       Second node: " + ows.getSecondNode());
              debugMsg("       Add node to way: " + myNode);
              // Add myNode to position of second node
              overlapWay.addNode(overlapWay.getNodes().indexOf(ows.getSecondNode()), myNode);
              wayChanged = true;
              break;
            }
          }
        }
      }

      debugMsg("    -- -- -- -- --");
      cmds.add(new ChangeCommand(s_oWayOld, myWay));
      cmds.add(new ChangeCommand(way, overlapWay));

      replaceWayInList(s_oWay, myWay);
      replaceWayInList(way, overlapWay);

      s_oWay = myWay;

      cmds.addAll(cmds2);
      return cmds;
    }

    /**
     * Merges two nodes
     * @param myNode Node to merge
     * @param otherNode Node that will replace myNode
     * @return List of Commands.
     */
    private static List<Command> mergeNodes(Node myNode, Node otherNode){
      debugMsg("-- mergeNodes() --");

      List<Command> cmds = new LinkedList<Command>();

      debugMsg("   myNode: " + myNode + ", otherNode: " + otherNode);
      debugMsg("   myWay: " + s_oWay);

      Way tmpWay = new Way(s_oWay);
      // myNode is not part of myWay? Should not happen
      int j = s_oWay.getNodes().indexOf(myNode);
      if (j < 0) {
        return cmds;
      }

      // move otherNode to position of myNode
      cmds.add(new MoveCommand(otherNode,
                (myNode.getEastNorth().getX() - otherNode.getEastNorth().getX()),
                (myNode.getEastNorth().getY() - otherNode.getEastNorth().getY())
                ));
      otherNode.setCoor(myNode.getCoor());

      s_oWay.addNode(j, otherNode);
      if (j == 0) {
          // first + last point
        s_oWay.addNode(s_oWay.getNodesCount(), otherNode);
      }

      s_oWay.removeNode(myNode);

      cmds.add(new ChangeCommand(tmpWay, s_oWay));

      replaceWayInList(tmpWay, s_oWay);

      if (getWaysOfNode(myNode).size() == 0) {
          debugMsg("    Delete node: " + myNode);
          cmds.add(new DeleteCommand(myNode));
          s_oNodes.remove(myNode);
      }

      debugMsg("   updated myWay: " + s_oWay);
      return cmds;
    }

    /**
     * Copy keys from old to new way
     * @param d_way Destination way
     * @param s_way Source way
     * @param newSource value for source key
     * @param otherNode Node that will replace myNode
     * @return destination way
     */
    private static Way updateKeys(Way d_way, Way s_way, String newSource) {
      debugMsg("-- updateKeys() --");

        d_way.put("source", newSource);

        // Building key
        if (s_way.hasKey("building")) {
          if (d_way.get("building").equals("yes")) {
            d_way.put("building", s_way.get("building"));
          }
        }

        // Building:levels key
        if (s_way.hasKey("building:levels") && !d_way.hasKey("building:levels")) {
            d_way.put("building:levels", s_way.get("building:levels"));
        }

        // Building:flats key
        if (s_way.hasKey("building:flats") && !d_way.hasKey("building:flats")) {
            d_way.put("building:flats", s_way.get("building:flats"));
        }

        // Start_date key
        if (s_way.hasKey("start_date") && !d_way.hasKey("start_date")) {
            d_way.put("start_date", s_way.get("start_date"));
        }

        // Ref:ruian:building key
        if (s_way.hasKey("ref:ruian:building")) {
            d_way.put("ref:ruian:building", s_way.get("ref:ruian:building"));
        }

        // Ref:ruian:building key
        if (s_way.hasKey("building:ruian:type")) {
            d_way.put("building:ruian:type", s_way.get("building:ruian:type"));
        }

        // Remove obsolete ref:ruian key
        if (s_way.hasKey("ref:ruian:building") && d_way.hasKey("ref:ruian")) {
            if (d_way.get("ref:ruian").equals(s_way.get("ref:ruian:building"))) {
            d_way.remove("ref:ruian");
            }
        }

        return d_way;
    }

// -----------------------------------------------------------------------------------------------------
    /**
     * Try connect way to other buildings.
     * @param way Way to connect.
     * @return Commands.
     */
    public static Command connect(Way newWay, LatLon pos, boolean ctrl, boolean alt, String source) {
        debugMsg("-- connect() --");

        LinkedList<Command> cmds = new LinkedList<Command>();
        LinkedList<Command> cmds2 = new LinkedList<Command>();
        LinkedList<Command> xcmds = new LinkedList<Command>();

        s_bCtrl = ctrl;
        s_bAlt = alt;

//         calcDistance();
        getNodes(newWay);
        getWays(newWay);


        s_oWayOld = getOldWay(pos);
//         getConnectedNodes(s_oWayOld);

        if (s_oWayOld == null) {
          s_bAddNewWay = true;
          cmds.add(new AddCommand(newWay));
          s_oWayOld = newWay;
          s_oWay = new Way( newWay );
        } else {
            s_bAddNewWay = false;
            /*
            * Compare ways
            * Do not continue when way is traced again.
            * Old and new ways are equal - have the same count
            * of nodes and all nodes are on the same place
            */
            int o, n, nodesCount, nodesFound;
            nodesFound = 0;
            nodesCount = newWay.getNodesCount();
            // 1) have the same numbers of nodes?
            if (newWay.getNodesCount() == s_oWayOld.getNodesCount()) {
              debugMsg("   Old and New ways have " + s_oWayOld.getNodesCount() + " nodes");
              // 2) All nodes have the same coordination
              outer: for (n = 0; n < nodesCount; n++) {
                Node newNode = newWay.getNode(n);
                debugMsg("    New.Node(" + n + ") = " + newNode.getCoor().toDisplayString());
                inner: for (o = 0; o < nodesCount; o++) {
                  Node oldNode = s_oWayOld.getNode(o);
                  debugMsg("     -> Old.Node(" + o + ") = " + oldNode.getCoor().toDisplayString());
                  if (oldNode.getCoor().equalsEpsilon(newNode.getCoor())) {
                    debugMsg("     Nodes: New(" + n + ") and Old(" + o + ") are equal.");
                    nodesFound += 1;
                    continue outer;
                  }
                }
              }

              debugMsg("   nodesCount = " + nodesCount + "; nodesFound = " + nodesFound);
              if (nodesCount == nodesFound) {
                debugMsg("   Ways are equal!");
                return new SequenceCommand("Nothing", null);
              }
            }

            // Ways are different - merging
            debugMsg("   Ways are NOT equal!");
            debugMsg("   -------------------");

            // Create a working copy of the oldWay
            Way tempWay = new Way(s_oWayOld);
            debugMsg("s_oWayOld: " + s_oWayOld);

            // Add New nodes
            for (int i = 0; i < newWay.getNodesCount(); i++) {
              tempWay.addNode(tempWay.getNodesCount(), newWay.getNode(i));
              s_oNodes.add(newWay.getNode(i));
            }

            // Remove Old nodes
            for (int i = 0; i < s_oWayOld.getNodesCount() - 1; i++) {
              tempWay.removeNode( s_oWayOld.getNode(i) );
            }

            replaceWayInList(s_oWayOld, tempWay);
            // Remove old nodes from list of working nodes list
            for (int i = 0; i < s_oWayOld.getNodesCount() - 1; i++) {
              Node nd = s_oWayOld.getNode(i);
              List<Way> ways = getWaysOfNode(nd);
              if (ways.size() == 0) {
                  debugMsg("    Delete node: " + nd);
                  cmds2.add(new DeleteCommand(nd));
                  s_oNodes.remove(nd);
              }

            }
            s_oWay = tempWay;
            s_oWay = updateKeys(s_oWay, newWay, source);
            replaceWayInList(s_oWayOld, s_oWay);
            debugMsg("updatedWay: " + s_oWay);
        }

        xcmds.add(new ChangeCommand(s_oWayOld, s_oWay));
        xcmds.addAll(cmds2);

        cmds.add(new SequenceCommand(tr("Trace"), xcmds));

        // Modify the way
        debugMsg("");
        debugMsg("-----------------------------------------");
        xcmds = new LinkedList<Command>(removeFullyCoveredWays());
        if (xcmds.size() > 0) {
          cmds.add(new SequenceCommand(tr("Remove Fully covered ways"), xcmds));
        }

        debugMsg("");
        debugMsg("-----------------------------------------");
        xcmds = new LinkedList<Command>(mergeWithExistingNodes());
        if (xcmds.size() > 0) {
          cmds.add(new SequenceCommand(tr("Connect to other ways"), xcmds));
        }

        debugMsg("");
        debugMsg("-----------------------------------------");
        xcmds = new LinkedList<Command>(fixOverlapedWays());
        if (xcmds.size() > 0) {
          cmds.add(new SequenceCommand(tr("Fix overlaped ways"), xcmds));
        }

        debugMsg("");
        debugMsg("-----------------------------------------");
        xcmds = new LinkedList<Command>(removeSpareNodes());
        if (xcmds.size() > 0) {
          cmds.add(new SequenceCommand(tr("Remove spare nodes"), xcmds));
        }

//         debugMsg("");
//         debugMsg("-----------------------------------------");
//         xcmds = new LinkedList<Command>(connectTo());
//         if (xcmds.size() > 0) {
//           cmds.add(new SequenceCommand(tr("Connect to other ways"), xcmds));
//         }

//         debugMsg("");
//         debugMsg("-----------------------------------------");
//         xcmds = new LinkedList<Command>();
//         xcmds.add(new ChangeCommand(s_oWayOld, trySplitWayByAnyNodes(s_oWay)));
//         if (xcmds.size() > 0) {
//           cmds.add(new SequenceCommand(tr("Connect close nodes"), xcmds));
//         }

//         listWays();
        debugMsg("-----------------------------------------");

//         new TracerDebug().OutputCommands(cmds);

        Command cmd = new SequenceCommand(tr("Trace object"), cmds);

        return cmd;
    }


    /**
     * Try connect way to other buildings.
     * @param way Way to connect.
     * @return Commands.
     */
    public static List<Command> connectTo() {
        debugMsg("-- connectTo() --");

        Map<Way, Way> modifiedWays = new HashMap<Way, Way>();
        LinkedList<Command> cmds = new LinkedList<Command>();

        Way way = new Way(s_oWay);

        for (int i = 0; i < way.getNodesCount() - 1; i++) {
            Node n = way.getNode(i);
            debugMsg("   Node: " + n);
            LatLon ll = n.getCoor();

            // Will merge with something else?
            double minDistanceSq = s_dMinDistanceN2N;
            Node nearestNode = null;
            for (Node nn : new LinkedList<Node>(s_oNodes)) {
              debugMsg("    Node: " + nn);
                if (!nn.isUsable() || way.containsNode(nn) || s_oWay.containsNode(nn) || !isInSameTag(nn)) {
                    debugMsg("    -> Continue");
                    continue;
                }
                double dist = nn.getCoor().distance(ll);
                debugMsg("    Dist: "+ dist+"; minDistanceSq: "+ minDistanceSq);
                if (dist <= minDistanceSq) {
                    minDistanceSq = dist;
                    nearestNode = nn;
                }
            }

            debugMsg("   Nearest: " + nearestNode + " distance: " + minDistanceSq);
            if (nearestNode == null) {
//                  cmds.addAll(tryConnectNodeToAnyWay(n, modifiedWays));
            } else {
                debugMsg("+add Node distance: " + minDistanceSq);
                cmds.addAll(mergeNodes(n, nearestNode));
            }
        }

        for (Map.Entry<Way, Way> e : modifiedWays.entrySet()) {
            cmds.add(new ChangeCommand(e.getKey(), e.getValue()));
        }

        List<Command> cmd = cmds;
        return cmd;
    }

    /**
     *  Check whether there is a building fully covered by traced building
     *  @return List of commands
     */
    private static  List<Command> mergeWithExistingNodes() {
      debugMsg("-- mergeWithExistingNodes() --");

      LinkedList<Command> cmds  = new LinkedList<Command>();
      LinkedList<Command> cmds2 = new LinkedList<Command>();
      List<Node> tmpNodesList   = new LinkedList<Node> (s_oNodes);
      List<Node> deletedNodes   = new LinkedList<Node> ();
      Way        tmpWay         = new Way(s_oWay);


      for (Node otherNode : tmpNodesList) {
        if (s_oWay.getNodes().indexOf(otherNode) >= 0) {
          continue;
        }

        for (int i = 0; i < tmpWay.getRealNodesCount(); i++) {
          Node myNode = tmpWay.getNode(i);
          if (deletedNodes.indexOf(myNode) < 0 && otherNode.getCoor().distance(myNode.getCoor()) <= s_dMinDistanceN2N) {
            debugMsg(    "Replace node: " + myNode + " by node: " + otherNode );
            int myNodeIndex = s_oWay.getNodes().indexOf(myNode);
            debugMsg(    "Node index: " + myNodeIndex);
            if (myNodeIndex >= 0) {
              s_oWay.addNode(myNodeIndex, otherNode);
              if (myNodeIndex == 0) {
                // First node - close the way
                s_oWay.addNode(s_oWay.getNodesCount(), otherNode);
              }
              s_oWay.removeNode(myNode);
              replaceWayInList(tmpWay, s_oWay);

              if (deletedNodes.indexOf(myNode) < 0 &&  getWaysOfNode(myNode).size() <= 1) {
                debugMsg("    Delete node: " + myNode);
                s_oNodes.remove(myNode);
                cmds2.add(new DeleteCommand(myNode));
                deletedNodes.add(myNode);
              }
            }
          }
        }
      }

      replaceWayInList(tmpWay, s_oWay);
      cmds.add(new ChangeCommand(s_oWayOld, s_oWay));
      cmds.addAll(cmds2);
      return cmds;
    }

    /**
     *  Check whether there is a building fully covered by traced building
     *  @return List of commands
     */
    private static  List<Command> removeFullyCoveredWays() {
      debugMsg("-- removeFullyCoveredWays() --");

      LinkedList<Command> cmds = new LinkedList<Command>();
      List<Way> tmpWaysList = new LinkedList<Way> (s_oWays);

      for (Way w : tmpWaysList) {
        if (!w.isUsable() || !isSameTag(w) || w.equals(s_oWay)) {
          continue;
        }

        Way tmpWay = new Way(w);
        if (isNodeInsideWay(w.getBBox().getCenter(), s_oWay)) {
          debugMsg("   Delete way: " + w);
          cmds.add(new DeleteCommand( w ));
          s_oWays.remove(w);
          // Remove old nodes from list of working nodes list
          for (int i = 0; i < tmpWay.getNodesCount() - 1; i++) {
            Node nd = tmpWay.getNode(i);
            if (!nd.isUsable()) {
              continue;
            }

            if ( getWaysOfNode(nd).size() == 0) {
                debugMsg("    Delete node: " + nd);
                cmds.add(new DeleteCommand(nd));
                s_oNodes.remove(nd);
            }
          }
        }
      }
      return cmds;
    }

    /**
     *  Check all nodes of the given way and merge them with close nodes
     *  @param way
     *  @return List of commands
     */
    private static  List<Command>  fixOverlapedWays () {
      debugMsg("-- fixOverlapedWays() --");

      LinkedList<Command> cmds = new LinkedList<Command>();

      for (Way w : new LinkedList<Way>(s_oWays)) {
        if (!w.equals(s_oWay) && isSameTag(w) && areWaysOverlaped(s_oWay, w)) {
          cmds.addAll(correctOverlaping(w));
        }
      }

      debugMsg("    s_oWay: fixOverlapedWays(): " + new TracerDebug().FormatPrimitive(s_oWay.toString()));

      return cmds;
    }

    /**
     *  Remove spare nodes - nodes on straight line that are not needed anymore
     *  @return List of commands
     */
    private static  List<Command>  removeSpareNodes () {
      debugMsg("-- removeSpareNodes() --");

      LinkedList<Command> cmds  = new LinkedList<Command>();
      LinkedList<Command> cmds2 = new LinkedList<Command>();
      Way        tmpWay         = new Way(s_oWay);

      for (Way w : new LinkedList<Way>(s_oWays)) {
        if (!w.equals(s_oWay) && isSameTag(w)) {
          Way origWay = new Way(w);
          cmds2 = new LinkedList<Command>();
          boolean wayChanged = true;

          while (wayChanged) {
            for (int i = 0; i < w.getRealNodesCount(); i++) {
              Node middleNode = w.getNode(i);
              wayChanged = false;

              if (getWaysOfNode(middleNode).size() == 1 && secondarydNodes.indexOf(middleNode) < 0) {
                Node prevNode = w.getNode(i == 0 ? w.getRealNodesCount() -1 : i - 1);
                Node nextNode = w.getNode(i == w.getNodesCount() ? 1 : i + 1);

                if (pointIsOnLine(middleNode.getCoor(), prevNode.getCoor(), nextNode.getCoor())) {
                  debugMsg("    Delete Spare node: " + middleNode);
                  wayChanged = true;
                  w.removeNode(middleNode);
                  if (i == 0) {
                    // Close the way again
                    w.addNode(w.getNodesCount(), w.getNode(0));
                  }

                  replaceWayInList(origWay, w);
                  if (getWaysOfNode(middleNode).size() == 0) {
                    cmds2.add(new DeleteCommand(middleNode));
                    s_oNodes.remove(middleNode);
                  }
                  break;
                }
              }
            }
          }
          if (origWay.getNodesCount() != w.getNodesCount()) {
            cmds.add(new ChangeCommand(origWay, w));
            cmds.addAll(cmds2);
          }
        }
      }

      return cmds;
    }

    /**
     * Try connect node "node" to ways of other buildings.
     *
     * Zkusi zjistit, zda node neni tak blizko nejake usecky existujici budovy,
     * ze by mel byt zacnenen do teto usecky. Pokud ano, provede to.
     *
     * @param node Node to connect.
     * @param m map of ways.
     * @throws IllegalStateException
     * @throws IndexOutOfBoundsException
     * @return List of Commands.
     */
  private static List<Command>  tryConnectNodeToAnyWay(Node node, Map<Way, Way> m)
            throws IllegalStateException, IndexOutOfBoundsException {

        debugMsg("-- tryConnectNodeToAnyWay() --");

        LatLon ll = node.getCoor();
        List<Command> cmds = new LinkedList<Command>();

        // node nebyl slouceny s jinym
        // hledani pripadne blizke usecky, kam bod pridat
        double minDist = Double.MAX_VALUE;
        Way nearestWay = null;
        int nearestNodeIndex = 0;
        for (Way ww : s_oWays) {
            if (!ww.isUsable() || ww.containsNode(node) || !isSameTag(ww)) {
                continue;
            }

            if (m.get(ww) != null) {
                ww = m.get(ww);
            }

            for (Pair<Node, Node> np : ww.getNodePairs(false)) {
                //double dist1 = TracerGeometry.distanceFromSegment(ll, np.a.getCoor(), np.b.getCoor());
                double dist = distanceFromSegment2(ll, np.a.getCoor(), np.b.getCoor());
                //debugMsg(" distance: " + dist1 + "  " + dist);

                if (dist < minDist) {
                    minDist = dist;
                    nearestWay = ww;
                    nearestNodeIndex = ww.getNodes().indexOf(np.a);
                }
            }
        }
        debugMsg("   Nearest way: " + nearestWay + " distance: " + minDist);
        if (minDist < s_dMinDistanceN2oW) {
            Way newNWay = new Way(nearestWay);

            boolean duplicateNodeFound = false;
            for ( int i = 0; i < newNWay.getNodesCount(); i++) {
              if (newNWay.getNode(i).getCoor().distance(node.getCoor()) <= s_dMinDistanceN2N ) {
                // Do not put duplicated node, merge nodes instead
                cmds.addAll(mergeNodes(node, newNWay.getNode(i)));
                duplicateNodeFound = true;
              }
            }

            if (!duplicateNodeFound) {
              newNWay.addNode(nearestNodeIndex + 1, node);
            }

            debugMsg("   New way:" + newNWay);
            debugMsg("   +add WayOld.Node distance: " + minDist);
            m.put(nearestWay, newNWay);
            replaceWayInList(newNWay, nearestWay);
            debugMsg("   Updated nearest way: " + nearestWay);
            debugMsg("   =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        }
      return cmds;
    }

    private static double distanceFromSegment2(LatLon c, LatLon a, LatLon b) {
      debugMsg("-- distanceFromSegment2() --");

      double x;
      double y;

        StraightLine oStraightLine1 = new StraightLine(
            new Point2D.Double(a.getX(),a.getY()),
            new Point2D.Double(b.getX(),b.getY()));
        StraightLine oStraightLine2 = new StraightLine(
            new Point2D.Double(c.getX(),c.getY()),
            new Point2D.Double(c.getX() + (a.getY()-b.getY()),c.getY() - (a.getX()-b.getX())));
        Point2D.Double oPoint = oStraightLine1.GetIntersectionPoint(oStraightLine2);

        if ((oPoint.x > a.getX() && oPoint.x > b.getX()) || (oPoint.x < a.getX() && oPoint.x < b.getX()) ||
            (oPoint.y > a.getY() && oPoint.y > b.getY()) || (oPoint.y < a.getY() && oPoint.y < b.getY())) {
          return 100000;
        }

        x=c.getX()-oPoint.getX();
        y=c.getY()-oPoint.getY();

        return Math.sqrt((x*x)+(y*y));
    }

    /**
     * Try split way by any existing building nodes.
     *
     * Zkusi zjistit zda nejake usecka z way by nemela prochazet nejakym existujicim bodem,
     * ktery je ji velmi blizko. Pokud ano, tak puvodni usecku rozdeli na dve tak, aby
     * prochazela takovym bodem.
     *
     * @param way Way to split.
     * @throws IndexOutOfBoundsException
     * @throws IllegalStateException
     * @return Modified way
     */
    private static Way trySplitWayByAnyNodes(Way way)
            throws IndexOutOfBoundsException, IllegalStateException {

        debugMsg("-- trySplitWayByAnyNodes() --");
        // projdi kazdou novou usecku a zjisti, zda by nemela vest pres existujici body
        int i = 0;
        while (i < way.getNodesCount()) {
            // usecka n1, n2
            LatLon n1 = way.getNodes().get(i).getCoor();
            LatLon n2 = way.getNodes().get((i + 1) % way.getNodesCount()).getCoor();
            debugMsg(way.getNodes().get(i) + "-----" + way.getNodes().get((i + 1) % way.getNodesCount()));
            double minDistanceSq = Double.MAX_VALUE;


            Node nearestNode = null;
            for (Node nod : s_oNodes) {
                if (!nod.isUsable() || way.containsNode(nod) || !isInSameTag(nod)) {
                    continue;
                }
                LatLon nn = nod.getCoor();
                //double dist = TracerGeometry.distanceFromSegment(nn, n1, n2);
                double dist = distanceFromSegment2(nn, n1, n2);
//                double angle = TracerGeometry.angleOfLines(n1, nn, nn, n2);
                //debugMsg("Angle: " + angle + " distance: " + dist + " Node: " + nod);
                if (!n1.equalsEpsilon(nn) && !n2.equalsEpsilon(nn) && dist < minDistanceSq){ // && Math.abs(angle) < maxAngle) {
                  minDistanceSq = dist;
//                  maxAngle = angle;
                    nearestNode = nod;
                }
            }
            debugMsg("   Nearest: " + nearestNode + " distance: " + minDistanceSq);
            if (nearestNode == null || minDistanceSq >= s_dMinDistanceN2tW) {
                // tato usecka se nerozdeli
                i++;
                debugMsg("");
                continue;
            } else {
                // rozdeleni usecky
                way.addNode(i + 1, nearestNode);
                i++;
                debugMsg("   +add Way.Node distance: " + minDistanceSq);
                debugMsg("");
                //i++;
                continue; // i nezvetsuji, treba bude treba rozdelit usecku znovu
            }
        }
        return way;
    }

    /**
     * Determines if the specified node is a part of a building.
     * @param n The node to be tested
     * @return True if building key is set and different from no,entrance
     */
    private static boolean isInSameTag(Node n) {
        debugMsg("-- isInSameTag() --");
        for (OsmPrimitive op : n.getReferrers()) {
            if (op instanceof Way) {
                if (isSameTag((Way) op)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines if the specified primitive denotes a building.
     * @param p The primitive to be tested
     * @return True if building key is set and different from no,entrance
     */
    protected static final boolean isSameTag(Way w) {
      debugMsg("-- isSameTag() --");
      return (w.getKeys().get("building") == null ? false : !w.getKeys().get("building").equals("no") && !w.getKeys().get("building").equals("entrance"));
    }

}
