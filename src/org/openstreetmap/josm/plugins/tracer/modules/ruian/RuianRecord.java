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

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.tools.Utils;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.util.*;
import java.lang.StringBuilder;

import org.openstreetmap.josm.plugins.tracer.TracerUtils.*;

/**
 * The Tracer RUIAN record class
 *
 */

public class RuianRecord {

    private double   m_coor_lat, m_coor_lon;
    private String   m_source;
    private long     m_ruian_id;
    private int      m_levels;
    private int      m_flats;
    private String   m_usage_code;
    private String   m_usage_key;
    private String   m_usage_val;
    private String   m_finished;
    private String   m_valid_from;

    private ArrayList <LatLon> m_geometry;
    private ArrayList <Address> m_address_places;

    /**
    * Constructor
    *
    */
    public void RuianRecord () {
      init();
    }

    /**
    * Initialization
    *
    */
    private void init () {

      m_coor_lat = 0;
      m_coor_lon = 0;
      m_source = "";
      m_ruian_id = 0;
      m_levels = 0;
      m_flats = 0;
      m_usage_code = "";
      m_usage_key = "";
      m_usage_val = "";
      m_finished = "";
      m_valid_from = "";
      m_geometry = new ArrayList<LatLon> ();
      m_address_places = new ArrayList<Address> ();

    }

    /**
    * Parse given JSON string and fill variables with RUIAN data
    *
    */
    public void parseJSON (String jsonStr) {


    init();

    long     m_adr_id = 0;
    String   m_house_number = "";
    String   m_house_number_typ = "";
    String   m_street_number = "";
    String   m_street = "";
    String   m_place = "";
    String   m_suburb = "";
    String   m_city = "";
    String   m_district = "";
    String   m_region = "";
    String   m_postcode = "";


    JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(jsonStr.getBytes()));
    JsonObject obj = jsonReader.readObject();
    jsonReader.close();

      try {
        JsonObject coorObjekt = obj.getJsonObject("coordinates");

        try {
          m_coor_lat = Double.parseDouble(coorObjekt.getString("lat"));
        } catch (Exception e) {
          System.out.println("coordinates.lat: " + e.getMessage());
        }

        try {
          m_coor_lon = Double.parseDouble(coorObjekt.getString("lon"));
        } catch (Exception e) {
          System.out.println("coordinates.lon: " + e.getMessage());
        }

        try {
          m_source = obj.getString("source");
        } catch (Exception e) {
          System.out.println("source: " + e.getMessage());
        }

      } catch (Exception e) {
        System.out.println("coordinates: " + e.getMessage());
      }

// =========================================================================
      try {
        JsonObject building = obj.getJsonObject("stavebni_objekt");

        try {
          m_ruian_id = Long.parseLong(building.getString("ruian_id"));
        } catch (Exception e) {
          System.out.println("stavebni_objekt.ruian_id: " + e.getMessage());
        }

        try {
          m_house_number = building.getString("cislo_domovni");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.cislo_domovni: " + e.getMessage());
        }

        try {
          m_house_number_typ = building.getString("cislo_domovni_typ");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.cislo_domovni_typ: " + e.getMessage());
        }

        try {
          m_street_number = building.getString("cislo_orientacni");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.cislo_orientacni: " + e.getMessage());
        }

        try {
          m_adr_id = Long.parseLong(building.getString("adresni_misto_kod"));
        } catch (Exception e) {
            System.out.println("stavebni_objek.tadresni_misto_kod: " + e.getMessage());
        }

        try {
          m_street = building.getString("ulice");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.ulice: " + e.getMessage());
        }

        try {
          m_place = building.getString("cast_obce");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.cast_obce: " + e.getMessage());
        }

        try {
          m_suburb = building.getString("mestska_cast");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.mestska_cast: " + e.getMessage());
        }

        try {
          m_city = building.getString("obec");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.obec: " + e.getMessage());
        }

        try {
          m_district = building.getString("okres");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.okres: " + e.getMessage());
        }

        try {
          m_region = building.getString("kraj");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.kraj: " + e.getMessage());
        }

        try {
          m_postcode = building.getString("psc");
        } catch (Exception e) {
            System.out.println("stavebni_objekt.psc: " + e.getMessage());
        }

        try {
          m_levels = Integer.parseInt(building.getString("pocet_podlazi"));
        } catch (Exception e) {
          System.out.println("stavebni_objekt.pocet_podlazi: " + e.getMessage());
        }

        try {
          m_flats = Integer.parseInt(building.getString("pocet_bytu"));
        } catch (Exception e) {
          System.out.println("stavebni_objekt.pocet_bytu: " + e.getMessage());
        }

        try {
          m_usage_code = building.getString("zpusob_vyuziti_kod");
        } catch (Exception e) {
          System.out.println("stavebni_objekt.m_objekt_zpusob_vyuziti_kod: " + e.getMessage());
        }

        try {
          m_usage_key = building.getString("zpusob_vyuziti_key");
        } catch (Exception e) {
          System.out.println("stavebni_objekt.zpusob_vyuziti_key: " + e.getMessage());
        }

        try {
          m_usage_val = building.getString("zpusob_vyuziti_val");
        } catch (Exception e) {
          System.out.println("stavebni_objekt.m_objekt_zpusob_vyuziti_val: " + e.getMessage());
        }

        try {
          m_finished = building.getString("plati_od");
        } catch (Exception e) {
          System.out.println("stavebni_objekt.plati_od: " + e.getMessage());
        }

        try {
          m_valid_from = building.getString("dokonceni");
        } catch (Exception e) {
          System.out.println("stavebni_objekt.dokonceni: " + e.getMessage());
        }

      } catch (Exception e) {
        System.out.println("stavebni_objekt: " + e.getMessage());
      }

// =========================================================================
      try {
        JsonArray arr = obj.getJsonArray("geometry");

        for(int i = 0; i < arr.size(); i++)
        {
          System.out.println("i="+i);
          JsonArray node = arr.getJsonArray(i);

          try {
            LatLon coor = new LatLon(
              LatLon.roundToOsmPrecision(node.getJsonNumber(1).doubleValue()),
              LatLon.roundToOsmPrecision(node.getJsonNumber(0).doubleValue())
            );
            System.out.println("coor: " + coor.toString());
            m_geometry.add(coor);
          } catch (Exception e) {
          }

        }
      } catch (Exception e) {
      }

// =========================================================================
      try {
        JsonArray arr = obj.getJsonArray("adresni_mista");

        for(int i = 0; i < arr.size(); i++)
        {
          JsonObject addrPlace = arr.getJsonObject(i);
          Address addr = new Address();

          try {
            m_adr_id = Long.parseLong(addrPlace.getString("ruian_id"));
          } catch (Exception e) {
            System.out.println("adresni_mista.ruian_id: " + e.getMessage());
          }

          try {
            m_house_number = addrPlace.getString("cislo_domovni");
          } catch (Exception e) {
            System.out.println("adresni_mista.cislo_domovni: " + e.getMessage());
          }

          try {
            m_street_number = addrPlace.getString("cislo_orientacni");
          } catch (Exception e) {
            System.out.println("adresni_mista.cislo_orientacni: " + e.getMessage());
          }

          try {
            m_street = addrPlace.getString("ulice");
          } catch (Exception e) {
            System.out.println("adresni_mista.ulice: " + e.getMessage());
          }

          addr.setRuianID(m_adr_id);

          if (m_house_number_typ.equals("číslo popisné"))
            addr.setConscriptionNumber(m_house_number);
          else if (m_house_number_typ.equals("číslo evidenční"))
            addr.setProvisionalNumber(m_house_number);

          addr.setStreetNumber(m_street_number);
          addr.setStreet(m_street);
          addr.setPlace(m_place);
          addr.setSuburb(m_suburb);
          addr.setCity(m_city);
          addr.setDistrict(m_district);
          addr.setRegion(m_region);
          addr.setCountryCode("CZ");
          addr.setPostCode(m_postcode);

          m_address_places.add(addr);
        }
      } catch (Exception e) {
      }

