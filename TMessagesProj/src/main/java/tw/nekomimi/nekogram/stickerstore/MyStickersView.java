package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;

/** The sticker packs this user has uploaded to the store, as a grid of the same cards the
 *  store uses. Loaded from the backend filtered by owner id, paged as the user scrolls. */
public class MyStickersView extends FrameLayout {

    private static final int PAGE_SIZE = 50;
    private static final int LOAD_MORE_THRESHOLD = 9;

    private final BaseFragment fragment;
    private final String ownerId;
    private final RecyclerListView listView;
    private final GridLayoutManager layoutManager;
    private final RadialProgressView progressView;
    private final TextView infoView;
    private final ArrayList<StickerPackInfo> packs = new ArrayList<>();      // uploaded to the store
    private final ArrayList<StickerPackInfo> display = new ArrayList<>();    // custom (not uploaded yet) + packs
    private HashSet<String> mySets;
    private tw.nekomimi.nekogram.skin.SkinSpec appliedSkin = tw.nekomimi.nekogram.skin.KuroSkinManager.get().spec();
    private final HashSet<StickerPackInfo> custom = new HashSet<>();
    private final HashSet<String> seen = new HashSet<>();
    private final RecyclerListView.SelectionAdapter adapter;
    private int nextPage = 1;
    private boolean loading;
    private boolean hasMore = true;
    private int generation;

