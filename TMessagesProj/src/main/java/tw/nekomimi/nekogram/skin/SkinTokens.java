package tw.nekomimi.nekogram.skin;

/**
 * The active skin resolved ONCE into plain final fields, so screens never look values up by
 * string path while binding or drawing (a typo is a compile error, not a silently missing value).
 *
 * Colours are 0xAARRGGBB; 0 means "the skin doesn't set it" and the caller keeps Telegram's own
 * colour (see KuroUi for the fallbacks). Sizes are dp; -1 means "not set".
 * An instance never changes. A new skin produces a new instance with a higher version.
 */
public final class SkinTokens {

    /** Increases every time a different skin (or preview) becomes active; caches compare against it. */
    public final int version;
    public final String skinId;

    // layer 1: colour tokens
    public final int accent, onAccent, background, surface, surfaceElevated;
    public final int textPrimary, textSecondary, divider, icon, error, success;

    // layer 1: page gradient (0 = not set); middle is optional
    public final int gradientTop, gradientMiddle, gradientBottom;
    public final String gradientDirection;

    // layer 1: shapes / elevation
    public final int cardRadiusDp, buttonRadiusDp, dialogRadiusDp, bubbleRadiusDp;
    public final int inputRadiusDp;      // -1 = not set
    public final int titleSizeSp, bodySizeSp, captionSizeSp; // -1 = not set
    public final int cardElevationDp;

    // layer 2: components
    public final int cardBackground, cardBorderColor;
    public final int cardBorderWidthDp;  // -1 = not set (use the default hairline)
    public final int buttonBackground, buttonForeground;
    public final int buttonHeightDp;     // -1 = not set
    public final int inputBackground, inputForeground, inputHint;
    public final int appBarBackground, appBarForeground;
    public final int dialogBackground, dialogScrim;
    public final int bottomBarBackground;

    // layer 3: screens
    public final int chatsBackground, contactsBackground, profileBackground, settingsBackground, themeStoreBackground;
    public final int profileAvatarDp, profileCardRadiusDp;
    public final int gridColumns;
    public final int previewRadiusDp;
    /** Which built-in layout pack draws the screens: "classic" (Telegram's own) for now. */
    public final String layout;
    public final boolean flatLists;      // listStyle == "flat"
    public final boolean showDividers;

    // layer 4: effects (read by the settings that already honour them)
    public final boolean blur, glass, animations, reducedMotion;

    SkinTokens(SkinSpec s, int version) {
        this.version = version;
        this.skinId = s.id;

        accent = s.accentColor;
        onAccent = s.colorValue("tokens.colors.onAccent");
        background = s.colorValue("tokens.colors.background");
        surface = s.colorValue("tokens.colors.surface");
        surfaceElevated = s.colorValue("tokens.colors.surfaceElevated");
        gradientTop = s.colorValue("tokens.gradient.top");
        gradientMiddle = s.colorValue("tokens.gradient.middle");
        gradientBottom = s.colorValue("tokens.gradient.bottom");
        gradientDirection = s.stringValue("tokens.gradient.direction", "vertical");
        textPrimary = s.colorValue("tokens.colors.textPrimary");
        textSecondary = s.colorValue("tokens.colors.textSecondary");
        divider = s.colorValue("tokens.colors.divider");
        icon = s.colorValue("tokens.colors.icon");
        error = s.colorValue("tokens.colors.error");
        success = s.colorValue("tokens.colors.success");

        cardRadiusDp = s.cardRadiusDp;
        dialogRadiusDp = s.dialogRadiusDp;
        buttonRadiusDp = s.intValue("tokens.shapes.buttonRadiusDp", 26);
        bubbleRadiusDp = s.bubbleRadius != null ? s.bubbleRadius : -1;
        inputRadiusDp = s.intValue("tokens.shapes.inputRadiusDp", -1);
        titleSizeSp = s.intValue("tokens.typography.titleSizeSp", -1);
        bodySizeSp = s.intValue("tokens.typography.bodySizeSp", -1);
        captionSizeSp = s.intValue("tokens.typography.captionSizeSp", -1);
        cardElevationDp = s.intValue("tokens.elevation.cardDp", 2);

        cardBackground = s.colorValue("components.card.background");
        cardBorderColor = s.colorValue("components.card.borderColor");
        cardBorderWidthDp = s.intValue("components.card.borderWidthDp", -1);
        buttonBackground = s.colorValue("components.button.background");
        buttonForeground = s.colorValue("components.button.foreground");
        buttonHeightDp = s.intValue("components.button.heightDp", -1);
        inputBackground = s.colorValue("components.input.background");
        inputForeground = s.colorValue("components.input.foreground");
        inputHint = s.colorValue("components.input.hint");
        appBarBackground = s.colorValue("components.appBar.background");
        appBarForeground = s.colorValue("components.appBar.foreground");
        dialogBackground = s.colorValue("components.dialog.background");
        bottomBarBackground = s.colorValue("components.bottomNavigation.background");
        dialogScrim = s.colorValue("components.dialog.scrim");

        chatsBackground = s.colorValue("screens.chats.background");
        contactsBackground = s.colorValue("screens.contacts.background");
        profileBackground = s.colorValue("screens.profile.background");
        settingsBackground = s.colorValue("screens.settings.background");
        themeStoreBackground = s.colorValue("screens.themeStore.background");
        profileAvatarDp = s.intValue("screens.profile.avatarSizeDp", 100);
        profileCardRadiusDp = s.intValue("screens.profile.cardRadiusDp", s.cardRadiusDp);
        gridColumns = s.gridColumns;
        previewRadiusDp = s.intValue("screens.themeStore.previewRadiusDp", 14);
        layout = s.stringValue("screens.chats.layout", "classic");
        flatLists = SkinSpec.LIST_FLAT.equals(s.listStyle);
        showDividers = s.boolValue("screens.chats.showDividers", true);

        blur = s.boolValue("effects.blur", false);
        glass = s.boolValue("effects.glass", false);
        animations = s.boolValue("effects.animations", true);
        reducedMotion = s.boolValue("effects.reducedMotion", false);
    }
}
