package tw.kurocore.kurogram.stickerstore;

import tw.kurocore.kurogram.feature.FeatureUnavailableView;
import tw.kurocore.kurogram.feature.KuroFeatures;
import tw.kurocore.kurogram.feature.ThemeStore;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.view.View;
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

/** Grid of store themes: everyone's (ownerId == 0) or one owner's. Searches by title on the server. */
public class ThemesTabView extends FrameLayout {

    private static final int PAGE_SIZE = 30;
    private static final int LOAD_MORE_THRESHOLD = 8;

    private final BaseFragment fragment;
    private final long ownerId;
    private final String emptyText;
    private final RecyclerListView listView;
    private final GridLayoutManager layoutManager;
    private final RecyclerListView.SelectionAdapter adapter;
    private final SkeletonView progressView;
    private final TextView infoView;
    private FeatureUnavailableView unavailableView;
    private final ArrayList<ThemeInfo> themes = new ArrayList<>();
    private final HashSet<String> seen = new HashSet<>();
    private String query = "";
    private int nextPage = 1;
    private boolean loading;
    private boolean hasMore = true;
    private int generation;
    private final Runnable searchRunnable = this::reload;
    private boolean inset;
    private boolean ownerMode; // the signed-in user's own page: a "create" tile first, a menu on every card, stats
    private Runnable onCreate;
    private StatsListener statsListener;
    // sort + category filters (only on the store's own grid, not on a profile's list)
    private static final int HEADER_DP = 56;
    private String sort = "popular";
    private String category;
    private android.view.View header;
    private TextView sortPopular, sortNewest;
    private android.widget.LinearLayout categoryRow;
    private final ArrayList<String> categoryNames = new ArrayList<>();
    private static ArrayList<String> cachedCategories;

    /** What was last loaded, kept so a rebuilt screen (skin change) shows its grid at once instead of reloading. */
    private static final class Cache {
        final ArrayList<ThemeInfo> themes;
        final int nextPage;
        final boolean hasMore;
        final long at = System.currentTimeMillis();

        Cache(ArrayList<ThemeInfo> themes, int nextPage, boolean hasMore) {
            this.themes = new ArrayList<>(themes);
            this.nextPage = nextPage;
            this.hasMore = hasMore;
        }
    }

    private static final java.util.HashMap<Long, Cache> CACHE = new java.util.HashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private void saveCache() {
        if (query.isEmpty() && "popular".equals(sort) && category == null) {
            CACHE.put(ownerId, new Cache(themes, nextPage, hasMore));
        }
    }

    private boolean restoreCache() {
        Cache c = CACHE.get(ownerId);
        if (c == null || System.currentTimeMillis() - c.at > CACHE_TTL_MS || c.themes.isEmpty()) {
            return false;
        }
        themes.clear();
        seen.clear();
        themes.addAll(c.themes);
        for (ThemeInfo t : themes) {
            seen.add(t.id);
        }
        nextPage = c.nextPage;
        hasMore = c.hasMore;
        progressView.setVisibility(GONE);
        infoView.setVisibility(GONE);
        listView.setVisibility(VISIBLE);
        adapter.notifyDataSetChanged();
        fireStats();
        return true;
    }

    /** Totals of all the creator's themes (the list keeps loading pages in owner mode), for the creator dashboard. */
    public interface StatsListener {
        void onStats(int themes, long downloads, long likes);
    }

    /** Turns this list into the creator's own: the first tile starts a new theme, each card gets an options menu. */
    public void setOwnerMode(Runnable create, StatsListener stats) {
        ownerMode = create != null;
        onCreate = create;
        statsListener = stats;
        adapter.notifyDataSetChanged();
        if (ownerMode && themes.isEmpty() && infoView.getVisibility() == GONE) {
            listView.setVisibility(VISIBLE);
        }
        fireStats();
    }

    private void fireStats() {
        if (statsListener == null) {
            return;
        }
        long d = 0, l = 0;
        for (ThemeInfo t : themes) {
            d += t.downloads;
            l += t.likes;
        }
        statsListener.onStats(themes.size(), d, l);
    }

    private int offset() {
        return ownerMode ? 1 : 0;
    }

    /** Cards on the profile's surface card need a different fill from the card itself. */
    public void setInset(boolean inset) {
        this.inset = inset;
        listView.setAdapter(adapter);
    }

