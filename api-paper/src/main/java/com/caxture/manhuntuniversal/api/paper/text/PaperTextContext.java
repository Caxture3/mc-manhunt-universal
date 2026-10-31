package com.caxture.manhuntuniversal.api.paper.text;
import com.caxture.manhuntuniversal.api.text.TextContext;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
/** The normal fallback already includes requested prefix and expanded placeholders. */
public record PaperTextContext(TextContext text, CommandSender recipient, boolean includePrefix, Component fallback) { }
