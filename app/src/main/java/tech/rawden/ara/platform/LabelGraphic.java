package tech.rawden.ara.platform;

import javafx.scene.Node;

import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Supplier;

public abstract class LabelGraphic {

    public static LabelGraphic none() {
        return new LabelGraphic() {
            @Override
            public Node createGraphicNode() {
                return null;
            }
        };
    }

    public abstract Node createGraphicNode();

    public static class IconGraphic extends LabelGraphic {
        private final String icon;

        public IconGraphic(String icon) {
            this.icon = icon;
        }

        public String getIcon() {
            return icon;
        }

        @Override
        public Node createGraphicNode() {
            var fi = new FontIcon(icon);
            fi.getStyleClass().add("graphic");
            return fi;
        }
    }

    public static class NodeGraphic extends LabelGraphic {
        private final Supplier<Node> node;

        public NodeGraphic(Supplier<Node> node) {
            this.node = node;
        }

        @Override
        public Node createGraphicNode() {
            var n = node.get();
            if (n != null) n.getStyleClass().add("graphic");
            return n;
        }
    }
}
