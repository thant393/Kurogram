package tw.kurocore.kurogram.feature.stub;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tw.kurocore.kurogram.feature.SpecialContacts;

/** Safe no-op Special Contacts for builds without the private module. */
public final class SpecialContactsStub implements SpecialContacts {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
    }

    @Override
    public boolean isBackground() {
        return false;
    }

    @Override
    public void setBackground(boolean background) {
    }

    @Override
    public ArrayList<Long> contactIds() {
        return new ArrayList<>();
    }

    @Override
    public void saveContactIds(List<Long> ids) {
    }

    @Override
    public JSONObject settings(long userId) {
        return null;
    }

    @Override
    public void saveSettings(long userId, JSONObject settings) {
    }

    @Override
    public void removeSettings(long userId) {
    }

    @Override
    public ArrayList<Action> actions(long userId) {
        return new ArrayList<>();
    }

    @Override
    public String lastActionText(long userId) {
        return null;
    }
}
