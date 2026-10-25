package com.example.dripclient;

public abstract class Setting {
    public final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public abstract String save();

    public abstract void load(String s);

    public static class Bool extends Setting {
        public boolean value;

        public Bool(String name, boolean def) {
            super(name);
            this.value = def;
        }

        @Override
        public String save() {
            return String.valueOf(value);
        }

        @Override
        public void load(String s) {
            value = Boolean.parseBoolean(s);
        }
    }

    public static class Num extends Setting {
        public double value;
        public final double min, max, step;

        public Num(String name, double def, double min, double max, double step) {
            super(name);
            this.value = def;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public void set(double v) {
            v = Math.round(v / step) * step;
            value = Math.max(min, Math.min(max, v));
        }

        @Override
        public String save() {
            return String.valueOf(value);
        }

        @Override
        public void load(String s) {
            try {
                set(Double.parseDouble(s));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** Przycisk w ustawieniach modulu (np. "Zapisz uklad"). */
    public static class Action extends Setting {
        public final Runnable run;

        public Action(String name, Runnable run) {
            super(name);
            this.run = run;
        }

        @Override
        public String save() {
            return "";
        }

        @Override
        public void load(String s) {
        }
    }

    /** Przypisanie klawisza z klawiatury (-1 = brak). */
    public static class Key extends Setting {
        public int code;

        public Key(String name, int def) {
            super(name);
            this.code = def;
        }

        @Override
        public String save() {
            return String.valueOf(code);
        }

        @Override
        public void load(String s) {
            try {
                code = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
