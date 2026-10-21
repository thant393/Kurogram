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
    private final RadialProgressView progressView;
    private final TextView infoView;
    private final ArrayList<ThemeInfo> themes = new ArrayList<>();
    private final HashSet<String> seen = new HashSet<>();
    private String query = "";
    private int nextPage = 1;
    private boolean loading;
    private boolean hasMore = true;
    private int generation;
    private final Runnable searchRunnable = this::reload;

    public ThemesTabView(Context context, BaseFragment fragment, long ownerId, String emptyText) {
        super(context);
        this.fragment = fragment;
        this.ownerId = ownerId;
        this.emptyText = emptyText;

        layoutManager = new GridLayoutManager(context, 2);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager);
        listView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(16));
        listView.setClipToPadding(false);
        listView.setSelectorDrawableColor(0);
        adapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public int getItemCount() {
                return themes.size();
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                ThemeCardView card = new ThemeCardView(parent.getContext());
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
                lp.setMargins(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
                card.setLayoutParams(lp);
                return new RecyclerListView.Holder(card);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((ThemeCardView) holder.itemView).setTheme(themes.get(position));
            }
        };
        listView.setAdapter(adapter);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < themes.size() && fragment.getParentActivity() != null) {
                fragment.presentFragment(new ThemeDetailActivity(themes.get(position)));
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
        if (ownerId <= 0 && query.isEmpty()) {
            themes.addAll(BuiltInThemes.all());
        }
        nextPage = 1;
        hasMore = true;
        loading = false;
        adapter.notifyDataSetChanged();
        infoView.setVisibility(GONE);
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
        ThemeStoreApi.fetchThemes(ThemeStoreApiSort.NEWEST, nextPage, PAGE_SIZE, ownerId > 0 ? String.valueOf(ownerId) : null, query, new ThemeStoreApi.ListCallback() {
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
                progressView.setVisibility(GONE);
                adapter.notifyDataSetChanged();
                if (themes.isEmpty()) {
                    infoView.setText(query.isEmpty() ? emptyText : "No themes match \"" + query + "\"");
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
                if (themes.isEmpty()) {
                    infoView.setText(message);
                    infoView.setVisibility(VISIBLE);
                }
            }
        });
    }

    private static final class ThemeStoreApiSort {
        static final String NEWEST = "newest";
    }
}