      if (m_address_places.size() == 0 &&
            m_house_number.length() > 0) {

        Address addr = new Address();
        addr.setRuianID(m_adr_id);

        if (m_house_number_typ.equals("číslo popisné"))
          addr.setConscriptionNumber(m_house_number);
        else if (m_house_number_typ.equals("číslo evidenční"))
          addr.setProvisionalNumber(m_house_number);

        addr.setStreetNumber(m_street_number);
        addr.setStreet(m_street);
        addr.setPlace(m_place);
        addr.setSuburb(m_suburb);
        addr.setCity(m_city);
        addr.setDistrict(m_district);
        addr.setRegion(m_region);
        addr.setCountryCode("CZ");
        addr.setPostCode(m_postcode);

        m_address_places.add(addr);
      }
    }

  /**
   *  Return number of nodes in the building
   *  @return Count of nodes in building
   */
  public int getCoorCount() {
    if (m_geometry == null)
      return 0;
    else
      return m_geometry.size();
  }

  /**
   *  Return coordinates of node
   *  @return geometry node coordinates
   */
  public LatLon getCoor(int i) {
    return m_geometry.get(i);
  }

  /**
   *  Return number of levels in the building
   *  @return Number of levels
   */
  public String getBuildingLevels() {
    if (m_levels > 0)
      return Integer.toString(m_levels);
    else
      return "";
  }

  /**
   *  Return number of flats in the building
   *  @return Number of flats
   */
  public String getBuildingFlats() {
    if (m_flats > 0)
      return Integer.toString(m_flats);
    else
      return "";
  }

  /**
   *  Return date of finish of building
   *  @return Date of finish
   */
  public String getBuildingFinished() {
    return TracerUtils.convertDate(m_finished);
  }

  /**
   *  Return RUIAN building usage code
   *  @return RUIAN building usage code
   */
  public String getBuildingUsageCode() {
    return m_usage_code;
  }

  /**
   *  Return key of building type
   *  @return Key of building type
   */
  public String getBuildingTagKey() {
    return m_usage_key;
  }

  /**
   *  Return type of building
   *  @return Type of building
   */
  public String getBuildingTagValue() {
    return m_usage_val;
  }

  /**
   *  Return type of building
   *  @return Type of building
   */
  public long getBuildingID() {
    return m_ruian_id;
  }

  /**
   *  Return data source
   *  @return Data source
   */
  public String getSource() {
    return m_source;
  }
}