    /** The store page keeps room for its chip row (layout themeStore.chips = off removes it); a creator's page never has one. */
    private int topPaddingDp() {
        return ownerId == 0 && StoreLook.chipsOn() ? HEADER_DP : 6;
    }

    /** Re-reads the layout keys that change the frame of the grid (columns, chip row). */
    private void applyStoreLayout() {
        int columns = StoreLook.fitColumns(Math.max(0, listView.getWidth() - listView.getPaddingLeft() - listView.getPaddingRight()), StoreLook.columns(), 170);
        if (layoutManager.getSpanCount() != columns) {
            layoutManager.setSpanCount(columns);
        }
        boolean wantHeader = ownerId == 0 && StoreLook.chipsOn();
        if (wantHeader && header == null) {
            buildHeader(getContext());
        } else if (!wantHeader && header != null) {
            removeView(header);
            header = null;
            categoryRow = null;
            sortPopular = null;
            sortNewest = null;
        }
        listView.setPadding(listView.getPaddingLeft(), AndroidUtilities.dp(topPaddingDp()), listView.getPaddingRight(), listView.getPaddingBottom());
    }

    /** Rebuilds the cards with the active skin's colours and radii (data is kept). */
    public void applySkin() {
        applyStoreLayout();
        if (header != null) {
            header.setBackground(KuroBarStyle.headerFade());
            updateChips();
        }
        // New cells pick up the colours / radius; keep the scroll position across the swap.
        android.os.Parcelable state = layoutManager.onSaveInstanceState();
        listView.setAdapter(adapter);
        layoutManager.onRestoreInstanceState(state);
    }

