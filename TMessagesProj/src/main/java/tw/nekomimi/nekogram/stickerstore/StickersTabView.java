package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

public class StickersTabView extends FrameLayout {

    private final BaseFragment fragment;
    private final RecyclerListView listView;
    private final GridLayoutManager layoutManager;
    private final Adapter adapter;
    private final RadialProgressView progressView;
    private final TextView infoView;
    private final TextView sortPopular;
    private final FrameLayout header;
    private final TextView sortNewest;

    private final ArrayList<StickerPackInfo> packs = new ArrayList<>();
    /** What the grid actually shows: packs filtered by the header search query. */
    private final ArrayList<StickerPackInfo> shown = new ArrayList<>();
    private String searchQuery = "";
    private String currentSort = StickerStoreApi.SORT_NEWEST;
    private boolean loading;
    private int requestGeneration;

    // Paging: the store holds ~1000+ packs but one request returns at most PAGE_SIZE, so
    // more pages are fetched as the user scrolls near the end of what's loaded.
    private static final int PAGE_SIZE = 50;
    private static final int LOAD_MORE_THRESHOLD = 12; // items from the end
    private static final int HEADER_HEIGHT_DP = 56;
    private static final int HEADER_FADE_DP = 16; // extra height below the chips that the background fades out over
    private final HashSet<String> seenShortNames = new HashSet<>();
    private int nextPage = 2;
    private boolean hasMore;
    private boolean loadingMore;

