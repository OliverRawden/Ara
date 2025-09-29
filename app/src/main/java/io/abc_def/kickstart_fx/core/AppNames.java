package io.abc_def.kickstart_fx.core;

public abstract class AppNames {

    public static AppNames ofMain() {
        return new Main();
    }

    public static AppNames ofCurrent() {
        // TODO: If you are using multiple different distributions
        // e.g. a release build and a test/beta build, you can implement custom naming here
        return new Main();
    }

    public static String propertyName(String name) {
        return ofCurrent().getGroupName() + "." + ofCurrent().getArtifactName() + "." + name;
    }

    public static String packageName() {
        return packageName(null);
    }

    public static String packageName(String name) {
        return ofCurrent().getGroupName() + "." + ofCurrent().getArtifactName() + (name != null ? "." + name : "");
    }

    public abstract String getName();

    public abstract String getKebapName();

    public abstract String getSnakeName();

    public abstract String getUppercaseName();

    public abstract String getGroupName();

    public abstract String getArtifactName();

    public abstract String getExecutableName();

    public abstract String getDistName();

    private static class Main extends AppNames {

        @Override
        public String getName() {
            return "Kickstart FX";
        }

        @Override
        public String getKebapName() {
            return "kickstart-fx";
        }

        @Override
        public String getSnakeName() {
            return "kickstart_fx";
        }

        @Override
        public String getUppercaseName() {
            return "KICKSTART_FX";
        }

        @Override
        public String getGroupName() {
            return "io.abc_def";
        }

        @Override
        public String getArtifactName() {
            return "kickstart_fx";
        }

        @Override
        public String getExecutableName() {
            return "kickstartfx";
        }

        @Override
        public String getDistName() {
            return "kickstartfx";
        }
    }
}
