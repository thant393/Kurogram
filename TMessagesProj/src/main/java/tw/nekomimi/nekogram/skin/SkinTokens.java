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

    // layer 2: shared cells and section headers (schemaMinor 1). -1 / 0 = not set: the cell keeps Telegram's own value.
    // Only cells that opted in (useSkinTokens) read these; see KuroUi.
    public final int cellHeightDp, cellTitleSizeSp, cellValueSizeSp, cellDividerInsetDp;
    /** -1 not set, 0 regular, 1 medium, 2 bold. Telegram has one emphasised weight, so 1 and 2 look the same. */
    public final int cellTitleWeight;
    /** -1 not set, 0 hidden, 1 shown. */
    public final int cellDividerVisible;
    public final int cellDividerColor;
    public final int sectionHeaderSizeSp, sectionHeaderWeight, sectionHeaderColor;
    public final int cellTwoLineHeightDp, sectionInfoSizeSp;
    /** components.cell.paddingStartDp / iconSizeDp: -1 = not set. pressedColor: 0 = not set (the list keeps Telegram's own selector). */
    /** components.fab.sizeDp / radiusDp for the Chats compose button; -1 = not set. */
    public final int fabSizeDp, fabRadiusDp;
    /** Theme Store: screens.themeStore.previewAspect (-1 not set, 0 square, 1 portrait, 2 tall), cardRadiusDp / cardGapDp / titleSizeSp (-1 = not set). */
    /** screens.profile.cardRadiusDp when the skin sets it, else -1 (ProfileActivity then keeps Telegram's own card radius). */
    public final int profileSectionRadiusDp;
    public final int storePreviewAspect, storeCardRadiusDp, storeCardGapDp, storeTitleSizeSp;
    /** components.button.style: -1 not set, 0 filled, 1 outlined, 2 text. Only the Theme Store buttons read it. */
    public final int buttonStyle;
    /** ux.animationScale: -1 = not set. ux.ripple: -1 not set, 0 off, 1 on. */
    public final float uxAnimationScale;
    public final int uxRipple;
    /** ux.transition: -1 not set, 0 default, 1 fade, 2 slide, 3 none. */
    public final int uxTransition;
    /** components.search.*: 0 / -1 = not set. Only SearchField instances that opted in read these. */
    public final int searchBackground, searchHintColor, searchIconColor, searchRadiusDp;
    public final int cellPaddingStartDp, cellIconSizeDp, cellPressedColor;
    /** components.section.style: -1 not set, 0 flat (full-width, square), 1 card. Radius/gap: -1 = Telegram's own (16 / 12 dp). */
    public final int sectionStyle, sectionRadiusDp, sectionGapDp;
    /** screens.<id>.background for the row-list settings screens; 0 = not set (the screen keeps the theme's own background). */
    public final int screenBgPrivacy, screenBgNotifications, screenBgDataStorage, screenBgDevices;
    /** components.session.*; -1 = not set. */
    public final int sessionAvatarRadiusDp, sessionNameSizeSp;
    /** components.switch.*; 0 = not set (the switch keeps the theme's own colours). */
    public final int switchTrackOn, switchTrackOff, switchThumbOn, switchThumbOff;

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

    private static int weight(String w) {
        return "regular".equals(w) ? 0 : "medium".equals(w) ? 1 : "bold".equals(w) ? 2 : -1;
    }

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

        cellHeightDp = s.intValue("components.cell.heightDp", -1);
        cellTitleSizeSp = s.intValue("components.cell.titleSizeSp", -1);
        cellValueSizeSp = s.intValue("components.cell.valueSizeSp", -1);
        cellDividerInsetDp = s.intValue("components.cell.dividerInsetDp", -1);
        cellTitleWeight = weight(s.stringValue("components.cell.titleWeight", null));
        cellDividerVisible = s.has("components.cell.dividerVisible") ? (s.boolValue("components.cell.dividerVisible", true) ? 1 : 0) : -1;
        cellDividerColor = s.colorValue("components.cell.dividerColor");
        sectionHeaderSizeSp = s.intValue("components.section.headerSizeSp", -1);
        sectionHeaderWeight = weight(s.stringValue("components.section.headerWeight", null));
        sectionHeaderColor = s.colorValue("components.section.headerColor");
        cellTwoLineHeightDp = s.intValue("components.cell.twoLineHeightDp", -1);
        sectionInfoSizeSp = s.intValue("components.section.infoSizeSp", -1);
        searchBackground = s.colorValue("components.search.background");
        searchHintColor = s.colorValue("components.search.hintColor");
        searchIconColor = s.colorValue("components.search.iconColor");
        searchRadiusDp = s.intValue("components.search.radiusDp", -1);
        uxAnimationScale = s.has("ux.animationScale") ? Math.max(0f, s.floatValue("ux.animationScale", 1f)) : -1f;
        String uxTransitionName = s.stringValue("ux.transition", null);
        uxTransition = "default".equals(uxTransitionName) ? 0 : "fade".equals(uxTransitionName) ? 1 : "slide".equals(uxTransitionName) ? 2 : "none".equals(uxTransitionName) ? 3 : -1;
        uxRipple = s.has("ux.ripple") ? (s.boolValue("ux.ripple", true) ? 1 : 0) : -1;
        profileSectionRadiusDp = s.intValue("screens.profile.cardRadiusDp", -1);
        String aspectName = s.stringValue("screens.themeStore.previewAspect", null);
        storePreviewAspect = "square".equals(aspectName) ? 0 : "portrait".equals(aspectName) ? 1 : "tall".equals(aspectName) ? 2 : -1;
        storeCardRadiusDp = s.intValue("screens.themeStore.cardRadiusDp", -1);
        storeCardGapDp = s.intValue("screens.themeStore.cardGapDp", -1);
        storeTitleSizeSp = s.intValue("screens.themeStore.titleSizeSp", -1);
        String buttonStyleName = s.stringValue("components.button.style", null);
        buttonStyle = "filled".equals(buttonStyleName) ? 0 : "outlined".equals(buttonStyleName) ? 1 : "text".equals(buttonStyleName) ? 2 : -1;
        fabSizeDp = s.intValue("components.fab.sizeDp", -1);
        fabRadiusDp = s.intValue("components.fab.radiusDp", -1);
        cellPaddingStartDp = s.intValue("components.cell.paddingStartDp", -1);
        cellIconSizeDp = s.intValue("components.cell.iconSizeDp", -1);
        cellPressedColor = s.colorValue("components.cell.pressedColor");
        String sectionStyleName = s.stringValue("components.section.style", null);
        sectionStyle = "flat".equals(sectionStyleName) ? 0 : "card".equals(sectionStyleName) ? 1 : -1;
        sectionRadiusDp = s.intValue("components.section.radiusDp", -1);
        sectionGapDp = s.intValue("components.section.gapDp", -1);
        screenBgPrivacy = s.colorValue("screens.privacy.background");
        screenBgNotifications = s.colorValue("screens.notifications.background");
        screenBgDataStorage = s.colorValue("screens.dataStorage.background");
        screenBgDevices = s.colorValue("screens.devices.background");
        sessionAvatarRadiusDp = s.intValue("components.session.avatarRadiusDp", -1);
        sessionNameSizeSp = s.intValue("components.session.nameSizeSp", -1);
        switchTrackOn = s.colorValue("components.switch.trackOn");
        switchTrackOff = s.colorValue("components.switch.trackOff");
        switchThumbOn = s.colorValue("components.switch.thumbOn");
        switchThumbOff = s.colorValue("components.switch.thumbOff");

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
