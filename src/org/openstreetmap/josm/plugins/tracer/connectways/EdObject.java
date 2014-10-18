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

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import java.util.Collections;


public abstract class EdObject {
    private Object m_refs;
    private final WayEditor m_editor;
    private final OsmPrimitive m_original;
    private boolean m_modified;
    private boolean m_deleted;
    private boolean m_finalized;

    protected EdObject (WayEditor editor, OsmPrimitive original) {
        m_refs = null;
        m_editor = editor;
        m_original = original;
        m_modified = false;
        m_deleted = false;
        m_finalized = false;
    }

    public WayEditor getEditor() {
        return m_editor;
    }

    protected void setModified() {
        m_modified = true;
    }

    protected void setFinalized() {
        m_finalized = true;
    }

    public boolean isFinalized() {
        return m_finalized;
    }

    protected void setDeleted() {
        m_deleted = true;
    }

    public boolean isDeleted() {
        return m_deleted;
    }

    public boolean isModified() {
        return m_modified;
    }

    public void addRef(EdObject ref) {
        if (m_refs == null) {
            m_refs = ref;
            return;
        }
        if (m_refs instanceof EdObject) {
            if (m_refs != ref)
                m_refs = new EdObject[] { (EdObject)m_refs, ref };
            return;
        }
        for (EdObject r: (EdObject[]) m_refs) {
            if (r == ref)
                return;
        }

        EdObject[] refs = (EdObject[]) m_refs;
        EdObject[] nrefs = new EdObject[refs.length + 1];
        System.arraycopy(refs, 0, nrefs, 0, refs.length);
        nrefs[refs.length] = ref;
        m_refs = nrefs;
    }

    public void removeRef(EdObject ref) {
        if (m_refs == null) {
            return;
        }
        if (m_refs instanceof EdObject) {
            if (m_refs == ref)
                m_refs = null;
            return;
        }
        EdObject[] refs = (EdObject[])m_refs;

        int idx = -1;
        for (int i = 0; i < refs.length; i++) {
            if (refs[i] == ref) {
                idx = i;
                break;
            }
        }

        if (idx == -1)
            return;

        if (refs.length == 2) {
            m_refs = refs[1-idx];
            return;
        }

        EdObject[] nrefs = new EdObject[refs.length - 1];
        System.arraycopy(refs, 0, nrefs, 0, idx);
        System.arraycopy(refs, idx + 1, nrefs, idx, nrefs.length - idx);
        m_refs = nrefs;
    }

    protected OsmPrimitive originalPrimitive() {
        return m_original;
    }

    protected abstract OsmPrimitive currentPrimitive();

    protected boolean hasEditorReferrers() {
        return m_refs != null;
    }

    public boolean hasOriginal() {
        return m_original != null;
    }

    protected void checkEditable() {
        checkNotFinalized();
        checkNotDeleted();
    }

    protected void checkNotFinalized() {
        if (isFinalized())
            throw new IllegalStateException(tr("EdObject is finalized"));
    }

    protected void checkNotDeleted() {
        if (isDeleted())
            throw new IllegalStateException(tr("EdObject is deleted"));
    }

    public boolean matches(Match m) {
        checkNotDeleted();
        return m.match(currentPrimitive());
    }

    public boolean isTagged() {
        checkNotDeleted();
        return currentPrimitive().isTagged();
    }

    public <T extends EdObject> List<T> getEditorReferrers(Class<T> type) {
        if (m_refs == null)
            return Collections.emptyList();
        if (m_refs instanceof EdObject) {
            if (!type.isInstance(m_refs))
                return Collections.emptyList();

            List<T> result = new ArrayList<T>(1);
            result.add(type.cast(m_refs));
            return result;
        }
        
        EdObject[] refs = (EdObject[])m_refs;
        List<T> result = new ArrayList<T> ();
        for (EdObject obj: refs) {
            if (type.isInstance(obj)) {
                result.add(type.cast(obj));
            }
        }
        return result;
    }

    public boolean isReferredBy(EdObject other) {
        if (m_refs == null)
            return false;
        if (m_refs instanceof EdObject)
            return ((EdObject)m_refs) == other;
        
        EdObject[] refs = (EdObject[])m_refs;
        for (EdObject obj: refs) {
            if (obj == other)
                return true;
        }
        return false;
    }

    public <T extends OsmPrimitive> List<T> getExternalReferrers(Class<T> type) {

        if (!hasOriginal())
            return Collections.emptyList();
            
        List<OsmPrimitive> parents = m_original.getReferrers();
        List<T> result = new ArrayList<T> ();
        for (OsmPrimitive parent: parents) {
            if (!type.isInstance(parent))
                continue;
            if (getEditor().isEdited(parent))
                continue;
            result.add(type.cast(parent));
        }
        return result;
    }

    public boolean hasExternalReferrers () {
        if (!hasOriginal())
            return false;

        List<OsmPrimitive> parents = m_original.getReferrers();
        for (OsmPrimitive parent: parents) {
            if (!getEditor().isEdited(parent))
                return true;
        }
        return false;
    }    
}