    public StickersTabView(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        tw.nekomimi.nekogram.skin.KuroUi.applyPageBackground(this);

        layoutManager = new GridLayoutManager(context, 3);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager);
        // Top padding = the header's height, so the first row starts right below the header
        // instead of under it; clipToPadding(false) lets rows scroll up *beneath* it.
        listView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(HEADER_HEIGHT_DP), AndroidUtilities.dp(8), AndroidUtilities.dp(16));
        listView.setClipToPadding(false);
        // No list-level press selector: it is a square highlight over the whole cell slot that
        // bleeds past the rounded card; each card draws its own ripple instead.
        listView.setSelectorDrawableColor(0);
        adapter = new Adapter();
        listView.setAdapter(adapter);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < shown.size()) {
                openPack(shown.get(position));
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= shown.size() - LOAD_MORE_THRESHOLD) {
                    loadMore();
                }
            }
        });

        // Sticky header: added AFTER the list so it is drawn on top of it. Its background is a
        // translucent fade (strongest behind the chips, gone at the bottom edge) rather than
        // a flat strip, so it blends into the page and the grid scrolls "into" it softly.
        // The chips themselves stay solid.
        header = new FrameLayout(context);
        header.setBackground(KuroBarStyle.headerFade());
        addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEADER_HEIGHT_DP + HEADER_FADE_DP, Gravity.TOP));

        LinearLayout sortRow = new LinearLayout(context);
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(sortRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        sortPopular = createSortChip(context, "Popular");
        sortNewest = createSortChip(context, "Newest");
        sortRow.addView(sortPopular, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 6, 0));
        sortRow.addView(sortNewest, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        sortPopular.setOnClickListener(v -> setSort(StickerStoreApi.SORT_POPULAR));
        sortNewest.setOnClickListener(v -> setSort(StickerStoreApi.SORT_NEWEST));

        progressView = new RadialProgressView(context);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 28, 0, 0));

        infoView = new TextView(context);
        infoView.setTextSize(14);
        infoView.setGravity(Gravity.CENTER);
        infoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        addView(infoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 28, 0, 0));
        infoView.setVisibility(GONE);

        updateSortChips();
        if (!restoreCache()) {
            loadPacks();
        }
    }

    /** Last loaded list, so a rebuilt screen (skin change) keeps its grid instead of reloading. */
    private static ArrayList<StickerPackInfo> cachedPacks;
    private static int cachedNextPage;
    private static boolean cachedHasMore;
    private static String cachedSort;
    private static long cachedAt;

    private void saveCache() {
        cachedPacks = new ArrayList<>(packs);
        cachedNextPage = nextPage;
        cachedHasMore = hasMore;
        cachedSort = currentSort;
        cachedAt = System.currentTimeMillis();
    }

    private boolean restoreCache() {
        if (cachedPacks == null || cachedPacks.isEmpty() || System.currentTimeMillis() - cachedAt > 5 * 60 * 1000L) {
            return false;
        }
        currentSort = cachedSort;
        packs.clear();
        seenShortNames.clear();
        addNewPacks(cachedPacks);
        nextPage = cachedNextPage;
        hasMore = cachedHasMore;
        loading = false;
        progressView.setVisibility(GONE);
        updateSortChips();
        applyFilter();
        return true;
    }

    /** Room to leave at the bottom of the grid so the last row can scroll fully above the
     *  floating tab bar that sits over this view. */
    public void setBottomInset(int px) {
        listView.setPadding(listView.getPaddingLeft(), listView.getPaddingTop(), listView.getPaddingRight(), px);
    }

    private TextView createSortChip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        chip.setTypeface(AndroidUtilities.bold());
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(AndroidUtilities.dp(18));
        if (selected) {
            bg.setColor(tw.nekomimi.nekogram.skin.KuroSkinManager.get().accent());
            chip.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        } else {
            bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        chip.setBackground(bg);
    }

    /** Restyles this tab for the active skin without rebuilding it (the loaded packs and scroll position are kept). */
    public void applySkin() {
        tw.nekomimi.nekogram.skin.KuroUi.applyPageBackground(this);
        header.setBackground(KuroBarStyle.headerFade());
        updateSortChips();
        android.os.Parcelable state = layoutManager.onSaveInstanceState();
        listView.setAdapter(adapter); // new cells pick up the card colours / radius
        layoutManager.onRestoreInstanceState(state);
    }

    private void updateSortChips() {
        styleChip(sortPopular, StickerStoreApi.SORT_POPULAR.equals(currentSort));
        styleChip(sortNewest, StickerStoreApi.SORT_NEWEST.equals(currentSort));
    }

    private void setSort(String sort) {
        // Sort chips stay tappable even while a previous load is still in flight - tapping
        // again just supersedes it. loadPacks() resets the loading state for the new request.
        if (sort.equals(currentSort)) {
            return;
        }
        currentSort = sort;
        updateSortChips();
        loadPacks();
    }

    private void loadPacks() {
        if (!StickerStoreApi.isConfigured()) {
            loading = false;
            progressView.setVisibility(GONE);
            listView.setVisibility(GONE);
            infoView.setVisibility(VISIBLE);
            infoView.setText("Sticker store isn't configured for this build.");
            return;
        }
        loading = true;
        loadingMore = false;
        hasMore = false;
        final int generation = ++requestGeneration;
        progressView.setVisibility(VISIBLE);
        infoView.setVisibility(GONE);
        listView.setVisibility(GONE);
        StickerStoreApi.fetchPacks(null, currentSort, 1, PAGE_SIZE, new StickerStoreApi.PacksCallback() {
            @Override
            public void onSuccess(ArrayList<StickerPackInfo> result) {
                if (generation != requestGeneration) return; // superseded by a newer sort tap
                loading = false;
                progressView.setVisibility(GONE);
                packs.clear();
                seenShortNames.clear();
                int added = addNewPacks(result);
                nextPage = 2;
                // Keep paging while the server keeps handing us packs we haven't seen. (Not
                // "page smaller than PAGE_SIZE", since a server may cap a page below what we
                // asked for - and a server that ignores the page number just returns
                // duplicates, which stops the loop instead of repeating forever.)
                hasMore = added > 0;
                saveCache();
                applyFilter();
                continueLoadingForSearch();
            }

            @Override
            public void onError(String message) {
                if (generation != requestGeneration) return;
                loading = false;
                progressView.setVisibility(GONE);
                listView.setVisibility(GONE);
                infoView.setVisibility(VISIBLE);
                infoView.setText(message);
            }
        });
    }

    /** Adds packs not already in the list (by short_name); returns how many were new. */
    private int addNewPacks(ArrayList<StickerPackInfo> incoming) {
        int added = 0;
        for (StickerPackInfo info : incoming) {
            String key = info.shortName != null ? info.shortName : "";
            if (!key.isEmpty() && !seenShortNames.add(key)) {
                continue;
            }
            packs.add(info);
            added++;
        }
        return added;
    }

    private void loadMore() {
        if (loading || loadingMore || !hasMore || !StickerStoreApi.isConfigured()) {
            return;
        }
        loadingMore = true;
        final int generation = requestGeneration;
        StickerStoreApi.fetchPacks(null, currentSort, nextPage, PAGE_SIZE, new StickerStoreApi.PacksCallback() {
            @Override
            public void onSuccess(ArrayList<StickerPackInfo> result) {
                if (generation != requestGeneration) return; // sort changed meanwhile
                loadingMore = false;
                int added = addNewPacks(result);
                nextPage++;
                hasMore = added > 0;
                if (added > 0) {
                    saveCache();
                    applyFilter();
                }
                continueLoadingForSearch();
            }

            @Override
            public void onError(String message) {
                if (generation != requestGeneration) return;
                // Leave hasMore as is: the next scroll event simply tries this page again.
                loadingMore = false;
            }
        });
    }

    /** Called from the header search field on every keystroke. Matches the pack's display name
     *  (once Telegram has resolved it) and its short name, case-insensitively. */
    public void setSearchQuery(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.equals(searchQuery)) {
            return;
        }
        searchQuery = q;
        applyFilter();
        continueLoadingForSearch();
    }

    private boolean matches(StickerPackInfo pack) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        if (pack.shortName != null && pack.shortName.toLowerCase().contains(searchQuery)) {
            return true;
        }
        String title = pack.getDisplayTitle();
        if (title == null) {
            org.telegram.tgnet.TLRPC.TL_messages_stickerSet cached = StickerSetResolver.getCached(pack.shortName);
            if (cached != null && cached.set != null) {
                title = cached.set.title;
            }
        }
        return title != null && title.toLowerCase().contains(searchQuery);
    }

    private void applyFilter() {
        shown.clear();
        for (StickerPackInfo pack : packs) {
            if (matches(pack)) {
                shown.add(pack);
            }
        }
        adapter.notifyDataSetChanged();
        if (loading) {
            return;
        }
        if (shown.isEmpty()) {
            listView.setVisibility(GONE);
            infoView.setVisibility(VISIBLE);
            infoView.setText(searchQuery.isEmpty() ? "No sticker packs yet." : "No sticker packs match your search.");
        } else {
            infoView.setVisibility(GONE);
            listView.setVisibility(VISIBLE);
        }
    }

    /** While searching, keep pulling pages so matches beyond the first page can show up. */
    private void continueLoadingForSearch() {
        if (!searchQuery.isEmpty() && hasMore && !loading && !loadingMore) {
            loadMore();
        }
    }

    private void openPack(StickerPackInfo pack) {
        if (pack.shortName == null || pack.shortName.isEmpty() || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        // A real navigated-to screen (not a popup/bottom sheet) with our own design -
        // see StickerPackDetailActivity.
        fragment.presentFragment(new StickerPackDetailActivity(pack));
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public int getItemCount() {
            return shown.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            StickerPackGridCell cell = new StickerPackGridCell(parent.getContext());
            // Shorter than the old 2-column height - with 3 columns the cells are narrower,
            // so a shorter card keeps the icon/text proportions compact instead of boxy.
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(132)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((StickerPackGridCell) holder.itemView).setPack(shown.get(position), fragment.getCurrentAccount());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            lp.leftMargin = AndroidUtilities.dp(4);
            lp.rightMargin = AndroidUtilities.dp(4);
            lp.topMargin = AndroidUtilities.dp(4);
            lp.bottomMargin = AndroidUtilities.dp(4);
            holder.itemView.setLayoutParams(lp);
        }
    }
}
