package tw.kurocore.kurogram.skin;

/**
 * A row cell that can drop its bottom divider when it is the last row of a card. Used when a layout hides
 * rows (RowMap), so the row left at the end of a card does not keep the divider of the row that followed it.
 */
public interface LastInCard {
    void setLastInCard(boolean last);
}