    public ThemesTabView(Context context, BaseFragment fragment, long ownerId, String emptyText) {
        super(context);
        this.fragment = fragment;
        this.ownerId = ownerId;
        this.emptyText = emptyText;

        layoutManager = new GridLayoutManager(context, StoreLook.columns());
        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager);
        StoreLook.autoSpan(listView, layoutManager, StoreLook::columns, 170, null);
        listView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(topPaddingDp()), AndroidUtilities.dp(10), AndroidUtilities.dp(16));
        listView.setClipToPadding(false);
        listView.setSelectorDrawableColor(0);
        adapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public int getItemCount() {
                return themes.size() + offset();
            }

            @Override
            public int getItemViewType(int position) {
                return ownerMode && position == 0 ? 1 : 0;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                if (viewType == 1) {
                    View tile = createTile(parent.getContext());
                    RecyclerView.LayoutParams tlp = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(250));
                    int tm = AndroidUtilities.dp(StoreLook.cardGapDp() / 2f);
                    tlp.setMargins(tm, tm, tm, tm);
                    tile.setLayoutParams(tlp);
                    return new RecyclerListView.Holder(tile);
                }
                ThemeCardView card = new ThemeCardView(parent.getContext()).setInset(inset);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
                int m = AndroidUtilities.dp(StoreLook.cardGapDp() / 2f);
                lp.setMargins(m, m, m, m);
                card.setLayoutParams(lp);
                return new RecyclerListView.Holder(card);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == 1) {
                    return;
                }
                final ThemeInfo info = themes.get(position - offset());
                ThemeCardView card = (ThemeCardView) holder.itemView;
                card.setTheme(info);
                card.setOwnerMenu(ownerMode ? anchor -> showOwnerMenu(info, anchor) : null);
            }
        };
        listView.setAdapter(adapter);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (ownerMode && position == 0) {
                if (onCreate != null) {
                    onCreate.run();
                }
                return;
            }
            int at = position - offset();
            if (at >= 0 && at < themes.size() && fragment.getParentActivity() != null) {
                fragment.presentFragment(new ThemeDetailActivity(themes.get(at)));
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= themes.size() - LOAD_MORE_THRESHOLD) {
                    loadNextPage();
                }
            }
        });

        if (ownerId == 0 && StoreLook.chipsOn()) {
            buildHeader(context);
        }

        progressView = new SkeletonView(context, StoreLook.columns(), 1.25f, true);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, ownerId == 0 && StoreLook.chipsOn() ? 56 : 0, 0, 0));
        infoView = new TextView(context);
        infoView.setTextSize(14);
        infoView.setGravity(Gravity.CENTER);
        infoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        infoView.setVisibility(GONE);
        addView(infoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        if (!restoreCache()) {
            reload();
        }
    }

    private void buildHeader(Context context) {
        android.widget.FrameLayout h = new android.widget.FrameLayout(context);
        h.setBackground(KuroBarStyle.headerFade());
        header = h;
        addView(h, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEADER_DP + 16, Gravity.TOP));

        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        categoryRow = new android.widget.LinearLayout(context);
        categoryRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        categoryRow.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        scroll.addView(categoryRow, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        h.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP, 0, 8, 0, 0));
        sortPopular = chip(context, "Popular");
        sortNewest = chip(context, "Newest");
        sortPopular.setOnClickListener(v -> setSort("popular"));
        sortNewest.setOnClickListener(v -> setSort("newest"));
        rebuildCategoryChips();

        if (cachedCategories != null) {
            categoryNames.addAll(cachedCategories);
            rebuildCategoryChips();
        } else {
            KuroFeatures.themes().fetchCategories(names -> {
                cachedCategories = names;
                categoryNames.clear();
                categoryNames.addAll(names);
                rebuildCategoryChips();
            });
        }
    }

    private TextView chip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        chip.setTypeface(AndroidUtilities.bold());
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(tw.kurocore.kurogram.skin.KuroUi.floatingRadiusDp(18)));
        if (selected) {
            bg.setColor(tw.kurocore.kurogram.skin.KuroUi.accent());
            chip.setTextColor(tw.kurocore.kurogram.skin.KuroUi.onAccent());
        } else {
            bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        chip.setBackground(bg);
    }

    private void updateChips() {
        if (sortPopular == null) return;
        styleChip(sortPopular, "popular".equals(sort));
        styleChip(sortNewest, "newest".equals(sort));
        for (int i = 0; i < categoryRow.getChildCount(); i++) {
            View v = categoryRow.getChildAt(i);
            if (!(v instanceof TextView) || v == sortPopular || v == sortNewest) continue;
            TextView c = (TextView) v;
            Object tag = c.getTag();
            styleChip(c, tag == null ? category == null : tag.equals(category));
        }
    }

    private void rebuildCategoryChips() {
        if (categoryRow == null || sortPopular == null) return;
        categoryRow.removeAllViews();
        categoryRow.addView(sortPopular, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 6, 0));
        categoryRow.addView(sortNewest, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 6, 0));
        if (!categoryNames.isEmpty()) {
            TextView all = chip(getContext(), "All");
            all.setOnClickListener(v -> setCategory(null));
            categoryRow.addView(all, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 6, 0));
            for (String name : categoryNames) {
                TextView c = chip(getContext(), name);
                c.setTag(name);
                c.setOnClickListener(v -> setCategory(name));
                categoryRow.addView(c, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 6, 0));
            }
        }
        updateChips();
    }

    private void setSort(String value) {
        if (value.equals(sort)) return;
        sort = value;
        updateChips();
        reload();
    }

    private void setCategory(String value) {
        if ((value == null && category == null) || (value != null && value.equals(category))) return;
        category = value;
        updateChips();
        reload();
    }

    public void setBottomInset(int px) {
        listView.setPadding(listView.getPaddingLeft(), listView.getPaddingTop(), listView.getPaddingRight(), px);
    }

    public void setSearchQuery(String q) {
        String clean = q == null ? "" : q.trim();
        if (clean.equals(query)) {
            return;
        }
        query = clean;
        removeCallbacks(searchRunnable);
        postDelayed(searchRunnable, 350);
    }

    public void reload() {
        generation++;
        themes.clear();
        seen.clear();
        nextPage = 1;
        hasMore = true;
        loading = false;
        adapter.notifyDataSetChanged();
        infoView.setVisibility(GONE);
        if (!KuroFeatures.themes().isAvailable()) {
            progressView.setVisibility(GONE);
            if (unavailableView == null) {
                unavailableView = new FeatureUnavailableView(getContext());
                addView(unavailableView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            return;
        }
        progressView.setVisibility(VISIBLE);
        loadNextPage();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(searchRunnable);
    }

    private void loadNextPage() {
        if (loading || !hasMore) {
            return;
        }
        loading = true;
        final int gen = generation;
        KuroFeatures.themes().fetchThemes(ownerId == 0 ? sort : ThemeStoreApiSort.NEWEST, nextPage, PAGE_SIZE, ownerId > 0 ? String.valueOf(ownerId) : null, query, category, new ThemeStore.ListCallback() {
            @Override
            public void onSuccess(ArrayList<ThemeInfo> result) {
                if (gen != generation) return;
                loading = false;
                int added = 0;
                for (ThemeInfo t : result) {
                    if (t.id != null && seen.add(t.id)) {
                        themes.add(t);
                        added++;
                    }
                }
                nextPage++;
                hasMore = added > 0 && result.size() >= PAGE_SIZE;
                saveCache();
                progressView.setVisibility(GONE);
                adapter.notifyDataSetChanged();
                fireStats();
                if (ownerMode && hasMore) {
                    loadNextPage(); // a creator's own list is small: load it all so the dashboard totals are complete
                }
                if (themes.isEmpty() && !ownerMode) {
                    infoView.setText(query.isEmpty() && category == null ? emptyText : category != null && query.isEmpty() ? "No themes in this category yet" : "No themes match \"" + query + "\"");
                    infoView.setVisibility(VISIBLE);
                    listView.setVisibility(INVISIBLE);
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
                if (themes.isEmpty() && !ownerMode) {
                    infoView.setText(message);
                    infoView.setVisibility(VISIBLE);
                } else if (themes.isEmpty()) {
                    android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /** The pinned first tile of the creator's own grid. */
    private View createTile(Context context) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        int accent = tw.kurocore.kurogram.skin.KuroUi.accent();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(StoreLook.cardRadiusDp()));
        bg.setColor(0x14000000 | (accent & 0xFFFFFF));
        bg.setStroke(AndroidUtilities.dp(1.5f), accent, AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        tile.setBackground(bg);
        TextView plus = new TextView(context);
        plus.setText("+");
        plus.setTextSize(34);
        plus.setTextColor(accent);
        plus.setGravity(Gravity.CENTER);
        tile.addView(plus, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        TextView label = new TextView(context);
        label.setText("Create New Theme");
        label.setTextSize(14);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(accent);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 4, 8, 0));
        return tile;
    }

    // ------------------------------------------------------------------ the owner's menu: edit, duplicate, export, delete

    private void showOwnerMenu(final ThemeInfo info, View anchor) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        android.widget.PopupMenu pm = new android.widget.PopupMenu(fragment.getParentActivity(), anchor);
        pm.getMenu().add(0, 1, 0, "Edit");
        pm.getMenu().add(0, 2, 1, "Duplicate");
        pm.getMenu().add(0, 3, 2, "Export JSON");
        pm.getMenu().add(0, 4, 3, "Delete");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    fragment.presentFragment(new ThemeUploadActivity(info));
                    return true;
                case 2:
                    fragment.presentFragment(new ThemeUploadActivity(info, true));
                    return true;
                case 3:
                    exportTheme(info);
                    return true;
                case 4:
                    confirmDelete(info);
                    return true;
                default:
                    return false;
            }
        });
        pm.show();
    }

    private void exportTheme(final ThemeInfo info) {
        KuroFeatures.themes().fetchVersionContent(info.id, info.version, content -> {
            if (fragment.getParentActivity() == null) {
                return;
            }
            if (content == null) {
                android.widget.Toast.makeText(getContext(), "Could not load the theme.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                java.io.File dir = new java.io.File(org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(), "cache");
                dir.mkdirs();
                String base = info.slug != null && !info.slug.isEmpty() ? info.slug : "theme";
                java.io.File file = new java.io.File(dir, base.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
                send.setType("application/json");
                send.putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(getContext(),
                        org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", file));
                send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fragment.getParentActivity().startActivity(android.content.Intent.createChooser(send, "Export " + info.title));
            } catch (Exception e) {
                android.widget.Toast.makeText(getContext(), "Could not export: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDelete(final ThemeInfo info) {
        new android.app.AlertDialog.Builder(fragment.getParentActivity()).setTitle("Delete \"" + info.title + "\"?")
                .setMessage("It disappears from the store. People who already applied it keep it.")
                .setPositiveButton("Delete", (d, w) -> ThemePublisher.remove(fragment.getCurrentAccount(), info, new ThemePublisher.Callback() {
                    @Override
                    public void done(String warning) {
                        ProfileTabView.notifyPackUploaded();
                        CACHE.remove(ownerId);
                        reload();
                    }

                    @Override
                    public void failed(String message) {
                        android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_LONG).show();
                    }
                })).setNegativeButton("Cancel", null).show();
    }

    private static final class ThemeStoreApiSort {
        static final String NEWEST = "newest";
    }
}
