package com.example.dripclient;

import com.example.dripclient.modules.AimAssistModule;
import com.example.dripclient.modules.AntiKostkaModule;
import com.example.dripclient.modules.AntiTrapModule;
import com.example.dripclient.modules.AutoLeverModule;
import com.example.dripclient.modules.BetterPaintingModule;
import com.example.dripclient.modules.ErrorKillerModule;
import com.example.dripclient.modules.HitboxModule;
import com.example.dripclient.modules.NameTagModule;
import com.example.dripclient.modules.TracerModule;
import com.example.dripclient.modules.TriggerBotModule;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class ModuleManager {
    public static final List<Module> MODULES = new ArrayList<>();
    public static final Set<String> FRIENDS = new LinkedHashSet<>();

    private ModuleManager() {
    }

    public static void init() {
        MODULES.add(new NameTagModule());
        MODULES.add(new TracerModule());
        MODULES.add(new HitboxModule());
        MODULES.add(new AimAssistModule());
        MODULES.add(new TriggerBotModule());
        MODULES.add(new AntiKostkaModule());
        MODULES.add(new BetterPaintingModule());
        MODULES.add(new AutoLeverModule());
        MODULES.add(new ErrorKillerModule());
        MODULES.add(new AntiTrapModule());
        load();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> type) {
        for (Module m : MODULES) if (type.isInstance(m)) return (T) m;
        return null;
    }

    public static boolean isFriend(String name) {
        return FRIENDS.contains(name.toLowerCase(Locale.ROOT));
    }

    public static void addFriend(String name) {
        if (name != null && !name.isBlank()) FRIENDS.add(name.trim().toLowerCase(Locale.ROOT));
    }

    public static void removeFriend(String name) {
        FRIENDS.remove(name.toLowerCase(Locale.ROOT));
    }

    /** Zwraca true, jesli dodano, false jesli usunieto. */
    public static boolean toggleFriend(String name) {
        if (isFriend(name)) {
            removeFriend(name);
            return false;
        }
        addFriend(name);
        return true;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dripclient.properties");
    }

    public static void save() {
        Properties p = new Properties();
        for (Module m : MODULES) {
            p.setProperty("mod." + m.name + ".enabled", String.valueOf(m.enabled));
            for (Setting s : m.settings) {
                p.setProperty("mod." + m.name + "." + s.name, s.save());
            }
            m.saveExtra(p);
        }
        p.setProperty("friends", String.join(",", FRIENDS));
        UiConfig.save(p);
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Mojang");
        } catch (IOException ignored) {
        }
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        for (Module m : MODULES) {
            m.enabled = Boolean.parseBoolean(p.getProperty("mod." + m.name + ".enabled", "false"));
            for (Setting s : m.settings) {
                String v = p.getProperty("mod." + m.name + "." + s.name);
                if (v != null) s.load(v);
            }
            m.loadExtra(p);
        }
        UiConfig.load(p);
        String fr = p.getProperty("friends", "");
        for (String n : fr.split(",")) addFriend(n);
    }
}