    public MyStickersView(Context context, BaseFragment fragment, long ownerTelegramId) {
        super(context);
        this.fragment = fragment;
        this.ownerId = String.valueOf(ownerTelegramId);

        layoutManager = new GridLayoutManager(context, tw.nekomimi.nekogram.skin.KuroSkinManager.get().spec().gridColumns);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager);
        listView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(16));
        listView.setClipToPadding(false);
        listView.setSelectorDrawableColor(0); // each card draws its own rounded ripple
        adapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public int getItemCount() {
                return display.size();
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                StickerPackGridCell cell = new StickerPackGridCell(parent.getContext()).setLarge(true);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
                lp.setMargins(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
                cell.setLayoutParams(lp);
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((StickerPackGridCell) holder.itemView).setPack(display.get(position), fragment.getCurrentAccount());
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
                int m = AndroidUtilities.dp(4);
                lp.setMargins(m, m, m, m);
                holder.itemView.setLayoutParams(lp);
            }
        };
        listView.setAdapter(adapter);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < display.size() && fragment.getParentActivity() != null) {
                StickerPackInfo info = display.get(position);
                if (custom.contains(info)) {
                    // Made by this user on Telegram but not in the store yet: one tap to upload it.
                    fragment.presentFragment(new UploadHubActivity(info.shortName));
                } else {
                    fragment.presentFragment(new StickerPackDetailActivity(info));
                }
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= display.size() - LOAD_MORE_THRESHOLD) {
                    loadNextPage();
                }
            }
        });

        progressView = new RadialProgressView(context);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        infoView = new TextView(context);
        infoView.setTextSize(14);
        infoView.setGravity(Gravity.CENTER);
        infoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        infoView.setVisibility(GONE);
        addView(infoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        reload();
    }

    /** Space for the floating tab bar / button at the bottom of the grid. */
    public void setBottomInset(int px) {
        listView.setPadding(listView.getPaddingLeft(), listView.getPaddingTop(), listView.getPaddingRight(), px);
    }

    /** Applies the active skin (column count, card style) and rebuilds the cells. */
    public void applySkin() {
        tw.nekomimi.nekogram.skin.SkinSpec skin = tw.nekomimi.nekogram.skin.KuroSkinManager.get().spec();
        if (skin == appliedSkin) {
            return; // nothing changed since the cells were last built
        }
        appliedSkin = skin;
        layoutManager.setSpanCount(skin.gridColumns);
        listView.setAdapter(adapter); // new cells pick up the radius / style
    }

    /** Starts over from page 1 (also used after the user uploads a new pack). */
    public void reload() {
        generation++;
        packs.clear();
        display.clear();
        custom.clear();
        seen.clear();
        nextPage = 1;
        hasMore = true;
        loading = false;
        adapter.notifyDataSetChanged();
        progressView.setVisibility(VISIBLE);
        infoView.setVisibility(GONE);
        loadNextPage();
        loadMine();
    }

    /** Asks Telegram which sets this account created, so ones not yet in the store can be offered. */
    private void loadMine() {
        final int account = fragment.getCurrentAccount();
        if (!String.valueOf(org.telegram.messenger.UserConfig.getInstance(account).getClientUserId()).equals(ownerId)) {
            return;
        }
        final int gen = generation;
        OwnerCheck.fetchMine(account, new OwnerCheck.Callback() {
            @Override
            public void onResult(HashSet<String> shortNames, HashSet<Long> setIds) {
                if (gen != generation) return;
                mySets = shortNames;
                rebuildDisplay();
                adapter.notifyDataSetChanged();
                if (!display.isEmpty()) {
                    progressView.setVisibility(GONE);
                    infoView.setVisibility(GONE);
                    listView.setVisibility(VISIBLE);
                }
            }

            @Override
            public void onError() {
            }
        });
    }

    private void loadNextPage() {
        if (loading || !hasMore) {
            return;
        }
        if (!StickerStoreApi.isConfigured()) {
            showInfo("Sticker store isn't configured for this build.");
            return;
        }
        loading = true;
        final int gen = generation;
        StickerStoreApi.fetchPacks(null, StickerStoreApi.SORT_NEWEST, nextPage, PAGE_SIZE, ownerId, new StickerStoreApi.PacksCallback() {
            @Override
            public void onSuccess(ArrayList<StickerPackInfo> result) {
                if (gen != generation) return;
                loading = false;
                int added = 0;
                for (StickerPackInfo info : result) {
                    if (info.shortName != null && seen.add(info.shortName)) {
                        packs.add(info);
                        added++;
                    }
                }
                nextPage++;
                hasMore = added > 0 && result.size() >= PAGE_SIZE;
                progressView.setVisibility(GONE);
                rebuildDisplay();
                adapter.notifyDataSetChanged();
                if (display.isEmpty()) {
                    showInfo("You haven't uploaded any sticker packs yet.\nTap + to add one.");
                } else {
                    infoView.setVisibility(GONE);
                    listView.setVisibility(VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                if (gen != generation) return;
                loading = false;
                progressView.setVisibility(GONE);
                rebuildDisplay();
                adapter.notifyDataSetChanged();
                if (display.isEmpty()) {
                    showInfo(message);
                } else {
                    progressView.setVisibility(GONE);
                }
            }
        });
    }

    /** Packs Telegram lists as created by this user that are not in the store yet, followed by
     *  the packs already uploaded. */
    private void rebuildDisplay() {
        display.clear();
        custom.clear();
        final int account = fragment.getCurrentAccount();
        final long me = org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
        // Un-uploaded packs are only listed on the user's own profile.
        if (!String.valueOf(me).equals(ownerId)) {
            display.addAll(packs);
            return;
        }
        // mySets: short names Telegram says this account created (null until it has answered).
        if (mySets != null) {
            for (String name : mySets) {
                if (seen.contains(name) || seenLower(name)) {
                    continue;
                }
                StickerPackInfo info = new StickerPackInfo();
                info.shortName = name;
                custom.add(info);
                display.add(info);
            }
        }
        display.addAll(packs);
    }

    private boolean seenLower(String lower) {
        for (String s : seen) {
            if (s.equalsIgnoreCase(lower)) {
                return true;
            }
        }
        return false;
    }

    private void showInfo(String text) {
        progressView.setVisibility(GONE);
        listView.setVisibility(GONE);
        infoView.setVisibility(VISIBLE);
        infoView.setText(text);
    }
}
