package com.hexvane.aetherhaven.speech;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.util.MessageUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves {@link Message} trees to plain text for dialogue speech blips (player language when possible). */
public final class DialogueMessagePlainText {
    private DialogueMessagePlainText() {}

    @Nonnull
    public static String resolve(@Nonnull Message message, @Nullable String language) {
        String lang = language != null && !language.isBlank() ? language.trim() : I18nModule.DEFAULT_LANGUAGE;
        String plain = format(message.getFormattedMessage(), lang);
        return plain != null ? plain : "";
    }

    @Nullable
    private static String format(@Nullable FormattedMessage msg, @Nonnull String language) {
        if (msg == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (msg.rawText != null) {
            sb.append(msg.rawText);
        } else if (msg.messageId != null) {
            try {
                I18nModule i18n = I18nModule.get();
                String template = i18n != null ? i18n.getMessage(language, msg.messageId) : null;
                if (template == null && i18n != null && !I18nModule.DEFAULT_LANGUAGE.equals(language)) {
                    template = i18n.getMessage(I18nModule.DEFAULT_LANGUAGE, msg.messageId);
                }
                sb.append(template != null ? MessageUtil.formatText(template, msg.params, msg.messageParams) : msg.messageId);
            } catch (Exception e) {
                sb.append(msg.messageId);
            }
        }
        if (msg.children != null) {
            for (FormattedMessage child : msg.children) {
                String childText = format(child, language);
                if (childText != null) {
                    sb.append(childText);
                }
            }
        }
        return sb.toString();
    }
}
