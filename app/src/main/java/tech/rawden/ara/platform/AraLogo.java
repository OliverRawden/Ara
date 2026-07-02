package tech.rawden.ara.platform;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class AraLogo {

    private static final String LOGO_PATH = "/tech/rawden/ara/resources/img/icon/logo_sidebar.png";

    private AraLogo() {}

    public static Node createNode(double size) {
        var url = AraLogo.class.getResource(LOGO_PATH);
        if (url == null) {
            return new javafx.scene.layout.Region();
        }
        var image = new Image(url.toExternalForm());
        var view = new ImageView(image);
        view.setFitHeight(size);
        view.setSmooth(true);
        view.setPreserveRatio(true);
        return view;
    }
}
