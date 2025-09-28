package net.limitmedia.pong.core.localization;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class LocalizationService {
    private ResourceBundle bundle;

    public LocalizationService(Locale locale) {
        changeLocale(locale);
    }

    public void changeLocale(Locale locale) {
        Locale target = locale == null ? Locale.ENGLISH : locale;
        bundle = ResourceBundle.getBundle("i18n.messages", target);
    }

    public String translate(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException ex) {
            return key;
        }
    }
}
