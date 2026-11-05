package tw.kurocore.kurogram.feature.stub;

import java.util.ArrayList;

import tw.kurocore.kurogram.feature.ContactChanges;

/** Safe no-op Contact Changes for builds without the private module. */
public final class ContactChangesStub implements ContactChanges {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ArrayList<Entry> log() {
        return new ArrayList<>();
    }

    @Override
    public void clearLog() {
    }

    @Override
    public boolean checkForChanges(int account) {
        return false;
    }
}
